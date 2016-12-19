(ns pseidon-etl.metrics
  (:require [clj-time.core :as t]
            [clj-time.format :as f]
            [clj-time.coerce :as c]
            [fun-utils.core :refer [fixdelay-thread stop-fixdelay]]
            [taoensso.carmine :as car :refer [wcar]]
            [clojure.tools.logging :refer [info error]]))


;; Used for realtime low overhead internal metrics
;; shows how many messages read and how many written
;; the data is held in memory, the key is topic+date+hour and expired after 4 hours, i.e internally only the counters
;; for the last two hours is held.
;; persistence is done in the background to redis.

;; USAGE
;;
;; (def ctx (create-metrics->agent!))
;; (inc-by! ctx "hdfs" "in" (System/currentTimeMillis) 12)
;; (close-metrics-agent! ctx)

(defonce date-hour-format (f/formatter "yyyyMMddHHmm"))

(defn- update-redis! [path expire-seconds v]
  (try
    (let [final-path (str "/metrics/" path)]
      (car/incrby final-path v)
      (car/expire final-path expire-seconds))
    (catch Exception e (error e e))))

(defn- metrics->redis!
  "Updates the redis counters and returns an empty map"
  [redis-conn expire-seconds ctx]
  (car/wcar redis-conn
            (reduce-kv
              (fn [_ path cnt] (update-redis! path expire-seconds cnt)) nil ctx))
  {})

(defn delete-all-keys!
  "Used for testing to clear all keys in redis"
  [{:keys [redis-conn]}]
  (car/wcar redis-conn
            (car/flushdb)))

(defn get-key-vals [{:keys [redis-conn]} phase topic]
  (let [[_ ks] (car/wcar redis-conn
                         (car/scan 0 :match (str "/metrics/" phase "/" topic "/*")))]
    (zipmap
      ks
      (car/wcar redis-conn
                (doall (for [k ks] (car/get k)))))))

(defn create-metrics->agent!
  [& {:keys [redis rate expire-seconds] :or {redis {:host "localhost" :port 6379 :db 0} rate 10000 expire-seconds 86400}}]
  (let [ctx (agent {})
        redis-conn {:pool {} :spec redis}
        fd (fixdelay-thread rate
                            (try
                              (await (send-off ctx #(metrics->redis! redis-conn expire-seconds %)))
                              (catch Exception e (error e e))))]
    {:ctx ctx :fixdelay fd :redis-conn redis-conn :expire-seconds expire-seconds}))

(defn close-metrics-agent!
  "Closes any background threads, sends the last metrics to redis and wait for all
   submitted actions to clear"
  [{:keys [ctx fixdelay redis-conn expire-seconds]}]
  (stop-fixdelay fixdelay)
  (await (send-off ctx #(metrics->redis! redis-conn expire-seconds %))))
