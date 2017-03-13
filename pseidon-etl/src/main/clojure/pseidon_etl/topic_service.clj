(ns pseidon-etl.topic-service
  (:require [com.stuartsierra.component :as component]
            [pseidon-etl.db-service :refer [with-connection]]
            [pseidon-etl.mon :refer [register]]
            [pseidon-etl.parquet.schema :as parquet-schema]
            [clojure.tools.logging :refer [info error debug]]
            [kafka-clj.consumer.node :refer [add-topics! remove-topics!]]
            [fun-utils.cache :as cache]
            [fun-utils.core :refer [fixdelay-thread stop-fixdelay]]
            [clojure.java.jdbc :as j]
            [pseidon-etl.writer :as writer]
            [fun-utils.cache :as cache]
            [pseidon-etl.formats :as formats]))


(defn disable-topic! [db topic]
      {:pre [db]}
      (info "Disabling topic in pseidon_logs: " topic)
      (with-connection db
                       (j/execute!
                         (:conn db)
                         ["UPDATE pseidon_logs set enabled=0 WHERE log=?"
                          topic])))

(defn- load-topics
  "load topics from the pseidon-logs table"
  [{:keys [etl-group]} db]
  {:pre [etl-group]}
  (let [query (str "select log from pseidon_logs where log_group='" etl-group "' and enabled=1")]
  (info "Query: " query)
  (with-connection db
                   (j/with-query-results rs [query] (vec (map :log rs))))))



(defn -get-topic-meta
  "Load the pseidon_logs data for a log which is the metadata describing format + outputformat and hive table location
   log, format, output_format, hive_table_name, hive_url, hive_user, hive_pwd"
  ([db]
   (with-connection db
                    (j/with-query-results rs [(str "select * from pseidon_logs")] (vec rs))))
  ([log db]
   (first
     (with-connection db
                      (j/with-query-results rs [(str "select * from pseidon_logs where log='" log "'")] (vec rs))))))


(def get-topic-meta (cache/memoize -get-topic-meta))

(defn load-parquet-schema! [writer-ctx hive-conn topic hive-table]
  (info "Loading parquet schema for " topic " from hive " hive-conn " hive table " hive-table)
  (parquet-schema/update-writer-env! writer-ctx hive-conn topic hive-table))


(defn load-all-parquet-schemas!
  "Load all the topic meta and for each load the hive schema, then return a list of logs loaded"
  [db writer-ctx]

  (let [only-undefined-schemas #(not (parquet-schema/schema-defined? (:log %) writer-ctx))

        meta-for-undefined-schemas (filter #(and (only-undefined-schemas %) (not (= (:output_format %) "json"))) (-get-topic-meta db))

        load-f (fn [logs {:keys [log hive_table_name output_format hive_url hive_user hive_pwd]}]
                 (let [hive-conn (parquet-schema/hive-conn hive_url hive_user hive_pwd)]
                   (info "calling parquet-schema/update-writer-env!")
                   (parquet-schema/update-writer-env! writer-ctx hive-conn log hive_table_name)
                   (info "Loading parquet schema for " log " from hive " hive-conn " hive table " hive_table_name " " (count logs) " of " (count meta-for-undefined-schemas)))
                 (conj logs log))]

    (reduce load-f [] meta-for-undefined-schemas)))


(defn add-etl-topics!
  "If the output_format is parquet, a load schema is performed and added to the writer env
   for any output_format the add-topics! on the kafka node is called, this will start
   kafka consumption of the log"
  [multi-writer-ctx db node logs-to-add]
  (debug "add-etl-topics: " logs-to-add)
  (add-topics! node logs-to-add))

(defn update-topics!
  "Run every N seconds, reads all the enabled topics from the mysql db, and
   if any new enabled start them."
  [conf multi-writer-ctx database kafka-node]
  {:pre [conf multi-writer-ctx database kafka-node]}
  (try
    (debug ">>>>>>>>>>> update-topics! from: " (load-topics conf database))
    (let [topics-ref (get-in kafka-node [:node :topics-ref])
          logs (set (load-topics conf database))
          logs-to-add (clojure.set/difference logs (set @topics-ref))
          logs-to-remove (clojure.set/difference (set @topics-ref) logs)]

      (load-all-parquet-schemas! database multi-writer-ctx)

      (when (not-empty logs-to-add)
        (add-etl-topics! multi-writer-ctx database (:node kafka-node) logs-to-add))

      (when (not-empty logs-to-remove)
        (remove-topics! (:node kafka-node) logs-to-remove)))

    (info ">>>>>>>>>>> end update topics")
    (catch Exception e (error e e))))

(defn
  ^String
  -get-format
       "valid formats are determined by the pseidon-etl.formats namespace and multimethod implementations"
       [log state-a conf db]
       (let [format (formats/parse-format (or (:format (get-topic-meta log db)) "txt"))]
         (formats/format-descriptor state-a conf format)))

(defn
  ^String
  -get-output-format
  "valid formats json parquet"
  [log conf db]
  (let [format (:output_format (get-topic-meta log db))]
    (if format (clojure.string/lower-case format) "txt")))

(def get-format (cache/memoize -get-format))

(def ^String get-output-format (cache/memoize -get-output-format))

(defrecord TopicUpdaterService [conf database writer-service kafka-node monitor-service started-promise]
  component/Lifecycle

  (start [component]
    (prn "TOPIC SERVICE1")

    (info "Starting Topic Service")
    (try
      (do
        ;;run update topics once

        (update-topics! conf (:writer-ctx writer-service) (:database component) (:kafka-node component))

        (prn "TOPIC SERVICE2")
        (let [freq (get conf :topic-refresh-freq-ms 10000)
              delay-service (fixdelay-thread freq (update-topics! conf (:writer-ctx writer-service) (:database component) (:kafka-node component)))]

             ;register the topics that are being pulled by the kafka consumer node
             (when-let [mon-service (:monitor-service component)]
                       (register mon-service :topics (fn [] (deref (get-in kafka-node [:node :topics-ref])))))

             (assoc component :delay-service delay-service)))
      (finally
        (deliver started-promise true)
        (info "Completed Topic Service start"))))

  (stop [component]
    (if (:delay-service component)
      (do
        (stop-fixdelay (:delay-service component))
        (dissoc component :delay-service)))))


(defn create-topic-service
  "Returns a service that needs to be started still"
  [conf]
  (->TopicUpdaterService conf nil nil nil nil (promise)))