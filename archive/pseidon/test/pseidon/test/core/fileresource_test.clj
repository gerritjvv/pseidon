(ns pseidon.test.core.fileresource-test
  (:require [pseidon.core.fileresource :refer [write close-roll default-roll-check new-file-data close-all]]
            [pseidon.core.conf :refer [set-conf!]]
            [clojure.java.io :as io])
  (:import [java.util.concurrent.atomic AtomicBoolean AtomicInteger]
           [java.util.concurrent Executors TimeUnit]
           [java.io File])
  (:use midje.sweet))


(def basedir (let [basedir (str "target/testfilewrite/" (System/nanoTime))]
               (set-conf! :writer-basedir basedir)
               basedir))

(defn gunzip
  [fi]
  (with-open [i (io/reader
                 (java.util.zip.GZIPInputStream.
                  (io/input-stream fi)))
              ]
    (doall (line-seq i))))

            
(facts "Test write to file resources"
       
       (fact "Write data"
             (let [called (AtomicBoolean. false)
                   ]
              
               (prn "using basedir " basedir)
               (write "test" "wow" (fn [out] (.set called true)))
               (Thread/sleep 500)
               (.get called) => true)
             
             )
       (fact "Write data and test file exits"
             (let [called (AtomicBoolean. false)
                   file-key "abc-12333"
                   ]
               
               (write "test" file-key (fn [out] (.set called true)))
               (Thread/sleep 500)
               (prn ">>>> " (-> (File. basedir) (file-seq) (rest) (count)))
	             (count 
                 (->> 
	                 (File. basedir) 
	                 (file-seq) 
	                 (rest) 
	                 (filter #(> (count (re-seq (re-pattern file-key) (.getName %)))  0))
	                 )) => 1
             ))
       
       (fact "Write close file"
             (let [called (AtomicBoolean. false)
                   file-key (str (System/nanoTime))
                   topic "abc"
                   ]
                 (write topic file-key (fn [out] ))
                 (close-roll topic file-key)
                 (Thread/sleep 500)
                 (count 
                   (->> 
                      (File. basedir) 
                      (file-seq) 
                      (rest) 
                      (filter #(> (count (re-seq (re-pattern file-key) (.getName %)))  0)))
                   ) => 1
             ))
         (fact "Default roll on size"
               (let [file (let [file (File. basedir "testfile.txt")] (.createNewFile file) file)
                     file-data (new-file-data :file file)]
                 
                 (set-conf! :roll-size 10)
                 
                 (default-roll-check file-data) => false
                 (spit file (clojure.string/join \n (map str (range 0 1000))))
                 (Thread/sleep 100)
                 (default-roll-check file-data) => true
                 
               ))
         
         (fact "Default roll on last modified"
               (let [file (let [file (File. basedir "testfile.txt")] (.createNewFile file) file)
                     file-data (new-file-data :file file)]
                 
                 (set-conf! :roll-size 100000)
                 (set-conf! :roll-timeout 1000)
                 (spit file "hi")
                 (default-roll-check file-data) => false
                 
                 (Thread/sleep 1000)
                 (default-roll-check file-data) => true
                 
               ))
         
          (fact "Parallel roll write test"
               (let [
                     topic "mytopc"
                     file-key (str "roll-write-test")
                     service (Executors/newCachedThreadPool)
                     write-limit 1000
                     close-limit 100
                     write-count (AtomicInteger. 0)
                     close-count (AtomicInteger. 0)
                     
                     ^Runnable write-f (fn [] (Thread/sleep (rand-int 500)) (write topic file-key (fn [out] (.write out (.getBytes "histring\n") )))
                                         (.incrementAndGet write-count))
                     ^Runnable close-f (fn [] (Thread/sleep (rand-int (rand-int 1000))) (close-roll topic file-key)
                                         (.incrementAndGet close-count))]
                 
                 (set-conf! :roll-size 100000)
                 (set-conf! :roll-timeout 10000)
                 
                 (doseq [x (range write-limit)]
                   (.submit service write-f))
                 
                 (doseq [x (range close-limit)]
                   (.submit service close-f))
                                  
                  (.shutdown service)
                  (while (or 
                           (< (.get write-count) write-limit)
                           (< (.get close-count) close-limit))
                    (do
                      (prn "Wait for threads " (.get write-count))
                      (Thread/sleep 500)))
                            
                  (.get write-count) => write-limit
                  (.get close-count) => close-limit
                  
                  (close-all)
                  
                  (let [files 
                        (->> 
                      (File. basedir) 
                      (file-seq) 
                      (rest) 
                      (filter #(> (count (re-seq (re-pattern file-key) (.getName %)))  0)))]
                    
                    (> (count files) 0) => true 
                    
                    ;test that we write the number of lines
                    (reduce + (for [file files]
						                      (let [lines (gunzip file)]
						                        (count lines)))) => write-limit
						                    
                    )
               ))
       )

