(ns pseidon-etl.convert
  (:require
    [pjson.core :as pjson]
    [pseidon-etl.conf :refer [get-conf]]))


(defn ^"[B" json->bts [msg]
  (.getBytes ^String (pjson/write-str msg)))

(defn msg->json [msg]
  (pjson/read-str ^"[B" msg))
