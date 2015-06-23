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
deflib="hijack"
debug=""
echo "args $@"
CLEAN=0
while getopts ":dDPc" opt; do
  case $opt in
    P) pie="PIE=1"
      echo "position Indipendent code ENABLED"
      ;;
    d) debug="DEBUG=1"
      echo "debug is on"
      ;;
    c) CLEAN=1
      echo "force clean is on"
      ;;
    D) echo "debug is off"
      ;;
    \?) echo "Invalid option -$OPTARG"
      ;;
  esac
done
if [ $CLEAN -eq 0 ]
then
  echo "checking $(pwd)/libs/*/$deflib"
  if [ -e $(pwd)/libs/*/$deflib ]
  then
    echo "nothing to do ... $deflib already present"
    exit 0
  fi
fi
cdir=$(pwd)
if [ $CLEAN -eq 1 ]
then
  echo cleaning libs..
  rm -r ./obj/* ./libs/*
fi
ndk_cmd="ndk-build V=1 $debug $pie"
echo "Building hijack $ndk_cmd"
cd jni
if [ $CLEAN -eq 1 ]
then
  ndk-build V=1 clean
fi
$ndk_cmd
cd ${cdir}
