(ns pseidon.core.wal
  
  (:import (org.apache.commons.io FileUtils)
           (java.io RandomAccessFile)
           (java.nio.channels FileChannel$MapMode)
           )
)


(comment 
  
  Wall files are short lived mostly in memory files that are used to guard against corrupt or lost clojure.data.Diff
  Buffers are not forced so some information might be lost, but it does give some layer of protection.
  Ideally a WALFile is created and deleted shortly after creation when the intended file of writing has been committed.
  
  (def w (create-walfile "target/myfile123.txt-wal"))
  (wal-write w (byte-array (map byte (range 10))) )
  (close w)
  read
  (def w2 (open-walfile "target/myfile123.txt-wal"))
  or 
  (wal-seq "target/myfile123.txt-wal")
  
  Replay
  (doseq [data (wal-seq "target/myfile123.txt-wal")]
    ; do something with the message
    )
    
  
  )

(def gigabyte 1073741824)

(defrecord WALFile [^java.io.File file 
                    ^java.io.RandomAccessFile wal-out 
                    ^java.nio.channels.FileChannel wal-channel 
                    ^java.nio.MappedByteBuffer w-buf]
  )

(defn wal-write [{w-buf :w-buf} ^bytes data]
  "
   Writes the data byte array to the buffer as [4 bytes in data.length][data]
  "
  (.putInt w-buf (count data))
  (.put w-buf data 0 (count data))
  )

(defn ^WALFile open-walfile [^String file-name]
  "
   Opens an already created WAL file for reading
  "
  (let [ 
         file (java.io.File. file-name)
         wal-out (RandomAccessFile. file "rw")
         wal-channel (.getChannel wal-out)
         w-buf (.map wal-channel FileChannel$MapMode/READ_WRITE 0 gigabyte)
         ]
     (->WALFile file wal-out wal-channel w-buf)
     )
  )

(defn create-walfile [^String file-name]
  "
   Creates a new WALFile, any parent directories are also created.
   If the file already exists its overwritten.
  "
  (let [file (java.io.File. file-name) ]
   (FileUtils/forceMkdir (.getParentFile file))
   (.createNewFile file)
   (open-walfile file-name)
  ))

(defn wal-read [{w-buf :w-buf}]
  "Returns a byte array of the record written
   The format in w-buf is expected as [4 byte int describing the size][message in bytes len=size]
   Returns nil if no more data is available
  "
	  (let [size (.getInt w-buf) ]
	    (when (pos? size) 
	      (let[ arr (byte-array size)
	            ]
            (.get w-buf arr) 
	          arr
	        )
	      nil
	    )
	  )
  )

(defn close [{:keys [w-buf wal-channel wal-out] }]
  "Close all channels output streams and forces buffers in the walfile"
   (.force  w-buf)
   (.close  wal-channel)
   (.close  wal-out)
  )

(defn destroy [file-name]
  "First closes then deletes the walfile"
   (.delete (java.io.File. file-name))
  )

(defn close-destroy [^WALFile walfile]
   (close walfile)
   (destroy (-> walfile :file .getAbsolutePath ) )
   )

(defn wal-seq [^String file-name]
  "Returns a lazy sequence of byte arrays, each byte array represents a record that was written to the 
   walfile. 
  "
   (defn walfile-seq [^WALFile walfile]
      (let [ record (wal-read walfile) ]
        (if (nil? record)
            (do (close walfile) nil)
            (cons record (lazy-seq record (walfile-seq walfile))) 
           )
         )
      )
   
    (walfile-seq (open-walfile file-name))
    )


(defn wal-compact [{w-buf :w-buf}]
  "Compacts the buffer, this is usefull during replay as it shrinks the replay log as its being read
   Do not execute this method on each record read but rather on every N (e.g. 1000) records read.
  "
  (.flip w-buf)
  (.compact w-buf)
  (.force w-buf)
  )


(comment 
  
  Functions for writing compressed rolled log files that can be imported into 
  hadoop or any other storage system. Having files split into N MB chunks make them more manageable
  e.g split lzo files directly on the hadoop blocksize.
  
  )