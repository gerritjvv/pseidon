(ns
  ^{:doc "Abstracts away the App status rather than passing un-identified functions"}
  pseidon-hdfs.lifecycle)


(defprotocol IAppStatus
  (-shutdown [this])
  (-shutdown? [this]))


(defn app-status
  "Return a new instance of AppStatus init at stopped=false"
  []
  (let [stopped (atom false)]
    (reify IAppStatus
           (-shutdown? [_] @stopped)
           (-shutdown [_] (swap! stopped (fn [& _] true))))))
