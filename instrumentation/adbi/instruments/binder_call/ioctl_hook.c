/*
 *  Collin's Binary Instrumentation Tool/Framework for Android
 *  Collin Mulliner <collin[at]mulliner.org>
 *  http://www.mulliner.org/android/
 *
 *  (c) 2012,2013
 *
 *  License: LGPL v2.1
 *
 */

#define _GNU_SOURCE
#include <stdio.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <dlfcn.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <sys/select.h>
#include <string.h>
#include <termios.h>
#include <pthread.h>
#include <sys/epoll.h>
#include <stdarg.h>
#include <jni.h>
#include <stdlib.h>

#include "../base/hook.h"
#include "../base/base.h"
/* 
 * L'hijacking funziona, ma per qualche motivo succede che
 * dopo un tot di chiamate avvenva segfault inaspettato e
 * scorrelato dalla ioctl.
 * Entrambi i metodi di esecuzione: chiamando la funzione hookata 
 * oppure direttamente la __ioctl(fd,req,data) portano allo stesso risultato
 * SI NOTI BENE, che una volta aver segmentato, tutto lo stack android puo' 
 * risultare instabile, e successive injection potrebbero portare a una SIGSEV
 * immediata. Si consiglia pertanto un reboot.
 *
 * */


#include "ioctl_helper.h"
#include "ipc_examiner.h"
#define LOG_TAG "ioctl_hook.c"
#include "log.h"
#undef log
#define log LOGD
#define logf(...) {FILE *f = fopen("/data/local/tmp/log", "a+");\
  if(f!=NULL){\
    fprintf(f,"%s: ",__FUNCTION__);\
    fprintf(f, __VA_ARGS__);\
    fflush(f); fclose(f); }}

// this file is going to be compiled into a thumb mode binary
extern __ioctl(int,int,void*);
void __attribute__ ((constructor)) my_init(void);

static struct hook_t eph;

// for demo code only
static int counter;

// arm version of hook
extern int my_ioctl_hook_arm(int fd, int request, ...);

/*  
 *  log function to pass to the hooking library to implement central loggin
 *
 *  see: set_logfunction() in base.h
 */
static void my_log(char *msg)
{
  log("%s", msg);
}
int my_ioctl_hook_full(int fd, int request, void *data){
#if 0  //use hooked fnc
  int (*orig)(int fd, int request, ...);
  orig = (void*)eph.orig;
  //log("orig ioctl calling\n");
  hook_precall(&eph);
  int res = orig(fd, request,data);
  hook_postcall(&eph);
  log("orig ioctl called\n");
#else //use imported __ioctl
 
    
  if(request == BINDER_WRITE_READ && decode_binder_wr((struct binder_write_read *)data,"before")){
   // get_btd((struct binder_write_read *)data,1,"before");
   log("bwr->write_size = %x[outAvail]",((struct binder_write_read *)data)->write_size);
  }
 
   int res = __ioctl(fd, request,data);
   
   if(request == BINDER_WRITE_READ && decode_binder_wr((struct binder_write_read *)data,"after")){
   log("bwr->read_size = %x[in_dataCap]",((struct binder_write_read *)data)->read_size);
    // get_btd((struct binder_write_read *)data,0,"after");
   }
  return res;
#endif
}
int my_ioctl_hook(int fd, int request, ...)
{
  va_list ap;
  void * data;
  //log("my_ioctl_hook\n");
  va_start(ap, request);
  data = va_arg(ap, void *);
  //hook_precall(&eph);
  //int res = __ioctl(fd, request, data);
  /*  */
  if(request == BINDER_WRITE_READ && decode_binder_wr((struct binder_write_read *)data,"before")){
   log("bwr->write_size = %x[outAvail]",((struct binder_write_read *)data)->write_size);
   // get_btd((struct binder_write_read *)data,1,"before");
  }
 
   int res = __ioctl(fd, request,data);
   
  /* if(request == BINDER_WRITE_READ && decode_binder_wr((struct binder_write_read *)data,"after")){
     get_btd((struct binder_write_read *)data,0,"after");
   }
   */
  //hook_postcall(&eph);
  va_end(ap);
  return res;
}

void my_init(void)
{
  counter = 3;

  log("%s started\n", __FILE__);

  set_logfunction(my_log);

  hook(&eph, getpid(), "libc.", "ioctl", my_ioctl_hook_arm, my_ioctl_hook);
}

