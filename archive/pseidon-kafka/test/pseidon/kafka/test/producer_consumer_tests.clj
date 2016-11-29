(ns pseidon.kafka.test.producer-consumer-tests
  (:require [pseidon.kafka.test.util :refer [zookeeper kafka-server]]
            [pseidon.core.conf :refer [set-conf!]]
            [clojure.tools.logging :refer [info error]])
  (:use midje.sweet
        pseidon.kafka.consumer
        pseidon.kafka.producer
        pseidon.kafka.util
  ))

(use 'pseidon.kafka.producer)
(use 'pseidon.kafka.consumer)
(use 'pseidon.kafka.test.util)

(def zk (zookeeper))

(def kafka (kafka-server))
(def p (producer {"metadata.broker.list" "localhost:9092"}))
(def c (consumer {"zookeeper.connect" "localhost:2181" "group.id" "1"}))

(facts "Test produce consume"
       
       (fact "Test DataSink DataSource"
             (set-conf! "metadata.broker.list" "localhost:9092")
             (set-conf! "zookeeper.connect" "localhost:2181")
             (set-conf! "group.id" "1")
             
             
             (let [kafka-conf (get-kafka-conf)
                   {:keys [reader-seq] } (load-datasource kafka-conf)
                   {:keys [writer] } (load-datasink kafka-conf) ]
               
               (dotimes [n 10000]
                  (writer {:topic "test" :val "test"}))
               
                (let [msg (take 10 (reader-seq "test"))]
                 (count msg) => 10
                )
             )))
       

