# Message Sending

## Overview

Each datasource, channel and processor at any time can send messages to the message queue.
The destination depends on the topic of the message and if there is another processor for the message.

NOTE: if there is no processor for the message pseidon will detect this error and shutdown, the current functionality
is meant to help prevent lossed messages.

## How to send a message

Messages that are just sent without any tracking risk being lost if the application goes down unexpectedly, i.e. messages are not persisted but in memory.

An example of just sending is:

```clojure

(require '[pseidon.core.queue :refer [publish]])
(require '[pseidon.core.app :refer [data-queue]])
(require '[pseidon.core.message :refer [get-bytes-seq get-ids create-message]])

(let [bytes [(get-some-bytes)] 
      id (get-message-id)
      ds "myds"
      topic "testtopic"
      priority 1]

(publish data-queue (create-message     bytes
                                        ds
                                        id
                                        topic 
                                        true 
                                        (System/currentTimeMillis) 
                     priority)))    
```

Publish to a queue is an asynchronous operation.

### Tracking messages for durability.

Although the actual message is never stored on disk anywhere during the queue or tracking stages, a simple RUN-DONE API is used to mark the message as
RUN (this is just a marker to introduce the message to the tracking store) and DONE (the message has been processed). 

The processor that receives a message is not expected to recover messages that its not processed. Its only task is to process the message and then mark it as DONE.
The datasource/channel/processor that sends the message must mark the message as RUN, and importantly on startup check for all messages that was previously sent but not marked as DONE and resend them. 

Lets work through an example.

We have a channel that sends a byte 1 message to a processor, the processor will print out received and mark the message as done. 
The channel will also on startup check if a message exists that have not been marked as done and resend it.


#### DataSource testds

```clojure

(require '[pseidon.core.queue :refer [publish]])
(require '[pseidon.core.app :refer [data-queue]])
(require '[pseidon.core.tracking :refer [mark-run!]])
(require '[pseidon.core.message :refer [get-bytes-seq get-ids create-message]])

(defn publish-msg [msg]
                (publish data-queue msg))

(defn send-messages[]

  (let [bytes (pseidon.util.Bytes/toBytes "1")] 
      id (System/currentTimeMillis)
      ds "testds"
      topic "testtopic"
      priority 1]

     (mark-run! ds id) ;always mark as run before publishing
     (publish-msg (create-message     bytes
                                        ds
                                        id
                                        topic 
                                        true 
                                        (System/currentTimeMillis) 
                     priority))))

(defn start []
          (doseq [msg (select-ds-messages "testds")]
               publish-msg msg)
          (send-messages))


```

#### Processor testtopic


````clojure

(require '[pseidon.core.tracking :refer [mark-done!]])

(defn exec [{:keys [ds ids] :as msg}]
      (prn "received " msg)
      (mark-done! ds ids (fn [] (prn "message marked as run" ))

````


## mark-done!

This function has the following parameters:

* ds the datasource name
* ids can be a single item or a sequence of ids
* f an zero argument function that is called only after the message has been marked as done.

All the ids are updated and the function is called in a single transaction, which means if the callback function fails all updates are reverted.


## Sending multiple parts in a single message

The message can contain a sequence of arrays of bytes and a sequence of ids. 
This allows the message to contain multiple parts (or messages) in a single message instance.
The message's bytes and ids are treaded as if it was a single message.




