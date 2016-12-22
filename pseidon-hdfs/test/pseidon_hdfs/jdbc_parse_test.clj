(ns
  ^{:doc "Test the jdbc parser functionality"}
  pseidon-hdfs.jdbc-parse-test
  (:use midje.sweet)
  (:require [pseidon-hdfs.hdfs-copy-service :as s]
            [clojure.test :refer :all]))


(def jdbc-urls [["jdbc:mysql:loadbalance://localhost:3306,localhost:3310/sakila" "sakila"]
                ["jdbc:mysql://localhost:3306/sakila?profileSQL=true" "sakila"]
                ["jdbc:postgresql://localhost/test?user=fred&password=secret&ssl=true" "test"]])

(deftest jdbc-parse-test
         (fact "Test parse the db-name"
               (doseq [[url db-name] jdbc-urls]
                 (s/jdbc-db url) => db-name)))
