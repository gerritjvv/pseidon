(ns pseidon.core.error-reporting
    (:require
              [pseidon.core.conf :as conf]
              [robert.hooke :as hooke]
              [clojure.tools.logging :refer [info]]
              [riemann.client :as riemann])
    (:import  [java.util Arrays]))


(defonce host-name (-> (java.net.InetAddress/getLocalHost) .getHostName))

(def riemann-client-ref (ref nil))

(defn- extract-stack-trace [throwable]
       (if (and throwable (instance? Throwable throwable))
         (Arrays/toString (.getStackTrace ^Throwable throwable))
         nil))

(defn- get-log-tag [level]
       (condp = level
              :error "exception"
              :warn "warn"
              "other"))

(defn- include-throwable-msg [m throwable]
       (if throwable
         (assoc m
                :throwable (str throwable)
                :strack-trace (extract-stack-trace throwable))
         m))

(defn- create-riemann-msg [level throwable message]
       (include-throwable-msg
         {:host host-name
          :service "pseidon"
          :state (str level)
          :time (System/currentTimeMillis)
          :metric 1
          :description (str message)
          :tags  ["log" (get-log-tag level)]
          }
         throwable))

(defn- send-log-to-riemann [_ level throwable message]
       (try
         (if @riemann-client-ref
           (riemann/send-event @riemann-client-ref (create-riemann-msg level throwable message) false))
         (catch Exception e (do
                              (prn e)
                              (.printStackTrace e)))))

(def log-send-agent (agent nil))

(defn send-event [{:keys [state metric description tags] :or {tags ["log"] metric 1}}]
      (try
        (if @riemann-client-ref
          (riemann/send-event @riemann-client-ref {:host host-name
                                                   :service "pseidon"
                                                   :state (str state)
                                                   :time (System/currentTimeMillis)
                                                   :metric metric
                                                   :description (str description)
                                                   :tags tags
                                                   } false))
        (catch Exception e (do (prn e) (.printStackTrace e)))))

(defn- send-logging-event [f & args]
       (let [[_ level throwable message] args]
            (if (not= level :info)
              (send-log-to-riemann nil level throwable message))
            (apply f args)))


(defn- setup-log-intercepts []
       (hooke/add-hook #'clojure.tools.logging/log* send-logging-event))

(defn start []
       (let [riemann-conf (conf/get-conf2 :riemann-conf {:host "pipe4"})
             _ (do (info "Creating riemann conn with " riemann-conf))
              riemann-client (riemann/tcp-client riemann-conf)]
            (dosync
              (ref-set riemann-client-ref riemann-client)))
       (setup-log-intercepts))

(defn stop []
      (when-let [conn @riemann-client-ref]
                (riemann/close-client conn)
                (dosync (ref-set riemann-client-ref nil))))