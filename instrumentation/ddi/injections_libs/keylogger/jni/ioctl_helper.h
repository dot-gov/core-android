#ifndef __HEADER_BINDER_HELPER__
#define __HEADER_BINDER_HELPER__
#include "ipc_examiner.h" 
#include <sys/types.h>
#include <linux/binder.h>
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

char * get_tr(unsigned int tc);
char * get_tc(unsigned int tc);
char * get_intf_desc(struct binder_transaction_data *data,uint32_t transaction_code,char *info);
void hexdump(const void *_data, unsigned len);
void hexdump_16(const void *_data, unsigned len);
void binder_dump_txn(struct binder_txn *txn);
struct binder_transaction_data* get_btd(struct binder_write_read *bwr,char write,char *info);
void extract_key_pressed(char *interface_name,unsigned int *data,uint32_t code);
char * get_intf_desc_parcel(unsigned int *parcel,uint32_t transaction_code);

#endif //#ifndef __HEADER_BINDER_HELPER__

