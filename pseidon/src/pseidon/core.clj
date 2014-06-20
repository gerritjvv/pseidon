(ns pseidon.core
  (:gen-class)
  (:use [clojure.tools.cli :only [cli]]
        [pseidon.core.conf :only [load-config! get-conf2]]
        [pseidon.core.app :only [start-app stop-app refresh-plugins]]
        [clojure.tools.nrepl.server :only [start-server stop-server]]
        [clojure.java.shell :only [sh]])
  (:import [org.apache.commons.exec CommandLine DefaultExecuteResultHandler DefaultExecutor ExecuteWatchdog ProcessDestroyer ShutdownHookProcessDestroyer])
  (:require [reply.main :refer [launch-nrepl]]
            [clojure.tools.logging :refer [info error]]
            [pseidon.core.watchdog :refer [watch-critical-error]])

  )

(require '[clojure.tools.nrepl :as repl])

(declare send-shutdown)

(def shutdown-flag (atom false))

(defn run-process []
  (let [^ProcessDestroyer destroyer (ShutdownHookProcessDestroyer.)
        ^CommandLine cmd (CommandLine. "/opt/pseidon/bin/pseidon-process.sh")
        ^ExecuteWatchdog watchdog (ExecuteWatchdog. ExecuteWatchdog/INFINITE_TIMEOUT)
        ^DefaultExecuteResultHandler handler (DefaultExecuteResultHandler.)
        ^DefaultExecutor exec (doto (DefaultExecutor.) (.setExitValue 1) (.setWatchdog watchdog) (.setProcessDestroyer destroyer)
                                                       (.setWorkingDirectory (clojure.java.io/file "/opt/pseidon")))]
    (prn "Starting managed pseidon process " "/opt/pseidon/bin/pseidon-process.sh")
    (.execute exec cmd handler)
    (prn "see /opt/pseidon/log/serverlog.log")
    [handler exec]))

(defn restart-process? [^DefaultExecuteResultHandler handler]
  (.hasResult handler))


(defn is-shutdown? []
  @shutdown-flag)

(defn start-managed []
  (loop [[handler ^DefaultExecutor exec] (run-process)]
    (if (not (is-shutdown?))
      (if (restart-process? handler)
        (do
          (println "Managed process died. Restarting in 10 seconds.")
          (Thread/sleep 10000)
          (recur (run-process)))
        (do
          (Thread/sleep 1000)
          (recur [handler exec])))
      (do
        (println "Exiting process")
        (try (do (prn "send shutdown" (send-shutdown (get-conf2 :repl-port 7111)) "sent shutdown")) (catch Exception e (.printStackTrace e)))
        (.destroyProcess (.getWatchdog exec))
        (println "Destroyed managed process")
        (System/exit 0)
        ))))

(def repl-server (ref nil))

(defn cmd [args]

  (cli args
       ["-c" "--config" "Configuration directory that must contain a pseidon.edn file" :parse-fn #(java.io.File. %) :default (java.io.File. "/opt/pseidon/conf")]
       ["-stop" "--stop" "Shutdown the application" :flag true]
       ["-r" "--refresh-plugins" "Refresh all edited plugins" :flag true]
       ["-get-cp" "--get-cp" "Returns classpath string from the pseidon.edn classpath setting"]
       ["-repl" "--repl" "Opens a repl to the current running app" :flag true]
       ["-p" "--port" "Used with repl to specify the port to attach to"]
       ["-m" "--managed" "Used to start pseidon up with a manager that will restart if any process failure" :flag true]

       ))

(defn set-java-library-path [path]
  (when path
    (do
      (System/setProperty "java.library.path" path)
      (doto (.getDeclaredField ClassLoader "sys_paths")
        (.setAccessible true)
        (.set nil nil)))
    path))

(defn parse-cp-item [item]
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
  (if-let [conf (:config opts)]
    (if-not (cond (.exists conf) (complement (.isFile conf)))
      (do (println "The configuration directory " conf " is either not a directory or does not exist"))
      opts
      ))

  )

(defn check-opts [[opts _ usage]]
  (if-let [m (check-opts2 opts)] m (do (println usage) false))
  )


(defn start-repl [repl-port]
  (info "start repl on port " repl-port)
  (dosync (ref-set repl-server (start-server :port repl-port))))

(defn shutdown []
  (info "!!!!!!!!!>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> clean shutdown")
  (prn "!!!!!!!!!>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> clean shutdown")
  (try
    (stop-app)
    (catch Exception e (do (prn e))))

  (swap! shutdown-flag (fn [_] true)))

(defn app-refresh-plugins []
  (refresh-plugins)
  )


(defn send-refresh []
  "we need to connect to the repl"
  (with-open [conn (repl/connect :port (get-conf2 :repl-port 7111))]
    (-> (repl/client conn 1000) (repl/message {:op :eval :code "(do (pseidon.core/app-refresh-plugins) )"})
        (repl/response-values)
        )))

(defn send-shutdown [port]
  "we need to connect to the repl"
  (with-open [conn (repl/connect :port port)]
    (-> (repl/client conn 1000) (repl/message {:op :eval :code "(do (pseidon.core/shutdown) )"})
        (repl/response-values)
        )))
(defn run-repl [port]
  (prn "Connecting to localhost:" port)
  (launch-nrepl {:attach (str "localhost:" port)}))

(defn load-config [opts]
  (load-config! (clojure.string/join "/" [(:config opts) "pseidon.edn"])))

(defn -main [& args]
  (try (if-let [opts (check-opts (cmd args))]
         (do

           (info "Opts " opts " is repl " (:repl opts) " is stop " (:stop opts))
           (cond
             (:repl opts) (do (load-config opts) (run-repl (:port opts)))
             (:refresh-plugins opts) (send-refresh)
             (:stop opts) (do (load-config opts) (send-shutdown (get-conf2 :managed-repl-port 7110)) (System/exit 0))
             (:managed opts) (do

                               (load-config opts)
                               (start-repl (get-conf2 :managed-repl-port 7110))
                               (try (start-managed) (catch Exception e (do (.printStackTrace e)
                                                                           (System/exit -1)))))
             (contains? opts :get-cp) (.println System/out (get-classpath))
             :else
             (do
               (load-config opts)
               (info "Java Library Path: " (set-java-library-path (get-conf2 :java-library-path "/opt/hadoopgpl/native/Linux-amd64-64/")))
               (start-repl (get-conf2 :repl-port 7111))
               (start-app)
               ))))

       (catch Exception e (do (.printStackTrace e) (error e)))))

