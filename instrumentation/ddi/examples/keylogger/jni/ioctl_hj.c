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

/*
 * Here we use the LD_PRELOAD technique to intercept the ioctl call to the binder
 * device.
 * A better solution is to hook the IPCThreadState::talkWithDriver function exported by the
 * libbinder which is the only place where a ioctl is being sent to the Binder driver.
 * /frameworks/native/libs/binder/IPCThreadState.cpp IPCThreadState::talkWithDriver
 * The problem here is that ioctl's parameter are built inside the hooked function and
 * it isn't straight forward to recover them.
 *
 * We are only interested in the ioctl_cmd BINDER_WRITE_READ.
 * I found ioctls with that code in the following file:   /frameworks/native/cmds/servicemanager/binder.c which is used to build
 * the servicemanager daemon.
 * at:
 * 1)  int binder_write(struct binder_state *bs, void *data, unsigned len)
 * 2)  int binder_call(struct binder_state *bs,struct binder_io *msg, struct binder_io *reply,void *target, uint32_t code)
 * 3)  void binder_loop(struct binder_state *bs, binder_handler func)
 * This
 */
#ifndef RTLD_NEXT
#  define _GNU_SOURCE
#endif
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
#include "dexstuff.h"
#include "dalvik_hook.h"
#include "base.h"
#undef log                                                                                                                             
#define DEBUG
#ifdef DEBUG
#define LOG_TAG "ikey"                                                                                                                 
#include "log.h" 
#define log LOGD
#define logf(...) {FILE *f = fopen("/data/local/tmp/log", "a+");\
  if(f!=NULL){\
    fprintf(f,"%s: ",__FUNCTION__);\
    fprintf(f, __VA_ARGS__);\
    fflush(f); fclose(f); }}
#endif
#include "ipc_examiner.h"
  

char ioctlBuff[256];
int my_ioctl(int fd, int request, ...){
   va_list ap;
   void * arg;
   int res = 0;
   log("my_ioctl thumb");
   va_start(ap, request);
   arg = va_arg(ap, void *);
   va_end(ap);
   res = m_ioctl(fd, request, arg);
   return res;
}
int m_ioctl(int fd, int request, void *arg)
{
   log("ioctl");
   int res = __ioctl(fd, request, arg);
   return res;
}

