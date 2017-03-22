#!/usr/bin/env bash

### Install Kafka

#KAFKA_DOWNLOAD="http://apache.uvigo.es/kafka/0.9.0.0/kafka_2.10-0.9.0.0.tgz"
#KAFKA_FILE="kafka_2.10-0.9.0.0.tgz"
#KAFKA_DIR="/usr/local/kafka_2.10-0.9.0.0"

KAFKA_DOWNLOAD="http://apache.rediris.es/kafka/0.10.1.0/kafka_2.10-0.10.1.0.tgz"
KAFKA_FILE="kafka_2.10-0.10.1.0.tgz"
KAFKA_DIR="/usr/local/kafka_2.10-0.10.1.0"
KAFKA_DATA_DIR="/data/kafka-logs"

#ensure vagrant/rpm exists
mkdir -p /vagrant/rpm


if [ ! -f /vagrant/rpm/$KAFKA_FILE ]; then
    echo Downloading kafka...
    wget --no-check-certificate $KAFKA_DOWNLOAD -O "/vagrant/rpm/$KAFKA_FILE"
fi

if [ ! -d "$KAFKA_DATA_DIR" ]; then
   mkdir -p "$KAFKA_DATA_DIR"
   chmod -R 777 "$KAFKA_DATA_DIR"
fi

if [ ! -d "$KAFKA_DIR" ]; then
    ln -s $JAVA_HOME/bin/jps /usr/bin/jps

    cp -R /vagrant/rpm/$KAFKA_FILE /usr/local/
    cd /usr/local
    tar -xzf $KAFKA_FILE
    echo "export KAFKA_HOME=$KAFKA_DIR" >> /etc/profile.d/kafka.sh
fi

. /etc/profile.d/kafka.sh

echo "KAFKA_HOME=$KAFKA_HOME"
