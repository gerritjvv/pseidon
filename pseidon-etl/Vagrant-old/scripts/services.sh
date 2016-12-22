#!/usr/bin/env bash
### Setup all aux services required by the pseidon-etl2 app.
### These:
###   mysql, redis

yum install -y wget

### Setup mysql


yum -y install mariadb-server mariadb

systemctl start mariadb.service
systemctl enable mariadb.service


if [ ! -f /var/log/databasesetup ];
then
    echo ">>> Configuring MySQL Server"
    echo "CREATE USER 'dwadmin'@'localhost' IDENTIFIED BY 'dwadmin'" | mysql
    echo "CREATE DATABASE IF NOT EXISTS testdb" | mysql
    echo "GRANT ALL ON *.* TO 'dwadmin'@'localhost'" | mysql
    echo "FLUSH PRIVILEGES" | mysql

    echo "CREATE USER 'dwadmin'@'%' IDENTIFIED BY 'dwadmin'" | mysql
    echo "GRANT ALL ON *.* TO 'dwadmin'@'%'" | mysql

    echo "FLUSH PRIVILEGES" | mysql


    cat /vagrant/src/test/resources/db.sql | mysql "testdb"
    touch /var/log/databasesetup
fi

chkconfig mysqld on

echo "installed mysql login using  mysql -h192.168.4.10 -udwadmin -p'dwadmin'"


# install redis
echo "Installing redis"

if [ ! -f /var/log/redisinstall.log ];
then


 cd /root/
 wget http://download.redis.io/releases/redis-2.8.14.tar.gz
 tar -xzf redis-2.8.14.tar.gz
 mv /root/redis-2.8.14 /opt/
 cd /opt/redis-2.8.14
 make

 id -u somename &>/dev/null || useradd redis
 ln -s /opt/redis-2.8.14/src/redis-cli /usr/bin/

 if [ ! -f /etc/init.d/redis ]; then
  cp /vagrant/vagrant/config/redis-init /etc/init.d/redis
 fi

 mkdir -p /var/log/redis-dat
 chmod -R 777 /var/log/redis-dat

 touch /var/log/redisinstall.log
fi

if [ -f /etc/redis.conf ];
then
 mv /etc/redis.conf /etc/redis.conf-old
fi

chmod +x /etc/init.d/redis

cp /vagrant/vagrant/config/redis.conf /etc/
chkconfig redis on
service redis restart

