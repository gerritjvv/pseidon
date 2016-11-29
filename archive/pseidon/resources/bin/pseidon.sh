###########################
#
# Start the pseidon manager that will run a monitor the pseidon process
# e.g. pseidon.sh --managed
#!/usr/bin/env bash


abspath=$(cd ${0%/*} && echo $PWD/${0##*/})
BIN_HOME=`dirname $abspath`

PSEIDON_HOME=/opt/pseidon

export CONF_DIR=$PSEIDON_HOME/conf

#source environment variables
if [ "$1" != "--managed" ]; then
 SOURCE="/etc/sysconfig/pseidon"
 test -f $SOURCE && source $SOURCE
fi

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

CLIENT_CLASS="pseidon.core"
CLASSPATH=$CONF_DIR:$CONF_DIR/META-INF:$CLASSPATH

CLASSPATH="$CLASSPATH:$CONF_DIR:$CONF_DIR/META_INF"

echo $CLASSPATH
$JAVA -server $JAVA_GC $JAVA_HEAP $JAVA_OPTS -classpath "$CLASSPATH" $CLIENT_CLASS $@

if [ "$1" == "--stop" ];then
 for i in $(ps -aux | grep 'pseidon.core' | awk '{print $2}'); do
  kill $i &> /dev/null
 done
fi

exit 0
 