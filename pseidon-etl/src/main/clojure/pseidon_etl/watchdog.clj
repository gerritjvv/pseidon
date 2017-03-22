(ns pseidon-etl.watchdog
  (:gen-class)
  (:require [clojure.tools.logging :refer [info error]])
  (:import [org.apache.commons.exec CommandLine DefaultExecuteResultHandler DefaultExecutor ExecuteWatchdog ProcessDestroyer ShutdownHookProcessDestroyer]))


(defn cmd [& args]
  (doto (CommandLine. (str "test")) (.addArguments (into-array String args))))

(defn run-process [cmd-bash args]
  (let [^ProcessDestroyer destroyer (ShutdownHookProcessDestroyer.)
        ^CommandLine cmd (doto (CommandLine. (str cmd-bash)) (.addArguments (into-array String (mapv str args))))
        ^ExecuteWatchdog watchdog (ExecuteWatchdog. ExecuteWatchdog/INFINITE_TIMEOUT)
        ^DefaultExecuteResultHandler handler (DefaultExecuteResultHandler.)
        ^DefaultExecutor exec (doto (DefaultExecutor.) (.setExitValue 1) (.setWatchdog watchdog) (.setProcessDestroyer destroyer)
                                                       (.setWorkingDirectory (clojure.java.io/file "/opt/pseidon-etl")))]
    (info "Starting managed pseidon process " cmd-bash)
    (.execute exec cmd handler)
    (prn "see /opt/pseidon-etl/log/serverlog.log")
    [handler exec]))

(defn restart-process? [^DefaultExecuteResultHandler handler]
  (.hasResult handler))


(def shutdown? (atom false))

(defn start-managed [cmd-bash args]
  (loop [[handler ^DefaultExecutor exec] (run-process cmd-bash args)]
    (if-not (or @shutdown? (.isInterrupted (Thread/currentThread)))
      (if (restart-process? handler)
        (do
          (error "Managed process died. Restarting in 10 seconds.")
          (Thread/sleep 10000)
          (recur (run-process cmd-bash args)))
        (do
          (Thread/sleep 1000)
          (recur [handler exec])))
      (do
        ;we should not destroy the process here, it is the work of the init script
        ;to call the process shutdown, and the process should shutdown orderly without needing to be
        ;killed, killing is only for desperate situations where data loss is accepted.
        ;(.destroyProcess (.getWatchdog exec))
        (System/exit 0)
        ))))


(defn add-shutdown-hook []
  (let [^Runnable f (fn [] (reset! shutdown? true))]
    (-> (Runtime/getRuntime) (.addShutdownHook  (Thread. f)))))

(defn -main [& args]
  (add-shutdown-hook)
  (start-managed "/opt/pseidon-etl/bin/process.sh" args))