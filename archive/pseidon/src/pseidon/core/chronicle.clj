(ns pseidon.core.chronicle
  (:require [clojure.core.async :refer [thread go chan alt!! alts!! >!! <!! close! <! >! timeout]]
            [clojure.java.io :refer [file]]
            [clojure.tools.logging :refer [info error]]
            [pseidon.core.utils :refer [not-interrupted]]
            [pseidon.core.watchdog :refer [handle-critical-error]])
  (:import [net.openhft.chronicle IndexedChronicle ChronicleConfig ExcerptTailer ExcerptAppender NativeExcerpt]
           [java.util.concurrent.atomic AtomicLong AtomicInteger]
           [java.util.concurrent LinkedTransferQueue]
           [org.apache.commons.io FileUtils]
           [java.io File]
           [java.util Iterator]
           [pseidon.util Bytes Encoder Decoder])
  )

(declare write-to-chronicle)
(declare update-read-index)
         
(defn read-from-chronicle [^ExcerptTailer tailer ^Decoder decoder]
   "Read a bytes message from tailer"
   (let [size (.readInt tailer)
         ^bytes bts (byte-array size)]
     (.read tailer bts)
     (.decode decoder bts)))

(defn next-chornicle? [^ExcerptTailer tailer]
  (.nextIndex tailer))

(defn- get-tailer-index[^ExcerptTailer tailer]
  (.index tailer))
        
(defn- poll-queue [^LinkedTransferQueue queue]
  (.poll queue))

(defn- offer-queue [^LinkedTransferQueue queue msg]
  (.offer queue msg))

(defn- encode-msg [^Encoder encoder msg]
  (.encode encoder msg))


(defn path-to-str [path]
  (if (instance? File path)
    (.getAbsolutePath path)
    path))

(defprotocol ChronicleQueue 
  (offer! [this msg])
  (offer [this msg timeout-msg])
  (close [this])
  (poll! [this])
  (poll [this timeout-ms])
  (create-iterator [this])
  (get-size [this]))

(defrecord ChronicleQueueImpl [write-ch read-ch chronicle-ref size-f]
  ChronicleQueue
  (offer! [this msg]
    (>!! write-ch msg))
  (offer [this msg timeout-ms]
    "Blocks till the message can be placed on the queue and returns true otherwise returns false"
    (alt!! [[write-ch msg]] true (timeout timeout-ms) false))
  (poll! [this]
    "Poll for a value and blocks untill a value is available"
    (<!! read-ch))
  (poll [this timeout-ms]
    "Returns nil if timeout otherwise return a value"
    (let [[v _] (alts!! [read-ch (timeout timeout-ms)])]
          v))
          
  (create-iterator [this]
    (reify Iterator
      (hasNext [this]
        "Always returns true"
        true)
      (next [this]
        "Blocks till a value is available"
        (<!! read-ch))
      (remove [this]
        "Does nothing"
        )
    ))
  
  (close [this]
    (let [{:keys [^IndexedChronicle chronicle ^ExcerptAppender appender ^ExcerptTailer tailer ^IndexedChronicle chronicle-index]} @chronicle-ref]
      (info "!!!!!!!!!! closing chronicle index >>>>>>>>>>>>>>>>>>>>>>> ")
      (close! write-ch)
      (Thread/sleep 200)
      (.close appender)
      (.close tailer)
      (.close chronicle)
      (.close chronicle-index)))
  
  (get-size [this]
    (size-f))
    )

(defn ^File get-latest-chronicle-dir [parent-dir]
  "Takes as argument the parent directory of the chronicle indexes and looks for the 
   latest directory" 
  (last
    (sort-by #(.lastModified %)(filter #(.isDirectory %) (rest (file-seq (file parent-dir)))))))

(defn load-chronicle-path [path]
  (get-latest-chronicle-dir path))
    
(defn ^String new-chronicle-path [path]
  (str (path-to-str path) "/" (System/currentTimeMillis) "/queue"))
           
(defn read-chronicle-start-index [^IndexedChronicle chronicle]
  (let [^ExcerptTailer tailer (-> chronicle (.createTailer) (.toStart))
        start-index (.readLong tailer)]
    (info "read-chronicle-start-index " start-index)
    start-index))

(defn should-delete? [path]
  "Returns true if the read index is equal to the last written index"
  (let [chronicle (doto (IndexedChronicle. (path-to-str path) (ChronicleConfig/DEFAULT)))
        chronicle-index (IndexedChronicle. (str (path-to-str path) "-reader") (ChronicleConfig/SMALL))
        index (read-chronicle-start-index chronicle-index)
        last-written-index (.findTheLastIndex chronicle)
        ]
    (info "!!!!!!!!!!!! should delete ? " index " " last-written-index)
    (= (long index) (long last-written-index))))
    
           

(defn create-chronicle [path limit ^Decoder decoder & {:keys [new] :or {new true}}]
    (let [ chronicle (doto (IndexedChronicle. (path-to-str path) (ChronicleConfig/DEFAULT)))
           chronicle-index (IndexedChronicle. (str (path-to-str path) "-reader") (ChronicleConfig/SMALL))
           size (AtomicLong. 0)
           read-index (AtomicLong. (read-chronicle-start-index chronicle-index))
           array-queue (LinkedTransferQueue.)
           appender (-> chronicle (.createAppender) (.toEnd))
           tailer (let [tailer (.createTailer chronicle)]
                    (info "NEW INDEX? " new)
                    (if-not new
                      (do
                        (let [index (read-chronicle-start-index chronicle-index)
                              last-written-index (.findTheLastIndex chronicle)]
                          (if (> (- last-written-index index) (* 3 limit))
                            (do
                              (error "The queue data appears corrupted, we will not recover from the tail")
                              (.set read-index last-written-index))
                            (do 
		                          (info "Tailing to index " index)
		                          (.index tailer (dec index))
		                          ;copy into array-queue
		                          (if array-queue
		                            (while (next-chornicle? tailer)
		                              (do 
                                    (try
                                      (do 
                                        (offer-queue array-queue (read-from-chronicle tailer decoder))
                                        )
                                      (catch Exception e (error e e)))
                                    (.incrementAndGet size) 
                                      
		                                ))))))
                      )
                      (do 
                        (update-read-index chronicle-index 0)
                        (.set read-index 0))
                      )
                    tailer)
                      
           ]
      (info "Creating chronicle dir " path  " read index " (.get read-index))
      ;ChronicleQueueImpl [write-ch read-ch chronicle-ref queue-size chronicle-index]
      {:chronicle chronicle :appender appender :tailer tailer :queue-path path :chronicle-index chronicle-index
       :array-queue array-queue
       :read-index read-index
       :size size}))
        
(defn block-on-size [^AtomicLong size limit]
  (while (> (.get size) limit)
    (do 
      (Thread/sleep 200))))

(defn copy-ch [read-ch new-chronicle ^Encoder encoder ^long i]
  "Copies from the read channel to the new chronicle
   retrns the amount of records copied"
  (let [[v ch] (alts!! [read-ch (timeout 200)])]
    (if (= ch read-ch)
      (do 
        (write-to-chronicle new-chronicle encoder v)
        (recur read-ch new-chronicle encoder (inc i) ))
        i)))

(defn roll-chronicle! [chronicle path read-ch limit ^Encoder encoder ^Decoder decoder]
  "This function will create a new chronicle, copy any data left in the previous chronicle and deletes the previous
   Returns the new chornicle"
      (let [{:keys [chronicle appender tailer queue-path]} chronicle
            new-chronicle (-> path (new-chronicle-path) (create-chronicle limit decoder))]
          ;we must read all left over messages from the old channel into the new one
        
          (.close appender)
          ;while we copy the write channel might be blocked,
          (info "start copy")
          (info "copied " 
                (copy-ch read-ch new-chronicle encoder 0)); there are two threads contending for the copy-ch, we just need to get all items till none is left.
            
            
        
	        (future
	          ;do cleanup in a separate thread
		        (.close tailer)
		        (.close chronicle)
		        (-> queue-path (file ) (.getParentFile) (FileUtils/deleteDirectory)))
		        
	        (info "delete dir : " (-> queue-path (file ) (.getParentFile)))
	        new-chronicle
        ))
       

    
(def byte-array-cls (Class/forName "[B"))

(defn update-read-index [^IndexedChronicle chronicle index]
  (let [^NativeExcerpt e (-> chronicle (.createExcerpt) (.toStart))]
    (doto 
      e 
      (.startExcerpt 8)
      (.writeLong index)
      .finish)))

(defn write-to-chronicle [{:keys [^ExcerptAppender appender ^LinkedTransferQueue array-queue ^AtomicLong size]} ^Encoder encoder msg]
  "Write the bytes message to the chronicle appender"
  (let [^bytes bts (encode-msg encoder msg)
        cnt (count bts)]
	    (doto
           appender
                 (.startExcerpt (+ cnt 10))
                 (.writeInt cnt)
                 (.write bts)
                 .finish)
       (.incrementAndGet size)
       (offer-queue array-queue msg)
     ))
	

 (defn should-roll? [{:keys [^AtomicLong size] } ^long segment-limit]
  (> (.get size) segment-limit))
 
 (defn queue-size? [{:keys [^AtomicLong size ^AtomicLong read-index] }]
   (- (.get size) (.get read-index)))

 (defn should-block? [chronicle ^long limit]
   (>= (queue-size? chronicle) limit))
  
(defn- increment-atomic [^AtomicLong i]
  (.incrementAndGet i))

(defn- block-thread [] (Thread/sleep 200))

(defn create-queue [path limit ^Decoder decoder ^Encoder encoder & {:keys [segment-limit buffer] :or {segment-limit (* 2 limit) buffer -1}}]
  "Returns a ChronicleQueue with background writters and readers enabled"
  (let [
        segment-limit2 (if (> segment-limit limit) segment-limit (do (info "segment limit cannot be smaller than the limit setting to 2 * limit") 
                                                                   (* 2 limit)))
        write-ch (if (pos? buffer) (chan buffer) (chan))
        read-ch (chan)
        chronicle-ref (ref (if-let [p (load-chronicle-path path)] 
                             (do 
                               (if (should-delete? (str p "/queue"))
                                 (do
                                   (info ">>>>>>>>>>>>>>>> deleting chronicle " p)
                                   (-> p (file ) (FileUtils/deleteDirectory))))
                               
                               (create-chronicle (str p "/queue") limit decoder :new false))
                             (create-chronicle (new-chronicle-path path) limit decoder )))
        
        ]
    
    ;after reading we need to push a roll here to solve the SIGSEV errors
    ;that happen when chronicle is shutdown and restarted
    (dosync (ref-set chronicle-ref 
                                    (roll-chronicle! @chronicle-ref path read-ch limit encoder decoder)))
    (thread
      (try 
	      (while (not-interrupted)
	        (if-let [msg (<!! write-ch)]
           (let [chronicle @chronicle-ref]
	          
               (if (should-roll? chronicle segment-limit2)
                   (dosync (ref-set chronicle-ref 
                                    (roll-chronicle! chronicle path read-ch limit encoder decoder))))
               
               
               (while (should-block? chronicle limit)
                 (do
                 (block-thread)))
               
	             (write-to-chronicle @chronicle-ref encoder msg))
            ))
       
            (catch Exception e (handle-critical-error e "Error writing to chronicle") )))
    
    ;we loop forever, get a reference value from the chronicle-ref
    ;and get the tailer, loop through the tailer until it returns no values
    ;then go back and derefence the chronicle-ref again to get a new tailer, or the same.
    ;If the chronicle was rolled the current tailer will stop returning values and 
    ;the chronicle-ref will have a new chronicle instance with tailer assigned.
    (go 
      (while (not-interrupted)
        (try
          (let [{:keys [^IndexedChronicle chronicle-index ^LinkedTransferQueue array-queue ^AtomicLong read-index]} @chronicle-ref]
            (loop []
              (if-let [msg (poll-queue array-queue)]
                (do 
			                  (>! read-ch msg)
			                  (update-read-index chronicle-index (increment-atomic read-index)) 
                  
                  (recur)))))
          (catch Exception e (handle-critical-error e "Error reading from chronicle")))))
          
    (ChronicleQueueImpl. write-ch read-ch chronicle-ref (fn [] (queue-size? @chronicle-ref)))
    )
  )

