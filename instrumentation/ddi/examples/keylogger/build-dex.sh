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
cdir=$(pwd)

echo cleaning..
rm -r ./obj/* ./libs/*
echo Building lib
cd jni && ndk-build && cd -
cd src/
javas=""
output="keyclass"
classpath="/AOSPs/androidSources4.2_r1/prebuilts/sdk/9/android.jar:./${cdir}/obj"
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
