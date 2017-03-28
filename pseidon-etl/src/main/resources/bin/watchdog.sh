###########################
#
# Start the pseidon-etl manager that will run a monitor the pseidon-etl process
#
# Environment configuration
#   edit the file /etc/sysconfig/pseidon-etl-watchdog with the variables:
#   JAVA_HOME, JAVA_HEAP, JAVA_GC
#
#!/usr/bin/env bash


abspath=$(cd ${0%/*} && echo $PWD/${0##*/})
BIN_HOME=`dirname $abspath`

if [ -z "$PSEIDON_HOME" ]; then
 DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
 PSEIDON_HOME="${DIR}../"
fi

export CONF_DIR=$PSEIDON_HOME/conf

#source environment variables
SOURCE="/etc/sysconfig/pseidon-etl-watchdog"
test -f $SOURCE && source $SOURCE

# some Java parameters
if [ "$JAVA_HOME" != "" ]; then
#echo "run java in $JAVA_HOME"
JAVA_HOME=$JAVA_HOME
fi

if [ "$JAVA_HOME" = "" ]; then
echo "Error: JAVA_HOME is not set."
exit 1
fi

JAVA=$JAVA_HOME/bin/java


if [ -z "$JAVA_HEAP" ]; then
export JAVA_HEAP="-Xmx1024m"
fi

if [ -z "$JAVA_GC" ]; then
export JAVA_GC="-XX:+UseCompressedOops -XX:+UseG1GC"
fi

# check envvars which might override default args
# CLASSPATH initially contains $CONF_DIR
CLASSPATH=${CLASSPATH}:$JAVA_HOME/lib/tools.jar

# so that filenames w/ spaces are handled correctly in loops below
# add libs to CLASSPATH.
for f in $PSEIDON_HOME/lib/*.jar; do
CLASSPATH=${CLASSPATH}:$f;
done

CLIENT_CLASS="pseidon_etl.watchdog"
CLASSPATH=$CONF_DIR:$CLASSPATH

## Note we do not user -server here because this process is managing the process.sh process
## which is the server, and watchdog.sh should rather have a quicker startup time
$JAVA $JAVA_GC $JAVA_HEAP $JAVA_OPTS -classpath "$CLASSPATH" $CLIENT_CLASS $@

exit 0
