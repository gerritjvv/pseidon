(ns pseidon-hdfs.date-path
  (:require [clj-time.format :as time-f]
            [pseidon-hdfs.util :as util])
  (:import (org.joda.time DateTime)
           (org.apache.commons.lang StringUtils)))

(defonce datetime-formatter (time-f/formatter "yyyy-MM-dd-HH"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;; utility function

(defn remove-first-slash
  "Remove the front slash in a path if it exists"
  [^String path]
  (StringUtils/removeStart path "/"))

(defn remove-last-slash
  "Remove the front slash in a path if it exists"
  [^String path]
  (StringUtils/removeEnd path "/"))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;; public functions

(defmulti date-path (fn [dateformat basedir topic-parition host-name date file-name] dateformat))


(defmethod date-path "datehour"
  [_ base-dir topic-partition host-name date file-name]
  (let [^DateTime dt (time-f/parse datetime-formatter date)
        y (.getYear dt)
        m (util/padd-zero (.getMonthOfYear dt))
        d (util/padd-zero (.getDayOfMonth dt))
        h (util/padd-zero (.getHourOfDay dt))]
    (str (remove-last-slash base-dir) "/" (remove-last-slash (remove-first-slash topic-partition)) "/date=" y m d "/hour=" y m d h "/" host-name "-" file-name)))

(defmethod date-path :default
  [_ base-dir topic-partition host-name date file-name]
  (let [^DateTime dt (time-f/parse datetime-formatter date)
        y (.getYear dt)
        m (util/padd-zero (.getMonthOfYear dt))
        d (util/padd-zero (.getDayOfMonth dt))]
    (str (remove-last-slash base-dir) "/" (remove-last-slash (remove-first-slash topic-partition)) "/date=" y m d "/" host-name "-" file-name)))
