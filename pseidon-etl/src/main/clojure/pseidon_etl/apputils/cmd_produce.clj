(ns
  ^{:doc "App produce command

          Sends avro messages of type {ts:long, data:string}"}
  pseidon-etl.apputils.cmd-produce
  (:require [kafka-clj.client :as client]
            [pseidon-etl.apputils.util :as app-util]
            [fun-utils.threads :as fn-threads]
            [pseidon-etl.avro.avro-format :as avro-format])
  (:import (java.util.concurrent ExecutorService Executors TimeUnit)
           (pseidon_etl AvroSerializer)
           (org.apache.avro Schema$Parser Schema)
           (org.apache.avro.generic IndexedRecord GenericRecord GenericData GenericData$Record)
           (io.confluent.kafka.schemaregistry.client SchemaRegistryClient CachedSchemaRegistryClient)
           (io.confluent.kafka.serializers KafkaAvroEncoder KafkaAvroSerializer)
           (org.apache.kafka.common.serialization Serializer)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;; private functions

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


(def ^Schema TEST-MSG-SCHEMA (.parse (Schema$Parser.) (str "

  {
    \"type\" : \"record\",
    \"namespace\": \"Test\",
    \"name\" : \"TestMessage\",
    \"fields\": [
       {\"name\": \"ts\", \"type\" : \"long\"},
       {\"name\": \"data\", \"type\": \"string\"}
     ]
  }
")))



(defn ^"[B" generate-test-message [encoder topic]
  (encoder (str topic) (doto
                         (GenericData$Record. TEST-MSG-SCHEMA)
                         (.put 0 (System/currentTimeMillis))
                         (.put 1 (rand-str 100)))))

(defn update-message-stats! [messages-stats start-ts count-per-thread]
  (let [time (- (System/currentTimeMillis) (long start-ts))]
    (swap! messages-stats (fn [m] (merge-with concat m {:times [time] :totals [count-per-thread]})))))

(defn send-test-data
  "
  brokers : [{:host :port} ... ]
  "
  [schema-registry threads count-per-thread topic brokers conf]
  (let [conn (client/create-connector brokers (apply array-map conf))
        ^ExecutorService exec (Executors/newFixedThreadPool (int threads))
        errors-a (atom 0)

        messages-stats (atom {:times [] :totals []})        ;;times: []
        encoder (avro-format/encoder (avro-format/create-client-reg {:avro-schema-registry-url schema-registry}))
        ]
    (try
      (dotimes [_ threads]
        (fn-threads/submit exec (fn []

                                  (try

                                    (let [start-ts (System/currentTimeMillis)
                                          k 10000
                                          last-seen-ts (atom (System/currentTimeMillis))]

                                      (dotimes [i count-per-thread]
                                        (client/send-msg conn topic (generate-test-message encoder topic))

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

      ;;sleep 10 seconds to give the producer flush time
      (Thread/sleep 10000)
      (finally
        (client/close conn)))
    {:message-stats @messages-stats :errors @errors-a}))

(defn as-int [v]
  (if (instance? Number v)
    v
    (Integer/parseInt (str v))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;; public functions


(defn push-schema
  "Send the test schema to the schema registry"
  [topic schema-registry-url]

  (let [id (.register (CachedSchemaRegistryClient. (str schema-registry-url) (int 100))
                      (str topic)
                      TEST-MSG-SCHEMA)]

    (prn "Registered " TEST-MSG-SCHEMA " for topic " topic " under id " id)))

(defn send-data
  "{:message-stats {:times [] :totals []} :errors num}"
  [schema-registry threads count-per-thread topic brokers conf]
  (let [
        _ (push-schema topic schema-registry)

        thread-int (as-int threads)
        count-per-thread-int (as-int count-per-thread)
        stats (send-test-data schema-registry thread-int count-per-thread-int topic (app-util/format-brokers (clojure.string/split brokers #"[,;]")) conf)

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


