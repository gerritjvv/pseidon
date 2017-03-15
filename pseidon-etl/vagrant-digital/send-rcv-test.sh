#!/usr/bin/env bash


### Test sending data to kafka and consuming with pseidon-el

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

TEST_TOPIC="abc-`date +%s`"
N=100000
THREADS=4

DATADIR="/var/log/pseidondata"

## assert all boxes are running

#echo "check vagrant boxes"

#if [[ "`vagrant status broker1 broker2 broker3 services1 client | grep active | wc -l`" -ne "5" ]]; then

#       echo "The following vagrant boxes must be running broker1 broker2 broker3 services1 client"
#       exit -1
#fi

#echo "All vagrant boxes are running"

echo "create topic in kafka and send $N messages to $TEST_TOPIC"

time $DIR/produce.sh $TEST_TOPIC $THREADS $N || exit -1

echo "Done sending"


echo "enable topic $TEST_TOPIC in mysql table pseidon.pseidon_logs"

vagrant ssh client -c "

service pseidon-etl stop

rm -rf $DATADIR/*.gz

MYSQL_PWD=pseidon mysql -upseidon -e  \"insert into pseidon.pseidon_logs (log, format, output_format, log_group, enabled) values ('${TEST_TOPIC}', 'avro:ts=0;msg=1', 'json', 'etl', 1) on duplicate key update enabled=1\" || exit -1

service pseidon-etl start && timeout 120 bash -c 'until echo > /dev/tcp/localhost/8282 ; do sleep 2; done' || echo \"pseidon start failed\" ; exit -1

echo \"pseidon-etl started\"
"