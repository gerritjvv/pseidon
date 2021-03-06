###########################
#
# Start the pseidon-hdfs process
# e.g. pseidon-process.sh
#!/usr/bin/env bash


abspath=$(cd ${0%/*} && echo $PWD/${0##*/})
BIN_HOME=`dirname $abspath`


PSEIDON_HOME=/opt/pseidon-hdfs

export CONF_DIR=$PSEIDON_HOME/conf

#source environment variables
SOURCE="/etc/sysconfig/pseidon-hdfs"
test -f $SOURCE && source $SOURCE

if [ "$JAVA_HOME" = "" ]; then
echo "Error: JAVA_HOME is not set."
exit 1
fi

JAVA=$JAVA_HOME/bin/java

if [ ! -f "$JAVA" ]; then
 echo "JAVA_HOME is not correctly configured in $SOURCE, searching for java"
 ## try to find java

 for java_exec in $(find /usr/lib/jvm  -type f -executable -name 'java' | head -n 1);
 do

    JAVA="$java_exec"
    JAVA_HOME="`dirname $java_exec`/../"

    echo "USING $JAVA"
    echo "JAVA_HOME $JAVA_HOME"
 done

fi

if [ -z "$JAVA_HEAP" ]; then
export JAVA_HEAP="-Xmx4096m -Xms1024m -XX:MaxDirectMemorySize=2048M"
fi

if [ -z "$JAVA_GC" ]; then
export JAVA_GC="-XX:+UseCompressedOops -XX:+UseG1GC"
fi

if [ -z "$JAVA_OPTS" ]; then
export JAVA_OPTS=""
fi


# check envvars which might override default args
# CLASSPATH initially contains $CONF_DIR
CLASSPATH=${CLASSPATH}:$JAVA_HOME/lib/tools.jar

# so that filenames w/ spaces are handled correctly in loops below
# add libs to CLASSPATH.
for f in $PSEIDON_HOME/lib/*.jar; do
CLASSPATH=${CLASSPATH}:$f;
done

CLIENT_CLASS="pseidon_hdfs.pseidon_hdfs"
CLASSPATH=$CONF_DIR:$CONF_DIR/META-INF:$CLASSPATH

#profiling  -agentpath:/opt/yjp-2013-build-13048/bin/linux-x86-64/libyjpagent.so=port=8183,alloceach=1000,usedmem=90,onexit=memory,sampling

$JAVA -server $JAVA_GC $JAVA_HEAP $JAVA_OPTS -classpath "$CLASSPATH" $CLIENT_CLASS $@
