/*
 * =====================================================================================
 *
 *       Filename:  ipc_examiner.c * *    Description:  
 *
 *        Version:  1.0
 *        Created:  16/04/2015 13:10:42
 *       Revision:  none
 *       Compiler:  gcc
 *
 *         Author:  zad (), wtfrtfmdiy@gmail.com
 *   Organization:  ht
 *
 * =====================================================================================
 */
#include "ipc_examiner.h"
#include "ioctl_helper.h"
#include <stdlib.h>

#undef log
#define DEBUG
#ifdef DEBUG
#define LOG_TAG "ipc_ex"
#include "log.h"
#define log LOGD
#endif
#if 0
struct binder_write_read * get_binder_wr_work(void *ipcThreadStatus_ptr, char doReceive,struct binder_write_read *bwr)
{
  // data is the IPCThreadStatus pointer
  log("%s:",__FUNCTION__);
  if (ipcThreadStatus_ptr == NULL || bwr == NULL){
    log("invalid pointer passed\n");
    return NULL;
  }
  memset(bwr,sizeof(struct binder_write_read),0);
  unsigned int mIn = (unsigned int )(ipcThreadStatus_ptr + M_IN_PARCEL);
  unsigned int mOut = (unsigned int )(ipcThreadStatus_ptr + M_OUT_PARCEL);
  size_t in_dataSize= (size_t)((mIn + mDataSize));
  size_t out_dataSize= (size_t)((mOut + mDataSize));

  size_t in_dataPos= (size_t)((mIn + mDataPos));
  size_t out_dataPos= (size_t)((mOut + mDataPos));

  size_t in_dataCap= (size_t)((mIn + mDataCapacity));
  size_t out_dataCap= (size_t)((mOut + mDataCapacity));


  log("IN buffer is %p dataPos 0x%x=%x dataSize 0x%x=%x dataCap 0x%x=%x\n", mIn,  in_dataPos,*(size_t *)in_dataPos,
      in_dataSize,*(size_t*)in_dataSize,in_dataCap,*(size_t*)in_dataCap);
  log("OUT buffer is %p  dataPos 0x%x=%x dataSize 0x%x=%x dataCap 0x%x=%x\n", mOut,  out_dataPos, *(size_t*)out_dataPos,
      out_dataSize, *(size_t*)out_dataSize,out_dataCap, *(size_t*)out_dataCap);
  // Is the read buffer empty?
  const char needRead = in_dataPos >= in_dataSize;
  // We don't want to write anything if we are still reading
  // from data left in the input buffer and the caller
  // has requested to read the next data.
  const size_t outAvail = (!doReceive || needRead) ?  *(size_t*)out_dataSize : 0;
  bwr->write_size = outAvail;
  // mOut + mData , mi porta a uint8_t* mData;
  bwr->write_buffer = *(unsigned long*)(mOut + mData);
  log("write buffer is %s at 0x%x\n", outAvail?"with Data":"Empty",bwr->write_buffer);

  // This is what we'll read.
  if (doReceive && needRead) {
    bwr->read_size = *(signed long *)in_dataCap;
    bwr->read_buffer = *(unsigned long*)(mIn + mData);
  } else {
    bwr->read_size = 0;
    bwr->read_buffer = 0;
  }
  log("read buffer is %s (needRead=%d) at 0x%x\n", needRead?"with Data":"Empty",needRead,bwr->read_buffer);

  return bwr;
}
#endif

struct binder_write_read * get_binder_wr(void *ipcThreadStatus_ptr, char doReceive,struct binder_write_read *bwr)
{
  // data is the IPCThreadStatus pointer
  log("%s:",__FUNCTION__);
  if (ipcThreadStatus_ptr == NULL || bwr == NULL){
    log("invalid pointer passed\n");
    return NULL;
  }
  memset(bwr,sizeof(struct binder_write_read),0);
  unsigned int mIn = (unsigned int )(ipcThreadStatus_ptr + M_IN_PARCEL);
  unsigned int mOut = (unsigned int )(ipcThreadStatus_ptr + M_OUT_PARCEL);
  size_t in_dataSize= *(size_t *)((mIn + mDataSize));
  size_t out_dataSize= *(size_t *)((mOut + mDataSize));

  size_t in_dataPos= *(size_t *)((mIn + mDataPos));
  size_t out_dataPos= *(size_t *)((mOut + mDataPos));

  size_t in_dataCap= *(size_t *)((mIn + mDataCapacity));
  size_t out_dataCap= *(size_t *)((mOut + mDataCapacity));

   in_dataSize = (in_dataSize > in_dataPos) ? in_dataSize :in_dataPos;
   out_dataSize = (out_dataSize > out_dataPos) ? out_dataSize :out_dataPos;
  log("IN buffer is %p dataPos %x dataSize %x dataCap %x\n", mIn,  in_dataPos,
      in_dataSize,in_dataCap);
  log("OUT buffer is %p  dataPos %x dataSize %x dataCap %x\n", mOut,  out_dataPos,
      out_dataSize, out_dataCap);
  // Is the read buffer empty?
  const char needRead = in_dataPos >= in_dataSize;
  // We don't want to write anything if we are still reading
  // from data left in the input buffer and the caller
  // has requested to read the next data.
  const size_t outAvail = (!doReceive || needRead) ?  out_dataSize : 0;
  bwr->write_size = outAvail;
  // mOut + mData , mi porta a uint8_t* mData;
  bwr->write_buffer = *(unsigned long*)(mOut + mData);
  log("write buffer is %s at 0x%x\n", outAvail?"with Data":"Empty",bwr->write_buffer);

  // This is what we'll read.
  if (doReceive && needRead) {
    bwr->read_size = in_dataCap;
    bwr->read_buffer = *(unsigned long*)(mIn + mData);
  } else {
    bwr->read_size = 0;
    bwr->read_buffer = 0;
  }
  log("read buffer is %s (needRead=%d) at 0x%x\n", needRead?"with Data":"Empty",needRead,bwr->read_buffer);

  return bwr;
}
int decode_binder_wr(struct binder_write_read *bwr,char *info)
{
  if( info == NULL ){
    info = "";
  }
  if( bwr == NULL ){
    //log("%s [%s],NULL buffer passed \n",__FUNCTION__,info);
    return 0;
  }
  // Return immediately if there is nothing to do.
  if ((bwr->write_size == 0) && (bwr->read_size == 0)){
    log("%s [%s]:empty buffers nothing to do\n",__FUNCTION__,info);
    return 0;
  }else{
    //log("%s [%s]:data available \n",__FUNCTION__,info);
  }
  return 1;
 }
