(ns pseidon.kafka.producer
  (require [kafka-clj.client :refer [create-connector send-msg close]]
	   [clojure.tools.logging :refer [info]]))


(defn producer
  "Creates a Producer. m is the configuration
   metadata.broker.list : \"server:port,server:port\""
  [conf]
  (create-connector (get conf :bootstrap-brokers) conf))

(defn close-producer [p]
  (close p))

(defn send-message
  [producer message]
   (send-msg producer (:topic message) (:bts message)))


(defn send-messages
  [producer messages]
  (doseq [msg messages]
    (send-message producer msg)))

