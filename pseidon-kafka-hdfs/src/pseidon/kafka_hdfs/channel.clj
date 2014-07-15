(ns pseidon.kafka-hdfs.channel
  (:require [pseidon.core.conf :refer [get-conf2]]
            [pseidon.core.queue :refer [publish]]
            [pseidon.core.message :refer [create-message]]
            [pseidon.core.metrics :refer [add-meter update-meter] ]
            [pseidon.core.registry :refer [register ->Channel reg-get-wait] ]
            [pseidon.core.watchdog :refer [watch-critical-error handle-critical-error]]
            [pseidon.core.tracking :refer [select-ds-messages mark-run! mark-done! deserialize-message]]
     	      [clojure.tools.logging :refer [info error]]
            [taoensso.nippy :as nippy]
            [clj-json.core :as json]
            [pseidon.kafka-hdfs.json-csv :as json-csv]
            [clj-time.coerce :refer [from-long to-long]]
            [clj-json [core :as json]]
            [clj-time.format :refer [unparse formatter]]
            [clojure.java.jdbc :as sql]
            [fun-utils.core :refer [fixdelay]]
            [org.tobereplaced.jdbc-pool :refer [pool]])
  (:import (java.util.concurrent Executors TimeUnit)
           (java.util.concurrent.atomic AtomicBoolean)
           [org.apache.commons.lang StringUtils]
           [java.io DataOutputStream]
           [net.minidev.json JSONValue]))

(defmacro with-info [s body]
  `(let [x# ~body] (info ~s " " x#) x#))

(defn json-csv-encoder 
  "Data should be the data returned from json-csv/parse-definitions"
  [org-msg msg & data]
  (json-csv/json->csv data "," msg))

(defn json-tsv-encoder 
  "Data should be the data returned from json-csv/parse-definitions"
  [org-msg msg & data]
  (json-csv/json->csv data "\t" msg))

(defn ^"[B" default-encoder [org-msg msg & _]
  org-msg)

(defn nippy-encoder [org-msg msg & _]
  (nippy/freeze msg))

(defn json-encoder [org-msg msg & _]
  (let [^java.io.ByteArrayOutputStream bts-array (java.io.ByteArrayOutputStream. (* 2 (count msg)))]
    (with-open [^java.io.OutputStreamWriter appendable (java.io.OutputStreamWriter. bts-array)]
       (net.minidev.json.JSONValue/writeJSONString msg appendable))
    (.toByteArray bts-array)))
      

(defonce ^:constant encoder-map { 
                                 :default default-encoder
                                 :nippy nippy-encoder
                                 :json json-encoder
                                 :json-csv json-csv-encoder
                                 :json-tsv json-tsv-encoder})


(def get-fixed-encoder (memoize (fn [log-name]
                            (with-info (str "For topic " log-name " using encoder ")
				                            (let [key1 (keyword (str "kafka-hdfs-" log-name "-encoder"))
				                                  key2 :kafka-hdfs-default-encoder]
				                              (if-let [encoder (get encoder-map (get-conf2 key1 (get-conf2 key2 :default))) ]
				                                encoder
				                                default-encoder))))))

(defn get-db-connecion []
   {:classname (get-conf2 :etl-tracking-driver "com.mysql.jdbc.Driver")
                :subprotocol (get-conf2 :hdfs-tracking-subprotocol "mysql") 
                :subname     (get-conf2 :hdfs-tracking-subname "//localhost:3306/db") 
                :user        (get-conf2 :hdfs-tracking-user "root")
                :password    (get-conf2 :hdfs-tracking-pwd "")})

(defonce DEFAULT_DB (delay (pool 
                         (get-db-connecion)
                         :max-statements 100
                         :max-pool-size 1)))


(defn load-topics 
  "load topics from the kafka-logs table"
  [host & {:keys [db] :or {db (force DEFAULT_DB)}}]
  (sql/with-connection db
    (sql/with-query-results rs [(str "select log from kafka_logs where type='hdfs' and `group`='" (get (get-conf2 :kafka.redis-conf {}) :group-name "hdfs") "' and enabled=1")] (vec (map :log rs)))))

(defn- get-default-value 
  "Based on the source log data type a default type is returned, string \"\" boolean false and everything else -1"
  [data_type]
  (cond 
    (= data_type "string") ""
    (= data_type "boolean") false
    :else -1))

(defn- add-source-logs 
  "If no data value is specified the columns are retrieved from the source_logs source_log_fields tables
   updates the data key in parser-data only if data is empty, data can be two types, a vector of vectors or a string"
  [db {:keys [log type data] :as parser-data}]
  (if (empty? data)
    (assoc parser-data :data ;change the data field to the parsed source fields [[log_name default] [log_name default ...]]
      (sql/with-query-results rs 
                   [(str "select field_name, data_type from source_log_fields f, source_logs_view s where s.log_id = f.log_id and log_name=\"" log "\" order by field_id asc")] 
                   (vec (doall (map (fn [{:keys [field_name data_type]}] [field_name (get-default-value data_type)])  rs)))))
                   
    parser-data))

(defn load-parser-data 
  "load topics from the kafka-logs table"
  [& {:keys [db] :or {db (force DEFAULT_DB)}}]
  (sql/with-connection db
    (sql/with-query-results rs [(str "select log,type,data from kafka_log_encoders")]
      (vec (doall (map (partial add-source-logs db) rs))))))


(def ch-dsid "pseidon.kafka-hdfs.channel")
(defonce service (Executors/newSingleThreadExecutor))
(defonce host-name (-> (java.net.InetAddress/getLocalHost) .getHostName))

(def kafka-reader  (delay (let [{:keys [reader-seq]} (reg-get-wait "pseidon.kafka.util.datasource" 10000)] reader-seq)))

(defonce topic-parsers (ref {}))


(defn get-encoder [log-name]
  (if-let [encoder (get @topic-parsers log-name)]
    encoder
    (get-fixed-encoder log-name)))

(defn parse-parser-def 
  "Returns a function that acceps a single argument, t is :json-csv or :json-tsv the data is parsed using json-csv/parse-definitions before passing it to the encoder"
  [t data]
  (let [k-t (keyword t)
        encoder (get encoder-map k-t default-encoder)]
    (cond 
      (or (= k-t :json-csv) (= k-t :json-tsv))
      (let [parsed-def (json-csv/parse-definitions data)]
        (fn [o json-msg] 
          (apply encoder o json-msg parsed-def)))
      :else
      (fn [o json-msg] 
        (apply encoder o json-msg data)))))
        
(defn update-topic-parsers [topic-data]
  (dosync 
    (alter topic-parsers 
      (fn [m] 
        (try
          (reduce (fn [state {:keys [log type data]}]
                     (assoc state log (parse-parser-def type data))) {} topic-data)
          (catch Exception e (do (error e e) m)))))))            

(defn process-topics [topics]
  "Start in infinite loop that will read the current batch ids and send to the hdfs topic
  "
  (let [consume-meter-map (into {} (map (fn [n] [n (add-meter (str "pseidon.kafka_hdfs.channel-" n))]) topics))
        topics-ref (ref (set topics))
        _ (do (update-topic-parsers (load-parser-data)))
        {:keys [reader-seq add-topic remove-topic]}(reg-get-wait "pseidon.kafka.util.datasource" 10000)]
    
    
	  (fixdelay 10000
	    (try
	      (let [
             _ (update-topic-parsers (load-parser-data))
             _ (do (info "using parsers " @topic-parsers))
             logs (set (load-topics host-name))
             logs-to-add (clojure.set/difference logs @topics-ref)
             logs-to-remove (clojure.set/difference @topics-ref logs)]
         (info "logs " logs " logs-to-add " logs-to-add " logs-to-remove " logs-to-remove " topics-ref " topics-ref)
         (doseq [log logs-to-add]
           (add-topic log))
         (doseq [log logs-to-remove]
           (remove-topic log))
         (dosync 
           (if (not (empty? logs-to-add))
            (alter topics-ref #(apply conj % logs-to-add)))
           (if (not (empty? logs-to-remove))
            (alter topics-ref #(apply disj % logs-to-remove)))))
         (catch Exception e (handle-critical-error e))))
	  
        
		  (while (not (Thread/interrupted))
		    (let [rdr-seq (apply reader-seq topics)]
	        (doseq [msgs rdr-seq]
                 (let [msg-id 1]
                      (try
                        (do
                          (publish "pseidon.kafka-hdfs.processor" (create-message msgs ch-dsid msg-id ch-dsid true -1 1)))
                        (catch java.sql.BatchUpdateException e (info "ignore duplicate message " msg-id))))
                 )))))


(defn ^:dynamic channel-init []
  
  ;this will be called when any file written by this channel has been rolled
  ;we send the rolled file to the hdfs plugin and this plugin will take care of sending the file to hdfs

)

(defn ^:dynamic load-channel []
  (let [topics (get-conf2 :kafka-hdfs-topics [])]
  {:start (fn [] 
              (channel-init)
              (.submit service (watch-critical-error process-topics topics)))
   
   :stop (fn []
           (.shutdown service)
           (.awaitTermination service 10000 TimeUnit/MILLISECONDS)
           (.shutdownNow service))
   }))

(let [{:keys [start stop]} (load-channel)]
       (register (->Channel ch-dsid start stop)))  
           

  



