(ns pseidon-etl.memory
    (:require [clojure.tools.logging :refer [error]])
    (:import [java.lang.management ManagementFactory MemoryMXBean MemoryUsage]
      [javax.management MBeanServer ObjectName]))
;; monitor memory usage


(defonce ^MBeanServer mbeans (ManagementFactory/getPlatformMBeanServer))

(defonce ^ObjectName directpool (try
                                  (ObjectName. "java.nio:type=BufferPool,name=direct")
                                  (catch Exception e (do (error e e) nil))))

(defn bts->mb [^long v] (-> (/ v 1048576)
                            (* 1000)
                            int
                            (/ 1000)
                            double))

(defn get-attribute [objectName name]
      (try
        (when (and mbeans directpool)
              (.getAttribute mbeans objectName name))
        (catch Exception e (do (error e e) nil))))

(defn buffer-pool-stats []
      {:memory-used (bts->mb (get-attribute directpool "MemoryUsed"))
       :count (get-attribute directpool "Count")
       :total-capacity (bts->mb (get-attribute directpool "TotalCapacity"))})



(defn- memory-usage-map [^MemoryUsage m]
       {:init (bts->mb (.getInit m)) :used (bts->mb (.getUsed m)) :max (bts->mb (.getMax m)) :committed (bts->mb (.getCommitted m))})

(defn memory-stats []
      (let [^MemoryMXBean bean (ManagementFactory/getMemoryMXBean)
            ^MemoryUsage heap (.getHeapMemoryUsage bean)
            ^MemoryUsage non-heap (.getNonHeapMemoryUsage bean)]
           {:heap (memory-usage-map heap) :non-heap (memory-usage-map non-heap)}))