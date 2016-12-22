# pseidon-kafka

Provides a DataSource and Sink pluging to psiedon for kafka.

## Handling failures and avoid message loss

Rather than implementing the low level kafka api and trying to keep track of every message in zookeeper another route is taken.
Each group of messages retreived are given an id value that consists of the topic,parition,min-offset,max-offset. 
Its up to the user of this datasource to mark the message group id as done. 
When failure is detected the datasource on startup will scan for any message groups with status "ready/run" and replay them from kafka.

This allows the datasource to use the highlevel api for kafka, and not complicate the logic with what is essentially kafka internals.


The topic, partition and offset can be retreived from the MessageAndMetadata instance.

## Kafka supported versions
 Currently only the stable kafka release 0.7.2 is supported. 
 
## Configuration

Configuration is done in the /opt/pseidon/conf/pseidon.edn file.

Properties

| name | description |
| ---- | ----------- |

All properties are the standard kafka configuration properties with ':kafka.' prepended to it.
The ':kafka.' part is removed and the properties are passed in to kafka as is.
Note that all the property values should be single string values enclosed in " ".

For more configuration properties please see [http://kafka.apache.org/07/configuration.html](http://kafka.apache.org/07/configuration.html)


## Usage

Load the plugin into pseidon via the :classpath and :plugin-dirs properties in /opt/pseidon/conf/pseidon.edn.
Then use the datasource and datasinks provided via the registry api.

Add the following to both :classpath and :plugin-dirs

/opt/pseidon-kafka/lib/

/opt/pseidon-kafka/plugins

### Using the Data Sink to send messages to kafka

```clojure

(use 'pseidon.core.registry)

;;use the data sink to send
(let [{:keys [writer]} (reg-get-wait "pseidon.kafka.util.datasink" 5000)]
    (dotimes [i 1000]
           (writer {:topic "test" :val (.getBytes (str "test-" i))})))

;;use the data source to consume
(let [{:keys [reader-seq]}  (reg-get-wait "pseidon.kafka.util.datasource" 5000)
      messages (reader-seq "test") ]
      (doseq [msg message]
         (prn "Message " msg)))

```

## License

Copyright © 2013 
Distributed under the Eclipse Public License, the same as Clojure.
