# pseidon-hdfs

Import data from local disk into hdfs.

## Testing

This library relies on mysql and redis to be running locally.  

### Mysql

Create a mysql database, user and the following table in the database:

```sql
CREATE TABLE `pseidon_logs` (
  `log` varchar(100) NOT NULL,
  `format` varchar(200) DEFAULT NULL,        -- TXT:{"ts": 0, "sep": "byte1"}
  `output_format` varchar(200) DEFAULT NULL, -- TXT
  `base_partition` varchar(200) DEFAULT NULL,
  `log_partition` varchar(200) DEFAULT NULL,
  `hive_table_name` varchar(200) DEFAULT NULL,
  `hive_url` varchar(255),
  `hive_user` varchar(200) DEFAULT NULL,
  `hive_password` varchar(200) DEFAULT NULL,
  `quarantine` varchar(200) DEFAULT "/tmp/pseidon-quarantine",
  `dateformat` varchar(200) DEFAULT "datehour",
  `log_group` varchar(100) DEFAULT 'default',
  `enabled`  tinyint(1) DEFAULT '1',
  PRIMARY KEY (`log`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1
```


## Configuration

# Configuration

## Configuration file

The configuration file is located at ```/opt/pseidon-hdfs/conf/pseidon.edn```

#### Code 
The default configuration file is resources/pseidon.edn  

For production this must be set to a different value, to way to do this is via clojure bindings:  

```clojure

(binding [etl-lib.conf/*default-configuration* "myfile.edn"]
 (etl ... ))
```

#### Config DB

Depends on the mysql tables

  * ```hdfs_log_partitions```
   
<table>
 <tr><td>:hdfs-db-host</td><td>"hb01"</td><td>mysql db host name</td></tr>
 <tr><td>:hdfs-db-name</td><td>"logcollector"</td><td>mysql db name</td></tr>
 <tr><td>:hdfs-db-user</td><td>"pseidon"</td><td>mysql user name</td></tr>
 <tr><td>:hdfs-db-pwd</td><td>"pseidon"</td><td>mysql password</td></tr>
</table>

The table formats are:

 log             | varchar(255)           | NO   | PRI | NULL    |       |
| format          | enum('gpb','json')     | YES  |     | NULL    |       |
| output_format   | enum('parquet','json') | YES  |     | NULL    |       |
| hive_table_name | varchar(255)           | YES  |     | NULL    |       |
| hive_url        | varchar(255)           | YES  |     | NULL    |       |
| hive_user       | varchar(255)           | YES  |     | NULL    |       |
| hive_password   | varchar(255)           | YES  |     | NULL    |       |


### Config

Remember that the version of hadoop and hive that this project depends on needs to be the exact same version  
of that used for hadoop and hive in production, refer to the ```pom.xml``` file.

<table>
 <tr><td>:hdfs-conf</td><td>
  :hdfs-conf {"fs.default.name" "hdfs://<namenode>8020"
              "fs.defaultFS" "hdfs://<namenode>:8020"
              "fs.hdfs.impl" "org.apache.hadoop.hdfs.DistributedFileSystem"
            } </td>

 <td>hdfs configuration that points the hadoop client to the hdfs cluster</td></tr>
 <tr><td>:hdfs-dir-perms</td><td>Permissions to apply to directories uploaded to hdfs default is 0777</td></tr>
 <tr><td>:hdfs-file-perms</td><td>Permissions to apply to files uploaded to hdfs default is 0777</td></tr>

 <tr><td>:local-dir</td><td>"/tmp"</td><td>The directory from which the data should be loaded from</td></tr>
 <tr><td>:kafka-partition-cache-refresh</td><td>Time in milliseconds that the partition cache refresh will happen, default 30 000</td></tr>
 <tr><td>:copy-threads</td><td>The number of threads to use for file copying, default 8</td></tr>
 <tr><td>:proxy-user</td><td>The user that will be used instead of the kerberos signed in user</td></tr>
 <tr><td>:secure</td><td>Indicates that kerberos authentication is used</td></tr>
 <tr><td>:secure</td><td>Indicates that kerberos authentication is used</td></tr>
 <tr><td>:secure-user</td><td>Indicates that kerberos authentication user</td></tr>
 <tr><td>:secure-keytab</td><td>Indicates that kerberos authentication user keytab</td></tr>
</table>

### Other configuration options

<table>
<tr><td>:file-wait-time-ms</td><td>default 30000, milliseconds that a file should be old before its checked for upload</td></tr>
<tr><td>:hdfs-copy-freq</td><td>default 1000, milliseconds that files are checked for upload</td></tr>
</table>
###Monitoring

<table>
<tr><td>:monitor-port</td><td>8283 the port on which the monitoring stats will be shown</td></tr>
<tr><td>:repl-port</td><td>7113 is the repl port that will be opened when the app starts</td></tr>
</table>


# Kerberos

For reference see: http://henning.kropponline.de/2016/02/14/a-secure-hdfs-client-example/

Change ```/opt/pseidon-hdfs/conf/pseidon.edn``` to contain:


```
:hdfs-conf {"fs.default.name" "hdfs://<namenode>:50070"
              "fs.defaultFS" "hdfs://<namenode>:50070"
              "fs.hdfs.impl" "org.apache.hadoop.hdfs.DistributedFileSystem"
              "hadoop.security.authentication", "kerberos"
              }
```

For hive doc see: http://stackoverflow.com/questions/21375372/accessing-hive-metastore-using-jdbc-with-kerberos-keytab

For HDFS doc see: https://steveloughran.gitbooks.io/kerberos_and_hadoop/content/sections/jaas.html

For some systems, a different user is used than that in which the application is run, see https://hadoop.apache.org/docs/r1.0.4/webhdfs.html#ProxyUsers
