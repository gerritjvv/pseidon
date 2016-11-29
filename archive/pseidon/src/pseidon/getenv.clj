(ns pseidon.getenv
  (:gen-class)
  (:use [clojure.tools.cli :only [cli]] 
        [pseidon.core.conf :only [load-config! get-conf2 conf] ]
        ) 
  )

 (require '[clojure.tools.nrepl :as repl])
 
(def repl-server (ref nil))

(defn cmd [args] 
  
  (cli args
    ["-c" "--config" "Configuration directory that must contain a pseidon.edn file" :parse-fn (fn[^String name] (java.io.File. name)) :default (java.io.File. "/opt/pseidon/conf")]
    ["-get-cp" "--get-cp" "Returns classpath string from the pseidon.edn classpath setting"]
  ))



(defn parse-cp-item [^String item]
  (let [file (clojure.java.io/file item)]
	  (if (and  
	         (.exists file)
           (.isDirectory file)
	         (not (.endsWith item "*"))
	       )
	       (str item "/*:" item)
	       item    
	    )))

(defn get-classpath [] 
   (clojure.string/join ":" (map parse-cp-item (get-conf2 :classpath ["/opt/pseidon/lib/*"]))))

(defn check-opts2 [opts]
 "Check that all the options required are defined, else print the usage"
  (if-let [^java.io.File conf (:config opts)]
    (if-not (cond (.exists conf) (complement (.isFile conf)) )
     (do (println "The configuration directory " conf " is either not a directory or does not exist"))  
     opts
    ))
  
  )

(defn check-opts [[opts _ usage] ]
  (if-let [m (check-opts2 opts)] m (do (println usage) false)) 
  )

(defn -main [& args]
     (if-let [opts (check-opts (cmd args) ) ]
       (do
          (load-config! (clojure.string/join "/" [ (:config opts) "pseidon.edn"] ))
  	      (cond (contains? opts :get-cp) (.println System/out (get-classpath))
                :else (let [[opts _ usage] (cmd args)] (println opts "\n" usage)) 
                ))))

		    
