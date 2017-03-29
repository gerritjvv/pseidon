###########################
#
# Start the pseidon-hdfs manager that will run a monitor the pseidon-hdfs process
#!/usr/bin/env bash


abspath=$(cd ${0%/*} && echo $PWD/${0##*/})
BIN_HOME=`dirname $abspath`

if [ -z "$PSEIDON_HOME" ]; then
 PSEIDON_HOME="${BIN_HOME}/.."
fi

export CONF_DIR=$PSEIDON_HOME/conf

#source environment variables
SOURCE="/etc/sysconfig/pseidon-hdfs-watchdog"
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
export JAVA_HEAP="-Xmx2048m -Xms1024m -XX:MaxDirectMemorySize=2048M"
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

CLIENT_CLASS="pseidon_hdfs.watchdog"
CLASSPATH=$CONF_DIR:$CLASSPATH

echo $CLASSPATH
$JAVA -server $JAVA_GC $JAVA_HEAP $JAVA_OPTS -classpath "$CLASSPATH" $CLIENT_CLASS $@

exit 0
