#!/usr/bin/env bash
###
### These:
###   redis

# install redis
echo "Installing redis"

TYPE="$1"


  if [ ! -f /var/log/redisinstall.log ];
 then

  apt-get update
  apt-get -y install make gcc daemon tcl8.5

  cd /root/
  wget http://download.redis.io/releases/redis-2.8.14.tar.gz
  tar -xzf redis-2.8.14.tar.gz
  mv /root/redis-2.8.14 /opt/

  cd /opt/redis-2.8.14/deps
  make hiredis jemalloc linenoise lua geohash-int

  cd /opt/redis-2.8.14
  make

  id -u somename &>/dev/null || useradd redis
  ln -s /opt/redis-2.8.14/src/redis-cli /usr/bin/

  if [ ! -f /etc/init.d/redis ]; then
   cp /vagrant/config/redis-init /etc/init.d/redis
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

 cp /vagrant/config/redis.conf /etc/
 /etc/init.d/redis restart

