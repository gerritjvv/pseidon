(ns pseidon.core.watchdog
  (:use clojure.tools.logging
        pseidon.core.message)
  )

(defn handle-critical-error [ t & msg]
  (error t (clojure.string/join msg))
  ;any shutdown logic should be handled by JVM shutdown hooks
  (info "calling system exit !!!!!!!!!!!!!!!!: ")
  (java.lang.System/exit -1)
  )


;message error recovery etc should be tried here
(defn handle-msg-error [message  t & msg]
    ;write to file here
    (handle-critical-error t msg)
  )


(defn handle-agent-error [^Throwable e & msg]
  (handle-critical-error e msg)
  )

(defn agent-error-handler [^clojure.lang.Agent agent ^Throwable excp]
  (handle-agent-error excp "Critical error")
  ) 


(defn handle-normal-error [^Throwable e & msg]
  (error e (clojure.string/join msg)))


(defn watch-agent-error [f & arg1]
  (fn [& args] (try (apply f (concat arg1 args)) (catch Throwable t (handle-critical-error t "error")))) 
  )

(defn watch-normal-error [f]
  (fn [& args] (try (apply f args) (catch Throwable t (handle-normal-error t "error")))) 
  )


(defn watch-critical-error [f & arg1]
  (fn [& args] (try (apply f (concat arg1 args)) (catch Throwable t (handle-critical-error t "error")))) 
  )


(defn watch-msg-error [f]
  (fn [msg] (try (f msg) (catch Throwable t (handle-msg-error msg t "error")))) 
  )