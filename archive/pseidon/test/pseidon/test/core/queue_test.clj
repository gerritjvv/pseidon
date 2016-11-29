(ns pseidon.test.core.queue-test
  
  (:require [pseidon.core.queue :refer :all]
            [pseidon.core.message :as msg])
  (:use midje.sweet)
  (:import [java.util.concurrent Executors TimeUnit TimeoutException]
           [pseidon.util Bytes DefaultDecoders])
  
  )

(facts "Test pub sub"
       (fact "Test send and read message instance"
             (let [ch (channel "test")
                   ;create-message [bytes-f ds ids topic accept ts priority]
                   msg (msg/create-message (Bytes/toBytes "hi") "ds" "ids" "hi" true 0 1)
                   received-msgs (ref [])
                   ]
                 (consume ch (fn [msg]
                             (dosync
                               (commute received-msgs conj msg))
                             )
                             :decoder msg/MESSAGE-DECODER)

                 (publish ch msg)
                 (Thread/sleep 500)
                 (String. (:bytes-seq msg)) => (String. (:bytes-seq (first @received-msgs)))
                 (:ds msg) => (:ds (first @received-msgs))
                 (:topic msg) => (:topic (first @received-msgs))
                 (:ids msg) => (:ids (first @received-msgs))
                 (:ts msg) => (:ts (first @received-msgs))
                 (:accept msg) => (:accept (first @received-msgs))
                 (:priority msg) => (:priority (first @received-msgs))
                 
                 
             ))

       (fact "Test pub sub timeout"

             (let [
                   messages (range 100)
                   ch (channel "test" :limit 10 :buffer -1)
                   received-msgs (ref [])
                   ]
                 
               (try 
                 (doseq [msg messages]
                   (prn "publish " msg)
                   (publish ch msg :timeout 10))
                (catch TimeoutException e (prn "planned timeout")))
               (Thread/sleep 1000)
               
               (consume ch (fn [msg]
                             (dosync
                               (commute received-msgs conj msg))
                             )
                             :decoder DefaultDecoders/LONG_DECODER)
               
               (Thread/sleep 1000)
               
               (prn "messages " @received-msgs  " taken " (vec (sort (conj (take 11 messages) 11))))
               ;we conj 11 because this is the message that caused the timeout
               (= (sort @received-msgs) (vec (sort (conj (take 11 messages) 11))))  => true
               (close-channel ch)
               
               ))
       
       (fact "Test pub sub"

             (let [
                   messages (range 10)
                   ch (channel "test")
                   received-msgs (ref [])
                   ]
               (consume ch (fn [msg]
                             (dosync
                               (commute received-msgs conj msg)))
                        :decoder DefaultDecoders/LONG_DECODER)
                               
               (doseq [msg messages]
                 (prn "publish " msg)
                 (publish ch msg))
               
               (Thread/sleep 1000)
               
               (prn "messages " @received-msgs)
               (= (sort @received-msgs) messages) => true
               (close-channel ch)
               
               ))
       
       (fact "Test pub sub multi threaded"

             (let [
                   service (Executors/newCachedThreadPool)
                   ch (channel "test1223" :limit 10000)
                   received-msgs (ref [])
                   ]
               (consume ch (fn [msg]
                             (dosync
                               (commute received-msgs conj msg)))
                        :decoder DefaultDecoders/STR_DECODER)
               
               (doseq [msg (range 100)]
                 (.submit service (reify Runnable
                                    (run [this]
                                      (dotimes [i 100] 
                                        (publish ch (str msg "-" i)))))))
               
               (.shutdown service)
               (.awaitTermination service 1 TimeUnit/SECONDS)
               (Thread/sleep 1000)
               
               (count @received-msgs) => 10000
               (close-channel ch)
               
               ))
       )
      
       
               

