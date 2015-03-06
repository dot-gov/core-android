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
rm ./obj ./libs -r

cd jni
ndk-build
cd -

