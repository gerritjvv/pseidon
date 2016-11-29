(ns pseidon-hdfs.db-service
  (:require
    [clojure.tools.logging :refer [info error]]
    [hikari-cp.core :refer :all]
    [com.stuartsierra.component :as component]
    [clojure.java.jdbc :as j]
    [hikari-cp.core :refer :all]))


;;Creates generic database connections as components

(defn- create-pool [{:keys [auto-commit
                            connection-timeout
                            idle-timeout
                            max-lifetime
                            minimum-idle
                            adapter
                            username
                            password
                            database-name
                            server-name
                            port] :or
                           {auto-commit true
                            connection-timeout 30000
                            idle-timeout       600000
                            max-lifetime       1800000
                            minimum-idle       10
                            maximum-pool-size  2
                            adapter            :mysql
                            port               3306}}]
  (let [spec  {:auto-commit auto-commit
               :connection-timeout connection-timeout
               :idle-timeout idle-timeout
               :max-lifetime max-lifetime
               :minimum-idle minimum-idle
               :adapter (name adapter)
               :username username
               :password password
               :database-name database-name
               }]
    (try
      (make-datasource
         (if (= adapter :hsqldb) spec (assoc spec :port port :server-name server-name)))
      (catch Exception e (do
                           (error "Error connecting to db with spec " spec)
                           (throw e))))))

(defrecord DatabasePool [conf]
  component/Lifecycle

  (start [component]
    (if-not (:pool component)
      (assoc component :pool (create-pool (:conf component)))
      component))

  (stop [component]
    (if (:pool component)
      (do
        (-> component :pool close-datasource)
        (dissoc component :pool))
      component)))


(defn start-db [db]
  (component/start db))

(defn stop-db [db]
  (component/stop db))


(defmacro with-connection
  "Helper macro that executes the statements inside a jdbc connection extracted from the :pool in db
   statements should refer to conn as the connection name.
   e.g (j/query conn 'select * from tags'"
  [db & statements]
  `(j/with-db-connection [~'conn {:datasource (:pool ~db)}] ~@statements))

(defn create-database
  "host: localhost
   db:   db ;database name
   user: username
   pwd: passwrod
   keys :adapter :mysql
        :max-statements pool options default 200
        :max-pool-size default 1"
  [host db user pwd & keys]

  (let [conn (merge {:server-name host
                     :database-name db
                     :username user
                     :password     pwd}
                    (apply hash-map keys))]
    (->DatabasePool conn)))


