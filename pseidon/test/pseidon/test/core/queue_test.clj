(ns pseidon.test.core.queue-test
  
  (:require [pseidon.core.queue :refer :all])
  (:use midje.sweet)
  (:import [java.util.concurrent Executors TimeUnit])
  
  )

(facts "Test pub sub"
       
       (fact "Test pub sub"

             (let [
                   messages (range 10)
                   ch (channel "test")
                   received-msgs (ref [])
                   ]
               (consume ch (fn [msg]
                             (dosync
                               (commute received-msgs conj msg))))
                               
               (doseq [msg messages]
                 (prn "publish " msg)
                 (publish ch msg))
               
               (Thread/sleep 1000)
               
               (prn "messages " @received-msgs)
               (= (sort @received-msgs) messages) => true
               
               ))
       (fact "Test pub sub multi threaded"

             (let [
                   service (Executors/newCachedThreadPool)
                   ch (channel "test")
                   received-msgs (ref [])
                   ]
               (consume ch (fn [msg]
                             (dosync
                               (commute received-msgs conj msg))))
               
               (doseq [msg (range 10)]
                 (.submit service (reify Runnable
                                    (run [this]
                                      (dotimes [i 100] 
                                        (publish ch (str msg "-" i)))))))
               
               (.shutdown service)
               (.awaitTermination service 1 TimeUnit/SECONDS)
               (Thread/sleep 1000)
               
               (count @received-msgs) => 1000
               
               )))
               
