(ns pseidon-etl.writer-test
    (:use midje.sweet)
  (:require
    [pseidon-etl.writer :as writer]
    [clojure.test :refer :all]
    [clojure.tools.logging :refer [info]]
    [clojure.java.io :as io]
    [pjson.core :as pjson]
    [pseidon-etl.formats :as formats])
    (:import (java.io File)))


(deftest writer-test
  (fact "Test asBytes"

        ;test direct bytes
        (let [bts (byte-array 10)]
          (writer/as-bytes (writer/wrap-msg "test" bts)) => bts)

        ;test msg to bytes
        (let [msg {"a" 1 "b" 2}]
          (pjson/read-str (String. (writer/as-bytes (writer/wrap-msg "test" msg)) "UTF-8")) => msg))


  (fact "Test bytes?"
        (writer/bytes? (byte-array 10)) => true
        (writer/bytes? 1) => false)

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

          (writer/write-msgs {} writer-ctx (take 1000 (repeatedly #(writer/wrap-msg "test" (formats/->FormatMsg ts (.getBytes "HI") "HI")))))
          (writer/write-msgs {}  writer-ctx (take 1000 (repeatedly #(writer/wrap-msg "test" (formats/->FormatMsg ts (.getBytes "HI") "HI")))))
          (Thread/sleep 1000)
          (writer/close-writer writer-ctx)

          (prn "test dir " base-dir)

          (count (file-lines base-dir "test")) => 2000)))