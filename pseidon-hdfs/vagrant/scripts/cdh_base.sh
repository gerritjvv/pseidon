#!/usr/bin/env bash
########################################################################################
#                   Install the CDH repos and shared resources between
#                   the hdfs1 and client1 instances, see hdfs1.sh
########################################################################################


function install_cdh_repo {


  CLOUDERA_LIST="/etc/apt/sources.list.d/cloudera.list"

  if [ ! -f "${CLOUDERA_LIST}" ]; then
      sudo wget 'https://archive.cloudera.com/cdh5/ubuntu/precise/amd64/cdh/cloudera.list' -O "${CLOUDERA_LIST}"

      sudo apt-get update

      wget https://archive.cloudera.com/cdh5/ubuntu/precise/amd64/cdh/archive.key -O archive.key
      sudo apt-key add archive.key
  fi

}

install_cdh_repo

sudo apt-get install -y --force-yes hadoop-client