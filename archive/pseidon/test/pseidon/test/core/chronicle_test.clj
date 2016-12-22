(ns pseidon.test.core.chronicle-test
  (:require [pseidon.test.core.utils :refer [create-tmp-dir]]
            [pseidon.core.chronicle :refer [offer poll poll! offer! close create-queue]]
            [clojure.tools.logging :refer [info]])
  (:use midje.sweet
        pseidon.core.chronicle)
  (:import [java.io File]
           [pseidon.util DefaultEncoder DefaultDecoders]))


(facts "Test chronicle queue implementation"
       
       (fact "Test directory discovery return nil if no subdirs"
             (let [path (create-tmp-dir "chronicle" :delete-on-exit true)] 
               (load-chronicle-path path) => nil))
       
       (fact "Test directory discovery return latest dir"
             (let [
                   path (create-tmp-dir "chronicle" :delete-on-exit true)
                   path2 (File. path "b")
                   path3 (File. path "a");names are reverse order to ensure ordering is by ts.
                   ]
               (.mkdirs path2)
               (Thread/sleep 1000)
               (.mkdirs path3)
               (load-chronicle-path path) => path3))
       
      
      (fact "Test offer and get no limit or segment overflow"
             
             (let [limit 10000
                   path (create-tmp-dir "chronicle" :delete-on-exit true)
                   q (create-queue path limit DefaultDecoders/STR_DECODER DefaultEncoder/DEFAULT_ENCODER :segment-limit (* limit 2))] 
               (doall
                 (dotimes [i 10]
                   (dotimes [n 100]
                     (offer! q (str "msg " i "-" n) ))))
                 
               (close q)
               (info "starting to read")
                (let [read-queue (create-queue path limit DefaultDecoders/STR_DECODER DefaultEncoder/DEFAULT_ENCODER)]
                  (dotimes [i 10]
                    (dotimes [n 100]
                      (poll! read-queue) => (str "msg " i "-" n)))
                  (close read-queue)))
             
             )
     
      (fact "Test offer and get half, close, open and get other half"
             
             (let [limit 10000
                   path (create-tmp-dir "chronicle" :delete-on-exit true)
                   q (create-queue path limit DefaultDecoders/STR_DECODER DefaultEncoder/DEFAULT_ENCODER :segment-limit (* limit 2))
                   z 10] 
               (doall
                 (dotimes [i 1]
                   (dotimes [n z]
                     (offer! q (str "msg " i "-" n)))))
                 
               (close q)
                (let [read-queue (create-queue path limit DefaultDecoders/STR_DECODER DefaultEncoder/DEFAULT_ENCODER)]
                  (dotimes [i 2]
                    (dotimes [n (/ z 2)]
                      (poll! read-queue) => (str "msg 0-" (+ (* (/ z 2) i) n))))
                  (close read-queue))
                
                
             
             )
             
             
      
       
      (fact "Test offer and get no limit or segment overflow"
             
             (let [limit 10000
                   path (create-tmp-dir "chronicle" :delete-on-exit true)
                   q (create-queue path limit DefaultDecoders/STR_DECODER DefaultEncoder/DEFAULT_ENCODER :segment-limit (* limit 2))] 
               (doall
                 (dotimes [i 100]
                   (dotimes [n 100]
                     (offer! q (str "msg " i "-" n)))))
                 
               (close q)
                (let [read-queue (create-queue path limit DefaultDecoders/STR_DECODER DefaultEncoder/DEFAULT_ENCODER)]
                  (dotimes [i 100]
                    (dotimes [n 100]
                      (poll! read-queue) => (str "msg " i "-" n)))
                  (close read-queue)))
             
             )
      
      
       )
       (fact "Test write close, open write again"
             
             (let [limit 100000
                   path (create-tmp-dir "chronicle" :delete-on-exit true)
                   write-close (fn [n]
                                 (let [q (create-queue path limit DefaultDecoders/STR_DECODER DefaultEncoder/DEFAULT_ENCODER :segment-limit (* limit 2))]
                                   (doall
											                 (dotimes [i n]
                                               (offer! q (str "msg " i))))
	               
                                   (close q)))
                   read (fn [n]
                          (let [q (create-queue path limit DefaultDecoders/STR_DECODER DefaultEncoder/DEFAULT_ENCODER :segment-limit (* limit 2))
                                v (doall
                                         (map #(String. %) (filter (complement nil?) (repeatedly n #(poll q 100) ) ))
                                         )]
                            (.close q)
                            v))
                  ]
               
                    (dotimes [i 2]
                     (write-close 10)
                     (read 10)
                    )

                   (read 10) => '()
                   
                   (write-close 10)
                   (prn ">>>>> " (count (read 5)))
                   (count (read 5)) => 5
                  
                  ))
       
          (fact "Test offer write block on limit "
             (let [limit 10
                   path (create-tmp-dir "chronicle" :delete-on-exit true)
                   q (create-queue path limit DefaultDecoders/STR_DECODER DefaultEncoder/DEFAULT_ENCODER)]
               (doall
                 (dotimes [i 11]
                   (offer q (str "msg-" i) 100)))
               ;the following should block,timeout and return false
               (offer q (str "abc") 100) => false 
               
             ))
       
        
         (fact "Test offer write block on limit read the recover from limit block again"
             (let [limit 10
                   path (create-tmp-dir "chronicle" :delete-on-exit true)
                   q (create-queue path limit DefaultDecoders/STR_DECODER DefaultEncoder/DEFAULT_ENCODER)]
               (doall
                 (dotimes [i 11]
                   (offer! q (str "msg-" i))))
               ;the following should block,timeout and return false
               (offer q (str "abc") 100) => false 
               ;read two items
               (doall
                 (dotimes [i 3]
                   (not (nil? (poll q 100))) => true))
               
               ;write twrice no blocking
               (doall
                 (dotimes [i 3]
                   (offer q "msg" 100) => true))
               
               ;block again
               (offer q (str "abc") 100) => false 
               
             ))
         
          
          (fact "Test offer, roll on segment 10 times, read verify data"
             (let [limit 10
                   path (create-tmp-dir "chronicle" :delete-on-exit true)
                   q (create-queue path limit DefaultDecoders/STR_DECODER DefaultEncoder/DEFAULT_ENCODER :segment-limit 20)
                   write-read (fn [n] [(doall
                                         (for [i (range n)]
                                           (let [msg (str "msg-" i)]
                                             (offer! q msg)
                                             msg
                                           )))
                                       
                                       (doall
                                         (map #(String. %) (filter (complement nil?) (repeatedly n #(poll q 500) ) ))
                                         )])
                                
                                       
                   ]
                   
                   (doall
                     (dotimes [i 20]
		                   (let [[w r] (write-read 10)]
                         (info w " => " r)
		                     r => w)))
                   
             ))
          
 
       
          (fact "Test offer, roll on segment 5 times, leave data to copy during each roll"
             (let [limit 10
                   path (create-tmp-dir "chronicle" :delete-on-exit true)
                   q (create-queue path limit DefaultDecoders/STR_DECODER DefaultEncoder/DEFAULT_ENCODER :segment-limit 20 :buffer 100)
                   write-read (fn [x write-n read-n] [(doall
                                         (for [i (range write-n)]
                                           (let [msg (str "msg-" x "-" i)]
                                             (offer! q msg)
                                             msg
                                           )))
                                       
                                       (doall
                                         (map #(String. %) (filter (complement nil?) (repeatedly read-n #(poll q 1000) ) ))
                                         )])
                                
                                       
                   ]
                   
                       (let [[w r] (write-read 'a 20 5)]
                         (count r) => 5)
                       (let [[w r] (write-read 'b 20 5)]
                         (count r) => 5)
                       (let [[w r] (write-read 'c 20 5)]
                         (count r) => 5)
                   
                       (let [[w r] (write-read 'd 0 10)]
                         (count r) => 10)
                       (let [[w r] (write-read 'e 0 10)]
                         (count r) => 10)
                       (let [[w r] (write-read 'f 0 10)]
                         (count r) => 10)
                       (let [[w r] (write-read 'g 0 10)]
                         (count r) => 10)
                       (let [[w r] (write-read 'h 0 5)]
                         (count r) => 5)
                       (let [[w r] (write-read 'h 0 2)]
                         (count r) => 0)
                   
                   
             ))
         
           (fact "Test offer, roll on segment 5 times, leave data to copy during each roll"
             (let [limit 10
                   path (create-tmp-dir "chronicle" :delete-on-exit true)
                   q (create-queue path limit DefaultDecoders/STR_DECODER DefaultEncoder/DEFAULT_ENCODER :segment-limit 20)
                   write-read (fn [x write-n read-n] [(doall
                                         (for [i (range write-n)]
                                           (let [msg (str "msg-" x "-" i)]
                                             (if (offer q msg 100) msg nil)
                                           )))
                                       
                                       (doall
                                         (map #(String. %) (filter (complement nil?) (repeatedly read-n #(poll q 100) ) ))
                                         )])
                                
                                       
                   ]
                   
                   (let [[w r] (write-read 0 10 5)]
                         r => ["msg-0-0" "msg-0-1" "msg-0-2" "msg-0-3" "msg-0-4"])
                   
                   (let [[w r] (write-read 0 20 0)]
                     ;we expect the write to timeout and only return the messages that it could send.
                     (count (filter (complement nil?) w)) => 6)
                       
                   
             )))
      
           
       

