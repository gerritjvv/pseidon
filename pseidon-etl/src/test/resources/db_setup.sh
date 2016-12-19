#!/usr/bin/env bash

USER=$1

DIR=$(dirname $0)

echo "Recreating database testdb"
mysql -u$USER  -e "drop database if exists testdb; create database testdb";

echo "Creating schemas"

cat $DIR/db.sql | mysql -u$USER testdb

echo "Creating user"

mysql -u$USER -e "CREATE USER 'pseidon'@'localhost' IDENTIFIED BY 'pseidon'; GRANT ALL PRIVILEGES ON testdb.* TO 'pseidon'@'localhost';"
echo "DONE"
