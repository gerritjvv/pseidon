(ns pseidon-hdfs.hdfs-io
  (:import (org.apache.hadoop.fs Path FileSystem FSDataInputStream)
           (org.apache.hadoop.fs.permission FsPermission)
           (org.apache.hadoop.conf Configuration)
           (org.apache.hadoop.util Progressable)
           (org.apache.hadoop.io IOUtils)))



;public FSDataOutputStream
;create(Path f, boolean overwrite, int bufferSize, short replication, long blockSize, Progressable progress) throws IOException
;{
;
; FsPermission defaultPermission = FsPermission.getFileDefault().applyUMask(FsPermission.getUMask(conf));
;
;FsPermission permission = new FsPermission(defaultPermission.getUserAction(), defaultPermission.getUserAction(), defaultPermission.getOtherAction());
;return this.create(f, permission, overwrite, bufferSize, replication, blockSize, progress);

;;this.getConf().getInt("io.file.buffer.size", 4096), this.getDefaultReplication(f), this.getDefaultBlockSize(f))

(defn buffer-size [^FileSystem fs]
  (.getInt (.getConf fs) "io.file.buffer.size" (int 4096)))

(defn create [^FileSystem fs ^Path f overwrite]
  (let [_ (do
            (when-not (.getConf fs)
              (.setConf fs (Configuration.)))

            (prn "Conf " (.getConf fs)))

        ^FsPermission default-perm (.applyUMask (FsPermission/getFileDefault) (FsPermission/getUMask (.getConf fs)))
        ^FsPermission perm (FsPermission. (.getUserAction default-perm)
                                          (.getUserAction default-perm)
                                          (.getOtherAction default-perm))]
    (.create fs
             f
             perm
             (boolean overwrite)
             (int (buffer-size fs))
             (.getDefaultReplication fs f)
             (.getDefaultBlockSize fs f)
             (reify Progressable
               (progress [_])))))


(defn ^FSDataInputStream input-stream [^FileSystem fs ^Path path]
  (.open fs path))

(defn ^FileSystem local-filesys [^FileSystem fs]
  (FileSystem/getLocal (.getConf fs)))

(defn copy-file [^FileSystem srcfs ^Path src ^FileSystem destfs ^Path dest overwrite]
  (let [input (input-stream srcfs src)
        output (create destfs dest true)]
    (IOUtils/copyBytes input output (.getConf destfs) (boolean overwrite))))

(defn copy-from-local [^Path src ^FileSystem fs ^Path dest overwrite]
  (copy-file (local-filesys fs) src fs dest overwrite))
