(ns
  ^{:doc "App produce command"}
  pseidon-etl.apputils.cmd-produce
  (:require [kafka-clj.client :as client]
            [pseidon-etl.apputils.util :as app-util]
            [fun-utils.threads :as fn-threads])
  (:import (java.util.concurrent ExecutorService Executors TimeUnit)
           (org.apache.avro Schema$Parser Schema)
           (org.apache.avro.generic GenericData$Record)
           (pseidon_etl AvroSerializer)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;; private functions

(defonce ^Schema avro-schema (.parse (Schema$Parser.)
                                     "{\"name\": \"test\", \"type\": \"record\", \"fields\":[ {\"name\": \"ts\", \"type\": \"long\"}, {\"name\": \"data\", \"type\": \"string\"} ]}"))

(defn mean [coll]
  (let [sum (apply + coll)
        count (count coll)]
    (if (pos? count)
      (/ sum count)
      0)))

(defn rand-str
  "
  Returns a txt format message with <tab>
  "
  [len]
  (apply str (take len (repeatedly #(char (+ (rand 26) 65))))))

(defn ^"[B" generate-test-message []
  (AvroSerializer/serialize
    avro-schema
    (doto
      (GenericData$Record. avro-schema)
      (.put 0 (System/currentTimeMillis))
      (.put 1 (rand-str 100)))))


(defn update-message-stats! [messages-stats start-ts count-per-thread]
  (let [time (- (System/currentTimeMillis) (long start-ts))]
    (swap! messages-stats (fn [m] (merge-with concat m {:times [time] :totals [count-per-thread]})))))


(defn send-test-data
  "
  brokers : [{:host :port} ... ]
  "
  [threads count-per-thread topic brokers conf]
  (let [conn (client/create-connector brokers (apply array-map conf))
        ^ExecutorService exec (Executors/newFixedThreadPool (int threads))
        errors-a (atom 0)

        messages-stats (atom {:times [] :totals []})        ;;times: []

        ]
    (try
      (dotimes [_ threads]
        (fn-threads/submit exec (fn []

                                  (try

                                    (let [start-ts (System/currentTimeMillis)
                                          k 10000
                                          last-seen-ts (atom (System/currentTimeMillis))]

                                      (dotimes [i count-per-thread]
                                        (client/send-msg conn topic (generate-test-message))

                                        (when (zero? (rem i k))
                                          (let [ts (- (System/currentTimeMillis) @last-seen-ts)]
                                            (swap! last-seen-ts (constantly (System/currentTimeMillis)))

                                            (prn (.getName (Thread/currentThread))
                                                 " sent " i " messages in " ts "ms  Rate " (int (/ k (/ ts 1000))) "p/s"))))

                                      (update-message-stats! messages-stats start-ts count-per-thread))

                                    (catch Exception e
                                      (do
                                        (prn e)
                                        (.printStackTrace e)
                                        (swap! errors-a inc)))))))

      (.shutdown exec)
      (.awaitTermination exec Long/MAX_VALUE TimeUnit/MILLISECONDS)

      (finally
        (client/close conn)))
    {:message-stats @messages-stats :errors @errors-a}))

(defn as-int [v]
  (if (instance? Number v)
    v
    (Integer/parseInt (str v))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;; public functions


(defn send-data
  "{:message-stats {:times [] :totals []} :errors num}"
  [threads count-per-thread topic brokers conf]
  (let [
        thread-int (as-int threads)
        count-per-thread-int (as-int count-per-thread)
        stats (send-test-data thread-int count-per-thread-int topic (app-util/format-brokers (clojure.string/split brokers #"[,;]")) conf)

        errors (get stats :errors)
        times (get-in stats [:message-stats :times])
        mean-times (mean times)
        max-times (apply max times)
        min-times (apply min times)]

    (println
      "errors " errors "\n"
      "mean " (int mean-times) "ms\n"
      "max  " max-times "ms\n"
      "min  " min-times "ms\n"
      "rate " (int (/ (* thread-int count-per-thread-int) (/ mean-times 1000))) "p/s"
      "-------------------------------------------\n")

    (clojure.pprint/pprint stats)))


