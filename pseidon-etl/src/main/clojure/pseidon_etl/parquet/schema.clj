(ns
  ^{:doc "Parquet support, note that the actual parquet writing is supported in the fileape api
          Here we load the parquet schemas from hive

          Case sensitivity: All column names are converted to lower case,
                            to avoid issues with case names in hive column names and the actual log format spec in protobuf
          "}

  pseidon-etl.parquet.schema
  (:require
    [pseidon-etl.writer :as writer]
    [pseidon-etl.db-service :as db-service]
    [clojure.java.jdbc :as j]
    [clojure.tools.logging :refer [info error debug warn]]
    [clj-json.core :as clj-json])
  (:import
    (org.apache.hadoop.hive.serde2.typeinfo TypeInfo TypeInfoUtils)
    (org.apache.hadoop.hive.ql.io.parquet.convert HiveSchemaConverter)
    (org.apache.commons.lang3 StringUtils)
    (org.apache.parquet.schema MessageType)
    (java.io File)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;; Records and schemas
(defrecord HiveConn [hive-url hive-user hive-password])


(defn hive-conn [hive-url hive-user hive-password]
  (->HiveConn hive-url hive-user hive-password))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;; Private functions


(defn ^TypeInfo str->typeinfo
  "Returns the Hive TypeInfo instance, the String parameter describing the whole type is
   converted to lower case and trimmed."
  [^String s]
  (vec (TypeInfoUtils/getTypeInfosFromTypeString (StringUtils/lowerCase (StringUtils/trim s)))))

(defn ^MessageType
hive->parquet-schema [hive-type-map]
  (HiveSchemaConverter/convert (keys hive-type-map) (flatten (vals hive-type-map))))

(defn parse-describe-row
  "reduce function do (assoc state col_name (str->typeinfo data_type))"
  [state {:keys [col_name data_type] :as m}]
  (try
    ;;trim and convert the column name to lower case
    ;;the type info data_type is also strimmed and casted to lower case
    (assoc state (StringUtils/lowerCase (StringUtils/trim (str col_name))) (str->typeinfo data_type))
    (catch Exception e (do
                         (warn (str "Error while parsing (might be normal) " e " for " m))
                         (error e e)
                         state))))

(defmacro with-hive-connection
  "Create a single hive connection, do (f) and close it afterwards"
  [hive-conn & body]
  ;;note we use the db-service to create and close connection
  `(let [db-srv# (db-service/start-db
                   (db-service/create-database (:hive-url ~hive-conn) (:hive-user ~hive-conn) (:hive-password ~hive-conn) :protocol "hive2" :driver "org.apache.hive.jdbc.HiveDriver"))]
     (try
       (db-service/with-connection
         db-srv#
         ~@body)
       (finally
         (db-service/stop-db db-srv#)))))


(defn valid-data-type?
  "If a valid data type return true otherwise false
  data-str: string type representation"
  [data-str]
  (try
    (do
      (str->typeinfo data-str)
      true)
    (catch Exception e nil)))

(defn hive-describe-table [hive-conn table]
  (->>
    (with-hive-connection hive-conn (j/with-query-results rs [(str "describe " table)] (vec rs)))
    (filter :data_type)
    (filter (comp valid-data-type? :data_type))
    ;; filter out any dt hr columns
    (filter (complement #(#{"dt" "hr"} (StringUtils/trim (str (:col_name %))))))))

(defn load-schema
  "Load the parquet schema from hive for the topic"
  [hive-conn table]
  (let [hive-type-map (reduce parse-describe-row
                              {}
                              (hive-describe-table hive-conn table))]
    (when (and hive-type-map (not-empty hive-type-map))
      (hive->parquet-schema hive-type-map))))


(defn schema-defined? [topic mult-writer-ctx]
  (writer/multi-write-parquet-schema-defined? mult-writer-ctx topic))

;;; used to ensure we only load the schema once between multiple threads
;;; from hive
(defonce LOCK "LOCKOBJ")


(defn filename [basedir file]
  (.getAbsolutePath (File. (str basedir) (str file))))


(defn update-writer-env!
  "Load data from hive and update the multi writers env with the message type schema,
   only if the schema is not defined, this function is safe to call on every message
    and will only incur a cost on the fist schema update for the topic"
  [mult-writer-ctx hive-conf topic hive-table]

  ;;; note, this would always never run if pseidon.edn file :codec is not parquet or parquet-1,
  (info "parquet-schema: schema-defined? " (schema-defined? topic mult-writer-ctx))
  (when-not (schema-defined? topic mult-writer-ctx)
    (locking LOCK
      (let [schema (load-schema hive-conf hive-table)]
        (info ">>>>>>>>>>>>> Loaded schema from hive for " topic)
        (writer/multi-update-env mult-writer-ctx topic :parquet-codec :gzip :message-type schema)))))

