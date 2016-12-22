(ns pseidon.test.core.utils
  
  (:import [org.apache.commons.io FileUtils]
           [java.io File]))

(defn create-tmp-dir [prefix & {:keys [delete-on-exit] :or {:delete-on-exit true}}]
                "Create a new directory, if it exists the directory is deleted, an optional delete-on-exit can be specified.
                 The function returns the file object of type java.io.File"
                (let [ file (File. (str "target/" prefix "/" (System/currentTimeMillis)))]
                  (if (.exists file) (FileUtils/deleteDirectory file))
                  (.mkdirs file)
                  (if delete-on-exit
                    (FileUtils/forceDeleteOnExit file))
                  
                  file))
              
              