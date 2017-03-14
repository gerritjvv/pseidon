(ns
  ^{:doc

    "
  cmd <send|consume>

  send:  send test json data to kafka
         send <broker-list> <threads> <num-of-records-per-thread>

  consume: consume data from topics in kafka on a unique group and from the beginning
           used for performance and stability testing
           will run till ctrl+c or kill <pid>
           consume <broker-list> redis-host <topics>

  work-queue: display work units in the work queue
           host from limit [password <redis-password>]

  stop:
        stop the long running pseidon-etl application

  other: any other command will start the pseidon-etl application

  "}
  pseidon-etl.pseidon-etl
  (:require [pseidon-etl.app :as app]
            [pseidon-etl.conf :refer [load-conf]]
            [pseidon-etl.util :as util]
            [pseidon-etl.formats :as formats]
            [clojure.tools.logging :refer [error info]]
            [clj-logging-config.log4j :refer [set-logger!]]
            [clojure.tools.nrepl :as repl]
            [pseidon-etl.apputils.cmd-work-queue :as cmd-work-queue]
            [pseidon-etl.apputils.cmd-produce :as cmd-produce]
            [pseidon-etl.apputils.cmd-consume :as cmd-consume])
  (:gen-class)
  (:import (java.lang Thread$UncaughtExceptionHandler)))


(defn init [args]
  (set-logger!)

  (when-not (first args)
    (throw (RuntimeException. (str "Please specify a edn config file"))))

  (let [conf-file (first args)
        conf (load-conf conf-file)]
    (alter-var-root #'pseidon-etl.conf/*default-conf* (fn [_] conf))

    (app/start! conf)

    (info "App Started")))

(defn start [])

(defonce stopped (atom false))

(defn stop []
  (when-not @stopped
            (swap! stopped (fn [& args] true))
            (info "Shutting down")
            (app/stop!)
            (info "Stopped")))


(defn get-prop [conf-file k defval]
  (get (if (map? conf-file) conf-file (load-conf conf-file)) k defval))

(defn send-stop-signal [conf-file]
  (let [repl-port (get-prop conf-file :repl-port 7112)
        repl-host (get-prop conf-file :repl-host "localhost")]
    (with-open [conn (repl/connect :host repl-host :port repl-port)]
      (->
        conn
        (repl/client 60000)
        (repl/message {:op "eval" :code "(do (pseidon-etl.pseidon-etl/stop) (System/exit (int 0)))"})
        doall
        clojure.pprint/pprint))))

(defn add-shutdown-hook []
  (let [^Runnable f (fn []
                        (try
                            (stop)
                            (catch Exception e (error e e))))]
    (-> (Runtime/getRuntime) (.addShutdownHook  (Thread. f)))))

(defn add-uncaught-exceptionhandler! []
  (Thread/setDefaultUncaughtExceptionHandler (reify Thread$UncaughtExceptionHandler
                                               (uncaughtException [this t e]
                                                 (util/fatal-error e)))))

(defn consume [brokers redis-host topic]
  (cmd-consume/consume-data topic brokers redis-host))

(defn send-msgs [brokers topic threads count-per-thread & conf]
  (cmd-produce/send-data threads count-per-thread topic brokers (apply array-map conf)))

(defn work-queue [& args]
  (cmd-work-queue/read-redis-queue args))

(defn -main
  "   stop config-file
    or config-file ;; is start"
  [& args]

  (let [cmd (first args)]
    (cond
      (= cmd "send")
      (do (apply send-msgs (rest args))
          (System/exit 0))

      (= cmd "consume")
      (do
        (apply consume (rest args))
        (System/exit 0))

      (= cmd "work-queue")
      (do
        (apply work-queue (rest args))
        (System/exit 0))

      (= cmd "stop")
      (try
        (send-stop-signal (second args))
        ;;ignore exceptions from the nrepl.
        (catch Exception _ nil))
      :else
      (do
        (init args)

        (add-shutdown-hook)
        (add-uncaught-exceptionhandler!)

        (start)

        (while (not (Thread/interrupted))
          (Thread/sleep 1000))
        (System/exit 0)))))

