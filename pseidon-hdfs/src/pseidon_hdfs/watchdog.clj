(ns pseidon-hdfs.watchdog
  (:gen-class)
  (:require [clojure.tools.logging :refer [info error]])
  (:import [org.apache.commons.exec CommandLine DefaultExecuteResultHandler DefaultExecutor ExecuteWatchdog ProcessDestroyer ShutdownHookProcessDestroyer]
           (org.apache.commons.lang3 StringUtils)))


(def pseidon-home (let [env (System/getenv "PSEIDON_HOME")]
                    (if (StringUtils/isNotEmpty (str env))
                      (str env)
                      "/opt/pseidon-hdfs/")))

(defn run-process [cmd-bash args]
  (let [
        ^ProcessDestroyer destroyer (ShutdownHookProcessDestroyer.)
        ^CommandLine cmd (doto (CommandLine. (str cmd-bash)) (.addArguments (into-array args)))
        ^ExecuteWatchdog watchdog (ExecuteWatchdog. ExecuteWatchdog/INFINITE_TIMEOUT)
        ^DefaultExecuteResultHandler handler (DefaultExecuteResultHandler.)
        ^DefaultExecutor exec (doto (DefaultExecutor.) (.setExitValue 1) (.setWatchdog watchdog) (.setProcessDestroyer destroyer)
                                                       (.setWorkingDirectory (clojure.java.io/file pseidon-home)))]
    (info "Starting managed pseidon process " cmd-bash)
    (.execute exec cmd handler)
    (prn (str "see " pseidon-home "/log/serverlog.log"))
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
        (println "Exiting process")
        (.destroyProcess (.getWatchdog exec))
        (println "Destroyed managed process")
        (System/exit 0)
        ))))


(defn add-shutdown-hook []
  (let [^Runnable f (fn [] (reset! shutdown? true))]
    (-> (Runtime/getRuntime) (.addShutdownHook  (Thread. f)))))

(defn -main [& args]
  (add-shutdown-hook)
  (start-managed (str pseidon-home "/bin/process.sh") args))
