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
#       CREATED: 23/02/2015 10:06:27 CET
#      REVISION:  ---
#===============================================================================

# -d specify where to place the compiled class
# --classpath path to externa jar and class

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

echo cleaning libs..
rm -r ./obj/* ./libs/*
ndk_cmd="ndk-build V=1 $debug $pie"
echo "Building hijack $ndk_cmd"
cd jni &&  ndk-build V=1 clean && $ndk_cmd
cd ${cdir}
