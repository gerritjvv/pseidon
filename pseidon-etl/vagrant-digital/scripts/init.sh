#!/bin/bash
########################################################################
################# Install Java                    ######################
#################  called from Vagrantfile        ######################
########################################################################


### Box precise64

## from http://serverfault.com/questions/500764/dpkg-reconfigure-unable-to-re-open-stdin-no-file-or-directory
export DEBIAN_FRONTEND=noninteractive


KAFKA="$1"


## Install java
### From https://rais.wordpress.com/2015/03/16/setting-up-a-vagrant-java-8-environment/

sudo apt-get update -y
sudo apt-get install -y software-properties-common python-software-properties
sudo add-apt-repository -y ppa:webupd8team/java

## update packages
sudo apt-get update -y


## required to auto accept the license
## from http://stackoverflow.com/questions/19275856/auto-yes-to-the-license-agreement-on-sudo-apt-get-y-install-oracle-java7-instal
echo debconf shared/accepted-oracle-license-v1-1 select true | sudo debconf-set-selections
echo debconf shared/accepted-oracle-license-v1-1 seen true | sudo debconf-set-selections

## install java 8
sudo apt-get install -y oracle-java8-installer
sudo apt-get install -y oracle-java8-set-default

## setup java home
JAVA_HOME=/usr/lib/jvm/java-8-oracle/

if ! grep -q -F 'JAVA_HOME' /etc/profile.d/java.sh; then
 echo "Setting up java home to $JAVA_HOME"
 sudo bash -c "echo export JAVA_HOME=$JAVA_HOME >> /etc/profile.d/java.sh"

fi


. /etc/profile.d/java.sh

echo "JAVA_HOME=$JAVA_HOME"

echo done installing jdk