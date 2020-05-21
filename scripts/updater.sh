#!/bin/bash -e

if [ -z "$3" ]
then
  echo "Use: $0 instanceId services distribDirectoryUrl"
  exit 1
fi

instanceId=$1
services=$2

updateService=updater

distribDirectoryUrl=$3

. ./update.sh

runService runServices "clientDirectoryUrl=${distribDirectoryUrl}" "instanceId=${instanceId}" "services=${services}"
