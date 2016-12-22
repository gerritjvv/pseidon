(ns
  ^{:doc "Abstract utility functions"}
  pseidon-hdfs.util
  (:require [clojure.tools.logging :refer [error]]
            [clojure.string :as string]
            [clj-time.coerce :as time-c]
            [clj-json.core :as clj-json]
            [pseidon-hdfs.lifecycle :as app-life])
  (:import (org.joda.time DateTime)
           (java.util.concurrent ScheduledExecutorService TimeUnit)))


(defn padd-zero [n]
  (if (< n 10) (str 0 n) (str n)))

(defn if-null [v default]
  (if v v default))

(defn if-keys
  "If all keys exist in m and are not nil return v, otherwise return default"
  ([m ks]
   (reduce
     (fn [m k] (if (get m k) m (reduced nil)))
     m
     ks))
  ([m ks v default]
   (if (if-keys m ks)
     v
     default)))

(defmacro do-with-default [default & body]
  `(try
     (if-let [v# (do
                    ~@body)]
       v#
       ~default)
     (catch Exception e# (do
                           (error e# e#)
                           ~default))))

(defn ^String create-temp-file
  "Take a file name and returns the temp file for it"
  [^String remote-file]
  (string/join "/" ["/tmp" (string/replace (str "copying" remote-file) #"/" "_")]))

(defn get-local-dt-hr-str [^long ts]
  (let [^DateTime dt (time-c/from-long ts)
        y (padd-zero (.getYear dt))
        m (padd-zero (.getMonthOfYear dt))
        d (padd-zero (.getDayOfMonth dt))
        h (padd-zero (.getHourOfDay dt))]
    (str y "-" m "-" d "-" h)))

(defn as-bytes [msg]
  (.getBytes ^String (clj-json/generate-string msg) "UTF-8"))

(defn app-shutdown? [app-status]
  (or (nil? app-status) (app-life/-shutdown? app-status)))

(defn ignore-exception-on-shutdown
  "Apply the function an only rethrow the exception if shutdown? returns false"
  [app-status f & args]
  (try
    (apply f args)
    (catch RuntimeException e
      (when-not (app-shutdown? app-status)
        (throw e)))))

(defn check-not-nil [v msg]
  (if v
    v
    (throw (ex-info msg {}))))


(defn thread-local
  "Creates a ThreadLocal that returns the (suplier-f)"
  [suplier-f]
  (proxy [ThreadLocal] []
    (initialValue []
      (suplier-f))))

(defn thread-local-get [^ThreadLocal th]
  (.get th))

(defn schedule-fix-delay [^ScheduledExecutorService exec delay ^Runnable f]
  (.scheduleWithFixedDelay exec f (long delay) (long delay) TimeUnit/MILLISECONDS)
  exec)
