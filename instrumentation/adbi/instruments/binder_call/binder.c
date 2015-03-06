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
#include "binder.h"


#define DEBUG

//memset(tag,0,256-1);

#define DEBUG
#ifdef DEBUG
#undef log
/*
 * Android log priority values, in ascending priority order.
 */
typedef enum android_LogPriority
{
   ANDROID_LOG_UNKNOWN = 0, ANDROID_LOG_DEFAULT, /* only for SetMinPriority() */
   ANDROID_LOG_VERBOSE, ANDROID_LOG_DEBUG, ANDROID_LOG_INFO, ANDROID_LOG_WARN, ANDROID_LOG_ERROR, ANDROID_LOG_FATAL, ANDROID_LOG_SILENT, /* only for SetMinPriority(); must be last */
} android_LogPriority;
char tag[256];
#define log(...) {\
tag[0]=tag[1]=0;\
snprintf(tag,256,":%s",__FUNCTION__);\
__android_log_print(ANDROID_LOG_DEBUG, tag , __VA_ARGS__);}
#define logf(...) {FILE *f = fopen("/data/local/tmp/log", "a+");\
        if(f!=NULL){\
        fprintf(f,"%s: ",__FUNCTION__);\
        fprintf(f, __VA_ARGS__);\
        fflush(f); fclose(f); }}
#else
#define log(...)
#endif

// this file is going to be compiled into a thumb mode binary

void __attribute__ ((constructor)) my_init(void);

static struct hook_t eph;
static struct hook_t binderh;
static struct hook_t ioctl_h;

// arm version of hook
extern int binder_call_arm(uint32_t code,void *data,void *reply,uint32_t flags);
extern int ioctl_call_arm(int fd, int request, ...);

/*
int ioctl(int fd, int request, ...)
{
    va_list ap;
    void * arg;

    va_start(ap, request);
    arg = va_arg(ap, void *);
    va_end(ap);

    //return __ioctl(fd, request, arg);
}
*/
int my_ioctl(int fd, int request, void * data){
   log("ioctl_call() called request=%d\n",request);
   return __ioctl(fd, request, data);
}
int ioctl_call(int fd, int request, ...)
{
   int (*orig_ioctl)(int fd, int request, ...);
   va_list ap;
   void * arg;
   va_start(ap, request);
   //orig_ioctl = (void*) ioctl_h.orig;
   //hook_precall(&ioctl_h);
   //int res = orig_ioctl(fd, request, ap);
   //hook_postcall(&ioctl_h);
   arg = va_arg(ap, void *);
   va_end(ap);
   return my_ioctl(fd, request, arg);

}

int _binder_call(uint32_t code,void *data,void *reply,uint32_t flags)
{
   void (*orig_binder_call)(uint32_t code,void *data,void *reply,uint32_t flags);
   orig_binder_call = (void*)binderh.orig;
   hook_precall(&binderh);
   orig_binder_call(code,data,reply,flags);
   log("_binder_call() called code=%d\n",code);
   hook_postcall(&binderh);
}
void my_init(void)
{
	log("%s started\n", __FILE__)

        hook(&ioctl_h, getpid(), "libbinder.", "ioctl", ioctl_call_arm, ioctl_call);

	//hook(&binderh, getpid(), "libbinder.", "_ZN7android7BBinder10onTransactEjRKNS_6ParcelEPS1_j", binder_call_arm, _binder_call);
}

