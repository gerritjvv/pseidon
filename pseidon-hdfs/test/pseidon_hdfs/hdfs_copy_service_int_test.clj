(ns
  ^{:doc "Service integration test for hdfs copy
          Starts an embedded HiveServer, HDFS MiniCluster, Embedded DB and the hdfs copy service"}
  pseidon-hdfs.hdfs-copy-service-int-test
  (:require [pseidon-hdfs.test-utils :as test-utils]
            [clojure.java.io :as io]
            [clojure.test :refer :all])
  (:import (java.io File)
           (java.util.concurrent TimeoutException)))


(defonce ^String TEST-FILE-NAME "/mytopic-test-2016-05-17-00.gz")

(defn create-test-dir []
  (doto
    (File. (str "target/hdfs-copy-service-int-test/" (System/currentTimeMillis)))
    .mkdirs))

(defn create-test-hive-conf
  "Create the test configuration for hive"
  []
  {:local-dir (create-test-dir)
   :hive-host "localhost:10000"
   :copy-threads 1})


(defn start-app
  "Start hive and all other resources to run the hdfs copy service completely"
  []
  (let [conf (create-test-hive-conf)]
    (merge
      {:conf conf}
      (test-utils/startup-resources-all conf))))


(defn stop-app
  "Stop all app services started with start-app"
  [app]
  (test-utils/stop-resources-all app))

(defn create-test-file
  "Create a local test file, that will be copied by the copy service"
  [local-dir]
  (spit (str local-dir TEST-FILE-NAME) "TEST IS A TEST FILE"))

(defn list-rolled-files
  "List all rolled files with the name "
  [local-dir]
  (->>
    local-dir
    io/file
    file-seq
    (filter #(.endsWith (str %) ".gz"))))

(defn timeout?
  "Return true if we should throw a timeout"
  [start-ts timeout-ms]
  (> (- (System/currentTimeMillis) start-ts) timeout-ms))

(defn rolled-files?
  "Return true if any rolled files are still on disk, rolled files == *.gz"
  [local-dir]
  (pos? (count (list-rolled-files local-dir))))

(defn wait-till-file-copied
  "Blocks till there are no more files in local dir ending with .gz or the timeout-ms have passed,
   on  timeout a TimeoutException is thrown"
  [local-dir timeout-ms]
  (let [start-ts (System/currentTimeMillis)]
    (while (rolled-files? local-dir)
      (if (timeout? start-ts timeout-ms)
        (throw (TimeoutException.))
        (Thread/sleep 1000)))))

(defn copy-one-file
  "A single test that starts the app services, writes a test file and then wait for it to be copied, or timeout"
  []
  (let [app (start-app)
        local-dir (get-in app [:conf :local-dir])]
    (create-test-file local-dir)
    (wait-till-file-copied local-dir 30000)
    (stop-app app)))

(deftest test-hdfs-copy-service-component
         (copy-one-file)
         ;;dummy test parameter, this test will throw an exception if the local files are not copied
         (is true))
