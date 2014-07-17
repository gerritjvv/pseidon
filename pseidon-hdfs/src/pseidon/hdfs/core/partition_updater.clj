(ns pseidon.hdfs.core.partition-updater
  "When a new directory is loaded and external command is run,
   this allows integration with hive e.g add hive partitions and any other systems e.g notification"
  (:require [clojure.java.shell :refer [sh]]
            [clojure.tools.logging :as log])
  (:import  [java.util.concurrent Executors ExecutorService Callable TimeUnit TimeoutException]))


(defonce ^ExecutorService
         service (Executors/newCachedThreadPool))

(defn log-error [f & args]
      (prn "Running f " args)
       (let [{:keys [error exit] :as res} (apply f (flatten args))]
            (if (not= exit 0)
              (log/error error))
             res))

(defn run-cmd [cmd & args]
      (try
        (apply sh (cons cmd args))
        (catch Exception e {:exit -1
                            :error e})))

(defn error? [{:keys [exit]}]
      (not= exit 0))


(defn with-retry [n cmd & args]
      (loop [i 0]
            (let [res (log-error run-cmd cmd args)]
                 (if (and
                       (error? res)
                       (< i n))
                   (recur (inc i))
                   [i res]))))

(defn notify-error [res]
      (log/error "Error " res))


(defn with-timeout [^Long timeout-ms f args & {:keys [notify-f] :or {notify-f notify-error}}]
      (let [^Callable f2 #(apply f args)
            res (try
                  (-> service
                      (.submit f2)
                      (.get timeout-ms TimeUnit/MILLISECONDS))
                  (catch TimeoutException e {:exit -1 :error e}))]
           (if (or (not res) (error? res))
             (notify-f res))
           res))


(defn with-timeout-async [^Long timeout-ms f args & {:keys [notify-f] :or {notify-f notify-error}}]
      (let [^Callable f with-timeout]
           (.submit service f)))

