(ns pseidon-hdfs.db-service-test
  (:require [midje.sweet :refer :all]
            [com.stuartsierra.component :as component]
            [clojure.tools.logging :refer [info error]]
            [pseidon-hdfs.db-service :refer :all]
            [pseidon-hdfs.test-utils :refer :all]
            [clojure.test :refer :all]
            [clojure.java.jdbc :as j]))



(deftest test-db-service
         (with-state-changes [(before :facts (do ))
                              (after :facts (do ))]

                             (fact "Test start and stop database"
                                   (let [db (-> (create-test-database) (component/start))]
                                     (with-connection db
                                                      (j/db-do-commands
                                                        conn
                                                        "CREATE TABLE IF NOT EXISTS emp (id INTEGER, name VARCHAR(50), age INTEGER)"))
                                     1 => 1
                                     (component/stop db)))))
