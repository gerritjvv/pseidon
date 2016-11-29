(ns pseidon.hdfs.core.hdfsprocessor
"Loads files into hdfs"  
  (:require [pseidon.core.tracking :refer [mark-done!]]
            [pseidon.core.conf :refer [get-conf2]]
            [clj-time.coerce :refer [from-long]]
            [clj-time.core :refer [year month day hour]]
            [clj-time.format :refer [unparse parse formatter]]
            [clojure.core.async :refer [>! <! go chan go-loop]]
            [pseidon.core.message :refer [get-ids]]
            [pseidon.core.registry :refer [register ->Processor]]
            [clojure.tools.logging :refer [info error]]
            [clojure.string :as cljstr]
            [pseidon.core.error-reporting :as reporting]
            [pseidon.hdfs.core.partition-updater :as updater]
           )
           
   (:import (org.apache.commons.lang StringUtils)
           (java.io File)
           (org.apache.hadoop.fs Path FileUtil FileSystem)
           (org.apache.hadoop.conf Configuration)
           (java.net InetAddress)
           )
  )

;apply a function in the map depending on a model key
(defmacro apply-model [model-key model-map & args]
   `(if-let [f# (get ~model-map ~model-key)]
       (f# ~@args)))

(defn ^:dynamic get-local-host-name []
  "Returns the local host name, any exception will be printed and 'localhost' returned"
  (try
    (.getHostName (InetAddress/getLocalHost))
    (catch Exception e (do 
                         (error e e)
                         "localhost"))))

(defonce host-name (get-local-host-name))

(defn ^:dynamic number-format [^Integer n]
  (if (< n 10) (str "0" n) (str n)))
  
(def ^:dynamic dt-formatter (formatter "yyyyMMdd"))
(def ^:dynamic hr-formatter (formatter "yyyyMMddHH"))

(defn- ^:dynamic ^String extract-file-date [^String file-name]
  (if-let [d (re-find #"\d{10}" file-name)] d  (clojure.string/replace (re-find #"\d{4}-\d{2}-\d{2}-\d{2}" file-name) #"-" "")))

(def ^:dynamic hdfs-dir-formatters {1
                                    (fn [date-hr]
                                      "Expects the format yyyyMMddHH
                                       returns dt=yyyyMMdd/hr=yyyyMMddHH
                                       "
                                      (let [date (parse hr-formatter date-hr)
	                                          dt (unparse dt-formatter date)] ;parse dt
                                        (info "!!!! hdfs-dir-formatters: " date-hr) (str "dt=" dt "/hr=" date-hr)))
                                    2 (fn [date-hr]
                                        "Expects the format yyyyMMddHH
                                         returns year=yyyy/month=MM/day=dd/hour=HH
                                         "
                                   
                                        (let [date (parse hr-formatter date-hr)]
                                          (str "year=" (year date) "/month=" (number-format (month date))
                                                                   "/day=" (number-format (day date))
                                                                   "/hour=" (number-format (hour date)))))
                                    })

(def ^:dynamic file-name-parsers {1 
                                     (fn [file-name]
                                       "Expects a file name with type_id_hr_yyyyMMddHH.extension
                                        use this method as (let [ [type id _ date] (parse-file-name file-name)]  ) 
					                             "
                                       
																				(let [[type id ] (clojure.string/split file-name (re-pattern (get-conf2 "local-file-model-split" #"[_\.]")))
                                               date (extract-file-date file-name) ] 
																				 (info "Parsing file " file-name " to [ " type " " id " " date "]")
                                               [type id nil date]))
                                     2 (fn [file-name]
                                        "Expects a file name with type_yyyMMddHH.extension
                                         the id value part is returned empty
                                         " 
                                         (let [ [type] (clojure.string/split file-name (re-pattern (get-conf2 "local-file-model-split" #"[_\.]")))
                                                date (extract-file-date file-name)]
                                           [type "1" nil date]))
                                     })



(defonce c (chan))


(defonce hdfs-conf (delay
                       (doto (Configuration.) (.set "fs.default.name" (get-conf2 "hdfs-url" "hdfs://localhost:8020")) )))

(defonce fs (ref nil))


(defn ^:dynamic get-hdfs-conf []
  @hdfs-conf)

(defn ^:dynamic ^FileSystem get-fs []
  (dosync
    (if (nil? @fs)
      (let [fs2  ;CREATE File system here
                  (FileSystem/get (get-hdfs-conf))
                 ]
        (ref-set fs fs2)
        )))
  @fs
  )

(defn- ^String create-temp-file [^String remote-file]
  (clojure.string/join "/" ["/tmp" "copying" remote-file]))


(defn ^:dynamic file->hdfs [^String ds ^String id ^String local-file ^String remote-file & {:keys [partition-updater]}]
  "
   Copy(s) a local file to hdfs,
   any exception is printed and false is returned otherwise true is returned
  "
  (try
    (let [^String temp-file (create-temp-file remote-file)
          ^String temp-path (Path. temp-file)
          ^FileSystem fs (get-fs)]
      
      (info "Copy " local-file " to hdfs " remote-file  " temp file " temp-file)
      
      ;we need to first copy to a temp location then rename the file 
      ;once its been copied completely, otherwise the file will be readable halfway through the copy process
      (.copyFromLocalFile fs false (Path. local-file) temp-path)
      
      (if (not (.rename fs temp-path (Path. remote-file)))
            (let [parent-path (-> remote-file clojure.java.io/file .getParent)] ;create the directories and rename again
              (.mkdirs fs (Path. parent-path))

              (if partition-updater (partition-updater parent-path))

              (if (not (.rename fs temp-path (Path. remote-file)))
                (throw (RuntimeException. (str "Unable to create file " remote-file))))
              
              ))
      
      (info "Copy Done local-file: " local-file)

      (mark-done! ds id (fn [] 
                          ;on mark done we delete the local file being copied.
                          (clojure.java.io/delete-file local-file true)
                          (info "deleted local-file " local-file)
                          ) )
      (reporting/send-event {:state "ok" :description (str remote-file) :tags ["hdfs-upload"] :metric 2})

      true
      )
    (catch java.io.FileNotFoundException fe (do 
                                              (mark-done! ds id (fn []))
                                              (info local-file " not found ignoring")
                                              true))
    (catch Exception e (do 
                         (error e e)
                         false
                         ))))

(defn ^:dynamic load-processor []
  (let [ ;globals
        local-file-model (get-conf2 "hdfs-local-file-model" 2) ;what local-file-model to apply
        hdfs-dir-model (get-conf2 "hdfs-dir-model" 1) ;what hdfs directory model to apply
        hdfs-dir (get-conf2 "hdfs-base-dir" "/log/raw") ;get the base hdfs dir
        updater-cmd (get-conf2 "hdfs-partition-update-cmd" "echo")
        updater-timeout (get-conf2 "hdfs-partition-update-timeout" 10000)
        updater-f (fn [file]
                      (updater/with-timeout-async
                        updater-timeout
                        #(updater/with-retry 3 updater-cmd %)
                        file))
        ]
    
	  (letfn [
      (recover-ready-messages [])
            
	    (copy! []
	           (go-loop []
	             (if-let [data (<! c)]
		              (do
                    (try
                     (let [[ds id local-file remote-file]  data]
		                   ;retry till the file has been uploaded
		                   (while (false? (file->hdfs ds id local-file remote-file :partition-updater updater-f))
		                    (do
                        (error "File " local-file " could not be copied to " remote-file " retrying")
                        (Thread/sleep 1000))
                      ))
                     (catch Throwable e (error e e)))
                    (recur)))))
                 
                     
	                   
	    (start []
                (info "calling recover-ready-messages") 
                (recover-ready-messages)
                (copy!)
                (info  "hdfs processor started")
                )
	    (stop [] (.close (get-fs))
	          )
	    (exec [ {:keys [topic ts ds ids] :as msg } ] ;unpack message
                  (info "hdfs exec -> message " topic " ids " (get-ids msg))
	          (try
              (let [ids (get-ids msg)];get the message ids as a sequence
                   (doseq [id ids] ;for each id (the id should be the local file-name) upload the file
                          (let [
                                 ^String local-file id
                                 ^String file-name (-> local-file java.io.File. .getName)
                                 ^String file-path (-> local-file java.io.File. .getParent)
                                 [type-name _ _ date-hr] (apply-model local-file-model file-name-parsers file-name); inserts the correct model function
                                 date-dir (apply-model hdfs-dir-model hdfs-dir-formatters date-hr)
                                 remote-file (if (= file-path "/d1/pseidonfiles")
                                               (str hdfs-dir "/"
                                                     (cljstr/replace type-name #"-etl" "")
                                                     "/"
                                                     date-dir
                                                     "/" host-name "-" file-name) ;construct the remote file-name using default base and topic name for a log
                                                (str (cljstr/replace file-path #"/d1/pseidonfiles" "")
                                                    "/"
                                                   date-dir
                                                   "/" host-name "-" file-name)) ;construct the remote file-name using the configured base + path for a log
                                 ]
                               (go
                                 (>! c [ds id local-file remote-file]) ;here id is the local file
                                 ))))
              (catch Exception e (error e e))))
	   
	    
	    ]
	    (register (->Processor "hdfs" start stop exec)))))


(load-processor)





