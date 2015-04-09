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

/*
 * Here we use the LD_PRELOAD technique to intercept the ioctl call to the binder
 * device.
 * A better solution is to hook the IPCThreadState::talkWithDriver function exported by the
 * libbinder which is the only place where a ioctl is being sent to the Binder driver.
 * /frameworks/native/libs/binder/IPCThreadState.cpp IPCThreadState::talkWithDriver
 * The problem here is that ioctl's parameter are built inside the hooked function and
 * it isn't straight forward to recover them.
 *
 * We are only interested in the ioctl_cmd BINDER_WRITE_READ.
 * I found ioctls with that code in the following file:   /frameworks/native/cmds/servicemanager/binder.c which is used to build
 * the servicemanager daemon.
 * at:
 * 1)  int binder_write(struct binder_state *bs, void *data, unsigned len)
 * 2)  int binder_call(struct binder_state *bs,struct binder_io *msg, struct binder_io *reply,void *target, uint32_t code)
 * 3)  void binder_loop(struct binder_state *bs, binder_handler func)
 * This
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
char * get_tr(unsigned int tc)
{
   switch (tc)
   {
   case BR_ERROR:
      return "BC_ERROR";
   case BR_SPAWN_LOOPER:
      return "BR_SPAWN_LOOPER";
   case BR_DEAD_REPLY:
      return "BR_DEAD_REPLY";
   case BR_TRANSACTION:
      return "BR_TRANSACTION";
   case BR_REPLY:
      return "BR_REPLY";
   case BR_TRANSACTION_COMPLETE:
      return "BR_TRANSACTION_COMPLETE";
   case BR_INCREFS:
      return "BR_INCREFS";
   case BR_ACQUIRE:
      return "BR_ACQUIRE";
   case BR_NOOP:
      return "BR_NOOP";
   case BR_ACQUIRE_RESULT:
      return "BR_ACQUIRE_RESULT";
   case BR_RELEASE:
      return "BR_RELEASE";
   case BR_DECREFS:
      return "BR_DECREFS";
   case BR_ATTEMPT_ACQUIRE:
      return "BR_ATTEMP_ACQUIRE";
   case BR_DEAD_BINDER:
      return "BR_DEAD_DINDER";
   case BR_FAILED_REPLY:
      return "BR_FAILED_REPLY";
   case BR_CLEAR_DEATH_NOTIFICATION_DONE:
      return "BR_CLEAR_DEATH_NOTIFICATION_DONE";
   default:
      return "UNKNOWN";
   }
}
char * get_tc(unsigned int tc)
{
   switch (tc)
   {
   case BC_ENTER_LOOPER:
      return "BC_ENTER_LOOPER";
   case BC_EXIT_LOOPER:
      return "BC_EXIT_LOOPER";
   case BC_FREE_BUFFER:
      return "BC_FREE_BUFFER";
   case BC_TRANSACTION:
      return "BC_TRANSACTION";
   case BC_REPLY:
      return "BC_REPLY";
   case BC_ACQUIRE_RESULT:
      return "BC_ACQUIRE_RESULT";
   case BC_INCREFS:
      return "BC_INCREFS";
   case BC_ACQUIRE:
      return "BC_ACQUIRE";
   case BC_INCREFS_DONE:
      return "BC_INCREFS_DONE";
   case BC_ACQUIRE_DONE:
      return "BC_ACQUIRE_DONE";
   case BC_RELEASE:
      return "BC_RELEASE";
   case BC_DECREFS:
      return "BC_DECREFS";
   case BC_ATTEMPT_ACQUIRE:
      return "BC_ATTEMP_ACQUIRE";
   case BC_REGISTER_LOOPER:
      return "BC_REGISTER_LOOPER";
   case BC_DEAD_BINDER_DONE:
      return "BC_DEAD_DINDER_DONE";
   case BC_REQUEST_DEATH_NOTIFICATION:
      return "BC_REQUEST_DEATH_NOTIFICATION_DONE";
   case BC_CLEAR_DEATH_NOTIFICATION:
      return "BC_CLEAR_DEATH_NOTIFICATION_DONE";
   default:
      return "UNKNOWN";
   }
}
char * get_intf_desc(void const *data){
   ioctlBuff[0]=ioctlBuff[1]=0;
   unsigned int* ui_data = data;
   if(data){
      log("data available");
      unsigned int str_len = ui_data[0];
      log("coping %d chars",str_len);
      snprintf(ioctlBuff,sizeof(ioctlBuff),"%s",(char *)((unsigned int *) data)[2]);
   }else{
      snprintf(ioctlBuff,sizeof(ioctlBuff),"%s","null data");
   }
   return ioctlBuff;
}
void hexdump(void *_data, unsigned len)
{
   unsigned char *data = _data;
   unsigned count;



   for (count = 0; count < len; count++) {
      if ((count & 15) == 0)
         log("%04x:", count);
      log(" %02x %c", *data, (*data < 32) || (*data > 126) ? '.' : *data);
      data++;
      if ((count & 15) == 15)
         log("\n");
   }
   if ((count & 15) != 0)
      log("\n");
}
struct binder_object
{
    uint32_t type;
    uint32_t flags;
    void *pointer;
    void *cookie;
};

struct binder_txn
{
    void *target;
    void *cookie;
    uint32_t code;
    uint32_t flags;

    uint32_t sender_pid;
    uint32_t sender_euid;

    uint32_t data_size;
    uint32_t offs_size;
    void *data;
    void *offs;
};

void binder_dump_txn(struct binder_txn *txn)
{
    struct binder_object *obj;
    unsigned *offs = txn->offs;
    unsigned count = txn->offs_size / 4;

    log("target %p  cookie %p  code %08x  flags %08x\n",
            txn->target, txn->cookie, txn->code, txn->flags);
    log("  pid %8d  uid %8d  data %8d  offs %8d\n",
            txn->sender_pid, txn->sender_euid, txn->data_size, txn->offs_size);
    //hexdump(txn->data, txn->data_size);
    while (txn->data!=NULL && count--) {
        obj = (void*) (((char*) txn->data) + *offs++);
        log("  - type %08x  flags %08x  ptr %p  cookie %p\n",
                obj->type, obj->flags, obj->pointer, obj->cookie);
    }
}
int ioctl(int fd, int request, ...)
{
   va_list ap;
   void * arg;

   va_start(ap, request);
   arg = va_arg(ap, void *);
   va_end(ap);
   struct binder_write_read *bwr = arg;
   struct binder_transaction_data *btd = NULL;
   uint32_t transaction_code = 0;
   if (request == BINDER_WRITE_READ && arg != NULL && bwr->write_buffer) {
      transaction_code = ((unsigned int *) bwr->write_buffer)[0];
      btd = (struct binder_transaction_data *) &((unsigned int *) bwr->write_buffer)[1];
      log("ioctl> BINDER_WRITE_READ fd %d req request=%d tc=%d(%s) %s", fd, request, transaction_code, get_tc(transaction_code), (btd->flags & TF_ONE_WAY) ? "ONW WAY" : "");
      //,get_intf_desc(btd->data.ptr.buffer));
      if (transaction_code == BC_TRANSACTION || transaction_code == BC_REPLY) {
         // log("ioctl> BC_TRANSACTION fd %d req BINDER_WRITE_READ (%d) tc=%s ", fd, request, get_tc(transaction_code));
      }
   }
   int res = __ioctl(fd, request, arg);
   if (btd != NULL && btd->flags & TF_ACCEPT_FDS && !(btd->flags & TF_ONE_WAY)) {
      if (request == BINDER_WRITE_READ && arg != NULL && bwr->read_buffer) {
         uint32_t * ptr = (uint32_t*) bwr->read_buffer;
         uint32_t *end = ptr + (bwr->read_consumed / 4);

         transaction_code = *ptr++;
         log("ioctl> after call fd %d req request=%d tr=%d(%s)", fd, request, transaction_code, get_tr(transaction_code));
         if (transaction_code == BR_REPLY || transaction_code == BR_TRANSACTION) {
            struct binder_txn *txn = (void *) ptr;
            if ((end - ptr) * sizeof(uint32_t) < sizeof(struct binder_txn)) {
               log("parse: txn too small!\n");
               return res;
            }
            binder_dump_txn(txn);
         }
         //btd = (struct binder_transaction_data *) &((unsigned int *) bwr->read_buffer)[1];

         //if (transaction_code == BR_TRANSACTION || transaction_code == BR_REPLY) {
         //   log("ioctl> fd %d req BINDER_WRITE_READ (%d) tr=%s ", fd, request, get_tr(transaction_code));
         //}
      }
   }

   return res;
}
static int (*orig_register)(int, int, int, int) = 0x0;

int jniRegisterNativeMethods_(int a1, int a2, int a3, int a4)
{
   int v4; // r5@1
   char * v5; // r6@1
   int v6; // r7@1
   int v7; // r0@1
   int v9; // [sp+4h] [bp-2Ch]@1
   char *v10; // [sp+Ch] [bp-24h]@2
   int v11; // [sp+10h] [bp-20h]@1
   int v12; // [sp+14h] [bp-1Ch]@1

   v9 = a4;
   v4 = a1;
   v5[0] = a2;
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
   log("Native registration called find class '%s'", v5);
   //  if ( (*(int (__fastcall **)(int, int, int, int))(*(_DWORD *)v4 + 860))(v4, v12, v6, v9) < 0 )
   //  {
   //    asprintf(&v10, "RegisterNatives failed for '%s', aborting", v5);
   //    (*(void (__fastcall **)(int, char *))(*(_DWORD *)v4 + 72))(v4, v10);
   //  }
   //  scoped_local_ref<_jclass *>::~scoped_local_ref(&v11);
   if (orig_register == 0x0) {
      *(void **) (&orig_register) = dlsym(RTLD_NEXT, "jniRegisterNativeMethods");
      if (dlerror()) {
         log("%s\n", "error locating original lib");
         errno = EACCES;
         return -1;
      }
   }
   return (*orig_register)(a1, a2, a3, a4);

}
