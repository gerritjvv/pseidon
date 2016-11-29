(ns plugins.datasources.testsftp
  (:require
      [pseidon.core.ds.ftp :refer [ftp-connect get-files get-line-seq! load-recover-messages! delete-done-file]]
      [pseidon.core.conf :refer [get-conf2]]
      [pseidon.core.utils :refer [fixdelay]]
    )
    (:use pseidon.core.registry)
  )


;(defrecord DataSource [name start stop list-files reader])

(def ^:dynamic url (get-conf2 "testftp-url" "sftp://localhost"))
(def ^:dynamic uid (get-conf2 "testftp-uid" "ftptest"))
(def ^:dynamic pwd (get-conf2 "testftp-pwd" "tech!sw78"))

(def ^:dynamic conn (ftp-connect url uid pwd))
(def ^:dynamic name-space "testftp")
; (defn get-files [conn dir pred-filter]

(defn ^:dynamic delete-donefiles [] 
  (comment 
  (fixdelay 600000 
            (doseq [file files]
              (delete-done-file conn name-space file)
              )
            )
)
  )

;register the testftp datasource
(register (->DataSource "testftp" 
                         #(load-recover-messages! name-space) 
                         #(.close (:fs conn) )
                         #(get-files conn name-space "/" 
                              (fn [file] (.endsWith file ".txt" ) ))
                         #(get-line-seq! conn name-space % 50 )  
                         ))
             


 


