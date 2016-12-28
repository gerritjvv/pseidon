(ns pseidon-hdfs.hdfs-copy-service-test
  (:require
    [pseidon-hdfs.hdfs-copy-service :as copy-service]
    [pseidon-hdfs.test-utils :as test-utils]
    [clojure.java.io :as io]
    [clojure.test :refer :all]
    [pseidon-hdfs.hive :as hive])
  (:use midje.sweet
        conjure.core)
  (:import (org.apache.hadoop.hdfs MiniDFSCluster)))


(defn start-resources []
  (test-utils/startup-resources))

(defn shutdown-resources [ctx]
  (test-utils/shutdown-resources ctx))

(defn file-system [{:keys [cluster]}]
  (.getFileSystem ^MiniDFSCluster cluster))

(defn create-test-file []
  (let [dir (str "target/test/copyservicetest/" (System/currentTimeMillis))
        file (str dir "/test-2016-03-20-09.gz")]
    (.mkdirs (io/file dir))
    (spit file "ABCTEST")
    file))

(defn create-unique-test-file [dir]
  (let [file (str dir "/myfile-2016-03-20-09." (System/currentTimeMillis) ".gz")]
    (.mkdirs (io/file dir))
    (spit file "ABCTEST")
    file))

(defn re-create-file [file]
  (spit file "ABCTEST")
  file)

(defn local-file-exists? [local-file]
  (.exists (io/file local-file)))

(defn test-copy-no-existing-dir [{:keys [hdfs]}]
  (let [state {}
        local-file (create-test-file)
        hive-called (atom nil)
        hive-ctx (hive/create-ctx)]

    ;;;;;;;; copy the local-file to the calculated remote file destination
    ;;;;;;;; the remote file is /log/raw2/test/dt=20160320/hr=2016032009/test-2016-03-20-09.gz
    (stubbing [
               pseidon-hdfs.hive/with-hive-conn (fn [hive-ctx hive-url hive-port hive-user hive-pwd f]
                                                  (f nil))
               pseidon-hdfs.hdfs-copy-service/add-hive-partition
               (fn [state hive-conn hive-db-name hive-table-name fs parent-path file topic]
                 (swap! hive-called (constantly [hive-table-name (copy-service/file-name->date file)])))

               pseidon-hdfs.hdfs-copy-service/_write-copy-metrics (fn [& _])]

              (copy-service/file->hdfs
                {}
                hive-ctx
                nil
                (file-system hdfs)
                state
                {:file local-file :file-ok true}
                nil)

              ;; hive add partition was called
              ;; check the file has been copied
              ;; and the local file does not exist anymore
              (prn "hive-called: " @hive-called)
              (and
                (=
                  @hive-called ["test" "2016-03-20-09"])
                (pseidon-hdfs.hdfs-util/hdfs-path-exists? (file-system hdfs) (str "/log/raw2/test/date=20160320/hour=2016032009/" copy-service/host-name "-test-2016-03-20-09.gz"))
                (not (local-file-exists? local-file))))))


(defn test-copy-file-exists-existing-dir [{:keys [hdfs]}]
  (let [state {}
        local-file (create-test-file)
        hive-called (atom nil)
        hive-ctx (hive/create-ctx)

        copy-f #(copy-service/file->hdfs
                  {}
                 hive-ctx
                 nil
                 (file-system hdfs)
                 state
                 {:file local-file :file-ok true}
                 nil)]

    ;;;;;;;; copy the local-file to the calculated remote file destination
    ;;;;;;;; the remote file is /log/raw2/test/dt=20160320/hr=2016032009/test-2016-03-20-09.txt
    (stubbing [pseidon-hdfs.hive/add-partition              ;;add [table datestr] to hive-called
               (fn [_ _ table datestr]
                 (swap! hive-called (constantly [table datestr])))

               pseidon-hdfs.hdfs-copy-service/_write-copy-metrics (fn [& _])]

              (copy-f)

              ;;set hive copy to nil
              (swap! hive-called (constantly nil))

              ;;recreate the file and copy again
              (let [local-file (re-create-file local-file)]
                (copy-f)

                ;;test that the file exists, and the local file has been deleted
                ;;also test that the hive add partition was not called
                (and
                  (=
                    @hive-called nil)
                  (pseidon-hdfs.hdfs-util/hdfs-path-exists? (file-system hdfs) (str "/log/pseidon/test/date=20160320/hour=2016032009/" copy-service/host-name "-test-2016-03-20-09.gz"))
                  (not (local-file-exists? local-file)))))))

(defn test-copy-bad-file-to-quarantine [{:keys [hdfs]}]
  (let [state {}
        local-file (create-test-file)
        hive-ctx (hive/create-ctx)

        copy-f #(copy-service/file->hdfs
                  {}
                 hive-ctx
                 nil
                 (file-system hdfs)
                 state
                 {:file local-file :file-ok nil}
                 nil)]

    (stubbing [pseidon-hdfs.hdfs-copy-service/_write-copy-metrics (fn [& _])]

              (copy-f)

              ;;recreate the file and copy again
              (let [local-file (re-create-file local-file)]
                (copy-f)

                ;;test that the file exists, and the local file has been deleted
                ;;also test that the hive add partition was not called
                (and
                  (pseidon-hdfs.hdfs-util/hdfs-path-exists? (file-system hdfs) (str "/tmp/pseidon-quarantine/test/date=20160320/hour=2016032009/" copy-service/host-name "-test-2016-03-20-09.gz")))))))

(deftest copy-service-test
  ;; suspend test till a way of testing with a nil hive url or providing the url can be done
         ;(facts "Test copy without directory created"
         ;       (let [ctx (start-resources)]
         ;         (test-copy-no-existing-dir ctx) => true
         ;         (shutdown-resources ctx)))

         (facts "Test copy with directory created"
                (let [ctx (start-resources)]
                  (test-copy-file-exists-existing-dir ctx) => true
                  (shutdown-resources ctx)))

         (facts "Test copy with corrupt files are sent to quarantine"
                (let [ctx (start-resources)]
                  (test-copy-bad-file-to-quarantine ctx) => true
                  (shutdown-resources ctx))))
