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
#include <stdlib.h>
#include <stdarg.h>
extern int my_ioctl_hook_full(int fd, int request, void *data);

int my_ioctl_hook_arm(int fd, int request, ...)
{
  va_list ap;
  void * arg;
  va_start(ap, request);
  arg = va_arg(ap, void *);
  int res = my_ioctl_hook_full(fd, request, arg);
  va_end(ap);
  return res;
}

