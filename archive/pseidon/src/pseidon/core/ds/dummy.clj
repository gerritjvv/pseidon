(ns pseidon.core.ds.dummy)

(use '[pseidon.core.registry :as r])

(defn get-files [] 
  (let [files ["/opt/pseidon/plugins/datasources/myds.clj" "resources/plugins/datasources/myds.clj"] ]
    (filter #(.exists (java.io.File. %)) files)
  ))
  
(defn dummy-ds [name] 
  (r/->DataSource name (fn [] (prn "Starting " name)) (fn [] (prn "closing " name)) 
                  get-files
             (fn [file]  (line-seq (clojure.java.io/reader file )))
             )
   )

  

