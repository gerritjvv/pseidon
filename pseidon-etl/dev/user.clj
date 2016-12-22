(ns user
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.pprint :refer (pprint)]
            [clojure.repl :refer :all]
            [clojure.test :as test]
            [com.stuartsierra.component :as component]
            [dev-system]
            [clojure.tools.logging :refer [info error]]
            [clojure.tools.namespace.repl :refer (refresh refresh-all)]))



(def system nil)

(defn start
  "Starts the current development system."
  []
  (alter-var-root #'system dev-system/start))

(defn stop
  "Shuts down and destroys the current development system."
  []
  (alter-var-root #'system
                  (fn [s] (when s (dev-system/stop s)))))

(defn go
  "Initializes the current development system and starts it running."
  []
  (start))

(defn reset []
  (stop)
  (refresh :after 'user/go))
