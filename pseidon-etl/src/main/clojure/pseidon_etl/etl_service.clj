(ns pseidon-etl.etl-service
  (:gen-class)
  (:import
    [java.util.concurrent Executors ExecutorService Callable]
    [java.net InetAddress]
    (com.codahale.metrics MetricRegistry Meter JmxReporter)
    (java.util.concurrent.atomic AtomicLong AtomicBoolean))
  (:require [thread-load.core :as load]
            [fun-utils.queue :as futils-queue]
            [pseidon-etl.memory :as memory]
            [clojure.core.async :as async :refer [<!!]]
            [pseidon-etl.writer :as writer]
            [pseidon-etl.util :as util]
            [kafka-clj.client :refer [create-client-service send-msg get-metadata-error-ch]]
            [kafka-clj.consumer.node :refer [read-msg! buffered-msgs] :as node]
            [kafka-clj.consumer.work-organiser :refer [get-saved-offset]]
            [kafka-clj.msg-persist :refer [write-to-retry-cache]]
            [kafka-clj.produce :refer [message]]
            [pseidon-etl.convert :refer [msg->json json->bts]]
            [pseidon-etl.mon :refer [register]]
            [pseidon-etl.errors :as errors]
            [pseidon-etl.topic-service :as topic-service]
            [fun-utils.core :as futils :refer [go-seq thread-seq]]
            [clojure.tools.logging :refer [info error]]
            [com.stuartsierra.component :as component]
            [pseidon-etl.formats :as formats]

    ;;;import for formats multi methods
            [pseidon-etl.avro.avro-format]
            ))

(defonce ETL-VERSION "0.1.0")

;note this must be a function otherwise the available processors will be read at compile time
(defn cpu-count [] (.availableProcessors (Runtime/getRuntime)))

(def host-name (.getHostName (InetAddress/getLocalHost)))


(defn- mark-meter [^Meter meter ^long n]
  (.mark meter n))

(defn- default-init [state]
  (fn [& _]
    (assoc state :status :ok)))

(defn- default-terminate [state & _] (assoc state :status :terminate))

(defn _write-msg
  "Msg can be an object or a byte array, the object is parsed to a json string and then the bytes are extracted"
  ([state topic msg]
    ;;msg must have type formats/FormatMsg
   (try
     (let [output-format (topic-service/get-output-format topic (:conf state) (:db state))
           codec (condp = (name output-format)
                   "parquet" :parquet
                   :gzip)]

       ;note that only messages with a none nil node and batch-ts will be written to the etl map log
       (writer/multi-write (:writer-ctx state) (writer/wrap-msg topic msg codec)))
     (catch Exception e
       (error e e)))))

(defn exec-etl
  "For each message or messages run the do-etl-work!
   Binds the etl configuration to (:conf state)"
  [req-metric state {:keys [topic bts]}]
  (mark-meter req-metric 1)
  (let [conf (:conf state)
        format (topic-service/get-format topic (:format-state state) conf (:db state))]
    (_write-msg state topic (formats/bts->msg conf topic format bts))))

(defn- metric->map [^Meter timer]
  {:count            (.getCount timer)
   :five-minute-rate (.getFiveMinuteRate timer)
   :mean-rate        (.getMeanRate timer)
   :one-minute-rate  (.getOneMinuteRate timer)})

(defn- publisher
  "Read messages from kafka and sends it to the pool
   See exec-write"
  [{:keys [node]} pool topic-status]
  (let [^AtomicLong counter (AtomicLong. 0)]
    (fn []
      (while (not (Thread/interrupted))
        ;loop forever till interrupted
        (try
          (loop [msg (read-msg! node)]
            (when msg
              (load/publish! pool msg)
              (recur (read-msg! node))))
          (catch InterruptedException _ (-> (Thread/currentThread) (.interrupt)))
          (catch Exception e (error e e)))))))

(defn- shutdown-all [component]
  (util/wait-zero-activity! "etl" (get-in component [:etl-service :activity-counter]))

  (.shutdownNow ^ExecutorService (get-in component [:etl-service :exec-service]))
  (load/shutdown-pool (get-in component [:etl-service :pool]) 10000)

  ;;signal the etl part has been shutdown
  (.set ^AtomicBoolean (get-in component [:etl-service :shutdown-flag]) true)
  (Thread/sleep 1000)

  (.stop ^JmxReporter (get-in component [:etl-service :jmx-metrics-reporter]))

  (util/wait-zero-activity! "writer" (get-in component [:etl-service :writer-activity-counter]))

  (writer/multi-close (get-in component [:etl-service :writer-ctx]))
  (util/wait-till-no-files (get-in component [:conf :data-dir] "/tmp/"))
  (dissoc component :etl-service))

(defrecord ETLService [conf db topic-service kafka-node kafka-client writer-service monitor-service]
  component/Lifecycle

  (start [component]

    ;;;;; wait for topic-service to complete loading
    (info "Waiting for topic-service to complete loading ")

    @(:started-promise topic-service)

    (info "Using conf " (:conf component))

    (assert (:writer-ctx writer-service))
    (assert (:callback-f-ref writer-service))
    (assert (:activity-counter writer-service))

    (try
      (if (:etl-service component)
        component
        (let [
              ;preload all etl libraries, if any connection failures that should happen here
              topic-errors-ref (ref {})                     ;keep track of errors per topic
              shutdown-flag (AtomicBoolean. false)


              topic-status (atom {})
              pool (load/create-pool)                       ;queue-limit queue-type thread-pool
              threads (get conf :etl-threads 2)
              exec-service (Executors/newSingleThreadExecutor)

              writer-ctx (:writer-ctx writer-service)

              state (assoc component
                      :format-state (atom {})
                      :conf conf
                      :db db
                      :writer-ctx writer-ctx
                      :shutdown-flag shutdown-flag)

              ^MetricRegistry metric-registry (MetricRegistry.)
              req-metric (.meter metric-registry "pseidon-etl-req-p/s")

              ^AtomicLong activity-counter (:activity-counter writer-service)
              exec-f1 (fn [state msg]
                        (when msg
                          (.incrementAndGet activity-counter)
                          (exec-etl req-metric state msg)))

              exec-f (errors/handle-errors->fn db topic-errors-ref (get-in component [:conf :etl-error-threshold] 100) exec-f1)

              ;; topics are added and removed via the pseidon-etl.topic-service
              ^Callable publisher-f (publisher (:kafka-node component) pool topic-status)]

          ;;listen on writer-buffer and for each batch of messages
          ;;write to the writer-ctx which will write to local disk
          (go-seq
            (fn [_]
              (error (str "Error in getting metadata, failing application")))
            (get-metadata-error-ch (get-in component [:kafka-client :client])))

          (when monitor-service

            (register monitor-service :etl-service-thread-queue
                      (fn [] (futils-queue/size (:queue pool))))
            (register monitor-service :etl-service-thread-metrics
                      (fn [] (metric->map req-metric)))

            (register monitor-service :version
                      (fn [] ETL-VERSION))

            (register monitor-service :memory
                      (fn []
                        {:direct (memory/buffer-pool-stats)
                         :heap   (memory/memory-stats)}))

            ;; shows the kafka consumer redis pool active/incative/total byte size
            (register monitor-service :kafka-redis
                      (fn [] {:active    (node/conn-pool-active (:node kafka-node))
                              :idle      (node/conn-pool-idle (:node kafka-node))
                              :byte-size (node/conn-pool-byte-size (:node kafka-node))}))

            (register monitor-service :kafka-consumer
                      (fn [] (node/node-stats (:node kafka-node)))))

          (dotimes [_ threads]
            (info "add etl consumer!")
            (load/add-consumer
              pool
              (default-init state)
              exec-f
              default-terminate))

          (.submit exec-service publisher-f)
          (assoc component :etl-service {:pool                    pool
                                         :jmx-metrics-reporter    (.build (JmxReporter/forRegistry metric-registry))

                                         :exec-service            exec-service
                                         :shutdown-flag           shutdown-flag
                                         :activity-counter        activity-counter
                                         :writer-activity-counter (:activity-counter writer-service)
                                         :writer-ctx              writer-ctx})))
      (catch Exception e (do
                           (error ">>>>>>>>>>>>>> Fatal start error")
                           (error e e)
                           (throw e)))))

  (stop [component]
    (when (:etl-service component)
      (try
        (shutdown-all component)
        (catch Exception e (do
                             (error e e)
                             component)))
      component)))

(defn create-etl-service
  "Return a non started ETLService component
   Use component/start to initialize"
  [conf]
  (->ETLService conf nil nil nil nil nil nil))