(ns pseidon-hdfs.conf
  (:require [clojure.edn :as edn]))



(defn load-conf
  "Load a configuration file in edn format"
  [file]
  (edn/read-string (slurp file)))


(def load-conf-m (memoize load-conf))

;core main will neet to set this usin alter-var-root
(def ^:dynamic *default-conf* nil)


(defn conf [] (if *default-conf* *default-conf* (load-conf-m "resources/pseidon.edn")))

(defn get-conf [k if-not-found]
  (get (conf) k if-not-found))
