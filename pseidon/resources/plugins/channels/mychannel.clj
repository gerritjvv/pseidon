(ns plugins.channels.mychannel)
(use '[pseidon.core.app])
(use '[pseidon.core.registry :as r])
(use '[pseidon.core.ds.dummy :as d])
(use '[pseidon.core.queue :as q])
(use '[pseidon.core.message :as m])


(prn "hi")
(defn send-file [file]
        (doseq [line ((:reader-seq (r/reg-get "ds-test")) file)]
           (prn "Sending " line) ;/prefixdir/topic-/dateparition
          (q/publish data-queue (m/->Message (.getBytes line) "test" true (System/currentTimeMillis) 1) )
       ))
   

(defn run [] 
  (prn "Startin sending data")
 (doseq [file ((:list-files (r/reg-get-wait "ds-test" 10000)))]
         (send-file file)
        )
 )

(defn stop []
  (prn "stop my channel")
  )


(r/register (r/->Channel "ch-test" run stop))

         
        

