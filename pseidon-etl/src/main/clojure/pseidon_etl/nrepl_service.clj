(ns pseidon-etl.nrepl-service
  (:require
    [com.stuartsierra.component :as component]
    [clojure.tools.nrepl.server :refer [start-server stop-server]]))


(defrecord NReplService [conf]
  component/Lifecycle

  (start [component]
    (if (:server component)
      component
      (assoc component :server (start-server :port (get conf :repl-port 7112)))))
  (stop [component]
    (if (:server component)
      (stop-server (:server component)))
    (dissoc component :server)))

;;;; nrepl service that does nothing
(defrecord NOOPReplService []
  component/Lifecycle

  (start [component])
  (stop [component]))

(defn create-nrepl-service [conf]
  (->NReplService conf))

(defn create-noop-nrepl-service []
  (->NOOPReplService))