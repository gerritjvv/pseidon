(ns pseidon.view.server
  (:require 
    [org.httpkit.server :refer [run-server]]
    [pseidon.core.conf :refer [get-conf2]]
    [compojure.route :refer [files not-found]]
    [compojure.handler :refer [site]]
    [ring.util.response :as resp]
    
    [compojure.core :refer [defroutes GET context]]
    [pseidon.view.datastore :refer [datastore-list datastore-create datastore-delete]]
    [pseidon.view.registry :refer [registry-index]]
    [pseidon.view.tracking :refer [tracking-index]]
    [pseidon.view.metrics  :refer [metrics-index]]
    [pseidon.view.home :refer [home-index]]
    )
    (:use clojure.tools.logging)
  )


(defroutes all-routes
  (GET "/" [] (fn [req] (home-index req)))
  (GET "/registry" [] registry-index)
  (GET "/tracking" [] (fn [req] (tracking-index req) ))
  (GET "/metrics"  [] (fn [req] (metrics-index req)))
  (GET "/datastore" [] datastore-list)
  (GET "/datastore/delete" [] datastore-delete)
  (GET "/datastore/create" [] (fn [req] (datastore-create req)
                                (resp/redirect (str "/datastore?path=" (-> req :query-params (get "path" "/pseidon")) )  )))
  
  )

(defn start []
  (let [ port (get-conf2 "view.port" 8282)
        ]
    (run-server (site #'all-routes) {:port port}
    )))
