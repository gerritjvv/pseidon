(ns pseidon-etl.jaas-service
  (:require
    [com.stuartsierra.component :as component]
    [clojure.tools.logging :refer [info error]])
  (:import (javax.security.auth.login LoginContext)))


(defn jaas-login ^LoginContext [jaas-name]
  (info "trying jaas login for name " jaas-name)
  (let [login (LoginContext. (str jaas-name))]
    (.login login)
    (info "login complete: have " login)
    login))


(defn run-jaas [^LoginContext ctx]
  )

(defrecord JaasService [conf]
  component/Lifecycle

  (start [component]
    (info "Starting Topic Service: " (:jaas conf))
    (when-let [jaas-name (:jaas conf)]
      (assoc component :jaas-thread (run-jaas (jaas-login jaas-name)))))

  (stop [component]
    ))


(defn create-jaas-service
  "Returns a service that needs to be started still"
  [conf]
  (->JaasService conf))