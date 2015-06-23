#!/bin/bash - 
#===============================================================================
#
#          FILE: build-dex.sh
# 
#         USAGE: ./build-dex.sh 
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

output="perfAcc"
debug=""
pie=""
lib=""
deflib="libsmsdispatch"
CLEAN=0
echo "args $@"
while getopts ":dDo:l:Pc" opt; do
  case $opt in
    P) pie="PIE=1"
      echo "position Indipendent code ENABLED"
      ;;
    d) debug="DEBUG=1"
      echo "debug is on"
      ;;
    D) echo "debug is off"
      ;;
    c) CLEAN=1
      echo "force clean is on"
      ;;
    l) lib="DESTLIB=$OPTARG"
      deflib="lib$OPTARG"
      echo "overwriting outputso to $OPTARG"
      ;;
    o) output="$OPTARG"
      echo "overwriting output jar to $output"
      ;;
    \?) echo "Invalid option -$OPTARG"
      ;;
  esac
done
if [ $CLEAN -eq 0 ]
then
  echo "checking $(pwd)/libs/*/$deflib.so "
  if [ -e $(pwd)/libs/*/$deflib.so ]
  then
    echo "nothing to do ... $deflib.so already present"
    exit 0
  fi
fi
cdir=$(pwd)
if [ $CLEAN -eq 1 ]
then
  echo cleaning libs..
  rm -r ./obj/* ./libs/*
fi
ndk_cmd="ndk-build V=1 $debug $lib $pie"
echo Building base
cd ../../../adbi/instruments/base/jni
if [ $CLEAN -eq 1 ]
then
  ndk-build V=1 clean
fi
$ndk_cmd
echo Building dalvikhook
cd ${cdir}
cd  ../../dalvikhook/jni
if [ $CLEAN -eq 1 ]
then
  ndk-build V=1 clean
fi
$ndk_cmd
echo Building smsdispatch
cd ${cdir}
cd jni && $ndk_cmd
cd ${cdir}
echo "skipping java part"
exit 0
cd src/
javas=""
classpath="$cdir/../../extrajar/android-api9.jar:$cdir}/obj"
for i in  `find . -name "*.java"`;
do
  javas+="$i "
  newpath=`dirname $i` 
  if ! `echo $classpath | grep -q $newpath`
  then
    classpath=$classpath:$newpath
    echo $newpath added
  fi
done
echo /usr/bin/javac -verbose -d ${cdir}/obj -classpath ${classpath} ${javas}
/usr/bin/javac -verbose -d ${cdir}/obj -classpath ${classpath} ${javas}
class=""
cd ${cdir}/obj
for i in  `find . -name "*.class"`;
do
  class="$class $i"
done
echo /usr/bin/jar cf ${cdir}/${output}.jar  ${class}
/usr/bin/jar cf ${cdir}/${output}.jar  ${class}
dx --dex --no-strict --output=${cdir}/obj/${output}.dex ${cdir}/${output}.jar
cd ${cdir}
