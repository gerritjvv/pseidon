(ns pseidon-hdfs.test-utils
  (:import
    (org.apache.hadoop.conf Configuration)
    [org.apache.hadoop.hdfs MiniDFSCluster]
    (org.apache.hive.service.server HiveServer2)
    (org.apache.hadoop.hive.conf HiveConf))
  (:require [pseidon-hdfs.db-service :refer :all :as db-service]
            [pseidon-hdfs.mon :as mon]
            [pseidon-hdfs.lifecycle :as hdfs-lifecycle]
            [pseidon-hdfs.hdfs-copy-service :as copy-service]
            [clojure.tools.logging :refer [info]]
            [com.stuartsierra.component :as component]
            [pseidon-hdfs.hive :as hive]
            [clojure.java.jdbc :as j]))

(defonce PSEIDON_LOGS_TABLE_CREATE "CREATE TABLE IF NOT EXISTS pseidon_logs
                                                        (log VARCHAR(20),
                                                         format VARCHAR(10), output_format VARCHAR(10),
                                                         hive_table_name VARCHAR(100), hive_url VARCHAR(100),
                                                         hive_user VARCHAR(100), hive_password VARCHAR(100),
                                                         base_partition VARCHAR(100),
                                                         log_partition VARCHAR(100),
                                                         quarantine VARCHAR(100)
                                                         )")

(defn invoke-private-method [obj fn-name-string & args]
  (let [m (first (filter (fn [x] (.. x getName (equals fn-name-string)))
                         (.. obj getClass getDeclaredMethods)))]
    (. m (setAccessible true))
    (. m (invoke obj args))))

(defn shutdown-hdfs [{:keys [^MiniDFSCluster cluster]}]
  (doto cluster .shutdownDataNodes))

(defn startup-hdfs
  "Startup a mini hdfs cluster and wait for it to become active
  Returns {:cluster hdfs-clustre :uri namenode-uri}"
  []
  (let [conf (Configuration.)
        hdfs-cluster (MiniDFSCluster. conf 1 true nil)
        ]
    (.waitActive hdfs-cluster)
    {:cluster hdfs-cluster :uri (-> hdfs-cluster .getFileSystem .getUri .toString)}))

(defn hdfs-uri
  "Get the hdfs uri from what is returned by startup-hdfs"
  [{:keys [uri]}]
  uri)


(defn create-test-database []
  (let [db (create-database " mem " (str "target/mydb-" (System/currentTimeMillis)) "SA" " " :adapter :hsqldb)
        db' (start-db db)]

    (db-service/with-connection db'
                                (j/db-do-commands conn
                                                  PSEIDON_LOGS_TABLE_CREATE))
    db))



(defn create-streaming-db [^HiveServer2 server]
  (let [conn (hive/hive-connection "localhost:10000")]
    (hive/create-db conn "pseidon" (str "target/pseidontest/" (System/currentTimeMillis)))
    server))


(defn create-table
  "Creates a test pseidon.$table-name with column tname:string and partitioned by dt, hr"
  [^HiveServer2 server table-name]
  (let [conn (hive/hive-connection "localhost:10000")]
    (hive/sql-exec conn (str "CREATE TABLE IF NOT EXISTS pseidon." table-name " (tname string) PARTITIONED BY (dt string, hr string)"))
    server))

(defn ^HiveConf hive-conf []
  (doto
    (HiveConf.)
    (.setInt "port" 10000)))

(defn startup-resources []
  {:hdfs (startup-hdfs)})

(defn wait-for [server ms]
  (Thread/sleep ms)
  server)

(defn add-hdfs-uri [hdfs conf]
  (merge conf
         {:hdfs-conf
          {"fs.default.name" (hdfs-uri hdfs)
           "fs.defaultFS"    (hdfs-uri hdfs)
           "fs.hdfs.impl"    "org.apache.hadoop.hdfs.DistributedFileSystem"}}))

(defn create-hive-resources []
  (doto (HiveServer2.) (.init (hive-conf)) .start (wait-for 2000)
                       create-streaming-db
                       (create-table "mytopic_test")
                       (create-table "hive_metrics")
                       (create-table "hdfs_metrics")))

(defn shutdown-hive-resources [^HiveServer2 hive-resources]
  (.stop hive-resources))

(defn hive-url [^HiveServer2 hive-resources]
  (invoke-private-method hive-resources "getServerInstanceURI"))

(defn startup-resources-all [conf]
  (let [app-status (hdfs-lifecycle/app-status)

        ;;create a hive server instance, and create the topic, hive and hdfs metric tables required by the add hive partition code in the hdfs copy service
        hive-server (create-hive-resources)
        hdfs (startup-hdfs)]
    (component/start-system
      {
       :hive-server       hive-server
       :app-status        app-status
       :hdfs              hdfs
       :monitor-service   (mon/create-monitor-service conf)
       :db                (component/start (create-test-database))
       :hdfs-copy-service (component/using
                            (copy-service/create-hdfs-copy-service (assoc
                                                                     (add-hdfs-uri hdfs conf)
                                                                     ;;for testing test file-wait-time-ms to 0
                                                                     :file-wait-time-ms 0) app-status)
                            [:db :monitor-service])})))

(defn shutdown-resources [{:keys [hdfs]}]
  (shutdown-hdfs hdfs))

(defn stop-resources-all [{:keys [hdfs monitor-service hdfs-copy-service db]}]
  (component/stop hdfs-copy-service)
  (component/stop db)
  (component/stop monitor-service)
  (shutdown-hdfs hdfs))
