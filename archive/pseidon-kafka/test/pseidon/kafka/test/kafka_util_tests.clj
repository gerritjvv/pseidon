(ns pseidon.kafka.test.kafka-util-tests
  (:import [kafka.message MessageAndMetadata MessageAndOffset])
  (:use midje.sweet
        pseidon.kafka.kafka-util
        ))

(facts "Test kafka_util"
       
       (fact ""

              (let [{:keys [topic offset partition key value] } (to-clojure (MessageAndMetadata. "test", "test2", "test3", 1, 10)) ]
                    topic => "test3"
                    offset => 10
                    partition => 1
                    key => "test"
                    value => "test2"
              
              )
              
              (let [{:keys [topic offset partition key value] } (to-clojure (to-clojure (MessageAndMetadata. "test", "test2", "test3", 1, 10))) ]
                    topic => "test3"
                    offset => 10
                    partition => 1
                    key => "test"
                    value => "test2"
              
              )
             
             ))

