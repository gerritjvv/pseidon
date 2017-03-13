#!/usr/bin/env bash
###
### These:
###   client

function install_mvn {
    echo "installing maven"
    sudo apt-get install -y maven

    touch ~/.m2/settings.xml
    cat /vagrant/pseidon-etl/vagrant-digital/config/mvn-settings.xml > ~/.m2/settings.xml

}

function install_mysql {
    echo "installing mysql"

    sudo apt-get -y install debconf-utils
    sudo debconf-set-selections <<< 'mysql-server mysql-server/root_password password pseidon'
    sudo debconf-set-selections <<< 'mysql-server mysql-server/root_password_again password pseidon'
    sudo apt-get -y install mysql-server
    echo "CREATE USER 'pseidon'@'localhost' IDENTIFIED BY 'pseidon'" | mysql -uroot -p'pseidon'
    echo "GRANT ALL PRIVILEGES ON * . * TO 'pseidon'@'localhost'" | mysql -uroot -p'pseidon'

    cat /vagrant/pseidon-etl/vagrant-digital/config/db.sql | mysql -uroot -p'pseidon'
}

function deploy_pseidon {


  SRV1=$1
  BRK1=$2
  BRK2=$3
  BRK3=$4

  touch ~/.m2/settings.xml
  cat /vagrant/pseidon-etl/vagrant-digital/config/mvn-settings.xml > ~/.m2/settings.xml

  cd /vagrant/

  mvn -pl '!pseidon-hdfs' clean install -DskipTests=true || exit -1


  groupadd pseidon
  useradd -g pseidon pseidon

  #install the debian package
  find -L /vagrant/pseidon-etl/target -iname "*.deb" | xargs dpkg -i || exit -1


  sed "s;{redis};${SRV1};g" /vagrant/pseidon-etl/vagrant-digital/config/pseidon.edn |\
   sed "s;{brk1};${BRK1};g" |\
   sed "s;{brk2};${BRK2};g" |\
   sed "s;{brk3};${BRK3};g" \
       > /opt/pseidon-etl/conf/pseidon.edn

  chown -R pseidon:pseidon /opt/pseidon-etl

}


if [ -z "$3" ]; then
 echo "Type <services1-ip1> <broker1-ip> <broker2-ip> <broker3-ip>"
 exit -1
fi

SRV1=$1
BRK1=$2
BRK2=$3
BRK3=$4



#install_mvn
#install_mysql

deploy_pseidon $SRV1 $BRK1 $BRK2 $BRK3