(ns pseidon.core.tracking
 (:require [clojure.java.jdbc :as sql]
           [pseidon.core.conf :refer [get-conf2] ]
           [pseidon.core.utils :refer [buffered-select fixdelay]]
           [clojure.tools.logging :refer [info error]]
           )
 (:import [org.apache.commons.lang StringUtils]
          [java.sql Timestamp]
          [java.util Date])
  )

(def status-run "run")
(def status-done "done")

(defn ^Date now [] (Date.))




(defn create-table[db]
    "Creates the message table, if it already exists the method silently fails."
		(defn create-tables
		  []
		  (do 
        (clojure.java.jdbc/with-connection db
          (clojure.java.jdbc/transaction
				    (sql/create-table
				      "messagetracking"
				      [:dsid "VARCHAR(255)" "NOT NULL" "PRIMARY KEY"]
				      [:status "VARCHAR(20)" "NOT NULL"]
				      [:ts "TIMESTAMP" "NOT NULL"])
				   (sql/do-commands "CREATE INDEX messagetraking_index1 ON messagetracking(dsid, status, ts)")
           (sql/do-commands "CREATE INDEX messagetraking_index2 ON messagetracking(ts, status)")))
        ))
		
	   (defn wrap-table-exist-exception [f]
	    (try (f) 
	        (catch java.sql.BatchUpdateException e 
	               (if-not (re-find #"name already exists" (.toString e)) (throw e)))))
      (wrap-table-exist-exception
           create-tables))

(defn create-spec [path]
   (let [spec  {:classname "org.hsqldb.jdbcDriver" 
     :subprotocol "hsqldb" 
     :subname (str "file:"  path ";create=true")
     :user "sa"
     :password "sa"
     }]
     (create-table spec)
     spec
     ))

(def dbspec (delay (create-spec (get-conf2 "tracking-db-dir" "/tmp/pseidon-tracking4"))))

(defn ^:Dynamic ensure-started [] )

(defmacro txn-helper [spec & body]
	      `(clojure.java.jdbc/with-connection
          (force ~spec)
          (clojure.java.jdbc/transaction ~@body
	         )))

(defmacro with-txn
 [spec body]
    `(txn-helper ~(if spec spec dbspec )  ~body))


(defn as-str [& s] (apply str s))

(defn create-query-paging [{:keys [tbl properties predicate from max] :or {max 100} }]
  "Creates a SQL query using paging and ROWNUM()"
  (str "SELECT * from (select " (clojure.string/join "," (map #(str "a." %) properties)) 
                        ", ROWNUM() rnum from (select " (clojure.string/join "/" properties) 
                        " from " tbl
                        (if-not predicate "" (str " WHERE " predicate)) " order by dsid,ts,status) a "
                        " WHERE ROWNUM() <= " (+ from max)
                        ") WHERE rnum >= " from))



(defn query [q max]
   (sql/with-query-results rs [q] 
     (let [dsid (:dsid (first rs) )]
       (vec (take max rs))))
   )

(defn get-message [dsid]
  (first (query (str "select * from messagetracking where dsid='" dsid "'") 1)))
  

(defn delete-message! [dsid]
    (sql/delete-rows :messagetracking ["dsid=?" dsid]))
  
(defn- parse-ts [ts]
  (cond 
    (instance? Timestamp ts) ts
    (instance? Date ts) ts
    (instance? Number ts) (Timestamp. (.longValue ^Number ts))
    :else (Timestamp. ts)))

(defn insert-message! [{:keys [dsid status ^Long ts]}]
  "Insert data into the table"
  [dsid status ts]
  (clojure.java.jdbc/insert-values
   :messagetracking
   [:dsid :status :ts]
   [dsid status (parse-ts ts)]))



(defn destruct-dsid [^String ds-id]
  "Destructs the ds-id into [ns id]"
  (let [ [ns & ids] (StringUtils/split ds-id \u0001)]
    [ns (clojure.string/join \u0001 ids)]
    ))

(defn mark-run! [^String ds ^String id & {:keys [db] :or {db @dbspec}}]
  " The ds and id values cannot hold any byte 1 characters, the key formed is ds byte1 id and must be unique, 
    if the object already exists in the database a unique constraint exception will be thrown.

    This method saves the message tracking metadata with status==run
  "
  {:pre (and (string? ds) (string? id)) }
  (with-txn db
	  (let [ds-id (clojure.string/join \u0001 [ds id] )]
        (insert-message! {:dsid ds-id :status status-run :ts (now)})    
        )))

;(defn create-query-paging [{:keys [tbl properties predicate from max]}]
  
(defn deserialize-message [{:keys [dsid status ts] }]
  (let [[ds id] (destruct-dsid dsid)]
        {:ds ds :ids id :status status :ts ts}))

(defn select-messages  
  "Returns a vector of messages from to max
  "
  ([^long from ^long max]
  (query (create-query-paging {:tbl "messagetracking" :properties ["*"] :from from :max max} ) (+ from max)))
  ([^String where ^long from ^long max]
    (query (create-query-paging {:tbl "messagetracking" :predicate where :properties ["*"] :from from :max max} ) (+ from max))))

(defn update-check-message! [dsid f-check params]
  "This method updates a blog entry, but before updating and checks the current message first"
  [dsid params]
   (let [msg (get-message dsid)]
     
	   (if (f-check msg)
		  (do 
      (clojure.java.jdbc/update-values
		   :messagetracking
		   ["dsid=?" dsid]
		   params)
      )
	     (throw (RuntimeException. (str "The message tracking state is not valid dsid: " dsid  " msg: " msg)))
		  )
   ))

(defn update-message! [dsid params]
  "This method updates a blog entry"
  [dsid params]
  (clojure.java.jdbc/update-values
   :messagetracking
   ["dsid=?" dsid]
   params))
(defn- util-join [v]
  (StringUtils/join v \u0001))

(defn mark-done! 
  [^String ds ids ^clojure.lang.IFn f & {:keys [db] :or {db @dbspec}}]
  "
    Applies the function f inside a transaction together with the set status to done
    If f fails the status will be rolled back.
    The status is set first to ensure that if there is any failure with the emebedded db the function f is never applied.
  "
   {:pre (string? ds)}
    (with-txn db
      ;if ids is a sequence update every id and then apply f
     (do
      (doseq [id (if (sequential? ids) ids [ids])]
				      (update-check-message!  
				            (util-join [ds id])
                    (fn [msg] (= (:status msg) status-run))
					          {:status status-done}))
        ))
     (f)
     )

  
(defn select-ds-messages  
  "Returns a vector of messages from to max"
  [^String ds & {:keys [db max status] :or {db @dbspec max 100 status status-run} } ]
  (with-txn db
          (select-messages (str "dsid like '" (clojure.string/join [ds \u0001 \%]) "' and status='" status "'") 0 max)))

(defn expire-old-messages [^Long ts & {:keys [db] :or {db @dbspec}}]
  "Runs a delete query on the current db to remove records older than ts"
  (with-txn db
    (sql/delete-rows "messagetracking" ["ts <= ?" (Timestamp. ts)])))


(defn message-statuscount [dsid & {:keys [db max] :or {db @dbspec max 100}}]
  "Returns the messages filtered by where and grouped by status as 
   [{:status \"status\" :n count-value} , ... ] 
  "
  (with-txn db
    (query (str "select status, count(*) as n from messagetracking where dsid like '" dsid \u0001 "%' group by status") max)))

(defn shutdown []
  )

(defn tracking-start [] 
  ;we need a way to cleanout records older than n this cleanout needs to run periodically
  ;this method will run every 6 hours (i.e. 4 times a day)
  (fixdelay 21600000 (expire-old-messages (get-conf2 "tracking.expirems" 2419200000))
  ))



