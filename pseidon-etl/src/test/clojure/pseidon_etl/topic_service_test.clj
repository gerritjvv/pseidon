(ns pseidon-etl.topic-service-test
  (:require
    [midje.sweet :refer :all]
    [clojure.test :refer :all]
    [com.stuartsierra.component :as component]
    [clojure.tools.logging :refer [info error]]
    ;[pseidon-etl.writer :refer [writer-service]]
    [kafka-clj.consumer.node :refer [create-kafka-node-service]]
    [pseidon-etl.db-service :refer :all]
    [pseidon-etl.test-utils :refer [create-db-tables startup-resources shutdown-resources]]
    [pseidon-etl.topic-service :refer :all]
    [pseidon-etl.test-utils :refer :all]

    [clojure.java.jdbc :as j]
    [clojure.java.io :as io])
  (:import [java.io File]))


(defonce state-ref (ref {}))


(defn- setup-database [db]
  (create-db-tables db)

  (with-connection db
                   (j/insert-values
                     :pseidon_logs
                     [:log :log_group :enabled]
                     ["test1" "etl" 1])))


(defn- insert-logs [db values]
  (with-connection db
                   (j/insert-values
                     :pseidon_logs
                     [:log :log_group :enabled]
                     values)))
(defn- update-logs [db log enabled]
  (with-connection db
                   (j/update-values
                     :pseidon_logs
                     ["log=?" log]
                     {:enabled enabled})))

(defn- load-resources []
  (let [resources (startup-resources "test1" "test2")]
    (dosync (alter state-ref assoc :resources resources))
    resources))

(defn _create-test-dir []
  (let [file (io/file (str "target/test/topic-service-test/dir-" (System/currentTimeMillis)))]
    (.mkdirs ^File file)
    (.getAbsolutePath file)))


(defn start-topic-service []
  (let [db (test-db-service)
        conf {:base-dir (_create-test-dir)}]

    (setup-database db)
    (component/stop db)
    (load-resources)
    (component/start
      (component/system-map
        :db db
        :writer-service (writer-service conf)
        :kafka-node (create-test-kafka-node-service (-> state-ref deref :resources) ["test1" "test2"])
        :topic-service (component/using
                         (create-topic-service {:topic-refresh-freq-ms 500 :etl-group "etl"})
                         {:database :db
                          :kafka-node :kafka-node
                          :writer-service :writer-service})))))

(defn stop-all []
  (component/stop (-> state-ref deref :system))
  (shutdown-resources (-> state-ref deref :resources)))

(deftest topic-service-test
  (require '[clojure.java.jdbc.sql :refer [update]])

  (with-state-changes [(before :facts
                               (let [system (start-topic-service)]
                                 (dosync (alter state-ref assoc :system system))))
                       (after :facts (stop-all))]

                      (fact "Test add and remove topics"
                            (insert-logs (-> state-ref deref :system :db) ["test2" "etl" 1])
                            (Thread/sleep 1000)
                            (-> state-ref deref :system :kafka-node :node :topics-ref deref) => #{"test1" "test2"})))