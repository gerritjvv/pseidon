(ns plugins.datasources.myds)

(use '[pseidon.core.registry :as r])
(use '[pseidon.core.ds.dummy :as d])


(r/register (d/dummy-ds "ds-test"))

(println "Reloaded")