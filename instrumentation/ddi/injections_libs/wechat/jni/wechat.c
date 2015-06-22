/*
 *  Collin's Dynamic Dalvik Instrumentation Toolkit for Android
 *  Collin Mulliner <collin[at]mulliner.org>
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
#include <stdlib.h>
#include <jni.h>

#include "hook.h"
#include "dexstuff.h"
#include "dalvik_hook.h"
#include "base.h"

#undef log
//#define USE_BD
#define DEBUG
#ifdef DEBUG
#define TAG  "wechat"
/*
 * Android log priority values, in ascending priority order.
 */
typedef enum android_LogPriority
{
   ANDROID_LOG_UNKNOWN = 0, ANDROID_LOG_DEFAULT, /* only for SetMinPriority() */
   ANDROID_LOG_VERBOSE, ANDROID_LOG_DEBUG, ANDROID_LOG_INFO, ANDROID_LOG_WARN, ANDROID_LOG_ERROR, ANDROID_LOG_FATAL, ANDROID_LOG_SILENT, /* only for SetMinPriority(); must be last */
} android_LogPriority;

//memset(tag,0,256-1);
char tag[256];
#define log(...) {\
tag[0]=tag[1]=0;\
snprintf(tag,256,"%s:%s",TAG,__FUNCTION__);\
__android_log_print(ANDROID_LOG_DEBUG, tag , __VA_ARGS__);}
#define logf(...) {FILE *f = fopen("/data/local/tmp/log", "a+");\
        if(f!=NULL){\
        fprintf(f,"%s: ",__FUNCTION__);\
        fprintf(f, __VA_ARGS__);\
        fflush(f); fclose(f); }}
#else
#define log(...)
#endif
struct dalvik_cache_t
{
   // for the call inside the hijack
   jclass cls_h;
   jmethodID mid_h;
};

static struct hook_t eph;
static struct dexstuff_t d;
//static struct dalvik_hook_t dpdu;



// switch for debug output of dalvikhook and dexstuff code
static int debug;

static void my_log(char *msg)
{
   log("%s", msg)
}
static void my_log2(char *msg)
{
   if (debug)
      log("%s", msg);
}
long int and_maj = -1;
long int and_min = -1;
long int and_rel = -1;
static void get_android_version()
{
   char android_version[10] = "";
   char *command = "getprop ro.build.version.release";
   FILE *fp = popen(command, "r");
   if (fgets(android_version, sizeof(android_version), fp) != NULL) {
      and_maj = strtol(&android_version[0], NULL, 10);
      and_min = strtol(&android_version[2], NULL, 10);
      and_rel = strtol(&android_version[4], NULL, 10);

      log("[*] version %s %d.%d.%d\n", android_version, and_maj, and_min, and_rel);
   } else {
      log("failed to get version\n");
   }
   if (fp) {
      pclose(fp);
   }
}
static struct dalvik_hook_t setKey_native;
//final void setkey(String password, int lockedDevice, int arithmetic)
//package com.tencent.kingkong.database;
void my_SetKey(JNIEnv *env,jobject this, jstring arg1,jint arg2, jint arg3)
{
   log("we are in jint arg1, jint arg2!!");
   jstring nullString = (*env)->NewStringUTF(env, NULL);
   dalvik_prepare(&d, &setKey_native, env);

   jboolean isCopy=JNI_TRUE;
      const char *utf=(*env)->GetStringUTFChars(env,arg1,&isCopy);
      log("pwd=\"%s\"",utf);
      log("calling : %s\n", setKey_native.method_name)
      if(utf){
      (*env)->ReleaseStringUTFChars(env, arg1,utf);
      (*env)->CallVoidMethod(env, this, setKey_native.mid, arg1,arg2,arg3);
      }else{
         log("null string");
         (*env)->CallVoidMethod(env, this, setKey_native.mid, nullString,arg2,arg3);
      }


         log("success calling : %s\n", setKey_native.method_name)

         dalvik_postcall(&d, &setKey_native);
      if ((*env)->ExceptionOccurred(env)) {
         log("got an exception!!");
         (*env)->ExceptionClear(env);
      }
      (*env)->DeleteLocalRef(env, nullString);
   return;
}


void my_nativeSetKey(JNIEnv *env,jobject this, jint arg1, jint arg2, jstring arg3,jint arithmetic)
{
   log("we are in!!");
   jstring nullString = (*env)->NewStringUTF(env, NULL);
   dalvik_prepare(&d, &setKey_native, env);

   jboolean isCopy=JNI_TRUE;
      const char *utf=(*env)->GetStringUTFChars(env,arg3,&isCopy);
      log("pwd=\"%s\"",utf);
      log("calling : %s\n", setKey_native.method_name)
      if(utf){
      (*env)->ReleaseStringUTFChars(env, arg3,utf);
      (*env)->CallVoidMethod(env, this, setKey_native.mid, arg1,arg2,arg3,arithmetic);
      }else{
         log("null string");
         (*env)->CallVoidMethod(env, this, setKey_native.mid, arg1,arg2,nullString,arithmetic);
      }


         log("success calling : %s\n", setKey_native.method_name)

         dalvik_postcall(&d, &setKey_native);
      if ((*env)->ExceptionOccurred(env)) {
         log("got an exception!!");
         (*env)->ExceptionClear(env);
      }
      (*env)->DeleteLocalRef(env, nullString);
   return;
}

static int my_epoll_wait(int epfd, struct epoll_event *events, int maxevents, int timeout)
{
   int (*orig_epoll_wait)(int epfd, struct epoll_event *events, int maxevents, int timeout);
   orig_epoll_wait = (void*) eph.orig;
   // remove hook for epoll_wait
   hook_precall(&eph);

   // resolve symbols from DVM
   dexstuff_resolv_dvm(&d);

   //dalvik_hook_setup(&setKey_native, "Lcom/tencent/kingkong/database/SQLiteConnection;", "nativeSetKey", "(IILjava/lang/String;I)V", 5, my_nativeSetKey);
   dalvik_hook_setup(&setKey_native, "Lcom/tencent/kingkong/database/SQLiteConnection;", "setkey", "(Ljava/lang/String;II)V", 4, my_SetKey);

   if (dalvik_hook(&d, &setKey_native)) {
      log("my_epoll_wait: hook setKey_native ok\n")
   } else {
      log("my_epoll_wait: hook setKey_native fails\n")
   }



   int res = orig_epoll_wait(epfd, events, maxevents, timeout);
   return res;
}


// set my_init as the entry point
void __attribute__ ((constructor)) my_init(void);

void my_init(void)
{
   log("started\n");
   get_android_version();
   debug = 1;
   // set log function for  libbase (very important!)
   set_logfunction(my_log2);
   // set log function for libdalvikhook (very important!)
   dalvikhook_set_logfunction(my_log2);

   //hook(&eph, getpid(), "libkkdb.", "nativeSetKey", my_nativeSetKey, 0);
   hook(&eph, getpid(), "libc.", "epoll_wait", my_epoll_wait, 0);
   dexstuff_resolv_dvm(&d);
   dalvik_dump_class(&d,"Lcom/tencent/kingkong/database/SQLiteConnection;");
}
