# pseidon

A Clojure library designed to ... well, that part is up to you.

## Usage

Big Data Imports


## Downloads

https://sourceforge.net/projects/pseidon/files

## Doc

### Startup Configuration 

There are three environment variables that can be set.

The file sources in pseidon.sh is /etc/sysconfig/pseidon

The variables are:

| Name | Description |
| ---- | ----------- |
| JAVA_HOME | error if not set |
| JAVA_HEAP | default is -Xmx4096m -Xms1024m -XX:MaxDirectMemorySize=2048M |
| JAVA_GC   | default is -XX:+UseCompressedOops -XX:+UseG1GC |
| JAVA_OPTS | default is empty |


### Configuration

| Name | Description | Default |
| ---- | ----------- | ------- |
| pseidon-queue-limit | the default queue's limit | 100 |
| worker-threads | the default thread pool's max number of threads | the number of cpus |


To completely isolate a slow topic from all other topic's execution specify the properties pseidon-queue-$topic-limit and worker-$topic-threads.

### Classpath setup and including new plugins

The classpath for pseidon is set using the classpath property in the pseidon.edn file

The default property value is /opt/pseidon/lib

To add in a new plugin with its own jars and files edit this property.

### Metrics

Pseidon uses the http://metrics.codahale.com/ library to show metrics.

Plugins can use the pseidon.core.metrics namespace to add metrics.

To see metrics for a running instance 

Open in a browser:

[http://localhost:8282/metrics](http://localhost:8282/metrics)

### Tracking

Each message send from a datasource is tracked through the system with two states
['running', 'done'].

'running' is when the message has leaved the datasource

'done' is when the message has be fully processed and can be dicarded

Open in a browser:

[http://localhost:8282/tracking?max=10&from=0](http://localhost:8282/tracking?max=10&from=0)


or to apply a query clause

[http://localhost:8282/tracking?q="dsid='myid' and status='running'"&max=10&from=0](http://localhost:8282/tracking?q="dsid='myid' and status='running'"&max=10&from=0)

There are three columns you can query:

  * dsid String
  * status String
  * ts TimeStamp format

### Registry

Open in a browser:

[http://localhost:8282/registry](http://localhost:8282/registry)

## Configuration

| Name | Description |
| ------ | --------- |
| pseidon-queue-path | Psiedon uses chronicle to store its internal queue messages, this path is used to store locally|
| psedon-queue-limit | the limit to use default is 50 |
| pseidon-queue-segment-limit | In disk backed queue will roll its files on this message limit default 1000000 |
| pseidon-queue-buff | The internal queue buffering default is -1 |
| worker-threads | each topic is assigned its own thread pool, this is the limit to use on each thread pool, default is the number of processors on the box|
| worker-$topic-threads | overrides worker-threads on a per topic basis |
| java-library-path | the java.library.path directory from where native libraries are laoded default "/opt/hadoopgpl/native/Linux-amd64-64/" |
| :psiedon-queue-type | "blocking-array" == BlockingArrayQueue "chronicle" == java chronicle, "chronicle" is the default |

## License

Apache License, Version 2.0
http://www.apache.org/licenses/LICENSE-2.0.html



