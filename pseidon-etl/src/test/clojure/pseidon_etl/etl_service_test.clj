(ns pseidon-etl.etl-service-test
  (:require
    [midje.sweet :refer :all]
    [clojure.test :refer :all]
    [kafka-clj.consumer.node :refer [create-kafka-node-service read-msg!]]
    [pseidon-etl.test-utils :as test-utils]
    [pseidon-etl.db-service :refer [with-connection]]
    [kafka-clj.client :as client]
    [clojure.java.jdbc :as j]
    [pseidon-etl.db-service :as db]
    [pseidon-etl.writer :as writer])
  (:import (java.util.concurrent TimeoutException)))

;
;(defn timeout? [timeout-ms ts]
;  (> (- (System/currentTimeMillis) (long ts))
;     timeout-ms))
;
;(defn rand-chars [len]
;  (apply str (take len (repeatedly #(char (+ (rand-int 90) 65))))))
;
;(defn send-messages [app-resources topic n]
;  (let [c (get-in app-resources [:kafka-client :client])]
;    (dotimes [i n]
;      (prn "sending " i)
;      (client/send-msg c topic (.getBytes (str (rand-chars 10)))))))
;
;(defn wait-for-data-files
;  "Look for files in the writer-service basedir, loop till either messages are found or timeout"
;  [{:keys [writer-service]} timeout-ms]
;  {:pre [writer-service (writer/base-dir writer-service)]}
;  (let [ts (System/currentTimeMillis)
;        base-dir (writer/base-dir writer-service)
;        f #(test-utils/read-msgs base-dir)]
;
;    (loop [msgs (f)]
;      (if (take 1 msgs)
;        msgs
;        (if (timeout? timeout-ms ts)
;          (throw (TimeoutException.))
;          (do
;            (prn "waiting for files to appear in " base-dir)
;            (Thread/sleep 2000)
;            (recur (f))))))))
;
;(defn read-messages [app-resources topic n]
;  (let [msgs (wait-for-data-files app-resources 60000)]
;    (prn "found " (count msgs) " msgs")))
;
;(defn insert-test-topics [{:keys [db]} & topics]
;  {:pre [db (first topics)]}
;
;  (db/with-connection db
;                      (apply
;                        j/do-commands
;                        (for [topic topics]
;                          (str "INSERT INTO pseidon_logs (log, format, output_format, log_group, enabled)
;                                VALUES('" topic "','txt:sep=tab;ts=0','json', '" test-utils/default-etl-group "', true)")))))
;
;(defn send-test-messages [app-resources topic n]
;  (insert-test-topics app-resources topic)
;  (send-messages app-resources topic n)
;  (read-messages app-resources topic n))
;
;
;(defn run-test [topic]
;  (test-utils/with-test-etl-service [topic] #(send-test-messages % topic 100)))
