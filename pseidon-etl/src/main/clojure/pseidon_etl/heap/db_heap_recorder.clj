(ns
  ^{:doc "Heap recorder that should be run as part of https://github.com/foursquare/heapaudit

          Sections
            Another hybrid way is to statically instrument the process of interest at launch time with dynamically injected recorders. This will provide an inclusive collection from the moment the targeted process starts to the moment it exits while not requiring prior code changes.
            java -javaagent:heapaudit.jar=\"-Icom/foursquare/test/MyTest@test.+ -Xrecorder=pseidon-etl.heap.db-heap-recorder@/opt/pseidon-etl/lib/pseidon-etl.jar\"  "}
  pseidon-etl.heap.db-heap-recorder
  (:gen-class
    :main false
    :post-init init
    :state state
    :prefix "-"
    :extends com.foursquare.heapaudit.HeapQuantile)

  (:require [pseidon-etl.conf :as conf]
            [schema.core :as s]
            [pseidon-etl.db-service :as db-service]
            [clojure.java.jdbc :as j]
            [clojure.tools.logging :refer [error]])
  (:import (java.util.concurrent Executors ThreadFactory TimeUnit)
           (com.foursquare.heapaudit HeapQuantile HeapRecorder$Threading HeapQuantile$Stats)
           (java.net InetAddress)
           (java.util Date)))


;;;;;;;;;;;;;;;;;;
;;;;; config and parameter schemas

(def DBConfSchema {s/Any s/Any
                   :heap-recorder {(s/optional-key :frequency-seconds) s/Int
                                   :db-host s/Str
                                   (s/optional-key :db-user) s/Str
                                   (s/optional-key :db-pwd)  s/Str}})
;;;;;;;;;;;;;;;;;;
;;;; private helper functions

(defonce HOST_NAME (try (.getHostName (InetAddress/getLocalHost)) (catch Exception e (do (error e e) "localhost"))))

(defn load-conf []
  "Loads the hardcoded /opt/pseidon-etl/conf/pseidon.edn conf file, we do not have access to the command line arguments here"
  (let [conf (conf/load-conf "/opt/pseidon-etl/conf/pseidon.edn")]
    (alter-var-root #'pseidon-etl.conf/*default-conf* (fn [_] conf))
    conf))

(defn create-db-service [{:keys [db-host db-user db-pwd]}]
  "Expects the configuration to contain db-host, db-user and db-pwd
   Returns a started DatabasePool"
  {:pre [db-host]}
  (db-service/start-db (db-service/create-database db-host db-user db-pwd)))

(defn thread-factory []
  (reify ThreadFactory
    (newThread [_ r]
      (doto (Thread. ^Runnable r)
        (.setDaemon true)))))

(defn truncate-str [^String s ^long max-len]
  (if (> (count s) max-len)
    (.substring s 0 max-len)
    s))

(defn insert-stat [db ^Date ts ^HeapQuantile$Stats stat]
  "Insert a single stat into the db"
  (j/insert-values
    :pseidon_heap_record
    [:ts :server_name :stat_name :occurances :avg_count :avg_size]
    [ts (truncate-str HOST_NAME 255) (truncate-str (.-name stat) 100) (.-occurances stat) (.-avgCount stat) (.-avgSize stat)]))

(defn push-stats [^HeapQuantile recorder db]
  (db-service/with-connection db
                              (run! (partial insert-stat db (Date.)) (.tally recorder HeapRecorder$Threading/Global true))))

;;;;;;;;;;;;;;;
;;;;; gen-class override functions

(defn -init [this]
  "Launch a db connection and repeating thread that will output the stats on every n configured seconds"
  (let [conf (load-conf)
        _ (s/validate DBConfSchema conf)

        frequency-seconds (get-in conf [:heap-recorder :frequency-seconds] 30)

        db (create-db-service (get conf :heap-recorder))
        exec (Executors/newScheduledThreadPool (int 1) (thread-factory))]

    (.scheduleWithFixedDelay #(push-stats db this) 10000 (.toMillis TimeUnit/SECONDS (long frequency-seconds)))
    [db exec]))

