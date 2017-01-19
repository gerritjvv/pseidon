#!/usr/bin/env bash
########################################################################################
#                   Install the CDH repos and shared resources between
#                   the hdfs1 and client1 instances, see hdfs1.sh
########################################################################################


function install_cdh_repo {


  CLOUDERA_LIST="/etc/apt/sources.list.d/cloudera.list"

  if [ ! -f "${CLOUDERA_LIST}" ]; then

      #use for latest cdh
      #sudo wget 'https://archive.cloudera.com/cdh5/ubuntu/precise/amd64/cdh/cloudera.list' -O "${CLOUDERA_LIST}"

      #use for 5.7.0
      echo "deb [arch=amd64] https://archive.cloudera.com/cdh5/ubuntu/precise/amd64/cdh precise-cdh5.7.0 contrib" > /etc/apt/sources.list.d/cloudera.list

      sudo apt-get update

      wget https://archive.cloudera.com/cdh5/ubuntu/precise/amd64/cdh/archive.key -O archive.key
      sudo apt-key add archive.key
  fi

}

install_cdh_repo

sudo apt-get install -y --force-yes hadoop-client