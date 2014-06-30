(ns pseidon.kafka.consumer
    (:require
              [clojure.tools.logging :refer [info error ]]
              [kafka-clj.consumer.node :as kfk]
              [kafka-events-disk.core :as ed]
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

  (let [node
        (kfk/create-node! (merge conf
                                 {:bootstrap-brokers bootstrap-brokers
                                  :use-earliest true :metadata-timeout 60000
                                  :consume-step (get conf :consume-step 10000)
                                  :conf conf
                                  }) topics)]
    (ed/register-writer! node {:path (get conf :kafka-events-path "/tmp/kafka-workunits")})
    node))

(defn close-consumer [c]
  (ed/close-writer! c)
  (kfk/shutdown-node! c))

(defn messages [c ]
  "Returns a lazy sequence that will block when data is not available"
    (kfk/msg-seq-buffered! c :step 1000))
