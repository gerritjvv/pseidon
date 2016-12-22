###########################
#
# Start the pseidon process
# e.g. pseidon-process.sh
#!/usr/bin/env bash


abspath=$(cd ${0%/*} && echo $PWD/${0##*/})
BIN_HOME=`dirname $abspath`

PSEIDON_HOME=/opt/pseidon

export CONF_DIR=$PSEIDON_HOME/conf

#source environment variables
SOURCE="/etc/sysconfig/pseidon"
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
export JAVA_HEAP="-Xmx4096m -Xms1024m -XX:MaxDirectMemorySize=2048M"
fi

if [ -z "$JAVA_GC" ]; then
export JAVA_GC="-XX:+UseCompressedOops -XX:+UseG1GC"
fi

if [ -z "$JAVA_OPTS" ]; then
export JAVA_OPTS="-Djava.library.path=$STREAMS_HOME/lib/native/Linux-amd64-64/"
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

cp=$($JAVA -classpath "$CLASSPATH" pseidon.getenv --get-cp | tail -n 1)

if [ -z "$cp" ]; then
cp="/opt/pseidon/lib/*"
fi

CLASSPATH="$CONF_DIR:$CONF_DIR/META_INF:$cp"

#profiling  -agentpath:/opt/yjp-2013-build-13048/bin/linux-x86-64/libyjpagent.so=port=8183,alloceach=1000,usedmem=90,onexit=memory,sampling

echo $CLASSPATH
$JAVA -server $JAVA_GC $JAVA_HEAP $JAVA_OPTS -classpath "$CLASSPATH" $CLIENT_CLASS $@
