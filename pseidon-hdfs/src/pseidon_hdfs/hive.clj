(ns pseidon-hdfs.hive
  (:import (java.sql DriverManager Statement Connection)
           (org.joda.time DateTime)
           (java.io File)
           (org.apache.commons.pool2 KeyedPooledObjectFactory PooledObject)
           (org.apache.commons.pool2.impl GenericKeyedObjectPool GenericKeyedObjectPoolConfig DefaultPooledObject)
           (org.apache.commons.lang StringUtils))
  (:require
            [clojure.tools.logging :refer [info]]
            [clj-time.format :as time-f]
            [clojure.java.io :as io]))

(defonce driver-name "org.apache.hive.jdbc.HiveDriver")


(defonce datetime-formatter (time-f/formatter "yyyy-MM-dd-HH"))

(defn- padd-zero [n]
  (if (< n 10) (str 0 n) (str n)))

(defn datestr->partition-spec
  "returns 'dt=' (str y m d) ',hr=' (str y m d h)}"
  [datestr]
  (let [^DateTime dt (time-f/parse datetime-formatter datestr)
        y (.getYear dt)
        m (padd-zero (.getMonthOfYear dt))
        d (padd-zero (.getDayOfMonth dt))
        h (padd-zero (.getHourOfDay dt))]
    (str "dt=" y m d ",hr=" y m d h)))


(defn this-or-else [v default]
  (if v v default))

(defn clean-host-name [host-name]
  (StringUtils/removeStart (str host-name) "//"))

(defn hive-connection [host-name & {:keys [port user password] :or {user "hive" password ""}}]
  (Class/forName driver-name)
  (info "Creating hive connection: "  (str "jdbc:hive2://" (clean-host-name host-name)) (this-or-else user "hive") (this-or-else password ""))
  {:conn (DriverManager/getConnection (str "jdbc:hive2://" (clean-host-name host-name)) (this-or-else user "hive") (this-or-else password ""))})

(defn close [{:keys [^Connection conn]}]
  (when conn
    (.close conn)))

(defn closed? [{:keys [^Connection conn]}]
  (or (not conn) (.isClosed conn)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;; connection pooling

(defrecord PoolKey [host-name user password])

(defn pool-key [host-name & {:keys [user password] :or {user "hive" password ""}}]
  (->PoolKey host-name (this-or-else user "hive") (this-or-else password "")))

(defn pooled-obj ^PooledObject [v]
  (DefaultPooledObject. v))

(defn pooled-obj-val [^PooledObject p]
  (.getObject p))

(defn keyed-pool-factory ^KeyedPooledObjectFactory []
  (reify KeyedPooledObjectFactory
    (makeObject [this {:keys [host-name port user password]}]
      (pooled-obj (hive-connection host-name :user user :password password)))

    (destroyObject [this k pooledObject]
      (close (pooled-obj-val pooledObject)))

    (validateObject [this k pooledObject]
      (not (closed? (pooled-obj-val pooledObject))))

    (activateObject [this k pooledObject]
      )
    (passivateObject [this k pooledObject]
      )))

(defn keyed-pool-conf ^GenericKeyedObjectPoolConfig []
  (doto
    (GenericKeyedObjectPoolConfig.)
    (.setMaxIdlePerKey 1)
    (.setMaxTotalPerKey 10)
    (.setMinIdlePerKey 0)
    (.setTestOnBorrow true)))

(defn hive-conn-keyed-pool ^GenericKeyedObjectPool[]
  (GenericKeyedObjectPool. (keyed-pool-factory) (keyed-pool-conf)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;; sql functions


(defn- ^Statement exec [^Statement st ^String sql]
  (.execute st sql)
  st)

(defn- ^Statement retry-exec [n ^Statement st ^String sql]
  (try
    (exec st sql)
    (catch Exception e (do
                         (info "Failed to execute " sql " retrying")
                         (if (pos? n)
                           (do
                             (Thread/sleep 2000)

                             (retry-exec (dec n) st sql))
                           (throw e)))))
  st)


(defn sql-exec [{:keys [^Connection conn]} sql]
  (exec (.createStatement conn) sql))

(defn create-db [conn db-name path]
  (sql-exec conn (str "CREATE DATABASE IF NOT EXISTS " db-name " LOCATION '" (.getAbsolutePath ^File (io/file path)) "'")))

(defn add-partition
  "yyyy-MM-dd-HH"
  [{:keys [^Connection conn]} db table datestr]
  (let [^Statement st (exec (.createStatement conn) (str "use " db))
        partition-spec (datestr->partition-spec datestr)]

    (retry-exec 5 st (str "ALTER TABLE " table " ADD IF NOT EXISTS PARTITION (" partition-spec ")"))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;; public api

(defn create-ctx []
  {:pool (hive-conn-keyed-pool)})

(defn close-ctx [{:keys [^GenericKeyedObjectPool pool]}]
  (.close pool))

(defn with-hive-conn [{:keys [^GenericKeyedObjectPool pool]} hive-url hive-user hive-pwd f]
  (let [k (pool-key hive-url :user hive-user :password hive-pwd)]
    (when-let [hive-conn (.borrowObject pool k)]
      (try
        (info "do-with-hive-conn " hive-conn)
        (f hive-conn)
        (finally
          (info "returning-hive-conn")
          (.returnObject pool k hive-conn)
          (info "returned-hive-conn"))))))
