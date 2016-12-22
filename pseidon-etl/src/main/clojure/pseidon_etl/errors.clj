(ns pseidon-etl.errors
    (:require
      [clojure.tools.logging :refer [error]]
      [pseidon-etl.topic-service :as topic-service]))


;namespace for logic when etl errors happen


(defn- safe-inc [a]
       (if a (inc ^long a) 1))

(defn disable-topic? [topic-errors-ref ^long error-threshold topic]
       (dosync
         (alter topic-errors-ref update topic safe-inc))
       (if (> (get @topic-errors-ref topic) error-threshold)
         (do
           ;we reset so that error counting can start anew, and also the db won't get flooded with inserts
           (dosync (alter topic-errors-ref assoc topic 0))
           true)
         false))

(defn disable-topic [db topic]
      {:pre [db]}
      (topic-service/disable-topic! db topic))

(defn handle-errors->fn [db topic-errors-ref error-threshold f]
       (fn [state msg]
           (try
             (f state msg)
             (catch InterruptedException _ nil)
             (catch Exception e
               (do
                 (error e e)
                 (if (disable-topic? topic-errors-ref error-threshold (:topic msg))
                   (disable-topic db (:topic msg))))))
           state))
