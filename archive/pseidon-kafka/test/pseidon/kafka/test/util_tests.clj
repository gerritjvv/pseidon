(ns pseidon.kafka.test.kafka-util-tests
  (:import [kafka.message MessageAndMetadata MessageAndOffset]
           [kafka.producer KeyedMessage])
  (:require [pseidon.core.conf :refer [get-conf set-conf!]])
  (:use midje.sweet
        pseidon.kafka.util
        ))

(facts "Test utils"
       
       (fact "get-kafka-conf"
             
             (set-conf! :kafka.abc 1)
             (set-conf! :kafka.hi 2)
             
             (get-kafka-conf) => {"abc" 1, "hi" 2}
              
              )
       (fact "create-message"
 
             
            (instance? KeyedMessage (create-message {:topic "a" :k 1 :val (.getBytes "hi")})) => true
            (instance? KeyedMessage (create-message "a" (.getBytes "hi"))) => true
            (instance? KeyedMessage (create-message "a" 1 (.getBytes "hi"))) => true
            
            ))
                           
