(ns pseidon-etl.mon
  (:import [clojure.lang IFn IRef Atom]
           [java.util Map List])
  (:require
    [pjson.core :as pjson]
    [org.httpkit.server :refer :all]
    [compojure.handler :refer [site]]
    [clojure.tools.logging :refer [info]]
    [compojure.core :refer [defroutes GET POST DELETE ANY context]]
    [com.stuartsierra.component :as component]))

;; Starts a component that will show a http page in json format on http://server:8080/metrics
;; USAGE
;;;  call (register service name v)
;;;  v can be a function, Map, List, Ref and or Atom
;;;  If you need to show a value that will change i.e is not constant register a function
;;;
;;;  e.g (register service "mytest" (fn [] {:a (System/currentTimeMillis)}))
;;;

(defprotocol IStatsable
  ;can be a Function, Map, Reference or Atomi
  (to-stats [this]))


(defprotocol IMonitorService
  (register [this name statsable]))



(defn- call-to-stats [statsables]
  (zipmap (keys statsables) (map to-stats (vals statsables))))

(defn- show-stats [stats-map-ref]
  (fn [req]
    (try
      (-> stats-map-ref deref call-to-stats pjson/write-str)
      (catch Exception e (do (.printStackTrace e) nil)))))



(defrecord MonitorService [conf statsables]
          IMonitorService
          (register [this name statsable]
            (let [v (:statsables this)]
              (dosync
                (commute v assoc name statsable)))
            this)

            component/Lifecycle
            (start [component]
                   (info "START Monitor service")
                   (try
                     (if (:http-server component)
                       component
                       (let [stats-map-ref (ref {})]

                            (defroutes all-routes
                                       (GET "/metrics" [] (show-stats stats-map-ref)))

                            (assoc component :statsables
                                   stats-map-ref
                                   :http-server
                                             ;;we downsize the monitoring resources used to avoid taking resources away from etl and also
                                             ;;avoid monitoring tools doing uknowningly DoS attacks
                                             ;;for more options see http://www.http-kit.org/server.html
                                   (run-server (site #'all-routes) {:port (get conf :monitor-port 8282) :thread 1 :queue-size 100}))))
                     (finally
                       (info "Complete Monitor Service start"))))

            (stop [component]
              (if-let [server (:http-server component)]
                (do (server)
                    (:dissoc :http-server component))
                component)))

(extend-protocol IStatsable
  IFn
  (to-stats [this] (this))
  IRef
  (to-stats [this] (deref this))
  Atom
  (to-stats [this] (deref this))
  Map
  (to-stats [this] this)
  List
  (to-stats [this] this))


(defn create-monitor-service [conf]
  (->MonitorService conf nil))

