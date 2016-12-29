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

function install_cdh_hdfs {

 sudo apt-get install -y --force-yes hadoop-hdfs-namenode hadoop-hdfs-datanode

}

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

     sudo mv /vagrant/vagrant/keytabs/hdfs1/hdfs.keytab "${CLUSTER_DIR}/"
     sudo chown hdfs:hadoop "${CLUSTER_DIR}"/hdfs.keytab
     sudo chmod 400 "${CLUSTER_DIR}"/*.keytab

     NAME_DIR="/var/local/hdfs/name"
     DATA_DIR="/var/local/hdfs/data"

     sudo mkdir -p "${NAME_DIR}"
     sudo mkdir -p "${DATA_DIR}"

     sudo chown -R hdfs:hdfs "${NAME_DIR}" "${DATA_DIR}"
     sudo chmod 700 "${NAME_DIR}"


     cat > /etc/default/hadoop-hdfs-datanode << EOF

        export HADOOP_SECURE_DN_USER=hdfs
        export HADOOP_SECURE_DN_PID_DIR=/var/lib/hadoop-hdfs
        export HADOOP_SECURE_DN_LOG_DIR=/var/log/hadoop-hdfs
        export JSVC_HOME=/usr/lib/bigtop-utils/
EOF

     sudo rm -rf "${DATA_DIR}/current"
     service hadoop-hdfs-namenode start

     yes | sudo -u hdfs hdfs namenode -format



 else
     echo "CDH is already configured"
 fi
}

install_cdh_hdfs
configure_cdh_hdfs


sudo service hadoop-hdfs-namenode restart
sudo service hadoop-hdfs-datanode restart

sudo -u hdfs kinit -kt /etc/hadoop/conf/hdfs.keytab hdfs/hdfs1.hdfs-pseidon@HDFS-PSEIDON
sudo -u hdfs hadoop fs -mkdir /tmp
sudo -u hdfs hadoop fs -chmod -R 1777 /tmp
