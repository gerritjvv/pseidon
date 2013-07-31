(ns pseidon.core.conf
  (:use clojure.tools.logging
        [clojure.edn]
        ))
;this module contains the application configuration defaults and all logic that is used to read the configuration

(def ^:dynamic *default-conf* "resources/conf/pseidon.edn")

(def conf (ref {}))

(defn set-conf! [k v]
  "Sets the configuration value"
  (dosync (alter conf (fn [p] (assoc p k v)) 
  )))
  
(defn load-props[file]
  (let [props (clojure.edn/read-string (slurp file)) ]
    (prn "Is Map " (map? props))
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


(defn get-conf [n]
  (get @conf n)  
  )

(defn get-conf2 [n default-v]
  (get @conf n default-v)  
  )