#!/usr/bin/env bash
########################################################################################
#                   Provision HDFS mr1 HDSF ONLY One Node Cluster
#
# Note:
#      This is for testing pseidon-hdfs
#      Only Namenode and DataNode are started, no Secondary Namenode is used.
########################################################################################

#reference: https://www.cloudera.com/documentation/enterprise/5-8-x/topics/cdh_ig_cdh5_install.html
NODE="$1"

sudo /vagrant/vagrant/scripts/init.sh

/vagrant/vagrant/scripts/cdh_base.sh

function install_cdh_hdfs {

 sudo apt-get install -y vim

 ## package install will try to run unconfigured instances
 sudo DEBIAN_FRONTEND=noninteractive apt-get install -y --force-yes hadoop-hdfs-journalnode hadoop-hdfs-namenode hadoop-hdfs-datanode

 echo "Ignore HDFS startup failures"
 ## stop jour
 sudo service hadoop-hdfs-journalnode stop

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
     sudo chmod -R 777 ${CLUSTER_DIR}/*.xml
     sudo cat "${VAGRANT_HDFS_CONF_DIR}/core-site.xml" > "${CLUSTER_DIR}/core-site.xml"
     sudo sed -e "s/\${node}/hdfs${NODE}/"  "${VAGRANT_HDFS_CONF_DIR}/hdfs-site.xml" > "${CLUSTER_DIR}/hdfs-site.xml"

     sudo cp /vagrant/vagrant/keytabs/hdfs${NODE}/hdfs${NODE}.keytab "${CLUSTER_DIR}/hdfs.keytab"
     sudo chown hdfs:hadoop "${CLUSTER_DIR}"/hdfs.keytab
     sudo chmod 400 "${CLUSTER_DIR}"/*.keytab

     NAME_DIR="/var/local/hdfs/name"
     DATA_DIR="/var/local/hdfs/data"
     JOURNAL_DIR="/var/local/hdfs/journal"

     sudo mkdir -p "${NAME_DIR}"
     sudo mkdir -p "${DATA_DIR}"
     sudo mkdir -p "${JOURNAL_DIR}"

     sudo chown -R hdfs:hdfs "${NAME_DIR}" "${DATA_DIR}" "${JOURNAL_DIR}"
     sudo chmod 700 "${NAME_DIR}"


     sudo chmod -R 777 /etc/default/hadoop-hdfs-datanode
     cat > /etc/default/hadoop-hdfs-datanode << EOF

        export HADOOP_SECURE_DN_USER=hdfs
        export HADOOP_SECURE_DN_PID_DIR=/var/lib/hadoop-hdfs
        export HADOOP_SECURE_DN_LOG_DIR=/var/log/hadoop-hdfs
        export JSVC_HOME=/usr/lib/bigtop-utils/
EOF

     sudo rm -rf "${DATA_DIR}/current"


     if [ "$NODE" -eq "1" ]; then
       sudo service hadoop-hdfs-journalnode restart
     fi


     yes | sudo -u hdfs hdfs namenode -format


 else
     echo "CDH is already configured"
 fi
}

install_cdh_hdfs
configure_cdh_hdfs

if [ "$NODE" -eq "1" ]; then
       service hadoop-hdfs-journalnode restart
fi

sudo service hadoop-hdfs-namenode restart
sudo service hadoop-hdfs-datanode restart

sudo -u hdfs kinit -kt /etc/hadoop/conf/hdfs.keytab hdfs/hdfs${NODE}.hdfs-pseidon@HDFS-PSEIDON
sudo -u hdfs hadoop fs -mkdir /tmp
sudo -u hdfs hadoop fs -chmod -R 1777 /tmp



