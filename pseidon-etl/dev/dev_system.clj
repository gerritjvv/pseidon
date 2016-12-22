(ns dev-system
  (:import [java.nio.file Files Paths]
           [java.net URI])
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.pprint :refer (pprint)]
            [clojure.repl :refer :all]
            [clojure.test :as test]
            [com.stuartsierra.component :as component]

            [clojure.tools.logging :refer [info error]]
            [clojure.tools.namespace.repl :refer (refresh refresh-all)]
            [pseidon-etl2.etl-service :as etl]
            [clojure.java.jdbc :as j]

            [pseidon-etl2.batch :as batch]
            [pseidon-etl2.mon :refer [create-monitor-service]]
            [pseidon-etl2.test-utils :refer [startup-resources shutdown-resources create-test-kafka-node-service create-test-kafka-client-service] :as test-util]
            [pseidon-etl2.etl-service :refer [create-etl-service]]
            [kafka-clj.client :refer [send-msg]]
            [pseidon-etl2.convert :refer [msg->json json->bts]]
            [pseidon-etl2.conf :as conf]
            [pseidon-etl2.db-service :as db]))



(defonce state-ref (ref {}))


(defn load-message []
  (batch/bts->protobts (System/currentTimeMillis) "1.33" (Files/readAllBytes (Paths/get "test-resources" (into-array ["adxbidrequests"])))))


(defn send-test-messages [system topic n]
  (let [c (get-in system [:kafka-client :client])
        test-msg (load-message)]
    (dotimes [i n]
      (send-msg c topic test-msg))))

(defn- load-resources []
  (let [resources (startup-resources "adx-bid-requests" "adx-bid-requests-etl" "etl-stats" "etl-metrics" "test1" "test2" "test1-etl" "test2-etl")]
    (dosync (alter state-ref assoc :resources resources))
    resources))

(defn- setup-database [db]
  (db/with-connection db
                   (j/do-commands
                     "CREATE TABLE kafka_logs (log VARCHAR(20), type VARCHAR(10), log_group VARCHAR(10), enabled INTEGER)"))
  (db/with-connection db
                   (j/do-commands
                     "CREATE TABLE log_pullers (source VARCHAR(20), log VARCHAR(20))"))
  (db/with-connection db
                   (j/insert-values
                     :kafka_logs
                     [:log :type :log_group :enabled]
                     ["adx-bid-requests" "etl" "etl" 1]))
  (db/with-connection db
                   (j/insert-values
                     :log_pullers
                     [:log :source]
                     ["adx-bid-requests" "radium1"])))


(defn stop-all []
  (shutdown-resources (-> state-ref deref :resources)))

(defn start [& _]
  (let [db (test-util/test-db-service)]
    (setup-database db)

    (load-resources)
    (component/start
      (component/system-map
        :monitor-service (create-monitor-service {})
        :db db

        :kafka-client (create-test-kafka-client-service (-> state-ref deref :resources))
        :kafka-node (create-test-kafka-node-service (-> state-ref deref :resources) ["adx-bid-requests" "test1" "test2"])
        :etl-service (component/using
                       (create-etl-service (conf/conf))
                       [:kafka-node :kafka-client :monitor-service :db])))))



(defn stop [s]
  (component/stop s)
  (stop-all))
