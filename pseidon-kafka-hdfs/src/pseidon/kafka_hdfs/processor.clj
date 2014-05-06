(ns pseidon.kafka-hdfs.processor
  
  (:require [pseidon.core.conf :refer [get-conf2]]
            [pseidon.core.queue :refer [publish]]
            [pseidon.core.message :refer [create-message]]
            [taoensso.nippy :as nippy]
            [clj-json.core :as json]
            [pseidon.kafka-hdfs.json-csv :as json-csv]
            [thread-exec.core :refer [get-layout default-pool-manager submit shutdown]]
            [pseidon.core.queue :refer [pool-manager]]
            [pseidon.core.metrics :refer [add-meter update-meter] ]
            [pseidon.core.registry :refer [register ->Processor reg-get-wait] ]
            [clj-time.coerce :refer [from-long to-long]]
            [clj-time.format :refer [unparse formatter]]
            [clj-tuple :refer [tuple]]
            [pseidon.kafka-hdfs.channel :refer [get-encoder encoder-map] :as channel ]
            [fileape.core :refer [ape write close]]
            ;[pseidon.core.fileresource :refer [write register-on-roll-callback]]
            [pseidon.core.tracking :refer [select-ds-messages mark-run! mark-done! deserialize-message]]
            [clojure.tools.logging :refer [info error]]
            [fun-utils.core :refer [fixdelay]])
     (:use goat.core)
     (:import [org.apache.commons.lang StringUtils]
              [java.io DataOutputStream]
              [net.minidev.json JSONValue])
  )


(def exec-meter (add-meter (str "pseidon.kafka_hdfs.processor" )))

(defonce data-meter-map (ref {}))
(defonce msg-meter-map (ref {}))

(defn get-msg-meter [log-name]
  (let [topic (str ".msgs-written-p/s-" log-name)]
	  (if-let [m (get @msg-meter-map topic)]
      m
	    (let [c (add-meter topic)]
		    (dosync
		       (commute msg-meter-map assoc topic c))
	     c))))

(defn get-data-meter [log-name]
  (let [topic (str ".bytes-written-p/s-" log-name)]
	  (if-let [m (get @data-meter-map topic)]
      m
	    (let [c (add-meter topic)]
		    (dosync
		       (commute data-meter-map assoc topic c))
	     c))))


(def ^:private ts-parser { :obj 
                          (fn [msg-data path-seq]
                            	   (reduce (fn [d k] (get d k)) msg-data path-seq))
                          
                           :now 
                           (fn [msg-data _]
                             (System/currentTimeMillis))
                          })

(defn nippy-decoder [^"[B" bts & _]
  (nippy/thaw bts))

(defn json-decoder [^"[B" bts & _]
  (let [v (JSONValue/parse (java.io.InputStreamReader. (java.io.ByteArrayInputStream. ^"[B" bts)))]
    (if (instance? net.minidev.json.JSONArray v)
      (if (> (.size ^net.minidev.json.JSONArray v) 1) 
        (into {} (second v))
        (into {} [v]))
      (into {} v))))



(defn ^"[B" default-decoder [bts & _] 
    bts)

(defonce ^:constant decoder-map {:default default-decoder
                                 :nippy nippy-decoder
                                 :json json-decoder})

  
(defmacro with-info [s body]
  `(let [x# ~body] (info ~s " " x#) x#))

 
(def get-decoder (memoize (fn [log-name]
                            (with-info (str "For topic " log-name " using decoder ")
				                            (let [key1 (keyword (str "kafka-hdfs-" log-name "-decoder"))
				                                  key2 :kafka-hdfs-default-decoder]
				                              (if-let [decoder (get decoder-map (get-conf2 key1 (get-conf2 key2 :default)))]
				                                decoder
				                                default-decoder))))))




(defn exec-write [topic ^java.io.OutputStream out ^"[B" bts]
       (if (nil? bts)
             (error "Receiving null byte messages from  ts ")
             (do
               (update-meter (get-data-meter topic) (count bts))
               (pseidon.util.Bytes/writeln out bts))
             
             ))

(def  get-ts-parser (memoize (fn [topic]
                               (with-info (str "For topic " topic " using ts parser ")
                                        (let [key1 (keyword (str "kafka-hdfs-" topic "ts-parser"))
                                              key2 :kafka-hdfs-ts-parser]
                                         (if-let [parser (get ts-parser (get-conf2 key1 (get-conf2 key2 :now) ))]
                                           parser
                                           (:now ts-parser)))))))


(def  get-ts-parser-args (memoize (fn [topic]
                                    (with-info (str "For topic " topic " using ts parser args ")
		                                        (let [key1 (keyword (str "kafka-hdfs-" topic "ts-parser-args"))
		                                              key2 :kafka-hdfs-ts-parser-args]
		                                         (if-let [parser-args (get-conf2 key1 (get-conf2 key2 ["ts"]) )]
		                                           parser-args
		                                           ["ts"]))))))

(defonce dsid "pseidon.kafka-hdfs.processor")
   
(defonce n-bytes (pseidon.util.Bytes/toBytes "1"))

(defn callback-f [{:keys [file] :as data}]
  
                                                       (let [ds dsid
                                                             topic "hdfs"
                                                             id (.getAbsolutePath file)]
                                                             (mark-run! ds id)
                                                             (publish topic (create-message
                                                                          n-bytes
                                                                          ds id topic true (System/currentTimeMillis) 1
                                                                        ))))

(defonce ape-conn (ape {:codec (keyword (get-conf2 :default-codec :gzip))
                    :base-dir (get-conf2 :writer-basedir "target") 
                    :check-freq (get-conf2 :writer-check-freq 5000) 
                    :rollover-size (get-conf2 :roll-size 134217728)
                    :rollover-timeout (get-conf2 :roll-timeout 60000)
                    :roll-callbacks [callback-f]}))

                           
(defn- map-flatten [packed-msg]
  "Flattens the packed msgs and maps to [topic key decoded-data]"
  (let [dateformat (formatter "yyyyMMddHH")]
  (filter (complement nil?) 
  (map 
    (fn [{:keys [topic bts] :as msg}] 
      (try
        (let [bdata ((get-decoder topic) bts)
            ts ((get-ts-parser topic) bdata (get-ts-parser-args topic))
            k (str topic "_" (unparse dateformat (if ts 
                                                   (from-long ts)
                                                   (from-long (System/currentTimeMillis)))))]
           (tuple topic k bts))
        (catch Exception e (do 
                             (error (str "Exception " e " looking at " (String. ^"[B" bts) " msg " msg))
                             ())
        )))
    (flatten (:bytes-seq packed-msg))))))
  
(defn- partition-msgs [msgs]
  "Excepts messages from packed-msg and each msg in msgs must be [topic key data], the messages are partitioned by key"
  (group-by second msgs))
         
;read messages from the logpuller and send to hdfs
(defn exec [ packed-msg ]
  ;group data by key, then for each message group call (write ape-conn ...) and write the bytes in one go
  (let [flatten-msgs (map-flatten packed-msg)
        cnt (count flatten-msgs)]
    (update-meter (get-msg-meter "total") cnt)
    (doseq [[k msgs] (partition-msgs flatten-msgs)]
      (write ape-conn k (fn [out] 
                          (doseq [[topic k bts] msgs]
                            ;we do not use a encoder here and just write out the original message
                            ;using encoders are too in efficient
                              (exec-write topic out bts))
                            )))))
     	                      

(defn ^:dynamic start []
  
  ;this will be called when any file written by this channel has been rolled
  ;we send the rolled file to the hdfs plugin and this plugin will take care of sending the file to hdfs
  
   ;we recover any messages that the hdfs plugin did not send
   (doseq [{:keys [ds ids ts] :as msg} (map deserialize-message (select-ds-messages dsid))]
       (info "Recovering msg [" msg "]")
       (if msg  
         (publish "hdfs" (create-message nil ds ids "hdfs" true (to-long ts) 1) )))
  
  )

(defn stop []
  (info "Shutdown file writers")
  (try 
    (close ape-conn)
    (catch Exception e (error e e)))
  (info "Shutdown file writers complete"))

;register processor with topic solace
(register (->Processor "pseidon.kafka-hdfs.processor" start stop exec))
(comment
(instrument-functions! 'pseidon.kafka-hdfs.processor)
(instrument-functions! 'fileape.core)

(fixdelay 20000 
  (info (get-top-fperf 20))))


