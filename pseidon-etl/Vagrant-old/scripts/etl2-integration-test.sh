#!/usr/bin/env bash

### This script runs the etl integration test
## Before running it ensure that the kafka server is up and running and that the current clojure code compiles
###

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

chmod +x $DIR/*.py
chmod +x $DIR/*.sh

TOPIC_NAME=$(date +%s)
MSG_COUNT=100000
MSG=$(cat $DIR/../../src/test/resources/mytopic.txt)

$DIR/create_topic_remote.sh $TOPIC_NAME

$DIR/push-kafka-messages.py $MSG_COUNT $TOPIC_NAME "$MSG"


## clean out mysql and insert test logs
echo "truncate kafka_logs" | mysql -h 192.168.4.10 -udwadmin -p'dwadmin' testdb
echo "insert into kafka_logs (log, format, output_format, enabled, log_group) values ($TOPIC_NAME, 'txt:sep=,;ts=0', "gzip", 1, 'etl')" | mysql -h 192.168.4.10 -udwadmin -p'dwadmin' testdb

echo "TOPIC " $TOPIC_NAME
echo "TOPIC ETL " ${TOPIC_NAME}-etl

cd $DIR/../../
(lein run vagrant/config/pseidon.edn)& FOR=$!


COUNTS=$($DIR/print-kafka-messages.py ${TOPIC_NAME}-etl $MSG_COUNT | wc -l)

trap 'kill $(jobs -p)' EXIT
kill `ps aux |grep "repl" | awk '{print $2}'`

if [ "$MSG_COUNT" -eq "$COUNTS" ];
then
  echo ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>"
  echo "Congrats!!! COUNT FOR ${TOPIC_NAME}-etl is $COUNTS and matches what was sent"
  echo "before rerunning remember to ps | grep "repl" to ensure the repl did die down"
  exit 0;
else
  echo ":( Counts do not match sent $MSG_COUNT and got $COUNTS in the etl topic"
  echo "before rerunning remember to ps | grep "repl" to ensure the repl did die down"
  exit -1;
fi
