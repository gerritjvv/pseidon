(ns pseidon-hdfs.app
  (:require [pseidon-hdfs.db-service :refer [create-database]]
            [clojure.tools.logging :refer [info error]]
            [pseidon-hdfs.nrepl-service :refer [create-nrepl-service]]
            [pseidon-hdfs.hdfs-copy-service :refer [create-hdfs-copy-service]]
            [pseidon-hdfs.riemann-service :refer [create-riemann-service]]
            [pseidon-hdfs.mon :refer [create-monitor-service]]
            [com.stuartsierra.component :as component])
  (:gen-class))


(defonce app-state (atom nil))

(defn start!
  "Start the application and save the state to app-state"
  [conf app-status]

  (reset! app-state
         (component/start
           (component/system-map
             :monitor-service (create-monitor-service conf)
             :nrepl-service (create-nrepl-service conf)

             :riemann-service (create-riemann-service conf)

             :db (create-database (get conf :hdfs-db-host)
                                  (get conf :hdfs-db-name)
                                  (get conf :hdfs-db-user)
                                  (get conf :hdfs-db-pwd))

             :hdfs-copy-service (component/using
                                  (create-hdfs-copy-service conf app-status)
                                  [:db :monitor-service])))))

(defn- call-safe [f & args]
  (try
    (apply f args)
    (catch Exception e (error e e))))


(defn stop!
  "Stop the application from the current app-state"
  []
  ;we need to stop the components in a specific order not just by their dependencies.

  (call-safe component/stop (:hdfs-copy-service @app-state))
  (info "Stopped copy service")

  (call-safe component/stop (:monitor-service @app-state))
  (info "Stopped monitor service")

  (call-safe component/stop (:nrepl-service @app-state))
  (info "Stopped nrepl service")

  (call-safe component/stop (:db @app-state))
  (info "Stopped db service")
  (shutdown-agents)
  (System/exit 0))

