(ns pseidon.test.core.metrics-tests
  (:require [ pseidon.core.metrics :refer [coerce-health-result run-health-checks register-health-check] ])
  (:import [com.codahale.metrics.health HealthCheck HealthCheck$Result HealthCheckRegistry])
  (:use midje.sweet)
  )

(facts "Test metrics library"
       (fact "Test health checks"
             
             (coerce-health-result  {:healthy true :msg "hi"} ) => (HealthCheck$Result/healthy "hi")
             
             (register-health-check "a" (fn [] {:healthy true :msg "hi" }))
             (-> (run-health-checks) (get "a")) => (HealthCheck$Result/healthy "hi")
             
             (register-health-check "b" (fn [] {:healthy false :msg "hi" }))
             (-> (run-health-checks) (get "b")) => (HealthCheck$Result/unhealthy "hi")
             
             (register-health-check "c" (fn [] {:msg "hi" }))
             (-> (run-health-checks) (get "c")) => (HealthCheck$Result/unhealthy "hi")
             
             (let [e (Exception. "error")]
               (register-health-check "d" (fn [] {:error e}))
               (-> (run-health-checks) (get "d")) => (HealthCheck$Result/unhealthy e))
             
             ))
