(ns pseidon-etl.formats-test
  (:require [pseidon-etl.formats :as formats]
            [clojure.test :refer :all]))


(deftest test-parse-format
  (is (= (formats/parse-format "txt:ts=0;sep=byte1") (formats/->Format "txt" {"ts" 0 "sep" "byte1"}))))

(deftest test-bts->msg

        (let [ts  (System/currentTimeMillis)
              input-str (str ts \u0001 "hi")
              bts (.getBytes input-str)
              f   (formats/parse-format "txt:ts=0;sep=byte1")
              msg (formats/bts->msg f bts)

              msg-str (formats/msg->string f msg)]


          (is (= (into [] (:msg msg)) [(str ts) "hi"]))
          (is (= (:ts msg) ts))
          (is (= (:bts msg) bts))

          (is (= input-str msg-str))))