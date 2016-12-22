(ns pseidon.kafka-hdfs.logpartition
  (:require 
   [pseidon.core.conf :refer [get-conf2]]
   [pseidon.core.utils :refer [buffered-select fixdelay]]
   [clojure.java.jdbc :as sql]
   [clojure.tools.logging :refer [debug info error]]
   [clojure.java.io :as io]
   [clj-json.core :as json]
   [clj-tuple :refer :all]           
   [clojure.string] 
   [pseidon.core.conf :refer [get-conf2]]
   [clj-time.coerce :refer [from-string from-long to-long from-long]]
   [clj-time.format :refer [unparse formatter]]
   [clj-time.core :as t]
    )
 )

(def dblog {:classname  "com.mysql.jdbc.Driver"
            :subprotocol "mysql"
            :subname (get-conf2 :hdfs-partition-db-subname "//localhost:3306/db")
            :user (get-conf2 :hdfs-partition-db-user "pseidon")
            :password (get-conf2 :hdfs-partition-db-password "pseidon")})


(defonce log-partition-map (ref {}))

(defn update-log-partition-map [ m ] 
  (dosync 
    (alter log-partition-map (fn [state] m)))
)


(defn refresh-map
  "load topics from the kafka-logs-partitions table"
  []
 (if-let [rs (sql/query dblog ["select logname,CONCAT(basepartition,'/',logpartition) as partition from hdfs_log_partitions "]) ]
  (update-log-partition-map (reduce (fn [out {:keys [logname partition]}]
                          (assoc out (str logname) (str partition))) {} rs))))
      
(def get-log-partition-map (delay 
                              (try
                                (do (refresh-map)
                                  (fixdelay (get-conf2 "hdfs-log-partition-map-refresh" 900000) (refresh-map))) 
                               (catch Exception e (error e e)))
                            log-partition-map))      

(defn get-log-hdfs-partition[log-name]
  ;(info "logname:"  log-name)
  ;(info "hdfs partition:" (-> get-log-partition-map force deref (get log-name)))
  (-> get-log-partition-map force deref (get log-name))
)
