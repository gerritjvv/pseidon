(ns pseidon.test.core.conf-test
  
  (:use pseidon.core.conf
        midje.sweet)
  )


(facts "Test configuration methods"
       
       (fact "Test sub conf"
             
             (set-conf! :abc.id 1)
             (set-conf! :abc.f 9)

             (get-sub-conf :abc) => {:id 1 :f 9}
             
             ))