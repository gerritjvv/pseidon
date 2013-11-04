(ns pseidon.core.queue
  (:require [clojure.tools.logging :refer [info error]]
            [pseidon.core.metrics :refer [add-histogram add-gauge update-histogram add-timer measure-time add-meter update-meter]]
            [pseidon.core.chronicle :as chronicle])
  
  (:use pseidon.core.conf)
  (:import 
          [java.util.concurrent ThreadFactory BlockingQueue Callable ThreadPoolExecutor SynchronousQueue TimeUnit ExecutorService ThreadPoolExecutor$CallerRunsPolicy]
          [clojure.lang IFn]
          [java.util Iterator]
          [java.util.concurrent TimeoutException]
          [pseidon.util Bytes Encoder Decoder DefaultEncoder DefaultDecoders])
  )

(def topic-services (ref {}))

;the master threads are daemon threads to allow the jvm to shutdown at any time
(def ^ExecutorService queue-master (java.util.concurrent.Executors/newCachedThreadPool
                                     (reify ThreadFactory
                                       (^Thread newThread [_ ^Runnable r]
                                         (doto (Thread. r) (.setDaemon true))))))

(def exec-timer (add-timer "pseidon.core.queue.exec-timer"))
(def queue-publish-meter (add-meter "pseidon.core.queue.publish-meter"))
(def queue-consume-meter (add-meter "pseidon.core.queue.consume-meter"))


(defn shutdown-threads []
  (.shutdown queue-master)
  (doseq [[_ service] @topic-services]
    (.shutdown service)
    (info "shutdown " service) 
    (if-not (.awaitTermination service 1 TimeUnit/SECONDS)
      (.shutdownNow service))))

;on jvm shutdown we shutdown all threads
(doto (Runtime/getRuntime) (.addShutdownHook (Thread. shutdown-threads)))

(defn- create-exec-service [topic]
  (let [threads (get-conf2 (keyword (str "worker-" topic "-threads")) (get-conf2 :worker-threads (-> (Runtime/getRuntime) .availableProcessors)))]
    (info "Creating thread pool for " topic " limit " threads)
      (doto (ThreadPoolExecutor. 0 threads 60 TimeUnit/SECONDS (SynchronousQueue.))
        (.setRejectedExecutionHandler  (ThreadPoolExecutor$CallerRunsPolicy.)))))
       
    
(defn ^ExecutorService get-exec-service [^String topic]
    (dosync
      (if-let [service (get @topic-services topic)] service
        (get (alter topic-services assoc topic (create-exec-service topic)) topic ))))
  
(defn submit [f]
  "Submits a function to a thread pool"
  (fn [msg]
    (let [^Callable callable (fn[] (try (measure-time exec-timer #(f msg)) 
                                                          (finally (update-meter queue-consume-meter))))
          ^ExecutorService service (get-exec-service (:topic msg))]
     
    (.submit service callable))))

(defprotocol IBlockingChannel 
             (doPut [this e timeout])
             (getIterator [this])
             (getSize [this])
             (close [this])
            
             )

(defrecord BlockingChannelImpl [chronicle]
           IBlockingChannel
           (doPut [this msg timeout]
             (chronicle/offer chronicle msg timeout))
           (getIterator [this ]
                (chronicle/create-iterator chronicle))
           (getSize [this]
             (chronicle/get-size chronicle))
           (close [this]
                (chronicle/close chronicle))
           )

(defn close-channel [^BlockingChannelImpl channel]
  (.close channel))

(defn ^BlockingChannelImpl get-worker-queue [& {:keys [limit buffer] :or {limit (get-conf2 :psedon-queue-limit 100) buffer (get-conf2 :pseidon-queue-buff 100)} }]
  (let [path  (get-conf2 :pseidon-queue-path (str "/tmp/data/pseidonqueue/" (System/currentTimeMillis)))
       segment-limit (get-conf2 :pseidon-queue-segment-limit 1000000)
       
       queue (chronicle/create-queue path limit :buffer buffer :segment-limit segment-limit)
       ]
    (info "Creating queue with path " path)
    (BlockingChannelImpl. queue)))

(defn ^BlockingChannelImpl channel [^String name & {:keys [limit buffer] :or {limit (get-conf2 :psedon-queue-limit 100) buffer (get-conf2 :pseidon-queue-buff 100)} }] 
  (info "Creating channel " name " :psedon-queue-limit " limit " buffer " buffer)
  (let [
        ^BlockingChannelImpl queue (get-worker-queue :limit limit :buffer buffer)]
        (add-gauge (str "pseidon.core.queue." name ".size") #(getSize queue))
        queue))

(defn- consume-messages [^BlockingChannelImpl channel ^IFn f]
    (loop [^Iterator it (.getIterator channel)]
        (while (and (not (.hasNext it)) (not (Thread/interrupted))) (Thread/sleep 100))
        (if it 
          (f (.next it)))
        (recur it)))
      

(defn consume [^BlockingChannelImpl channel f & {:keys [^Decoder decoder] :or {^Decoder decoder DefaultDecoders/BYTES_DECODER} }]
  "Consumes asynchronously from the channel"
  (let [ 
        ^Runnable runnable #(try 
                              (consume-messages channel (comp f (fn [^bytes bts] (.decode decoder bts))))
                              (catch Exception e (error e e)))
                                 ]
        (.submit queue-master runnable)))


(defn publish-bytes [^BlockingChannelImpl channel ^bytes msg & {:keys [timeout] :or {timeout (Integer/MAX_VALUE)}} ]
  (update-meter queue-publish-meter)
  (if-not (doPut channel msg timeout)
    (throw (TimeoutException. (str "publish-bytes timeout after " timeout " ms"))))
  )

(defn publish [^BlockingChannelImpl channel msg & {:keys [timeout ^Encoder  encoder] :or {timeout (Integer/MAX_VALUE) ^Encoder encoder DefaultEncoder/DEFAULT_ENCODER }} ]
  (publish-bytes channel (.encode encoder msg) :timeout timeout))

(defn publish-seq [^BlockingChannelImpl channel xs & {:keys [timeout ^Encoder  encoder] :or {timeout (Integer/MAX_VALUE) ^Encoder encoder DefaultEncoder/DEFAULT_ENCODER }}]
 (doseq [msg xs] (publish channel msg :timeout timeout :encoder encoder ))
 )
 
  