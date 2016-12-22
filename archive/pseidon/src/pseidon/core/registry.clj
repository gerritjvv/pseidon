(ns pseidon.core.registry 
  (:use clojure.tools.logging
        pseidon.core.watchdog)
  )

(defrecord DataSource [name run stop list-files reader-seq ])
(defrecord Channel [name run stop])
(defrecord DataSink [name run stop writer])
(defrecord Processor [name run stop exec])

(def reg-state (ref {}))

(def exec-service (ref {}))

(defn reg-list-all []  @reg-state)

(defn create-datasource [{:keys [name run stop list-files reader-seq]}]
  (DataSource. name run stop list-files reader-seq))

(defn create-channel [{:keys [name run stop]}]
  (Channel. name run stop))

(defn create-datasink [{:keys [name run stop writer]}]
  (DataSink. name run stop writer))

(defn create-processor [{:keys [name run stop exec]}]
  (Processor. name run stop exec))

(defn register [{name :name :as item}]
  "Register a service "
   (info "Regiser service " name  (class item))
  (dosync (alter reg-state (fn [p] (assoc p name item) ) ))
  )


(defn reg-get [name]
  "Gets a service registered"
   (get @reg-state name)
  )

(defn reg-get-wait [name timeout]
   "Gets a service by name and if its nil waits for timeout milliseconds"
   (let [start (System/currentTimeMillis)]
     (loop [diff 0 service (reg-get name)]
       (if-not (and (nil? service)
               (> timeout diff)
            )
            service
            (do (Thread/sleep 500) (recur (Math/abs (- start (System/currentTimeMillis)))  (reg-get name) ))
       )
     )   
  )
)


  (defn start-all []
    (dosync
      (alter exec-service (fn [x] (java.util.concurrent.Executors/newCachedThreadPool))) 
      )
    
    (doseq [[name {run :run}] @reg-state]
      (let [service ^java.util.concurrent.ExecutorService  @exec-service
            ^java.util.concurrent.Callable callable (fn [] (time ((watch-critical-error run))))]
          (.submit service callable))))
  
  (defn stop-all []
    (let [service ^java.util.concurrent.ExecutorService @exec-service]
      (try 
        (doseq [[name {stop :stop}] @reg-state]
          ((watch-normal-error stop))
          ) (finally (.shutdown service)))))
  