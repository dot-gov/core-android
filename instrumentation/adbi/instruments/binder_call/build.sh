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
deflib="libbhelp.so"
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
cd ../base/
if [ $CLEAN -eq 1 ]
then
  echo cleaning libs..
  rm -r ./obj/* ./libs/*
fi
ndk_cmd="ndk-build V=1 $debug $pie"
echo "Building base $ndk_cmd"
cd jni
if [ $CLEAN -eq 1 ]
then
  ndk-build V=1 clean
fi
$ndk_cmd
cd ${cdir}

echo cleaning libs..
rm -r ./obj/* ./libs/*
ndk_cmd="ndk-build V=1 $debug $pie"
echo "Building hijack $ndk_cmd"
cd jni
if [ $CLEAN -eq 1 ]
then
  ndk-build V=1 clean
fi
$ndk_cmd
cd ${cdir}

