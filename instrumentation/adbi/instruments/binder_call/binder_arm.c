/*
 *  Collin's Binary Instrumentation Tool/Framework for Android
 *  Collin Mulliner <collin[at]mulliner.org>
 *  http://www.mulliner.org/android/
 *
 *  (c) 2012,2013
 *
 *  License: LGPL v2.1
 *
 */

#include <sys/types.h>
#include <sys/epoll.h>
#include "binder.h"
#include <stdarg.h>

extern int _binder_call(uint32_t code,void *data,void *reply,uint32_t flags);
extern int my_ioctl(int fd, int request, void *data);
int binder_call_arm(uint32_t code,void *data,void *reply,uint32_t flags)
{
   _binder_call(code,data,reply,flags);
}
int ioctl_call_arm(int fd, int request, ...)
{
   va_list ap;
   void * arg;
   va_start(ap, request);
   arg = va_arg(ap, void *);
   va_end(ap);
   return my_ioctl(fd, request, arg);
   //return res;
}
