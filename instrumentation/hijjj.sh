#!/bin/bash
#arguments: <processNameToinject> <libraryToInject> <localFileForLog>
if [[ $# -lt 2 ]];
then
  echo "usage:"
  echo "$0: <process2inject> <library2use> "
  exit 0
fi
lib_name=$(basename $2)
if [[ ! -z $2 ]];
then
  adb push $2 /data/local/tmp/
  adb shell chmod 777 /data/local/tmp/$lib_name
fi
for i in 1 2; do
  n=0
  for a in `adb shell ps | grep $1` then; 
  do
    if [ $n -eq 1 ]
    then
      PID=$a
      break
    fi
    n=$((n+1))
  done

  if [ -z "$PID" ]
  then
    echo "no $1 found"
    exit 1
  else
    echo "$1 at \"$PID\""
    if [ $i -le 1 ]
    then
      if [[ -z $3 ]]
      then
        echo "skipping kill"
      else
        echo "kill process before re-hijack"
        adb shell su -c "kill $PID"
        adb shell "kill $PID"
        sleep 3
      fi
    else
      echo running /data/local/tmp/hijack -p $PID -l /data/local/tmp/$lib_name -f -d
      sleep 1
      adb shell su -c "/data/local/tmp/hijack -p $PID -l /data/local/tmp/$lib_name -d "
      adb shell  "/data/local/tmp/hijack -p $PID -l /data/local/tmp/$lib_name -d "
    fi
  fi
done
