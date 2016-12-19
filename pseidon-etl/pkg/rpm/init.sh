#!/usr/bin/env bash
# chkconfig: 2345 20 80
# description: pseidon
#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
PATH=/usr/bin:/sbin:/bin:/usr/sbin
export PATH

OK_STAT=0
DEAD_STAT=1
WATCH_DOG_LIVE_ETL_SERVICE_DEAD_STAT=2
WATCH_DOG_DEAD_ETL_SERVICE_LIVE_STAT=3
UNKOWN_STAT=4

mkdir -p /var/lock/subsys

[ -f /etc/sysconfig/pseidon-etl ] && . /etc/sysconfig/pseidon-etl
lockfile=${LOCKFILE-/var/lock/subsys/pseidon-etl}
gcronsd="${GCRONSD-/opt/pseidon-etl/bin/watchdog.sh} /opt/pseidon-etl/conf/pseidon.edn"
gcronsd_stop="${GCRONSD-/opt/pseidon-etl/bin/process.sh} stop /opt/pseidon-etl/conf/pseidon.edn"

REGEX1="pseidon_etl.watchdog"
REGEX2="pseidon_etl.pseidon_etl"


RETVAL=0
ISDAEMON=0
# Source function library.

FUNCTIONS="/etc/rc.d/init.d/functions"
[ -f $FUNCTIONS ] && . $FUNCTIONS && ISDAEMON=$(grep "daemon()" /etc/rc.d/init.d/functions  | wc -l)

JAVASH="/etc/profile.d/java.sh"
[ -f $JAVASH ] && . $JAVASH

if [ -z "$JAVA_HOME" ]; then
  echo "JAVA_HOME not set, using /usr/java/latest" >&2
  export JAVA_HOME="/usr/java/latest"
fi

start() {
  touch $lockfile

  status
  RETVAL=$?

  if [ $RETVAL = $OK_STAT ]; then
    echo "The pseidon is already running"
    RETVAL=$OK_STAT
  else
    echo -n $"Starting pseidon: "

    touch /opt/pseidon-etl/log/serverlog.log
    chmod -R 777 /opt/pseidon-etl/log/serverlog.log

    su - ${PSEIDON_USER:-pseidon} -l -m -c "exec $gcronsd < /dev/null >> /opt/pseidon-etl/log/serverlog.log 2>&1 &"

    counter=0
    while [ $counter -lt 30 ]
    do
        status
        RETVAL=$?
        [ "$RETVAL" = $OK_STAT ] && break
        sleep 1s
        counter=$(( counter + 1 ))
    done

  fi

  [ $RETVAL = $OK_STAT ] && echo " OK"
  [ $RETVAL = $DEAD_STAT ] && echo " FAILED"
  return $RETVAL
}

stop() {
  status
  RETVAL=$?

  if [ $RETVAL = $OK_STAT ]; then
    echo -n "Stopping pseidon-etl: "

    su - ${PSEIDON_USER:-pseidon} -l -m -c "exec $gcronsd_stop < /dev/null >> /opt/pseidon-etl/log/serverlog.log 2>&1 &"
    sleep 5s
    kill_proc
      
    counter=0
    while [ $counter -lt 100 ]
    do
      status
      RETVAL=$?
      echo "Waiting for shutdown $RETVAL"
      [ "$RETVAL" = $DEAD_STAT ] && break
      sleep 5s
      counter=$(( counter + 1 ))
      
      [ $counter -gt 3 ] && [ $RETVAL = $OK_STAT ] && kill_proc
    done

    [ $RETVAL = $DEAD_STAT ] && rm -f ${lockfile} && echo "OK"
  else
    echo "No pseidon instance is running" 
    RETVAL=$DEAD_STAT
  fi

  return $RETVAL
}

restart() {
  stop
  start
}

kill_proc() {
 pkill -15 -u ${PSEIDON_USER:-pseidon} -f "$REGEX1"
 pkill -15 -u ${PSEIDON_USER:-pseidon} -f "$REGEX2"
}

status() {
  if pgrep -f "$REGEX1" >/dev/null; then
    if pgrep -f "$REGEX2" >/dev/null; then
        #both services are running
        RETVAL=$OK_STAT
    else
        RETVAL=$WATCH_DOG_LIVE_ETL_SERVICE_DEAD_STAT
    fi
  else
    #watchdog is not running
    if pgrep -f "$REGEX2" >/dev/null; then
      RETVAL=$WATCH_DOG_DEAD_ETL_SERVICE_LIVE_STAT
    else
      #watchdog and etl service is dead
      RETVAL=$DEAD_STAT
    fi
  fi

  return $RETVAL
}

case "$1" in
start)
  start
  exit $?
  ;;
stop)
  stop
  RETVAL=$?
  if [ $RETVAL = $DEAD_STAT ]; then
    exit 0
  else
    exit 1
  fi
  ;;
restart)
  stop || exit $?
  start
  exit $?
  ;;
status)
  status
  RETVAL=$?
  [ $RETVAL = $OK_STAT ] && echo "Running"
  [ $RETVAL = $DEAD_STAT ] && echo "Stopped"
  [ $RETVAL = $WATCH_DOG_DEAD_ETL_SERVICE_LIVE_STAT ] && echo "Watchdog dead but etl service is dead"
  [ $RETVAL = $WATCH_DOG_LIVE_ETL_SERVICE_DEAD_STAT ] && echo "Watchdog live but etl service is dead"

  exit $RETVAL
  ;;
*)
  echo $"Usage: $0 {start|stop|status|restart}"
  exit $DEAD_STAT
  ;;
esac

exit $OK_STAT



