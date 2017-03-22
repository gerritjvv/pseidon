#!/usr/bin/env bash


################################################################################
##### called from services.sh
##### installs and runs the avro registry on the services1 vm ##################
##### see http://docs.confluent.io/1.0/installation.html#installation-apt ######
################################################################################

if [ -z "$1" ]; then
    echo "type <zookeeper-host>"
    exit -1
fi

ZOOKEEPER="$1"

HOST="0.0.0.0"

wget -qO - http://packages.confluent.io/deb/1.0/archive.key | sudo apt-key add -

sudo add-apt-repository "deb [arch=all] http://packages.confluent.io/deb/1.0 stable main"

sudo apt-get update && sudo apt-get install -y confluent-schema-registry


sudo cat /vagrant/config/schema-registry.properties | \
          sed "s;{hostname};${HOST};g" | \
          sed "s;{zookeeper};${ZOOKEEPER};g" > \
          /etc/schema-registry/schema-registry.properties


sudo nohup schema-registry-start /etc/schema-registry/schema-registry.properties &> /var/log/schemaregistry.log&

## check that the registry is open and listening on port 8081

echo "waiting for schema registry"

timeout 60 bash -c 'until echo > /dev/tcp/localhost/8081 ; do sleep 2; done' || exit -1

echo "Launched schema registry on port 8081"