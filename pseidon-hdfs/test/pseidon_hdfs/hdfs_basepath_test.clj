(ns
  ^{:doc "test that the /log/raw2 base path can be overwritten by querying the hdfs_log_partitions table"}
  pseidon-hdfs.hdfs-basepath-test
  (:require [pseidon-hdfs.hdfs-copy-service :as hdfs-copy-service]
            [pseidon-hdfs.db-service :as db-service]
            [pseidon-hdfs.test-utils :as test-utils]
            [com.stuartsierra.component :as component]
            [clojure.test :refer :all]
            [clojure.java.jdbc :as j])
  (:use midje.sweet))


;select base_partition, log_partition, hive_url, hive_table_name from pseidon_logs where log
(def system (atom {}))
(defn start []
  (let [db (component/start (test-utils/create-test-database))]
    (db-service/with-connection db
                                (j/db-do-commands conn
                                                  "CREATE TABLE IF NOT EXISTS pseidon_logs
                                                        (log VARCHAR(20),
                                                         format VARCHAR(10), output_format VARCHAR(10),
                                                         hive_table_name VARCHAR(100), hive_url VARCHAR(100),
                                                         hive_user VARCHAR(100), hive_password VARCHAR(100),
                                                         base_partition VARCHAR(100),
                                                         log_partition VARCHAR(100),
                                                         quarantine VARCHAR(100),
                                                         date_format VARCHAR(100)
                                                         )"))

    ;;note these two commands need to be run separately, on the latest jdbc versions
    ;;in a single sessions the pseidon_logs is not available till the connection session is flushed
    (db-service/with-connection db
                                (j/db-do-commands conn
                                                  "INSERT INTO pseidon_logs (hive_url, base_partition, log_partition, log, quarantine, date_format, hive_password, hive_user) VALUES('jdbc:mysql://localhost:3306/abc', '/newbase/path', 'abc', 'mylog', '/errorlogs', 'date', 'mypwd', 'myuser')"))
    {:db db}))

(defn stop [{:keys [db]}]
  (component/stop db))

(deftest test-base-path
  (with-state-changes [(before :facts (reset! system (start)))

                       (after :facts (if @system (stop @system)))]

                      (fact "Test that load-base-path! returns the base path and partition, if none defaults should be returned"
                            (let [cache (fun-utils.cache/create-loading-cache (partial hdfs-copy-service/load-kafka-partition-config (:db @system)))]
                              (hdfs-copy-service/get-topic-meta cache "mylog") => {:base_partition  "/newbase/path",
                                                                                   :date_format "date",
                                                                                   :hive_db_name    "abc"
                                                                                   :hive_password   "mypwd"
                                                                                   :hive_table_name "mylog"
                                                                                   :hive_url        "jdbc:mysql://localhost:3306/abc"
                                                                                   :hive_user       "myuser"
                                                                                   :log_partition   "abc"
                                                                                   :quarantine      "/errorlogs"}


                              (hdfs-copy-service/get-topic-meta cache "nolog") => {:base_partition  "/log/pseidon"
                                                                                   :date_format "datehour"
                                                                                   :hive_db_name    "pseidon"
                                                                                   :hive_password   ""
                                                                                   :hive_table_name "nolog"
                                                                                   :hive_url        nil
                                                                                   :hive_user       "hive"
                                                                                   :log_partition   "nolog"
                                                                                   :quarantine      "/tmp/pseidon-quarantine"}))))
