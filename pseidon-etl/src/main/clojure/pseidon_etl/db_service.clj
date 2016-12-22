(ns pseidon-etl.db-service
  (:import [java.io Closeable])
  (:require
    [clojure.tools.logging :refer [info]]
    [com.stuartsierra.component :as component]
    [clojure.java.jdbc :as j]
    [org.tobereplaced.jdbc-pool :refer [pool]]))


;;Creates generic database connections as components

(defrecord DatabasePool [conf conn]
  component/Lifecycle

  (start [component]
    (assoc component :pool (pool conn :max-statements (int (:max-statements conf)) :max-pool-size (int (:max-pool-size conf)))))

  (stop [component]
    (.close ^Closeable (:pool component))))


(defn start-db [db]
  (component/start db))

(defn stop-db [db]
  (component/stop db))


(defmacro with-connection
  "Helper macro that executes the statements inside a jdbc connection extracted from the :pool in db"
  [db & statements]
  `(j/with-connection (:pool ~db) ~@statements))

(defn create-database
  "host: \"//localhost:3306/db\"
   user: username
   pwd: passwrod
   keys :driver jdbc-driver class default is com.mysql.jdbc.Driver
        :protocl default is mysql
        :max-statements pool options default 200
        :max-pool-size default 1"
  [host user pwd & {:keys [driver protocol max-statements max-pool-size] :or
                          {driver "com.mysql.jdbc.Driver" protocol "mysql" max-statements 200 max-pool-size 1}}]

  (let [conn {:classname driver
              :subprotocol protocol
              :subname     host
              :user        user
              :password    pwd}]
    (->DatabasePool {:max-statements max-statements :max-pool-size max-pool-size} conn)))


