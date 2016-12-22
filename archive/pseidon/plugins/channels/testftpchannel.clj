(ns plugins.channels.testftpchannel
  
  (:require 
      [pseidon.core.datastore :refer [get-data-number]]
      [pseidon.core.conf :refer [get-conf2]]
      [pseidon.core.queue :refer [publish]]
      [pseidon.core.app :refer [data-queue]]
      [pseidon.core.conf :refer [set-conf!]]
      [pseidon.core.watchdog :refer [watch-critical-error]]
      [pseidon.core.message :refer [create-message batched-seq get-ids]]
      [pseidon.core.ds.ftp :refer [ftp-record-id]]
      [clojure.tools.logging :refer [info error]]
    )
    
     (:use pseidon.core.registry
           )
    
    )
  
;(set-conf! :zk-url "192.168.56.101")

(def ^:dynamic topic "abctopics")

;(defrecord DataSource [name start stop list-files reader])


(defn read-ftp [{:keys [list-files reader-seq] :as m}]
  "For each file reads each line and sends as a message"
  
  (let [service (java.util.concurrent.Executors/newFixedThreadPool 1) ]
   (if (nil? m) (throw (Exception. "The message cannot be nil here")))
   (if-let [files (list-files)]
    (do
	  (doseq [file files]
	    (.submit service 
	       (watch-critical-error 
	          (fn [] 
               (info "Looking at file " file)
	             (doseq [ [start-pos end-pos lines] (reader-seq file)]
                    ;each line-record will contain [start-pos end-pos lines] 
                    ;(info "Sending " file " line batch: " (count lines)  " start-pos " start-pos " end-pos " end-pos)
	                  (if-not (empty? lines) 
                     ;(defrecord Message [^clojure.lang.IFn bytes-f ^String ds ids ^String topic  accept ^long ts ^long priority] 
                     (publish data-queue (create-message (map #(.getBytes %) lines)
                                                         "testftp"
                                                         (ftp-record-id "testftp" file start-pos end-pos)
                                                         topic 
                                                         true 
                                                         (System/currentTimeMillis) 1)
                              )
                     )
                   )
              (info "Looking at file -END " file)
              )
           )
        )
     )
   )
    )
    (doto service .shutdown (.awaitTermination Long/MAX_VALUE java.util.concurrent.TimeUnit/MILLISECONDS))
    ;(while true (Thread/sleep 10000))
  )
 )

(defn run [] 
 ;start is called in its own thread and does not need to return  
      (let [ftp (reg-get-wait "testftp" 10000)]
         (while true (do (read-ftp ftp) (Thread/sleep 10000) ))        
        )
  )


(defn stop []
  
  )



(register (->Channel "testftpchannel" run stop))

        
