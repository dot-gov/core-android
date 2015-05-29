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
lib=""
while getopts ":do:l:" opt; do
  case $opt in
    d) debug="DEBUG=1"
      echo "debug is on"
      ;;
    l) lib="DESTLIB=$OPTARG"
      echo "overwriting outputso to $OPTARG"
      ;;
    o) output="$OPTARG"
      echo "overwriting output jar to $output"
      ;;
    \?) echo "Invalid option -$OPTARG" >&2
      ;;
  esac
done

cdir=$(pwd)

echo cleaning libs..
rm -r ./obj/* ./libs/*
ndk_cmd="ndk-build V=1 $debug $lib"
echo Building base
cd ../../../adbi/instruments/base/jni &&  ndk-build V=1 clean && $ndk_cmd
echo Building dalvikhook
cd ${cdir}
cd  ../../dalvikhook/jni &&  ndk-build V=1 clean && $ndk_cmd
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
