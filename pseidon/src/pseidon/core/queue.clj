(ns pseidon.core.queue
  (:require [clojure.tools.logging :refer [info error]]
            [thread-exec.core :refer [get-layout default-pool-manager submit shutdown]]
            [pseidon.core.metrics :refer [add-histogram add-gauge update-histogram add-timer measure-time add-meter update-meter]]
            [pseidon.core.registry :as r]
            [pseidon.core.watchdog :as watchdog])  
  (:use pseidon.core.conf)
  (:import 
          [pseidon.core.registry Processor]
          [java.util.concurrent TimeoutException ArrayBlockingQueue]))

(def queue-publish-meter (add-meter "pseidon.core.queue.publish-meter"))

;default-pool-manager [threshold max-groups start-group pool-size]
(defonce pool-manager (default-pool-manager 10 3 [0 8] (.availableProcessors (Runtime/getRuntime))))

;add-gauge [^String name ^clojure.lang.IFn f]
(add-gauge "pseidon.core.queue.dynamic-threads"
  (fn []
    (get-layout pool-manager :verbose)))

(add-gauge "pseidon.core.queue.size"
 (fn []  
   ))

(defn shutdown-threads []
  (shutdown pool-manager 2000))


(defn publish [topic msg]
  "Publish the message, it will be places on a pool of threads and run in the background"
   (update-meter queue-publish-meter)
   (if-let [ds (r/reg-get-wait topic 1000)]
     (if-let [exec (:exec ds)]
		  (submit pool-manager (:topic msg) 
		    (fn [] 
		      (try 
		        (exec msg)
		        (catch Exception e (watchdog/handle-critical-error e "Error while processing publihs")))))
      (throw (RuntimeException. (str "You can only publish to maps with an exec key specified, the topic " topic " returned " ds))))
    (throw (RuntimeException. (str "No data source found for topic " topic)))))
  
 
  