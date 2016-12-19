# pseidon-etl


# Configuration

## Configuration file

The configuration file is located at ```/opt/pseidon-etl/conf/pseidon.edn```

#### Config DB

The pseidon-etl uses the following tables:

  * pseidon_logs

<table>
<tr><td>:etl-db-host</td><td>"//localhost:3306/mydb?autoReconnect=true"</td><td>mysql db host name</td></tr>
<tr><td>:etl-db-user</td><td>"pseidon"</td><td>mysql db user name</td></tr>
<tr><td>:etl-db-pwd</td><td>"pseidon"</td><td>mysql db password</td></tr>
<tr><td>:etl-group</td><td>"etl"</td><td>The sub group for this etl machine, which si used in the log_group="$group" query above</td></tr>
</table>


```clojure
:writer {:parallel-files 1 :out-buffer-size 500000 :use-buffer false :flush-on-write true :write-multiplier 1}
```


#### Disk Write

<table>
<tr><td>:data-dir</td><td></td><td>Directory where files will be written to, must have pseidon user write permissions</td></tr>
<tr><td>:writer {:codec :gzip :check-freq 2000 :rollover-size 115000000 :rollover-abs-timeout 600000 :parallel-files 3 :rollover-timeout 60000}</td><td></td><td>refer to the https://github.com/gerritjvv/fileape api for information</td></tr>
</table>

##### Disk Write and unit tests

```clojure
;;this will not filter any messages based on timestamps
:writer {:allow-all-ts true} 
```

#### Kafka

<table>
<tr><td>:consume-step</td><td>10</td><td>The number of messages to include in a single work unit, 10 is the best for pseidon-etl</td></tr>
<tr><td>:kafka-brokers</td><td>:kafka-brokers [{:host "broker1" :port 9092} {:host "broker2" :port 9092} {:host "broker3" :port 9092}]</td><td>The kafka brokers to read messages from</td></tr>
<tr><td>:kafka-use-earliest</td><td>false</td><td>On startup when a log's offsets are not saved in the redis storage yet, if false the latest offset is used otherwise the start of the data from kafka in the log is used.</td></tr>
<tr><td>:redis-conf</td><td>:redis-conf {:host "redis:6379" :heart-beat 5}</td><td>If a single host is provided a single none redis cluster is expected, multiple hosts require a proper redis cluster, kafka offsets are stored in the redis cluster</td></tr>
<tr><td>:kafka-msg-ch-buffer-size</td><td>50</td><td>Buffer size between the kafka consumer background fetcher threads and the pseidon app</td></tr>
</table>

Kafka consume message queue length: Read the length of the redis queue ```etl-kafka-work-queue```

#### Internal Pool

The internal pool is used by the Kafka threads to place gathered information and then consumed by the etl threads.

The properties for this is:

<table>
 <tr><td>:pool</td><td>:pool {:queue-limit 100 :queue-type :array-queue} queue-type can be :array-queue, :spmc-array-queue, :mpmc-array-queue</td></tr>
</table>
#### Redis Metrics

<table>
<tr><td>:metrics</td><td>:metrics {:redis {:host :port 6379 :db 0}}</td><td>A single redis instance is expected, some metric values are written to this</td></tr>
</table>


#### Internal performance config

<table>
<tr><td>:etl-threads</td><td>10</td><td>number of threads dedicated to exclusive etl, note that higher is not always faster because other threads are required to feed the etl with messages and write to file</td></tr>
<tr><td>:io-threads</td><td>4</td><td>Internal value, for io threading from kafka, higher is not better, the max should be 4</td></tr>
</table>

###Monitoring

<table>
<tr><td>:monitor-port</td><td>8080 the port on which the monitoring stats will be shown</td></tr>
<tr><td>:repl-port</td><td>7122 is the repl port that will be opened when the app starts</td></tr>
</table>
               

## Parquet Performance Configuration:

```pseidon.edn```  

```clojure
 :writer {:codec :parquet :out-buffer-size 16384 :write-multiplier 2 :parquet-block-size 20971520} ;65536 == 64KB
 
```

```/etc/sysconfig/pseidon-etl```  
```
export JAVA_GC="-server -XX:+UseParallelGC -XX:+UseParallelOldGC"

export JAVA_HEAP="-Dio.netty.allocator.type=pooled -Xmx40g -Xms40g -XX:NewSize=12G -XX:PermSize=1024m -XX:MaxDirectMemorySize=16g -XX:+HeapDumpOnOutOfMemoryError  -XX:HeapDumpPath=/tmp/psiedon -Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=9010 -Dcom.sun.management.jmxremote.local.only=false -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false"

```


## Configuration dbs 


### pseidon_logs

```
CREATE TABLE IF NOT EXISTS `pseidon_logs` (
  `log` varchar(100) NOT NULL,
  `format` varchar(200) DEFAULT NULL,
  `output_format` varchar(200) DEFAULT NULL,
  `base_partition` varchar(200) DEFAULT NULL,
  `log_partition` varchar(200) DEFAULT NULL,
  `hive_table_name` varchar(200) DEFAULT NULL,
  `hive_url` varchar(255),
  `hive_user` varchar(200) DEFAULT NULL,
  `hive_password` varchar(200) DEFAULT NULL,
  `quarantine` varchar(200) DEFAULT "/tmp/pseidon-quarantine",
  `log_group` varchar(100) DEFAULT 'default',
  `enabled`  tinyint(1) DEFAULT '1',
  PRIMARY KEY (`log`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1
```


# Vagrant setup


## Zookeeper

`vagrant up zookeeper1` ip: 192.168.4.2

## Services box

Runs redis and mysql

`vagrant up services1` ip: 192.168.4.10
`redis-cli -h 192.168.4.10`
` mysql -h192.168.4.10 -udwadmin -p'dwadmin'`


## Kafka boxes

Note: requires zookeeper1 to be up and running

`vagrant up broker1`  ip: 192.168.4.40
`vagrant up broker2`  ip: 192.168.4.41
`vagrant up broker3`  ip: 192.168.4.42

### Create test topics

From the zookeeper boxes run

`/vagrant/vagrant/scripts/create_topic.sh test1`

Or locally 

`/vagrant/vagrant/scripts/create_topic_remote.sh test1`


## pseidon-etl

Before running the pseidon-etl run the kafka cluster tests to ensure the cluster is up and running

You will need python and the kafka-python library for this.

`vagrant/scripts/test-kafka.py`

On a new cluster the topic creation might fail so you will need to run test command a few times.


To run from the project checkout, ensure that all toe previous boxes are up and running.

Then type:

`lein run vagrant/config/pseidon.edn`

This option is most usefull for quick integration testing.



## Run automated tests against vagrant boxes

The tests are written in python.

Install pip

`sudo easy_install pip`

Install python kafka library https://github.com/mumrah/kafka-python

`pip install kafka-python`

### Script

`vagrant/scripts/etl2-integration-test.sh`
   
  * This script will create a unique topic plus its etl variant.  
  * Push N messages to kafka.  
  * Truncate the mysql kafka_logs table.
  * Insert an entry for the log into the mysql kafka_logs table.  
  * Then startup the etl app using `lein run vagrant/config/pseidon.edn`.
  * In parallel it will run the print-kafka-messages.py and after the correct messages have been read or a 60second timeout,  
the script exits.

### Cleanup and restarts

Topics cannot be removed from kafka without issues, for this reason non of the scripts will remove any topics creates,  
this might cause issues if you run it for some time, it is recommended after a few hundred runs (might be less) to  
clean and restart the cluster using `vagrant destroy`  `vagrant up`

# Vagrant kafka helper scripts

All scripts are in vagrant/scripts

<table>
<tr><td>print-kafka-messages.py</td><td>Reads messages from the start of a queue to end and print out each message, takes arguments topic n where n is the number of expected messages</td></tr>
<tr><td>push-kafka-messages.py</td><td>Sends messages to kafka arguments n topic message</td></tr>
<tr><td>test-kafka.py</td><td>Send and read messages from kafka, quick test to see the kafka cluster is working</td></tr>
<tr><td>create_topic_remote.sh</td><td>Creates a topic on the remote zookeeper1 machine</td></tr>
<tr><td>create_topic.sh</td><td>Creates a topic when your inside the zookeeper1 machine</td></tr>
</table>

# Fatal Errors

Some application errors are not recoverable from, for these the pseidon-etl application will exit itself and try to restart.  
Any fatal errors are logged to ```/tmp/fatalerrors```

# Heap Audit

# UI

Each pseidon-etl instance provides a json UI page where metrics are shown ```http://<server>:<monitor-port>/metrics```

```
{
  "etl-service-thread-queue": 0,
  "etl-service-thread-metrics": {
    "count": 5966301527,
    "five-minute-rate": 29099.873340123766,
    "mean-rate": 38578.78380001218,
    "one-minute-rate": 26454.222493182337
  },
  "open-connections": {
    "tcp-conn": 39
  },
  "topic-errors": {
    
  },
  "memory": {
    "direct": {
      "memory-used": 0.103,
      "count": 76,
      "total-capacity": 0.103
    },
    "heap": {
      "heap": {
        "init": 8192.0,
        "used": 5430.242,
        "max": 16384.0,
        "committed": 8348.0
      },
      "non-heap": {
        "init": 1026.437,
        "used": 61.93,
        "max": 1072.0,
        "committed": 1033.312
      }
    }
  },
  "redis": {
    "redis-conn": {
      "pool": "org.redisson.Redisson@43162ec3"
    }
  },

}
```
