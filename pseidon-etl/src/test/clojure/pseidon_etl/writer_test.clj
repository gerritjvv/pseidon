(ns pseidon-etl.writer-test
    (:use midje.sweet)
  (:require
    [pseidon-etl.writer :as writer]
    [clojure.test :refer :all]
    [clojure.tools.logging :refer [info]]
    [clojure.java.io :as io]
    [pjson.core :as pjson]
    [pseidon-etl.formats :as formats])
  (:import (java.io File)
           (java.util Arrays)))


(deftest writer-test



  (defn _create-test-dir []
    (let [file (io/file (str "target/test/writer-test/dir-" (System/currentTimeMillis)))]
      (.mkdirs ^File file)
      (.getAbsolutePath file)))

  (defn- file-lines [base-dir prefix]
    (mapcat line-seq (map io/reader (filter #(re-find (re-pattern prefix) (.getName %)) (file-seq (io/file base-dir))))))

  (fact "Test write file"
        (let [base-dir (_create-test-dir)
              ts (System/currentTimeMillis)
              writer-ctx (writer/open-writer {:codec :none :base-dir base-dir
                                              :rollover-size 100
                                              :rollover-timeout 1000
                                              :rollover-abs-timeout 1000})]

          (writer/write-msgs {} writer-ctx (take 1000 (repeatedly #(writer/wrap-msg "test" (formats/->FormatMsg (formats/->Format "txt" {}) ts (.getBytes "HI") {:msg "HI"})))))
          (writer/write-msgs {}  writer-ctx (take 1000 (repeatedly #(writer/wrap-msg "test" (formats/->FormatMsg (formats/->Format "txt" {}) ts (.getBytes "HI") {:msg "HI"})))))
          (Thread/sleep 1000)
          (writer/close-writer writer-ctx)

          (prn "test dir " base-dir)

          (count (file-lines base-dir "test")) => 2000)))