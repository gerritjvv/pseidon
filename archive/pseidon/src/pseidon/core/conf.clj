(ns pseidon.core.conf
  (:require [clojure.tools.logging :refer [info error]]
            [clojure.edn :as edn]))

;this module contains the application configuration defaults and all logic that is used to read the configuration

(def ^:dynamic *default-conf* "/opt/pseidon/conf/pseidon.edn")

(def conf (ref {}))

(defn set-conf! [k v]
  "Sets the configuration value"
  (dosync (alter conf (fn [p] (assoc p k v)) 
  )))
  
(defn load-props[file]
  (let [props (edn/read-string (slurp file)) ]
    (if (map? props) props (throw (Exception. (str "The config file " file " must be a map { :kw val :kw2 val2 }") )))
  ))
  
(defn load-config! [configFile]
(info "Loading config " configFile)
(dosync (alter conf
               (fn [p] (conj p (load-props configFile)  ))))

)

(defn load-default-config! []
  (load-config! *default-conf*)
  )


(defn get-sub-conf [parent-key]
  "If the config contains {:abc.id 1 :abc.f 1} calling this function as (get-sub-conf :abc) returns {:id 1 :f 1}"
  (into {}
        (map 
          #(let [[k v] %] 
             [(keyword 
                (clojure.string/replace (str k) 
                                        (re-pattern (str parent-key "\\.")) "")) 
              v]
             ) 
          @conf)))
  

(defn get-conf [n]
  (get @conf (keyword n))  
  )

(defn get-conf2 [n default-v]
  (if (empty? @conf)
    (load-default-config!))
  
  (get @conf (keyword n) default-v)  
  )
