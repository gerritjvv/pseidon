(ns pseidon.kafka.consumer
    (:require
              [clojure.tools.logging :refer [info error ]]
              [kafka-clj.consumer.node :as kfk]
              [kafka-clj.metrics :refer [ report-consumer-metrics ]]
              [clojure.tools.logging :refer [info]])
    )

(defn add-topic [node topic]
  (kfk/add-topics! node [topic]))

(defn remove-topic [node topic]
  (kfk/remove-topics! node [topic]))

(defn create-consumer [bootstrap-brokers topics conf]
  (info "!!!!!!!!!!!!!!!!!!!!!! Bootstrap-brokers " bootstrap-brokers " topics " topics)
  ;(report-consumer-metrics :csv :freq 10 :dir (get conf :kafka-reporting-dir "/tmp/kafka-consumer-metrics"))
  (info "config " conf)

  (kfk/create-node! (merge conf
                       {:bootstrap-brokers bootstrap-brokers
                        :use-earliest true :metadata-timeout 60000
                        :consume-step (get conf :consume-step 10000)
                        :conf conf
                        }) topics))

(defn close-consumer [c]
  (kfk/shutdown-node! c))

(defn messages [c ]
  "Returns a lazy sequence that will block when data is not available"
    (repeatedly #(kfk/read-msg-batch! c)))
