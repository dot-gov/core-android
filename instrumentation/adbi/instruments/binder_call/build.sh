#!/bin/bash - 
#===============================================================================
#
#          FILE: build.sh
# 
#         USAGE: ./build.sh 
# 
#   DESCRIPTION: 
# 
#       OPTIONS: ---
#  REQUIREMENTS: ---
#          BUGS: ---
#         NOTES: ---
#        AUTHOR: zad (), e.placidi@hackingteam.com
#  ORGANIZATION: ht
#       CREATED: 06/03/2015 09:32:52 CET
#      REVISION:  ---
#===============================================================================
debug=""
echo "args $@"
while getopts ":dDP" opt; do
  case $opt in
    P) pie="PIE=1"
      echo "position Indipendent code ENABLED"
      ;;
    d) debug="DEBUG=1"
      echo "debug is on"
      ;;
    D) echo "debug is off"
      ;;
    \?) echo "Invalid option -$OPTARG"
      ;;
  esac
done

cdir=$(pwd)
cd ../base/
echo cleaning libs..
rm -r ./obj/* ./libs/*
ndk_cmd="ndk-build V=1 $debug $pie"
echo "Building base $ndk_cmd"
cd jni &&  ndk-build V=1 clean && $ndk_cmd
cd ${cdir}

echo cleaning libs..
rm -r ./obj/* ./libs/*
ndk_cmd="ndk-build V=1 $debug $pie"
echo "Building hijack $ndk_cmd"
cd jni &&  ndk-build V=1 clean && $ndk_cmd
cd ${cdir}

