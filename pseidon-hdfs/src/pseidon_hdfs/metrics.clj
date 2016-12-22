(ns pseidon-hdfs.metrics
  (:import [com.codahale.metrics Meter MetricRegistry]))



(defn metric->map [^Meter timer]
  {:count (.getCount timer)
   :five-minute-rate (.getFiveMinuteRate timer)
   :mean-rate (.getMeanRate timer)
   :one-minute-rate (.getOneMinuteRate timer)})


(defn mark-meter [^Meter meter ^long n]
  (.mark meter n))

(defn metric-registry []
  (MetricRegistry.))


(defn meter [^MetricRegistry registry desc]
  (.meter registry (str desc)))
