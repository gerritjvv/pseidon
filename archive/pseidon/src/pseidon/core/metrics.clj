(ns pseidon.core.metrics
  (:import [com.codahale.metrics MetricRegistry Gauge Counter Meter MetricFilter Metric
            Histogram Timer Timer$Context JmxReporter CsvReporter ConsoleReporter]
           [com.codahale.metrics.health HealthCheck HealthCheck$Result HealthCheckRegistry]
           [java.util.concurrent TimeUnit]
           [java.io File]
           [java.util Locale]
  ))

(defonce registry (MetricRegistry.))
(defonce health-registry (HealthCheckRegistry.))

(defonce jmx-reporter (-> registry JmxReporter/forRegistry .build))

(defn start-csv-reporter [^File file ^Integer frequency]
  "Start a CsvReporter. The file must be a directory"
  (-> (CsvReporter/forRegistry registry) (.formatFor Locale/US) 
      (.convertRatesTo TimeUnit/SECONDS)
      (.convertDurationsTo TimeUnit/MILLISECONDS)
      (.build file)
      (.start frequency TimeUnit/SECONDS)))

(defn start-console-reporter [^Integer frequency]
  "Start a ConsoleReporter"
  (-> (ConsoleReporter/forRegistry registry)
      (.convertRatesTo TimeUnit/SECONDS)
      (.convertDurationsTo TimeUnit/MILLISECONDS)
      (.build)
      (.start frequency TimeUnit/SECONDS)))

(defn coerce-health-result [o]
  (if (instance? HealthCheck$Result o) 
    o
    (let [{:keys [^boolean healthy ^String msg ^Throwable error]} o]
      (cond error (HealthCheck$Result/unhealthy error)
        healthy (HealthCheck$Result/healthy msg)
        :else (HealthCheck$Result/unhealthy msg)))))

(defn run-health-checks []
  "Returns a map key=check-name value=HealthCheck.Result"
  (.runHealthChecks health-registry))

(defn register-health-check [^String name check-f]
  "check-f should return a map of {:healthy true/false :msg message :error Throwable],
   or alternatively a HealhCheck$Result instance"
  (.register health-registry name
    (proxy [HealthCheck]
           []
		      (check []
            (try
              (coerce-health-result (check-f))
              (catch Exception e (.printStackTrace e)))))))

  
(defn list-metrics []
 (.getMetrics registry))

(defn remove-all []
 (.removeMatching registry (reify MetricFilter
                             (matches [_ _ _] true))))


(defn ^Gauge create-gauge [^clojure.lang.IFn f]
  (reify Gauge (getValue [this] (f))))

(defn ^Gauge add-gauge [^String name ^clojure.lang.IFn f]
  "Takes 2 parameters name = name of metrics
   f = the function that provides the guage's value
  "
  (try
   (.register registry name (create-gauge f) )
   (catch IllegalArgumentException iaexcp (get (.getGauges registry) name))))


 (defn ^Counter add-counter [^String name]
   (try 
     (.counter registry name)
     (catch IllegalArgumentException iaexcp (.counter registry name))))
 
 (defn ^Meter add-meter[^String name]
   (try (.meter registry name)
     (catch IllegalArgumentException iaexcp (.meter registry name))))
 
 (defn ^Histogram add-histogram[^String name]
   (try 
     (.histogram registry name)
         (catch IllegalArgumentException iaexcp (.histogram registry name))))
 
 
 (defn ^Timer add-timer[^String name]
   (try (.timer registry name)
         (catch IllegalArgumentException iaexcp (.timer registry name))))
 
 
 (defn ^Timer$Context  start-time[^Timer timer]
   (.time timer))
 
 (defn stop-time[^Timer$Context timer-ctx]
   (.stop timer-ctx))
 
 (defn measure-time [^Timer timer ^clojure.lang.IFn f]
   (let [timer-ctx (start-time timer)]
     (try (f)
       (finally (stop-time timer-ctx)))))
   
 
  (defn update-histogram [^Histogram h ^long val]
    (.update h val))
  
  (defn update-meter
    ([^Meter meter]
      (.mark meter))
    ([^Meter meter ^long n]
      (.mark meter n)))
  
  (defn inc-counter
    ([^Counter counter ^long v]
       (.inc counter v))
    ([^Counter counter]
       (.inc counter)))
  
  (defn dec-counter
    ([^Counter counter ^long v]
       (.dec counter v))
    ([^Counter counter]
       (.dec counter)))
