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
/usr/bin/javac -verbose -d ./obj -classpath /AOSPs/androidSources4.2_r1/prebuilts/sdk/9/android.jar:./obj SMSDispatch.java
jar cf ddiclasses.jar org/mulliner/ddiexample/SMSDispatch.class
dx --dex --no-strict --output=./obj/ddiclasses.dex ddiclasses.jar
