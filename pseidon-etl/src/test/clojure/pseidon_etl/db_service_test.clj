(ns pseidon-etl.db-service-test
  (:require [midje.sweet :refer :all]
            [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [clojure.tools.logging :refer [info error]]
            [pseidon-etl.db-service :refer :all]
            [pseidon-etl.topic-service :as topic-service]
            [pseidon-etl.test-utils :refer :all]
            [clojure.java.jdbc :as j]))



(deftest db-service-tests
  (with-state-changes [(before :facts (do ))
                       (after :facts (do ))]

                      (fact "Test start and stop database"
                            (let [db (test-db-service)]
                              (with-connection db
                                               (j/do-commands
                                                 "CREATE TABLE IF NOT EXISTS emp (id INTEGER, name VARCHAR(50), age INTEGER)"))
                              1 => 1
                              (component/stop db)))



                      (fact "Test start and stop database disable topics"
                            (let [db (test-db-service)]
                              (with-connection db
                                               (j/do-commands
                                                 "DROP TABLE IF EXISTS pseidon_logs"))

                              (with-connection db
                                               (j/do-commands
                                                 "CREATE TABLE IF NOT EXISTS pseidon_logs (log VARCHAR(50), enabled INTEGER, log_group VARCHAR(50))"))
                              (with-connection db
                                               (j/do-commands
                                                 "INSERT INTO pseidon_logs (log, enabled, log_group) VALUES('test', 1, 'etl')"))

                              (topic-service/disable-topic! db "test")
                              (with-connection db
                                               (j/with-query-results rs ["select * from pseidon_logs"] (vec (map :enabled rs)))) => [0]
                              (component/stop db)))))

