(ns pseidon.test.hdfs.core.partition-updater-test
    (:require [pseidon.hdfs.core.partition-updater :refer [error? with-retry with-timeout]]
              [pseidon.hdfs.core.hdfsprocessor :refer [file->hdfs]]
             [pseidon.core.conf :refer [set-conf!]])
    (:use [midje.sweet])
    (:import [org.apache.hadoop.conf Configuration]
             [org.apache.hadoop.hdfs MiniDFSCluster]
             [java.io File]))


(facts "Test error?"
       (error? {:exit 0}) => false
       (error? {:exit 1}) => true

       )

(facts "Test retry with failures tries all"
       (let [[n res] (with-retry 10 "foo" "f")]
            n => 10
            res => #(not= (:exit %) 0)))

(facts "Test retry without fail runs once"
       (let [[n res] (with-retry 10 "ls" "-lh" ".")]
            n => 0
            res => #(= (:exit %) 0)))

;with-timeout [^Long timeout-ms f args & {:keys [notify-f] :or {notify-f notify-error}}
(fact "Test with-timeout function timeout"
      (let [f #(Thread/sleep %)
            notified (atom false)
            res (with-timeout 100 #(do (Thread/sleep %) 1) [10000] :notify-f (fn [x] (swap! notified (fn [x1] true))))]
           res => #(not= (:exit %) 0)
           @notified => true
           ))

(fact "Test with-timeout no timeout"
      (let [f #(Thread/sleep %)
            notified (atom false)
            res (with-timeout 10000 #(do (Thread/sleep %) {:exit 0 :code 1}) [100] :notify-f (fn [x] (swap! notified (fn [x1] true))))]
           res => {:exit 0 :code 1}
           @notified => false
           ))



(defn get-dfs-uri [ dfs-cluster]
       (-> dfs-cluster .getFileSystem .getUri .toString))


(defonce
  test-cluster
  (delay
    (let [conf (Configuration.)
          hdfs-cluster (MiniDFSCluster. conf 1 true nil)
          ]
         (.waitActive hdfs-cluster)
         (set-conf! :hdfs-url (get-dfs-uri hdfs-cluster))
         hdfs-cluster)))


(facts "Test with cluster"
      (let [updated (atom nil)]
           @test-cluster
           (file->hdfs "test" "test" "t.txt" "file_hddfs_test/remotefile.txt" :partition-updater (fn [file] (swap! updated (fn [& args] file))))
           (Thread/sleep 1000)
           @updated => "file_hddfs_test"))
