/*
 * =====================================================================================
 *
 *       Filename:  libme.c
 *
 *    Description:  
 *
 *        Version:  1.0
 *        Created:  03/04/2015 15:38:09
 *       Revision:  none
 *       Compiler:  gcc
 *
 *         Author:  zad (), wtfrtfmdiy@gmail.com
 *   Organization:  ht
 *
 * =====================================================================================
 */
#ifndef RTLD_NEXT
#  define _GNU_SOURCE
#endif
#include <dlfcn.h>
#include <errno.h>
#include <stdlib.h>
#include <strings.h>
#include <jni.h>
#include <linux/ioctl.h>
#include <linux/binder.h>
#include <stdio.h>
#include <stdint.h>
#include <android/log.h>
char tag[256]; 
char ioctlBuff[256]; 
//memset(tag,0,256-1);

#define log(...) {\
  tag[0]=tag[1]=0;\
  snprintf(tag,256,"zad %s:",__FUNCTION__);\
  __android_log_print(ANDROID_LOG_DEBUG, tag , __VA_ARGS__);}
char * get_tc(unsigned int tc){
  switch(tc){
    case BC_ENTER_LOOPER: return "BC_ENTER_LOOPER";
    case BC_EXIT_LOOPER: return "BC_EXIT_LOOPER";
    case BC_FREE_BUFFER : return "BC_FREE_BUFFER";
    case BC_TRANSACTION: return "BC_TRANSACTION";
    case BC_REPLY:return "BC_REPLY";
    case BC_ACQUIRE_RESULT: return "BC_ACQUIRE_RESULT";
    case BC_INCREFS: return "BC_INCREFS";
    case BC_ACQUIRE: return "BC_ACQUIRE";
    case BC_INCREFS_DONE: return "BC_INCREFS_DONE";
    case BC_ACQUIRE_DONE: return "BC_ACQUIRE_DONE";
    case BC_RELEASE: return "BC_RELEASE";
    case BC_DECREFS: return "BC_DECREFS";
    case BC_ATTEMPT_ACQUIRE: return "BC_ATTEMP_ACQUIRE";
    case BC_REGISTER_LOOPER: return "BC_REGISTER_LOOPER";
    case BC_DEAD_BINDER_DONE: return "BC_DEAD_DINDER_DONE";
    case BC_REQUEST_DEATH_NOTIFICATION: return "BC_REQUEST_DEATH_NOTIFICATION_DONE";
    case BC_CLEAR_DEATH_NOTIFICATION: return "BC_CLEAR_DEATH_NOTIFICATION_DONE";
    default: return "UNKNOWN";
  }
}
int ioctl(int fd, int request, ...)
{
  va_list ap;
  void * arg;

  va_start(ap, request);
  arg = va_arg(ap, void *);
  va_end(ap);
  if(request == BINDER_WRITE_READ && arg != NULL){
    struct binder_write_read *bwr = arg;
    unsigned int transaction_code = ((unsigned int *)bwr->write_buffer)[0]; 
    //log("ioctl> BINDER_WRITE_READ fd %d req request=%d tc=%d(%s)",fd,request,transaction_code,get_tc(transaction_code));
    if(transaction_code == BC_TRANSACTION || transaction_code == BC_REPLY){
    log("ioctl> BC_TRANSACTION fd %d req BINDER_WRITE_READ (%d) tc=%s ",fd,request,get_tc(transaction_code));
    }
  }
  return __ioctl(fd, request, arg);
}
static int (*orig_register)(int ,int,int,int) = 0x0;

int jniRegisterNativeMethods_(int a1, int a2, int a3, int a4)
{
  int v4; // r5@1
  int v5; // r6@1
  int v6; // r7@1
  int v7; // r0@1
  int v9; // [sp+4h] [bp-2Ch]@1
  char *v10; // [sp+Ch] [bp-24h]@2
  int v11; // [sp+10h] [bp-20h]@1
  int v12; // [sp+14h] [bp-1Ch]@1

  v9 = a4;
  v4 = a1;
  v5 = a2;
  v6 = a3;
  //v7 = (*(int (**)(void))(*(void *)a1 + 24))();
  v11 = v4;
  v12 = v7;
  //if ( !v7 )
  //{
   // log( "Native registration unable to find class '%s'", v5);
    // (*(void (__fastcall **)(int, char *))(*(_DWORD *)v4 + 72))(v4, v10);
  //}else{
  //  log( "Native registration  called find class '%s'", v5);
  //}
    log( "Native registration called find class '%s'", v5);
  //  if ( (*(int (__fastcall **)(int, int, int, int))(*(_DWORD *)v4 + 860))(v4, v12, v6, v9) < 0 )
  //  {
  //    asprintf(&v10, "RegisterNatives failed for '%s', aborting", v5);
  //    (*(void (__fastcall **)(int, char *))(*(_DWORD *)v4 + 72))(v4, v10);
  //  }
  //  scoped_local_ref<_jclass *>::~scoped_local_ref(&v11);
  if(orig_register==0x0){
  *(void **)(&orig_register) = dlsym(RTLD_NEXT, "jniRegisterNativeMethods");
  if(dlerror()) {
    log("%s\n","error locating original lib");
    errno = EACCES;
    return -1;
  }
  }
  return  (*orig_register)(a1,a2,a3,a4);

}
