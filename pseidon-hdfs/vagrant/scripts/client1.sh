#!/usr/bin/env bash
########################################################################################
#                   Provision HDFS mr1 HDSF ONLY One Node Cluster
#
# Note:
#      This is for testing pseidon-hdfs
#      Only Namenode and DataNode are started, no Secondary Namenode is used.
########################################################################################

#reference: https://www.cloudera.com/documentation/enterprise/5-8-x/topics/cdh_ig_cdh5_install.html


/vagrant/vagrant/scripts/init.sh

/vagrant/vagrant/scripts/cdh_base.sh

function configure_cdh_hdfs {

 ## using my_cluster as default cluster name

 CLUSTER_DIR="/etc/hadoop/conf.my_cluster"

 if [ ! -d "${CLUSTER_DIR}" ]; then
     sudo cp -r /etc/hadoop/conf.empty "${CLUSTER_DIR}"

     sudo update-alternatives --install /etc/hadoop/conf hadoop-conf "${CLUSTER_DIR}" 50
     sudo update-alternatives --set hadoop-conf "${CLUSTER_DIR}"
     sudo update-alternatives --display hadoop-conf

     sudo cp /vagrant/vagrant/config/krb5.conf /etc/

     VAGRANT_HDFS_CONF_DIR="/vagrant/vagrant/config/hdfs"
     sudo cat "${VAGRANT_HDFS_CONF_DIR}/core-site.xml" > "${CLUSTER_DIR}/core-site.xml"
     sudo cat "${VAGRANT_HDFS_CONF_DIR}/hdfs-site.xml" > "${CLUSTER_DIR}/hdfs-site.xml"

     sudo mv /vagrant/vagrant/keytabs/client1/hdfs.keytab "${CLUSTER_DIR}/"
     sudo chown hdfs:hadoop "${CLUSTER_DIR}"/hdfs.keytab
     sudo chmod 400 "${CLUSTER_DIR}"/*.keytab

     NAME_DIR="/var/local/hdfs/name"
     DATA_DIR="/var/local/hdfs/data"

     sudo mkdir -p "${NAME_DIR}"
     sudo mkdir -p "${DATA_DIR}"

     sudo chown -R hdfs:hdfs "${NAME_DIR}" "${DATA_DIR}"
     sudo chmod 700 "${NAME_DIR}"


 else
     echo "CDH is already configured"
 fi
}


function install_mvn {

 sudo apt-get -y install maven

}

function install_setup_db {
    export DEBIAN_FRONTEND=noninteractive
    sudo debconf-set-selections <<< 'mysql-server mysql-server/root_password password PASS'
    sudo debconf-set-selections <<< 'mysql-server mysql-server/root_password_again password PASS'
    sudo apt-get install -y mysql-server
    mysql -uroot -pPASS -e "SET PASSWORD = PASSWORD('');"

    mysql -e "create database if not exists pseidon"

    mysql -e "CREATE USER 'pseidon'@'localhost' IDENTIFIED BY 'pseidon'"
    mysql -e "GRANT ALL PRIVILEGES ON *.* TO 'pseidon'@'localhost' WITH GRANT OPTION"

   mysql -u pseidon -p'pseidon' pseidon << "EOF"
  CREATE TABLE `pseidon_logs` (
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
  `dateformat` varchar(200) DEFAULT "datehour",
  `log_group` varchar(100) DEFAULT 'default',
  `enabled` tinyint(1) DEFAULT 1,
  PRIMARY KEY (`log`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1
EOF

}

configure_cdh_hdfs

install_mvn
install_setup_db