(ns pseidon.test.hdfs.core.hdfsprocessor_test
  (:use [pseidon.hdfs.core.hdfsprocessor]
        [midje.sweet]
        [pseidon.core.conf]
        [pseidon.core.datastore]
        )
  
          
   (:import (org.apache.commons.lang StringUtils)
            (java.io File)
            (org.apache.hadoop.fs Path FileUtil FileSystem)
            (org.apache.hadoop.conf Configuration)
           )
  )
  
(def zk-server (org.apache.curator.test.TestingServer.))
(def zk-url (str "localhost:" (.getPort zk-server)))

(load-default-config!)       
(set-conf! :zk-url zk-url)



(defn insert-test-files 
  ([name-space file-name n]
  (loop [i n]
    (if (> i 0)
      (do
        (set-data! name-space (abs-path (str file-name i)) "")
        (recur (dec i))))))
   ([name-space n]
     (insert-test-files name-space "file-" n))
                   )

(facts "Test datasource read files"
       
       
       (fact "Test list-files"
             (let [name-space "test-list-files"
                   n 10]
               (insert-test-files name-space n)
               
              
               (set (list-files :name-space name-space)) => #{"file-1" "file-2" "file-3" "file-4" "file-5" "file-6" "file-7" "file-8" "file-9" "file-10"}
               
               ))
       
       
       (fact "Test list-files with paths"
             (let [name-space "test-list-files-with-paths"
                   n 10]
               (insert-test-files name-space "/mydir/abc/test" n
                                  )
               (set (list-files :name-space name-space)) => #{"/mydir/abc/test1" "/mydir/abc/test2" "/mydir/abc/test3" "/mydir/abc/test4" "/mydir/abc/test5" "/mydir/abc/test6" "/mydir/abc/test7" "/mydir/abc/test10" "/mydir/abc/test8" "/mydir/abc/test9"}
               
               ))
       )
