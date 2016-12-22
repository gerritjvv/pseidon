(ns pseidon-hdfs.pseidon-hdfs
  (:require [pseidon-hdfs.app :as app]
            [pseidon-hdfs.conf :refer [load-conf]]
            [clojure.tools.logging :refer [error info]]
            [clj-logging-config.log4j :refer [set-logger!]]
            [clojure.tools.nrepl :as repl]
            [pseidon-hdfs.lifecycle :as app-life])
  (:gen-class))

;; A crude approximation of your application's state.
(def state (atom {}))
(def app-status (app-life/app-status))

(defn init [args]
  (set-logger!)

  (if-let [conf-file (first args)]
    (let [conf (load-conf conf-file)]
      (alter-var-root #'pseidon-hdfs.conf/*default-conf* (fn [_] conf))
      (app/start! conf app-status))))

(defn start [])


(defn shutdown?
  "Returns true if stop was already called and the system is either in shutdown and has already shutdown"
  []
  (app-life/-shutdown? app-status))

(defn shutdown []
  (app-life/-shutdown app-status))

(defn stop []
  (when-not (shutdown?)
    (shutdown)
    (info "Shutting down")
    (app/stop!)
    (info "Stopped")
    (System/exit 0)))


(defn add-shutdown-hook []
  (let [^Runnable f (fn [] (try (stop) (catch Exception e (error e e))))]
    (-> (Runtime/getRuntime) (.addShutdownHook  (Thread. f)))))

(defn- send-stop-signal [conf-file]
  (let [repl-port (get (load-conf conf-file) :repl-port 7113)]
    (with-open [conn (repl/connect :port repl-port)]
      (->
        conn
        (repl/client 60000)
        (repl/message {:op "eval" :code "(pseidon-hdfs.pseidon-hdfs/stop) "})))))

(defn -main
  "   stop config-file
   or config-file ;; is start"
  [& args]
  (let [cmd (first args)]
    (cond
      (= cmd "stop")
      (send-stop-signal (second args))
      :else
      (do
        (init args)
        (add-shutdown-hook)
        (start)
        (while (not (Thread/interrupted))
          (Thread/sleep 1000))))))
