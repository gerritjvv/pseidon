(ns
  ^{:doc "Test that the file wait time is respected by the gz-file-seq function"}
  pseidon-hdfs.hdfs-file-wait-time-test
  (:require
    [pseidon-hdfs.hdfs-copy-service :as copy-service]
    [clojure.test :refer :all]
    [clojure.java.io :as io])
  (:import (java.io File)))

(defn create-test-file [dir]
  (let [^File dir-obj (io/file (str "target/tests/hdfs-file-wait-time-test/" dir))]

    (assert (.mkdirs dir-obj))
    (File/createTempFile "test" ".parquet" dir-obj)))

(deftest test-file-modification-time-seen
  (let [^File file (create-test-file "test-file-modification-time-seen")]

    (.setLastModified file (- (System/currentTimeMillis) 60000))
    (is
      (= (count (copy-service/gz-file-seq {:file-wait-time-ms 10000} (.getParent file)))
         1))))

(deftest test-file-modification-time-none
  (let [^File file (create-test-file "test-file-modification-time-none")]

    (is
      (= (count (copy-service/gz-file-seq {:file-wait-time-ms 10000} (.getParent file)))
         0))))

