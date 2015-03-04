#!/bin/bash
. ~/.bash_aliases
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
      echo "kill process before re-hijack"
      adb shell su -c "kill $PID"
      sleep 1
    else
      echo running /data/local/tmp/hijack -p $PID -l /data/local/tmp/$2 -f -d
      sleep 1
      adb shell su -c "/data/local/tmp/hijack -p $PID -l /data/local/tmp/$2 -d "
      logAc >~/audio_log
    fi
  fi
done
