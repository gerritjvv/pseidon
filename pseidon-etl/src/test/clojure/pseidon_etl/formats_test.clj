(ns pseidon-etl.formats-test
  (:require [pseidon-etl.formats :as formats]
            [clojure.test :refer :all])
  (:import (org.apache.commons.lang3 StringUtils)))

(defonce ^String TEST-MSG "5|aasdfghjkl|adfgasdgsd|315532800|dsfsdfdsf")

;(deftest test-parse-format
;  (is (= (formats/parse-format "txt:ts=0;sep=byte1") (formats/->Format "txt" {"ts" 0 "sep" "byte1"}))))
;
;(deftest test-extract-message-from-txt
;
;  (let [topic "testtopic"
;        conf {}
;        ts  (System/currentTimeMillis)
;        input-str (str ts \u0001 "hi")
;        bts (.getBytes input-str)
;        f   (formats/format-descriptor (atom {}) {} (formats/parse-format "txt:ts=0;sep=byte1;msg=1"))
;        msg (formats/bts->msg conf topic f bts)
;
;        msg-str (formats/msg->string conf topic f msg)]
;
;
;    (is (= msg-str "hi"))))
;
;
(defn test-message-extract-helper [format-str expected-ts? expected-msg]
  (let [topic "test"
        msg TEST-MSG
        bts (.getBytes (str msg))
        f (formats/format-descriptor (atom {}) {} (formats/parse-format format-str))
        format-msg (formats/bts->msg {} topic f bts)]

    (is (expected-ts? (:ts format-msg)))
    (is (= (seq (:msg format-msg)) (seq expected-msg)))

    (is (StringUtils/join (:msg format-msg) \|)
        (String. (bytes (formats/msg->bts {} topic f format-msg)) "UTF-8"))))

(deftest test-extract-time-from-txt
  (test-message-extract-helper "txt:sep=pipe;ts=3;msg=1"
                               #(= % 315532800)
                               (into-array String ["aasdfghjkl"])))
;
;(deftest test-extract-time-from-txt
;  (test-message-extract-helper "txt:sep=pipe;ts_sec=3;msg=1"
;                               #(= % (* 315532800 1000))
;                               "aasdfghjkl"))
;
;(deftest test-use-current-time-always-from-txt
;  (test-message-extract-helper "txt:sep=pipe;ts_sec=-1;msg=1"
;                               #(< (- % (System/currentTimeMillis)) 10000) ;;check that the time is within 10 seconds,
;                               ;; the time will never reliably be exrtact current mills as seen in the format method and in this test because the happen at different times
;                               "aasdfghjkl"))
;
;
;(deftest test-extract-whole-message
;  (test-message-extract-helper "txt:sep=pipe;ts_sec=3;msg=-1"
;                               #(= % (* 315532800 1000))
;                               TEST-MSG))
;
;
;(deftest test-array-splice-parser
;  (let [msg (into-array String ["ABC" "EFD"])]
;
;    (is
;      (=
;        ((formats/array-parser ":") msg)
;        msg))
;
;    (is
;      (=
;        (seq ((formats/array-parser "0:") msg))
;        (seq msg)))
;
;
;    (is
;      (=
;        (seq ((formats/array-parser ":-1") msg))
;        (seq msg)))
;
;    (is
;      (=
;        (seq ((formats/array-parser ":-1000") msg))
;        (seq msg)))
;
;    (is
;      (=
;        (seq ((formats/array-parser ":100000") msg))
;        (seq msg)))
;
;    (is
;      (=
;        (seq ((formats/array-parser "0:1") msg))
;        (seq (into-array String ["ABC"]))))
;
;    (is
;      (=
;        (seq ((formats/array-parser "0") msg))
;        (seq (into-array String ["ABC"]))))
;
;    (is
;      (=
;        (seq ((formats/array-parser "1") msg))
;        (seq (into-array String ["EFD"]))))
;    ))