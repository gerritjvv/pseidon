(ns

  ^{:doc "Use create-app-components and stop-app-components for testing
          and start! stop! for application start/stop"}
  pseidon-etl.app
  (:require
    [pseidon-etl.db-service :refer [create-database]]
    [clojure.tools.logging :refer [info error]]
    [pseidon-etl.jaas-service :as jaas-service]
    [pseidon-etl.nrepl-service :refer [create-nrepl-service]]
    [kafka-clj.consumer.node :refer [create-kafka-node-service shutdown-node!]]
    [kafka-clj.client :refer [create-client-service]]
    [pseidon-etl.etl-service :refer [create-etl-service]]
    [pseidon-etl.topic-service :refer [create-topic-service]]
    [pseidon-etl.mon :refer [create-monitor-service]]
    [pseidon-etl.writer :refer [writer-service]]
    [com.stuartsierra.component :as component])
  (:import (java.util.concurrent.atomic AtomicBoolean)))

(defonce ^AtomicBoolean shutdown? (AtomicBoolean. false))

(defn- app-create-kafka-node-service
  "Must be called with what startup-resources returns"
  [{:keys [work-calculate-freq
           kafka-use-earliest kafka-brokers redis-conf etl-group
           kafka-msg-ch-buffer-size] :or {etl-group "etl" kafka-use-earliest false work-calculate-freq 10000 kafka-msg-ch-buffer-size 50} :as conf}]
  (create-kafka-node-service
    {:bootstrap-brokers kafka-brokers
     :redis-conf        (assoc redis-conf :group-name etl-group)
     :conf              (merge
                          {:use-earliest        kafka-use-earliest
                           :work-calculate-freq work-calculate-freq
                           :msg-ch-buffer-size  kafka-msg-ch-buffer-size
                           :consume-step        1}

                          conf)}
    []))

(defonce app-state (atom nil))

(defn create-app-components
  "Create the app components based on the conf

   service-overrides is a map where each key's value can override a service in the system-map
   e.g {:db-service my-dbservice} will override the db service"
  [conf service-overrides]
  (component/start
    (component/system-map
      ;host user pwd
      :writer-service (:writer-service service-overrides (writer-service conf))
      :monitor-service (:monitor-service service-overrides (create-monitor-service conf))
      :nrepl-service (:nrepl-service service-overrides (create-nrepl-service conf))
      :kafka-node (:kafka-node-service service-overrides (app-create-kafka-node-service conf))
      :db (:db-service service-overrides
            (create-database (get conf :etl-db-host)
                             (get conf :etl-db-user)
                             (get conf :etl-db-pwd)))

      :topic-service (:topic-service service-overrides
                       (component/using
                         (create-topic-service (merge {:topic-refresh-freq-ms 10000 :etl-group "etl"} conf))
                         {:monitor-service :monitor-service
                          :database        :db
                          :writer-service  :writer-service
                          :kafka-node      :kafka-node}))

      :kafka-client (:kafka-client-service service-overrides
                      (create-client-service (:kafka-brokers conf) (merge {:consume-step 1} conf)))

      :etl-service (:etl-service service-overrides
                     (component/using
                       (create-etl-service conf)
                       [:topic-service :kafka-node :kafka-client :writer-service :monitor-service :db])))))

(defn start!
  "Start the application and save the state to app-state"
  [conf]

  (reset! app-state
          (create-app-components conf {})))

(defn- call-safe [f & args]
  (try
    (apply f args)
    (catch Exception e (error e e))))

(defn- stop-kafka-consume [{:keys [kafka-node]}]
  (if kafka-node
    (call-safe shutdown-node! (:node kafka-node))))

(defn- stop-topic-service [{:keys [topic-service]}]
  (if topic-service
    (call-safe component/stop topic-service)))

(defn- stop-etl-service [{:keys [etl-service]}]
  (if etl-service
    (call-safe component/stop etl-service)))


(defn stop-app-components [app]
  (stop-topic-service app)
  (info "Topic service stopped")
  (stop-kafka-consume app)
  (info "Kafka consume stopped")
  (stop-etl-service app)
  (info "Etl Service stopped"))

(defn stop!
  "Stop the application from the current app-state"
  []
  ;we need to stop the components in a specific order not just by their dependencies.
  (try
    (stop-app-components @app-state)
    (finally
      (do
        (info "Shutdown agents")
        (shutdown-agents)
        (info "Shutdown agents complete"))))
  (reset! app-state nil))

