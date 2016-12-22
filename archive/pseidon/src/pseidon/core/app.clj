(ns pseidon.core.app
    (:require [clojure.tools.logging :refer [error info]]
              [pseidon.core.queue :as q]
              [pseidon.core.registry :as r]
              [pseidon.core.conf :as c]
              [pseidon.core.datastore :as ds]
              [pseidon.core.tracking :as tracking]
              [pseidon.core.watchdog :as e]
              [pseidon.view.server :as view]
              [pseidon.core.message :as msg]
              [pseidon.core.error-reporting :as error-reporting]))

(use '[clojure.tools.namespace.repl :only (refresh set-refresh-dirs)])


;will reload all of the plugins
(defn refresh-plugins []
  "Reads the plugin-dirs list and if no such files are found uses the default test locations"
   (info (c/get-conf2 :plugin-dirs "NO PLUGIN CONFIG"))
   (apply set-refresh-dirs (c/get-conf2 :plugin-dirs []))
   (binding [*ns* *ns*  clojure.core/*e nil]
	   (refresh)
	   (if clojure.core/*e 
         (e/handle-critical-error clojure.core/*e (str clojure.core/*e) ))
   ))

(defn safe-call [ v ]
  "Calls each function in the sequence and catches any exceptions, all functions are garaunteed to be called"
  (pcalls (map #(try (%) (catch Exception e (error e e))) v)))

(defn stop-app []
   (info "Stopping")
       
     (q/shutdown-threads)
	  
	   (r/stop-all)
	    
     (shutdown-agents)
	   (ds/shutdown)
	   (tracking/shutdown)
	   (await-for 1000)
     (error-reporting/stop)
     (info "14<<<< Stopped App >>>>")

)

(defn start-app [& { :keys [start-plugins] :or {start-plugins true}}]

  (tracking/tracking-start)
  (refresh-plugins)
  (Thread/sleep 1000)
  (if start-plugins
        (r/start-all))
  (Thread/sleep 1000)
  (info "Started")
  (-> (Runtime/getRuntime) (.addShutdownHook  (Thread. (reify Runnable (run [this] (do  
                                                                                     (info "<<< Shutdown from System.exit or kill !!!!  >>>> ")
                                                                                     (try 
                                                                                       (stop-app)
                                                                                       (catch Exception e (error e e)) 
                                                                                         )))))))
                                                                                     
                                                                                     
  (view/start)
  (error-reporting/start)
  (info "View started")
  )



