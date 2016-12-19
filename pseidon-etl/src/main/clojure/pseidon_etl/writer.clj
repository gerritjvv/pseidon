(ns pseidon-etl.writer
  (:require
    [pseidon-etl.util :as util]
    [fileape.core :as ape-core]
    [fileape.parquet.writer :as ape-parquet-writer]
    [clojure.tools.logging :refer [error warn info debug fatal]]
    [fun-utils.core :as futils]
    [clj-time.coerce :as time-c]
    [clojure.core.async :as async]
    [clj-tuple :as tuple]
    [clj-json.core :as clj-json]
    [com.stuartsierra.component :as component]
    [clojure.string :as string]
    [pseidon-etl.formats :as formats])
  (:import
    (java.io DataOutputStream File)
    (org.joda.time DateTime)
    (java.nio.charset StandardCharsets)
    (java.util.concurrent.atomic AtomicLong)
    (pseidon_etl Util)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;; Global variables Records and Schemas

(defonce ^:private ^"[B" new-line-bts (.getBytes "\n"))
(defonce ^:private byte-array-class (Class/forName "[B"))

(deftype WriterMetricsKey [node topic file-name])

;msg should be the json message
(defrecord TopicMsg [^String topic msg codec])
(defn wrap-msg
  "topic:
   msg: foramts/FormatMsg
   codec: gzip, parquet"
  ([topic msg] (wrap-msg topic msg nil))
  ([topic msg codec] (->TopicMsg topic msg codec)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;; Private functions

(set! *unchecked-math* true)

(defn- padd-zero [^long n] (Util/paddZero n))

(defn get-local-dt-hr-str
  ([^long ts]
   (get-local-dt-hr-str (StringBuilder.) ts))
  ([^StringBuilder buff ^long ts]
   (let [^DateTime dt (time-c/from-long ts)
         y (padd-zero (.getYear dt))
         m (padd-zero (.getMonthOfYear dt))
         d (padd-zero (.getDayOfMonth dt))
         h (padd-zero (.getHourOfDay dt))]
     (.toString ^StringBuilder
                (doto
                  buff
                  (.append y)
                  (.append \-)
                  (.append m)
                  (.append \-)
                  (.append d)
                  (.append \-)
                  (.append h))))))

(defn msg->topic-datehr
  ([topic ^long ts]
   (let [^StringBuilder buff (StringBuilder.)]

     (.toString ^StringBuilder
                (doto
                  buff
                  (.append topic)
                  (.append \-)
                  (get-local-dt-hr-str ts)))))
  ([{:keys [topic msg]}]
    ;; msg must be of type formats/FormatMsg
   (msg->topic-datehr topic (:ts msg))))

(defn- ^"[B" str->bts [^String s]
  (.getBytes s StandardCharsets/UTF_8))

(defn bytes? [x]
  (= (Class/forName "[B")
     (class x)))

(defn ^"[B" as-bytes [topic-msg]
  (let [msg (:msg topic-msg)]
    (if (instance? byte-array-class msg)
      msg
      (str->bts (clj-json/generate-string msg)))))

;;helper function that deals with nil numbers
(defn _safe-add [a b]
  (if a (+ (long a) (long b)) b))

(defn- file-name [file]
  (.getName ^File (clojure.java.io/file file)))

(set! *unchecked-math* true)


(defn do-msg-write!
  "state: the current system state with keys :kafka-client :kafka-node
   msgs: [msg]: msg is a KafkaTSMsg keys [msg topic node codec], its assumed that all msgs have the same ts and same codec"
  [conf {:keys [writer-ctx flush-on-write]} k msgs]


  (let [codec (:codec (first msgs))
        ctx (if codec (assoc-in writer-ctx [:ctx :conf :codec] codec) writer-ctx)]

    (cond
      (= codec :parquet)

      (ape-core/write-timeout
        ctx k
        (fn [{:keys [parquet]}]
          (doseq [topic-msg msgs]
            (try
              (ape-parquet-writer/write! parquet (:msg topic-msg))
              (catch Exception e (do
                                   (error e (clj-json/generate-string {:topic (:topic topic-msg)
                                                                       :e     (str e)})))))))
        600000)

      :else
      (ape-core/write-timeout
        ctx k
        (fn [{:keys [^DataOutputStream out file] :as m}]

          ;;conf topic format msg
          (doseq [topic-msg msgs]
            (let [
                  format-msg (:msg topic-msg)                    ;;is a TopicMsg{:msg formats/FormatMsg}
                  bts (formats/msg->bts conf
                                        (:topic topic-msg)
                                        (:format format-msg)
                                        (:msg format-msg))]

              (.write out ^"[B" bts)
              (.write out new-line-bts)))
          (when flush-on-write
            (.flush out)))
        600000))))

(defn write-msgs
  "Main entry point, group messages by key and write to fileape
  topic-msgs keys [topic msg] where msg is json/map

  Messages are filtered by valid-ts? and invalid messages are grouped by the key nil"
  [conf writer topic-msgs]
  (try
    (doseq [[k msgs] (group-by msg->topic-datehr topic-msgs)]
      (when k                                               ;ignore key nil
        (do-msg-write! conf writer k msgs)))
    (catch Exception e (util/fatal-error e))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;; Public functions

(defn open-writer
  "open a writer context and return it
   config keys [:codec :check-freq :rollover-size :rollover-abs-timeout :parallel-files :rollover-timeout :roll-callbacks]"
  [conf]
  (info "Open Writer: " conf)

  (let [writer-ctx (ape-core/ape conf)]

    {:writer-ctx     writer-ctx
     :flush-on-write (get conf :flush-on-write false)}))

(defn update-writer-env [{:keys [writer-ctx]} topic & args]
  {:pre [writer-ctx]}
  (apply ape-core/update-env! writer-ctx topic args))

(defn parquet-schema-defined? [{:keys [writer-ctx]} topic]
  {:pre [writer-ctx]}
  (get (ape-core/get-env writer-ctx) topic))

(defn parquet-codec? [{:keys [writer-ctx]}]
  (#{:parquet :r1-parquet} (ape-core/get-codec writer-ctx)))

(defn close-writer [{:keys [writer-ctx]}]
  (ape-core/close writer-ctx))

(defn multi-writer-ctx
  "n is the number of writer contexes that should be created"
  [n ^AtomicLong activity-counter conf]
  (let [writer-chs (vec (take n (repeatedly #(async/chan 100))))
        writer-buffers (mapv #(futils/buffered-chan % 1000 500) writer-chs)
        writer-ctxs (take n (repeatedly #(open-writer conf)))]
    (doall writer-buffers)
    (doall writer-ctxs)

    (doseq [[writer-buffer writer-ctx] (partition 2 (mapcat vector writer-buffers writer-ctxs))]
      (futils/thread-seq
        (fn [json-msgs]
          (.incrementAndGet activity-counter)
          (try
            (let [start (System/currentTimeMillis)]
              (write-msgs conf writer-ctx json-msgs)
              (let [diff (- (System/currentTimeMillis) start)]
                (if (> diff 2000)
                  (info "Took " diff "ms  to write " (count json-msgs)))))
            (catch Exception e
              (util/fatal-error e))))
        writer-buffer))
    {:writer-chs writer-chs :writer-buffers writer-buffers :writer-ctxs writer-ctxs}))


(defn multi-write [{:keys [writer-chs]} msg]
  (async/>!! (rand-nth writer-chs) msg))

(defn multi-write-parquet-schema-defined?
  "True if all writers with codec :parquet have a parquet schema defined"
  [{:keys [writer-ctxs]} topic]
  ;;; note, this would always return nil or false if the pseidon.edn file :codec is not parquet or parquet-1,
  (every? #(parquet-schema-defined? % topic)
          (filter parquet-codec? writer-ctxs)))

(defn multi-update-env
  "Update the environment for all open writers for a particular topic"
  [{:keys [writer-ctxs]} topic & args]
  {:pre [writer-ctxs]}
  (doseq [writer writer-ctxs]
    (apply update-writer-env writer topic args)))

(defn multi-close [{:keys [writer-chs writer-ctxs]}]
  (doseq [writer-ch writer-chs]
    (async/close! writer-ch))
  (try
    ;;ignore close file exceptions
    (doseq [writer-ctx writer-ctxs]
      (close-writer writer-ctx))
    (catch Exception e nil)))


(defn exit-application
  "Used as error-handler to the file-ape api, and will print and exit the application when called"
  [e _]
  (util/fatal-error e))

(defrecord WriterService [conf activity-counter writer-ctx callback-f-ref]
  ;;The writer is closed in the etl-service for legacy reasons,
  component/Lifecycle
  (start [component]
    (if-not (:writer-ctx component)
      (let [
            base-dir (get conf :data-dir "/tmp/")
            activity-counter (AtomicLong. 0)
            callback-f-ref (atom (fn [& _]))

            callback-f (fn [{:keys [^File file ^AtomicLong record-counter]}]
                         (when-let [callback-f @callback-f-ref]
                           (callback-f file record-counter)))

            writer-ctx (multi-writer-ctx (get-in conf [:writer :write-multiplier] 1)
                                         activity-counter
                                         (merge {:codec                :gzip
                                                 :parquet-codec        "gzip"
                                                 :base-dir             base-dir
                                                 :check-freq           2000
                                                 :rollover-size        115000000
                                                 :rollover-abs-timeout 600000
                                                 :parallel-files       1
                                                 :rollover-timeout     600000
                                                 :roll-callbacks       [callback-f]
                                                 :error-handler        exit-application
                                                 :allow-all-ts         false
                                                 :env-key-parser       #(string/replace % #"[\-0-9]{2,3}" "") ;all env keys should only use the topic part e.g keys given are adx-bid-request-yyyy-MM-dd-HH
                                                 }
                                                (:writer conf)))]

        (info "WriterService:init: >>>>>>>>>>>> write-multiplier: " (get-in conf [:writer :write-multiplier] 2))
        (info "WriterService:init: >>>>>>>>>>>>>>>> writer-ctx " (:writer-ctxs writer-ctx))
        (assoc component
          :callback-f-ref callback-f-ref
          :activity-counter activity-counter
          :base-dir base-dir
          :writer-ctx writer-ctx))

      component))
  (stop [{:keys [writer-ctx] :as component}]

    (info ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> [ WriterService stop ] >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>")
    (when-not writer-ctx
      (warn "Stopping writer service, but component doesn't have writer-ctx key: " (str (keys component))))

    (when writer-ctx
      (multi-close writer-ctx))

    (dissoc component :writer-ctx)))


(defn writer-service [conf]
  (->WriterService conf nil nil nil))

