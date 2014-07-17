(ns pseidon.test.hdfs.core.partition-updater-test
    (:require [pseidon.hdfs.core.partition-updater :refer [error? with-retry with-timeout]]
             [pseidon.core.conf])
    (:use [midje.sweet]))


(facts "Test error?"
       (error? {:exit 0}) => false
       (error? {:exit 1}) => true

       )

(facts "Test retry with failures tries all"
       (let [[n res] (with-retry 10 "foo" "f")]
            n => 10
            res => #(not= (:exit %) 0)))

(facts "Test retry without fail runs once"
       (let [[n res] (with-retry 10 "ls" "-lh" ".")]
            n => 0
            res => #(= (:exit %) 0)))

;with-timeout [^Long timeout-ms f args & {:keys [notify-f] :or {notify-f notify-error}}
(fact "Test with-timeout"
      (let [f #(Thread/sleep %)
            notified (atom false)
            res (with-timeout 100 #(do (Thread/sleep %) 1) [10000] :notify-f (fn [x] (swap! notified (fn [x1] true))))]
           res => #(not= (:exit %) 0)
           @notified => true
           ))
