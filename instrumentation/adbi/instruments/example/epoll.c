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

#include <jni.h>
#include <stdlib.h>

#include "../base/hook.h"
#include "../base/base.h"
#include "epoll.h"


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

void __attribute__ ((constructor)) my_init(void);

static struct hook_t eph;
static struct hook_t dth;

// for demo code only
static int counter;

// arm version of hook
extern int my_epoll_wait_arm(int epfd, struct epoll_event *events, int maxevents, int timeout);
extern void my_death_arm(struct binder_state *bs, void *ptr);

/*  
 *  log function to pass to the hooking library to implement central loggin
 *
 *  see: set_logfunction() in base.h
 */
static void my_log(char *msg)
{
	log("%s", msg)
}

int my_epoll_wait(int epfd, struct epoll_event *events, int maxevents, int timeout)
{
	int (*orig_epoll_wait)(int epfd, struct epoll_event *events, int maxevents, int timeout);
	orig_epoll_wait = (void*)eph.orig;

	hook_precall(&eph);
	int res = orig_epoll_wait(epfd, events, maxevents, timeout);
	if (counter) {
		hook_postcall(&eph);
		log("epoll_wait() called\n");
		counter--;
		if (!counter)
			log("removing hook for epoll_wait()\n");
	}
        
	return res;
}
void my_death(struct binder_state *bs, void *ptr){
   void (*orig_svcinfo_death)(struct binder_state *bs,void* ptr);
   orig_svcinfo_death = (void*)dth.orig;
   hook_precall(&dth);
   orig_svcinfo_death(bs,ptr);
   log("epoll_wait() called\n");
   hook_postcall(&dth);
}
void my_init(void)
{
	counter = 3;

	log("%s started\n", __FILE__)
 
	//set_logfunction(my_log);

        hook(&dth, getpid(), "libc.", "svcinfo_death", my_death_arm, my_death);

	//hook(&eph, getpid(), "libc.", "epoll_wait", my_epoll_wait_arm, my_epoll_wait);
}

