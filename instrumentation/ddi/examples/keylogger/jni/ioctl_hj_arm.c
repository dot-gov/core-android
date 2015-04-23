/*
 * =====================================================================================
 *
 *       Filename:  libme.c
 *
 *    Description:  
 *
 *        Version:  1.0
 *        Created:  03/04/2015 15:38:09
 *       Revision:  none
 *       Compiler:  gcc
 *
 *         Author:  zad (), wtfrtfmdiy@gmail.com
 *   Organization:  ht
 *
 * =====================================================================================
 */
#include <dlfcn.h>
#include <errno.h>
#include <stdlib.h>
#include <strings.h>
#include <jni.h>
#include <linux/ioctl.h>
#include <linux/binder.h>
#include <stdio.h>
#include <stdint.h>
#include <android/log.h>
#include "ipc_examiner.h"
#undef log
#define DEBUG
#ifdef DEBUG
#define LOG_TAG "ikey_arm"
#include "log.h"
#define log LOGD
#define logf(...) {FILE *f = fopen("/data/local/tmp/log", "a+");\
  if(f!=NULL){\
    fprintf(f,"%s: ",__FUNCTION__);\
    fprintf(f, __VA_ARGS__);\
    fflush(f); fclose(f); }}
#endif

extern int my_talk(void *this,int doReceive);
int my_talk_arm(void *this,int doReceive)
{
   log("my_talk ARM> doReceive %d\n", doReceive);
   return my_talk(this,doReceive);
}

extern status_t my_writeTransact(void *this,int32_t cmd, uint32_t binderFlags,int32_t handle, uint32_t code, void* parcel, status_t* statusBuffer);
status_t my_writeTransact_arm(void *this,int32_t cmd, uint32_t binderFlags,int32_t handle, uint32_t code, void* parcel, status_t* statusBuffer)
{
   log("my_writeTransact ARM\n");
   return my_writeTransact(this,cmd,binderFlags,handle,code,parcel,statusBuffer);
}


extern int my_ioctl_hook_full(int fd, int request, void *data);

int my_ioctl_hook_arm(int fd, int request, ...)
{
  va_list ap;
  void * arg;
   log("my_ioctl_hook_arm ARM> request %d\n", request);
  va_start(ap, request);
  arg = va_arg(ap, void *);
  va_end(ap);
  return my_ioctl_hook_full(fd, request, arg);
}

