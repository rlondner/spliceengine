#!/bin/bash

mvn exec:exec -Dzoo > zoo.log &

if ps ax | grep -v grep | grep 'ZooKeeperServerMain' > /dev/null
then
 sleep 15
else
 echo "zoo is not running!!"
fi
