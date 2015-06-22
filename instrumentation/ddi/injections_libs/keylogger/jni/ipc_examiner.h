#ifndef _HEADER_IPC_EXAMINER
#define _HEADER_IPC_EXAMINER
#include <sys/types.h>
#include <linux/binder.h>
typedef enum {
	M_PROCESS = 0,
	M_MYTHREADID = 4,
	M_IN_PARCEL = 48, // Parcel
	M_OUT_PARCEL = 96, // Parcel
	M_CALLING_PID = 148
} ipcThreadState4_4_off;

typedef enum {
    mError = 0,
    mData = 4, //uint8_t *
    mDataSize = 8, // size_t
    mDataCapacity = 12, // size_t
    mDataPos = 16, //size_t
    mObjects = 20,
    mObjectsSize = 24,
    mObjectsCapacity = 28,
    mNextObjectHint = 32,
    mFdsKnown = 36,
    mHasFds = 37,
    mAllowFds = 38,
    mOwner = 40,
    mOwnerCookie = 44,
} parcel4_4_off;
/*  
struct binder_write_read {
	signed long write_size;
	signed long write_consumed;
	unsigned long write_buffer;
	signed long read_size;
	signed long read_consumed;
	unsigned long read_buffer;
};
*/

typedef int32_t status_t;
struct binder_write_read * get_binder_wr(void *ipcThreadStatus_ptr, char doReceive,struct binder_write_read *bwr);
int decode_binder_wr(struct binder_write_read *bwr,char *info);
#endif //#ifndef _HEADER_IPC_EXAMINER
