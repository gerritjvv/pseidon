(ns pseidon-hdfs.nrepl-service
  (:require
    [com.stuartsierra.component :as component]
    [clojure.tools.nrepl.server :refer [start-server stop-server]]))



(defrecord NReplService [conf]
  component/Lifecycle

  (start [component]
    (if (:server component)
      component
      (assoc component :server (start-server :port (get conf :repl-port 7113)))))
  (stop [component]
    (if (:server component)
      (stop-server (:server component)))
    (dissoc component :server)))

(defn create-nrepl-service [conf]
  (->NReplService conf))
