#define _GNU_SOURCE
#include <stdio.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <dlfcn.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <sys/select.h>
#include <string.h>
#include <termios.h>
#include <pthread.h>
#include <sys/epoll.h>
#include <stdlib.h>
#include <jni.h>

extern int my_epoll_wait(int epfd, struct epoll_event *events, int maxevents, int timeout);
extern int my_epoll_pwait(int epfd, struct epoll_event *events, int maxevents, int timeout, const sigset_t* ss);

int my_epoll_wait_arm(int epfd, struct epoll_event *events, int maxevents, int timeout)
{
  return  my_epoll_wait(epfd,events,maxevents,timeout);
}
int my_epoll_pwait_arm(int epfd, struct epoll_event *events, int maxevents, int timeout, const sigset_t* ss)
{
   return my_epoll_pwait(epfd,events,maxevents,timeout,ss);
}
