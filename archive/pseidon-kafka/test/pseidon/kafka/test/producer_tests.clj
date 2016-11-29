(ns pseidon.kafka.test.producer-tests
  (:import [kafka.message MessageAndMetadata MessageAndOffset]
           [kafka.producer KeyedMessage])
  (:require [pseidon.core.conf :refer [get-conf set-conf!]]
            [pseidon.kafka.util :refer [create-message]])
  (:use midje.sweet
        pseidon.kafka.producer
        ))

(facts "Test utils"
       
      
       (fact "to-bytes"
             (vec (toBytes "hi")) => (vec (.getBytes "hi" "UTF-8"))
             (vec (toBytes (.getBytes "hi" "UTF-8"))) => (vec (.getBytes "hi" "UTF-8"))
            
            )
       (fact "create-message"
             (prn "message " (create-message {:topic "a" :k "b" :val 1} ))
             (create-message {:topic "a" :k "b" :val 1} ) => (create-message "a" "b" 1)
             (create-message {:topic "a" :val 1} ) => (create-message "a" 1)
             (vec (map #(apply create-message %) [["z" {:a 1 }]  ["x" {:b 2} ]] )) => [ (create-message "z" {:a 1}) (create-message "x" {:b 2}) ]
             
             ))
                           
