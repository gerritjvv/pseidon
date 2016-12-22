(ns pseidon-etl.test-utils
  (:import [kafka_clj.util EmbeddedKafkaCluster EmbeddedZookeeper]
           [java.util Properties]
           (redis.embedded RedisServer)
           (org.apache.hive.service.server HiveServer2)
           (org.apache.hadoop.hive.conf HiveConf)
           (java.sql DriverManager)
           (java.io PrintWriter File)
           (java.net ServerSocket)
           (org.apache.commons.io FilenameUtils)
           (org.apache.commons.lang3.math NumberUtils))
  (:require
    [pseidon-etl.app :as app]
    [pseidon-etl.db-service :as db-service]
    [pseidon-etl.db-service :as db]
    [pseidon-etl.hive :as hive]
    [com.stuartsierra.component :as component]
    [clojure.tools.logging :refer [info]]
    [clojure.java.jdbc :as j]
    [kafka-clj.consumer.node :refer [create-kafka-node-service]]
    [kafka-clj.client :refer [create-client-service]]
    [clojure.string :as cljstr]
    [pseidon-etl.writer :as writer]))

(defn retry [sleep-ms timeout-ms f]
  (let [ts (System/currentTimeMillis)]

    (loop [res (f)]
      (if res
        res
        (if (> (- (System/currentTimeMillis) ts) timeout-ms)
          (throw (RuntimeException. (str "timeout waiting for " f)))
          (do
            (prn "waiting for " f)
            (Thread/sleep (long sleep-ms))
            (recur (f))))))))

(defn try-port [n]
  {:pre [(number? n)]}
  (try
    (with-open [^ServerSocket socket (ServerSocket. (int n))]
      (.getLocalPort socket))
    (catch Exception _ nil)))

(defn free-port [start-port]
  (or (try-port start-port) (try-port 0) (throw (Exception. "No free port found"))))

(defn invoke-private-method [obj fn-name-string & args]
  (let [m (first (filter (fn [x] (.. x getName (equals fn-name-string)))
                         (.. obj getClass getDeclaredMethods)))]
    (. m (setAccessible true))
    (. m (invoke obj args))))

(defn- brokers-as-map
  "Takes a comma separated string of type \"host:port,hostN:port,hostN+1:port\"
   And returns a map of form {host port hostN port hostN+1 port}"
  [^String s]
  (for [pair (cljstr/split s #"[,]")
        :let [[host port] (cljstr/split pair #"[:]")]]
    {:host host :port (Integer/parseInt port)}))

(defn startup-zk []
  (doto (EmbeddedZookeeper. (free-port 2181)) .startup))

(defn create-kafka-cluster [zk properties]
  (doto (EmbeddedKafkaCluster. (.getConnection zk) properties [(int -1) (int -1)]) .startup))

(defn startup-kafka []
  (let [zk (startup-zk)
        properties (doto
                     (Properties.)
                     (.setProperty "max.socket.request.bytes" "104857600"))
        kafka (create-kafka-cluster zk properties)]
    {:zk      zk
     :kafka   kafka
     :brokers (brokers-as-map (.getBrokerList kafka))}))


(defn- shutdown-kafka [{:keys [zk kafka]}]
  (.shutdown ^EmbeddedKafkaCluster kafka)
  (.shutdown ^EmbeddedZookeeper zk))

(defn startup-redis []
  (let [port (free-port 6379)
        redis (doto (RedisServer. (int port)) .start)]


    (when-not (.isActive ^RedisServer redis)
      (throw (RuntimeException. (str "Could not startup a RedisServer for testing on port " port))))

    (info "Started a RedisServer on port " port)
    {:server redis :port port}))

(defn shutdown-redis [{:keys [^RedisServer server]}]
  (when server
    (.stop server)))


(defn create-topics [resources & topics]
  (-> resources :kafka :kafka (.createTopics topics)))

(defn startup-resources [& topics]
  (let [res {:kafka (startup-kafka)
             :redis (startup-redis)}]
    (if (not-empty topics)
      (apply create-topics res topics))
    res))

(defn shutdown-resources [{:keys [kafka redis]}]
  (shutdown-kafka kafka)
  (shutdown-redis redis))


(defn create-test-kafka-client-service [state]
  (create-client-service (get-in state [:kafka :brokers]) {}))

(defn create-streaming-db [^HiveServer2 server]
  (let [conn (hive/hive-connection "localhost:10000")]
    (hive/create-db conn "streaming" (str "target/streaminghive/" (System/currentTimeMillis)))
    server))


(defn create-table
  "Creates a test streaming.$table-name with column tname:string and partitioned by dt, hr"
  [^HiveServer2 server table-name]
  (let [conn (hive/hive-connection "localhost:10000")]
    (hive/sql-exec conn (str "CREATE TABLE IF NOT EXISTS streaming." table-name " (tname string) PARTITIONED BY (dt string, hr string)"))
    server))

(defn wait-for [server ms]
  (Thread/sleep ms)
  server)

(defn ^HiveConf hive-conf []
  (doto
    (HiveConf.)
    (.setInt "port" 10000)))

(defn create-hive-resources []
  (Class/forName hive/driver-name)
  (DriverManager/setLogWriter (PrintWriter. System/out))
  (DriverManager/registerDriver (org.apache.hive.jdbc.HiveDriver.))
  (doto (HiveServer2.) (.init (hive-conf)) .start (wait-for 2000)
                       create-streaming-db
                       (create-table "mytopic_test")
                       (create-table "hive_metrics")
                       (create-table "hdfs_metrics")))

(defn shutdown-hive-resources [^HiveServer2 hive-resources]
  (.stop hive-resources))

(defn hive-url [^HiveServer2 hive-resources]
  (try
    (invoke-private-method hive-resources "getServerInstanceURI")
    (catch NullPointerException e
      "//localhost:10000")))

(defn create-kafka-node-service-test-conf [state]
  {:bootstrap-brokers (get-in state [:kafka :brokers])
   :redis-conf        {:host       "localhost"
                       :port       (int (get-in state [:redis :port]))
                       :max-active 5 :timeout 1000 :group-name (str (System/currentTimeMillis))}
   :conf              {:use-earliest        true
                       :work-calculate-freq 200}})

(defn create-test-kafka-node-service
  "Must be called with what startup-resources returns"
  [state topics]
  (create-kafka-node-service
    (create-kafka-node-service-test-conf state)
    topics))

(defn create-db-tables
  "Create the pseidon-etl tables used for testing
   db-service: A db-service component started from pseidon-etl.db-service"
  [db-service]
  (db/with-connection db-service
                      (j/do-commands
                        "DROP TABLE IF EXISTS kafka_logs"
                        "CREATE TABLE IF NOT EXISTS kafka_logs (log VARCHAR(20), type VARCHAR(10), log_group VARCHAR(10), enabled INTEGER)"
                        "DROP TABLE IF EXISTS kafka_formats"
                        "CREATE TABLE IF NOT EXISTS kafka_formats (log VARCHAR(20), format VARCHAR(10), output_format VARCHAR(10), hive_table_name VARCHAR(100), hive_url VARCHAR(100), hive_user VARCHAR(100), hive_pwd VARCHAR(100))")))


(defn create-test-database
  "Create and return a db-service backed by a in memory db"
  []
  (db-service/create-database "mem:testdb" "SA" "" :driver "org.hsqldb.jdbcDriver" :protocol "hsqldb"))

(defn test-db-service
  "Returns a started test db service"
  []
  (component/start (create-test-database)))

(defn writer-service [abs-timeout]
  (component/start (writer/writer-service
                     {:data-dir (str "target/tests//" (System/currentTimeMillis))
                      :writer {:rollover-abs-timeout abs-timeout}})))

(defn with-test-etl-service
  "Runs the function within a application context where services are test services
   f is called as (f app)"
  [topics f]
  (let [resources (startup-resources)

        ;;;setup test inmemory db with tables created
        db-service (test-db-service)
        _ (do (create-db-tables db-service))

        kafka-node-service (create-test-kafka-node-service resources topics)
        kafka-client-service (create-test-kafka-client-service resources)

        app-resources (app/create-app-components
                        {}
                        ;;service overrides
                        {:db-service           db-service
                         :kafka-node-service   kafka-node-service
                         :kafka-client-service kafka-client-service
                         :nrepl-service        (pseidon-etl.nrepl-service/create-noop-nrepl-service)})]

    (try
      (f app-resources)
      (finally
        (app/stop-app-components app-resources)))))


(defn input-stream [^File file]
  (cond
    (.endsWith (.getName file) ".gz") (java.util.zip.GZIPInputStream. (java.io.FileInputStream. file))
    :else
    (java.io.FileInputStream. file)))

(defn read-msgs [dir]
  (let [xf (comp

             ;;; filter our directories
             (filter #(.isFile ^File %))

             ;;filter out temporary files
             (filter #(not (NumberUtils/isNumber (FilenameUtils/getExtension (.getName ^File %)))))

             (mapcat #(line-seq (clojure.java.io/reader (input-stream %)))))]
    (transduce xf
               conj
               (file-seq (clojure.java.io/file dir)))))