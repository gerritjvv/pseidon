(ns
  ^{:doc
    "
       This service is the core of the pseidon-hdfs application and provides a component
       created using create-hdfs-copy-service that will run in the background and scan a directory
       for files to upload to hdfs
    "}
  pseidon-hdfs.hdfs-copy-service
  (:import [java.io File IOException DataOutputStream]
    (org.apache.hadoop.fs FileSystem)
    (org.apache.hadoop.conf Configuration)
    [java.util.concurrent ExecutorService TimeUnit]
    (org.joda.time DateTime)
    (org.apache.commons.lang StringUtils)
    (java.util.concurrent.atomic AtomicBoolean)
    (java.net InetAddress)
    (org.apache.hadoop.security UserGroupInformation))
  (:require
    [pseidon-hdfs.util :refer :all]
    [pseidon-hdfs.hdfs-util :refer :all]
    [pseidon-hdfs.metrics :as hdfs-metrics]
    [com.climate.claypoole :as cp]
    [pseidon-hdfs.mon :as hdfs-mon]
    [pseidon-hdfs.db-service :refer [with-connection]]
    [pseidon-hdfs.hive :as hive]
    [fileape.core :as ape]
    [clojure.java.jdbc :as j]
    [fun-utils.cache :as fun-cache]
    [fun-utils.core :refer [fixdelay fixdelay-thread stop-fixdelay]]
    [clj-time.format :as time-f]
    [clojure.tools.logging :refer [info error debug]]
    [clojure.java.io :as io]
    [clojure.string :as string]
    [com.stuartsierra.component :as component]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;; constants and forward declares

(declare topic->tablename)
(declare delete-any-crc)
(declare any-gz-file-seq)
(declare shutdown?)

(defonce DEFAULT-FILE-WAIT-TIME-MS 30000)

(defonce ^:private ^"[B" new-line-bts (.getBytes "\n"))
(defonce datetime-formatter (time-f/formatter "yyyy-MM-dd-HH"))

(defonce HDFS-VERSION "0.1.0")

(def host-name (.getHostName (InetAddress/getLocalHost)))

(defn default-topic-meta
  "default topic metadata"
  [log-name]
  {:base_partition  "/log/pseidon"
   :log_partition   log-name
   :hive_db_name    "pseidon"
   :hive_table_name (topic->tablename log-name)
   :quarantine      "/tmp/pseidon-quarantine"
   :hive_url        nil
   :hive_user       "hive"
   :hive_password   ""})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;; private util functions

(defn is-older-than [^File file ts-ms]
  (> (- (System/currentTimeMillis) (.lastModified file))
     (long ts-ms)))

(defn file-filter [file]
  (let [^File file-obj (io/file file)
        ^String file-name (.getName file-obj)]
    (and (or
           (.endsWith file-name ".gz")
           (.endsWith file-name ".parquet"))
         (.exists file-obj))))

(defn file-ok?
  "Checks for corrupt files based on the extension.
   Currently only parquet is checked"
  [file]
  (let [file-str (str file)]
    (cond
      (.endsWith file-str ".parquet") (fileape.parquet.writer/parquet-ok? file)
      :else true)))

(defn jdbc-db
  "Splits the url several times to get at the db part in the jdbc url
   e.g jdbc:mysql:loadbalance://localhost:3306,localhost:3310/mydb?param=1 here the function would return mydb"
  [url]
  (when url
    (-> url
        (string/split #"/")
        last
        (string/split #"[?:;]")
        first)))

(def map->hdfs-conf (memoize (fn [conf]
                               (reduce-kv (fn [^Configuration hdfs-conf k v] (doto hdfs-conf (.set (str k) v))) (Configuration.) conf))))

(defn file-name->date [file]
  (let [file-name (-> file io/file .getName)
        [_ date] (re-find #"-(\d\d\d\d-\d\d-\d\d-\d\d)" file-name)]
    date))

(defn file-name->topic [file]
  (->> file io/file .getName (re-find #"(.+)-\d\d\d\d-\d\d-\d\d-\d\d") second))

(defn remove-first-slash
  "Remove the front slash in a path if it exists"
  [^String path]
  (StringUtils/removeStart path "/"))

(defn remove-last-slash
  "Remove the front slash in a path if it exists"
  [^String path]
  (StringUtils/removeEnd path "/"))


(defn create-remote-file-name ^String [base-dir topic-partition file]
  (let [file-name (-> file io/file .getName)
        date (file-name->date file)]
    (if (and topic-partition date)
      (let [^DateTime dt (time-f/parse datetime-formatter date)
            y (.getYear dt)
            m (padd-zero (.getMonthOfYear dt))
            d (padd-zero (.getDayOfMonth dt))
            h (padd-zero (.getHourOfDay dt))]
        (str (remove-last-slash base-dir) "/" (remove-last-slash (remove-first-slash topic-partition)) "/dt=" y m d "/hr=" y m d h "/" host-name "-" file-name))
      (throw (RuntimeException. (str "Invalid file name " file " topic " topic-partition " date " date " must be <name>-yyyy-MM-dd-HH.<ext>"))))))


(defn- topic->tablename [topic]
  (-> topic
      str
      (StringUtils/replace "-" "_")))


(defn file-filter-metrics [file]
  (let [^File file-obj (io/file file)
        ^String file-name (.getName file-obj)]
    (and (and
           (.contains file-name "metrics")
           (.contains file-name ".gz"))
         (.exists file-obj))))


(defn any-metrics-gz-file-seq [dir]
  (->> dir io/file file-seq (filter file-filter-metrics)))

(defn gz-file-seq [{:keys [file-wait-time-ms] :or {file-wait-time-ms DEFAULT-FILE-WAIT-TIME-MS}} dir]
  (->> dir io/file file-seq (filter #(and
                                      (file-filter %)
                                      (is-older-than % (if (number? file-wait-time-ms) file-wait-time-ms DEFAULT-FILE-WAIT-TIME-MS))))))

(defn delete-any-crc [dir]
  (try
    (doseq [^File file (file-seq (io/file dir))]
      (when (.endsWith (.getName file) ".crc")
        (info "Removing crc file " (.getName file))
        (.delete file)))
    (catch Exception e (do
                         (error e e)))))

(defn print-rolled-file [{:keys [^File file]}]
  (prn "Rolled file " file))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;; metrics functions

(defn update-file-upload-meter
  "Pass in metric object created in the HdfsCopyService service"
  [{:keys [file-upload-meter file-size-meter]} file]
  (when (and file-upload-meter file-size-meter)
    (let [file-size (.length ^File (io/file file))]
      (hdfs-metrics/mark-meter file-upload-meter 1)
      (hdfs-metrics/mark-meter file-size-meter (long file-size)))))

(defn- _write-hive-metrics
  "Write hive-errors log"
  [{:keys [writer ^AtomicBoolean closed]} log status partition exception]
  (if-not (.get closed)
    (when-not (or (= "hdfs-metrics" log) (= "hive-metrics" log))
      (let [now (System/currentTimeMillis)
            k (str "hive-metrics-" (get-local-dt-hr-str now))]
        (ape/write writer k
                   (fn [{:keys [^DataOutputStream out]}]
                     (.write out ^"[B" (as-bytes {"ts" now "log" log "partition" partition "status" status "exception" (str exception)}))
                     (.write out new-line-bts)))))
    (info (str "Could not write hive metrics due to shutdown and writers already shutdown topic " log))))

(defn _write-copy-metrics
  "Write the metrics to disk using the key hdfs-metrics, not that hdfs-metrics are not written for the topic hdfs-metrics"
  [{:keys [writer ^AtomicBoolean closed]} topic ^String remote-file size status]
  (when (and writer closed)
    (if-not (.get closed)
      (when-not (= "hdfs-metrics" topic)
        (let [^File file (io/file remote-file)
              now (System/currentTimeMillis)
              k (str "hdfs-metrics-" (get-local-dt-hr-str now))]
          (ape/write writer k
                     (fn [{:keys [^DataOutputStream out]}]
                       (.write out ^"[B" (as-bytes {"hdfs_push_ts"     now
                                                    "log_name"         topic
                                                    "remote_file_name" (.getName file)
                                                    "hdfs_dir"         (.getParent file)
                                                    "host"             host-name
                                                    "file_size"        size
                                                    "status"           status}))
                       (.write out new-line-bts)))))
      (info (str "Could not write copy metrics due to shutdown and writers already shutdown topic " topic)))))


(defn shutdown-no-metrics? [closed base-dir]
  (if (shutdown? closed)
    (zero? (count (any-metrics-gz-file-seq base-dir)))
    false))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;; load copy filter and data validation functions

(defn add-hive-db-name [topic-meta]
  (when (not (empty? topic-meta))
    (assoc topic-meta :hive_db_name (jdbc-db (get topic-meta :hive_url)))))

(defn update-metadata-nils-with-defaults [log-name topic-meta]
  (when (not (empty? topic-meta))
    (-> topic-meta
        (update-in [:hive_db_name] #(if-null % "pseidon"))
        (update-in [:hive_table_name] #(if-null % log-name)))))


(defn load-topic-meta!
  "Query pseidon_logs and return a map with the columns selecting including :hive_db_name which is derived from add-hive-db-name"
  [log-name db]
  (let [query (str
                "select base_partition,
                        log_partition,
                        hive_url,
                        hive_table_name,
                        quarantine,
                        hive_user,
                        hive_password
                        from pseidon_logs where log='" log-name "'")
        ]

    (info "load-topic-meta!: query " query)

    ;;load the meta keys from the database and add hive:db
    (update-metadata-nils-with-defaults
      log-name
      (add-hive-db-name (first (with-connection db (j/query conn query)))))))

(defn load-kafka-partition-config
  "Load the configuration for log-name from the db
   returns map with keys [:base_partition, :log_partition, :hive_db_name, :hive_table_name, :quarantine, :hive_url, :hive_user, :hive_password]"
  [db log-name]
  (do-with-default (default-topic-meta log-name)
                   (load-topic-meta! log-name db)))

(defn get-topic-meta
  "Function uses the kafa partition cache, which is loaded on demand by the load-kafka-partition-config which is called
   in the HdfsCopyService component see ,fun-utils/create-load-cache"
  [kafka-partition-conf-cache log-name]
  (get kafka-partition-conf-cache log-name (default-topic-meta log-name)))


(defn validate-base-dir [component base-dir]
  (when-not base-dir
    (throw (RuntimeException. (str ":local-dir must be specified " (:conf component)))))
  (let [file (io/file base-dir)]
    (when-not (and (.exists file) (.canWrite file) (.isDirectory file))
      (throw (RuntimeException. (str "local-dir " base-dir " must exist as a directory and be writable"))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;; hdfs file copy


(defn delete-local-resources [state topic remote-file file size]

  (info "Deleting file " file)
  (io/delete-file file)
  (delete-any-crc (.getParent ^File (.getAbsoluteFile (io/file file))))
  (when state
    (_write-copy-metrics state topic remote-file size "complete")))


(defn add-hive-partition [state hive-conn hive-db-name hive-table-name file topic]
  (let [datestr (file-name->date file)]
    (try
      (hive/add-partition hive-conn hive-db-name hive-table-name datestr)
      (ignore-exception-on-shutdown (:app-status state) _write-hive-metrics state topic "ok" (hive/datestr->partition-spec datestr) nil)
      (catch IOException e (do
                             (error e e)
                             (ignore-exception-on-shutdown (:app-status state) _write-hive-metrics state topic "error" (hive/datestr->partition-spec datestr) e))))))

(defn file->hdfs
  "Copy a local file to hdfs
   Ensures that the parent directories are created and also
   sets the hive partition on directory creation"
  [hive-ctx kafka-partition-conf-cache ^FileSystem fs state {:keys [file file-ok]} metrics]
  (let [
        topic (check-not-nil (file-name->topic file) (str "No topic could be extracted for the file " file))
        size (.length ^File (io/file file))
        {:keys [base_partition                              ;;:base_partition, :log_partition, :hive_db_name, :hive_table_name, :quarantine, :hive_url, :hive_user, :hive_password
                log_partition
                hive_db_name
                hive_table_name
                quarantine
                hive_url
                hive_user
                hive_password]} (get-topic-meta kafka-partition-conf-cache topic)


        ;;if the file is not ok we push to quarantine
        base-dir (if file-ok base_partition quarantine)

        ^String remote-file (create-remote-file-name base-dir log_partition file)
        ^String remote-parent-path (.getParent (io/file remote-file))
        ^String temp-file (create-temp-file remote-file)]

    (if file-ok
      (info "copying file " file " via " temp-file " to " remote-file)
      (error "copying corrupt file " file "  to quarantine " remote-file))

    (update-file-upload-meter metrics file)

    (create-dir-if-not-exist
      fs remote-parent-path
      :on-create #(when (and file-ok hive_url)
                   (info "add-hive-partition [start]")
                   (try
                     (hive/with-hive-conn hive-ctx
                                          hive_url
                                          hive_user
                                          hive_password
                                          (fn [hive-conn]
                                            (add-hive-partition state
                                                                hive-conn
                                                                hive_db_name
                                                                hive_table_name
                                                                file
                                                                topic)))
                     (catch Exception e
                       (when (not (shutdown? (:app-status state)))
                         (error e (str "Error adding partition for " {:topic topic
                                                                      :file file
                                                                      :hive-url hive_url
                                                                      :hive-user hive_user
                                                                      :hive-table hive_table_name
                                                                      :hive-db-name hive_db_name})))))

                   (info "add-hive-partition [end]")))


    (hdfs-copy-file fs file temp-file)

    (if (hdfs-rename fs temp-file remote-file)
      (delete-local-resources state topic remote-file file size)
      (when (hdfs-path-exists? fs remote-file)              ;; if we couldnt rename
        (hdfs-delete fs remote-file)                        ;; delete the remote file, and try the rename again
        (when (hdfs-rename fs temp-file remote-file)        ;; then delete the local resources again
          (delete-local-resources state topic remote-file file size))))))


(defn- copy-f-coll [app-status hive-ctx kafka-partition-conf-cache fs state file metrics]
  (try
    (when-not (shutdown-no-metrics? (:closed state) (:base-dir state))
      (ignore-exception-on-shutdown app-status
                                    file->hdfs
                                    hive-ctx
                                    kafka-partition-conf-cache
                                    fs
                                    state
                                    {:file file :file-ok (file-ok? file)}
                                    metrics))
    (catch Exception e (when-not (shutdown? (:closed state))
                         (error e e)))))

(defn copy-files
  "fs-f  (fs-f) -> FileSystem"
  [conf copy-thread-exec hive-ctx kafka-partition-conf-cache fs-f state dir metrics]
  {:pre [copy-thread-exec kafka-partition-conf-cache (fn? fs-f) state dir (:app-status state)]}
  (let [files (gz-file-seq conf (io/file dir))]
    (when (pos? (count files))
      (info "Copy files " (count files))
      (info "Copy thread-exec: " copy-thread-exec))

    (cp/prun! copy-thread-exec
              (fn [f]

                (when (and (local-path-exists? f) (not (.contains ^String (str f) "/tmp/copying")))
                  (copy-f-coll (:app-status state) hive-ctx kafka-partition-conf-cache (fs-f) state f metrics)))
              files)))

(defn copy-metrics-files [component]
  (let [{:keys [app-status
                base-dir
                writer
                closed
                metrics
                fs-f
                kafka-partition-conf-cache
                hive-ctx]} (:hdfs-copy-service component)

        state {:writer writer :closed closed :app-status app-status :base-dir base-dir}]
    (doseq [file (any-metrics-gz-file-seq base-dir)]
      (copy-f-coll app-status hive-ctx kafka-partition-conf-cache (fs-f) state file metrics))))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;; HdfsCopyService functions


(defn shutdown?
  "Returns true if the component is shutdown"
  [closed]
  (.get ^AtomicBoolean closed))


(defn copy-files-runner [conf copy-thread-exec app-status hive-ctx kafka-partition-conf-cache fs-f writer closed base-dir metrics]
  (try
    (when-not (shutdown-no-metrics? closed base-dir)
      (copy-files
        conf
        copy-thread-exec
        hive-ctx
        kafka-partition-conf-cache
        fs-f
        (assoc {} :writer writer :closed closed :app-status app-status :base-dir base-dir)
        base-dir
        metrics))
    (catch Exception e
      (when-not (shutdown? closed)
        (error e e)))))

(defn stop-all
  "Stop all resources associated with the hdfs-copy-service, used component/stop"
  [component]
  ;;flag to shutdown
  (.set ^AtomicBoolean (get-in component [:hdfs-copy-service :closed]) true)

  ;;;shutdown the thread that continuously searches for files to copy
  (cp/shutdown (get-in component [:hdfs-copy-service :copy-delay]))

  ;;;wait up to a minute for copies to complete
  (doto
    ^ExecutorService (get-in component [:hdfs-copy-service :cache-exec])
    .shutdown
    (.awaitTermination 2 TimeUnit/MINUTES)
    .shutdownNow)


  ;;;close other resources
  (ape/close (get-in component [:hdfs-copy-service :writer]))

  ;;after all copying stopped, copy any metric files
  (copy-metrics-files component)

  (hive/close-ctx (get-in component [:hdfs-copy-service :hive-ctx])))


(defn create-file-ape [base-dir callback-f]
  (ape/ape {:codec                :gzip
            :base-dir             base-dir
            :check-freq           2000
            :rollover-size        115000000
            :rollover-abs-timeout 600000
            :parallel-files       1
            :rollover-timeout     600000
            :roll-callbacks       [callback-f]}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;; Components

;depends on conf => configuretation map, db => db-service component
(defrecord HdfsCopyService [conf app-status db monitor-service]
  component/Lifecycle
  (start [component]
    (if (:hdfs-copy-service component)
      component
      (let [;;;FileSystem is not thread safe, we need to create a new instance
            ;;;from the config source for every thread
            thread-local-fs (thread-local #(FileSystem/get (map->hdfs-conf (get-in component [:conf :hdfs-conf]))))
            fs-f #(thread-local-get thread-local-fs)

            base-dir (get-in component [:conf :local-dir])

            hive-ctx (hive/create-ctx)
            ;;we use an extra thread for re-running the copy command periodically
            copy-thread-exec (cp/threadpool (inc (get-in component [:conf :copy-threads] 2)) :daemon true :name "hdfs-copy")

            metric-registry (hdfs-metrics/metric-registry)
            file-upload-meter (hdfs-metrics/meter metric-registry "hdfs-file-load-p/s")
            file-size-meter (hdfs-metrics/meter metric-registry "hdfs-file-bytes-p/s")

            metrics {:metric-registry   metric-registry
                     :file-upload-meter file-upload-meter
                     :file-size-meter   file-size-meter}

            closed (AtomicBoolean. false)
            writer (create-file-ape base-dir print-rolled-file)
            cache-exec (cp/threadpool 1 :daemon true :name "cache-exec")
            kafka-partition-cache-refresh (get-in component [:conf :kafka-partition-cache-refresh] 60000)
            kafka-partition-conf-cache (binding [fun-cache/*executor* cache-exec]
                                         (fun-cache/create-loading-cache (partial load-kafka-partition-config db) :refresh-after-write kafka-partition-cache-refresh))]

        ;;check that the local-dir exists
        (validate-base-dir component base-dir)

           ;;support kerberos login via keytab files
        (when (:secure conf)
              (UserGroupInformation/loginUserFromKeytab (str (:secure-user conf)) (str (:secure-keytab conf))))

        (when monitor-service
          (hdfs-mon/register monitor-service
                             :hdfs-version (fn [] HDFS-VERSION))

          (hdfs-mon/register monitor-service
                             :hfds-file-load-ps (fn [] (hdfs-metrics/metric->map file-upload-meter)))

          (hdfs-mon/register monitor-service
                             :hfds-file-load-bytes-ps (fn [] (hdfs-metrics/metric->map file-size-meter))))

        (assoc component :hdfs-copy-service {:base-dir                   base-dir
                                             :metrics                    metrics
                                             :closed                     closed
                                             :writer                     writer
                                             :cache-exec                 cache-exec
                                             :fs-f                       fs-f ;create a new hadoop file system instance
                                             :hive-ctx                   hive-ctx
                                             ;;create a loading refresh cache that will reload using load-kafka-partition-config
                                             :kafka-partition-conf-cache kafka-partition-conf-cache
                                             :copy-delay
                                                                         (schedule-fix-delay
                                                                           copy-thread-exec
                                                                           (get conf :hdfs-copy-freq 1000)
                                                                           #(copy-files-runner
                                                                             conf
                                                                             copy-thread-exec
                                                                             app-status
                                                                             hive-ctx
                                                                             kafka-partition-conf-cache
                                                                             fs-f
                                                                             writer
                                                                             closed
                                                                             base-dir
                                                                             metrics))}))))

  (stop [component]
    (when (:hdfs-copy-service component)
      (stop-all component))

    (dissoc component :hdfs-copy-service)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;; Public API

(defn create-hdfs-copy-service [conf app-status]
  (->HdfsCopyService conf app-status nil nil))

