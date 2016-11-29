(ns pseidon.test.core.testutils-test
  (:import [java.io File])
  (:use midje.sweet
        pseidon.test.core.utils))


(facts "Test test utilities"
       
       (fact "Test create-tmp-dir"
             (let [path (create-tmp-dir "abc")]
               (type path) => File
               (.exists (File. "target/abc")) => true
               (.exists path) => true))
       (fact "Test create-tmp-dir with delete-on-exit"
             (let [path (create-tmp-dir "abc2" :delete-on-exit true)]
               (type path) => File
               (.exists (File. "target/abc2")) => true
               (.exists path) => true)))
             
             

