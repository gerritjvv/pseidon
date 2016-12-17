#!/bin/bash
# chkconfig: 2345 20 80
# description: pseidon-hdfs
#

PATH=/usr/bin:/sbin:/bin:/usr/sbin
export PATH

OK_STAT=0
DEAD_STAT=1
UNKOWN_STAT=4

mkdir -p /var/lock/subsys

[ -f /etc/sysconfig/pseidon-hdfs ] && . /etc/sysconfig/pseidon-hdfs
lockfile=${LOCKFILE-/var/lock/subsys/pseidon-hdfs}
gcronsd="${GCRONSD-/opt/pseidon-hdfs/bin/watchdog.sh} /opt/pseidon-hdfs/conf/pseidon.edn"
gcronsd_stop="${GCRONSD-/opt/pseidon-hdfs/bin/process.sh} stop /opt/pseidon-hdfs/conf/pseidon.edn"

REGEX1="pseidon_hdfs.watchdog"
REGEX2="pseidon_hdfs.pseidon_hdfs"


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

    touch /opt/pseidon-hdfs/log/serverlog.log
    chmod -R 777 /opt/pseidon-hdfs/log/serverlog.log

    su - ${PSEIDON_USER:-pseidon} -l -m -c "exec $gcronsd < /dev/null >> /opt/pseidon-hdfs/log/serverlog.log 2>&1 &"

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
    echo -n "Stopping pseidon-hdfs: "

    su - ${PSEIDON_USER:-pseidon} -l -m -c "exec $gcronsd_stop < /dev/null >> /opt/pseidon-hdfs/log/serverlog.log 2>&1 &"
    kill_proc
      
    counter=0
    while [ $counter -lt 30 ]
    do
      status
      RETVAL=$?
      [ "$RETVAL" = $DEAD_STAT ] && break
      sleep 1s
      counter=$(( counter + 1 ))

      [ $counter -gt 3 ] && [ $RETVAL = $OK_STAT ] && kill_proc
    done

    [ $RETVAL = $DEAD_STAT ] && rm -f ${lockfile} && echo "OK"
  else
    echo "No pseidon-hdfs instance is running"
    RETVAL=$DEAD_STAT
  fi

  return $RETVAL
}

restart() {
  stop
  start
}

kill_proc() {
  pkill -u ${PSEIDON_USER:-pseidon} -f "$REGEX1"
}

status() {
  if pgrep -f "$REGEX1" >/dev/null; then
    RETVAL=$OK_STAT
  else
    if pgrep -f "$REGEX2" >/dev/null; then
      RETVAL=$OK_STAT
    else
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
  exit $RETVAL
  ;;
*)
  echo $"Usage: $0 {start|stop|status|restart}"
  exit $DEAD_STAT
  ;;
esac

exit $OK_STAT

