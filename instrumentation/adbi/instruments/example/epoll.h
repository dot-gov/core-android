/*
 * epoll.h
 *
 *  Created on: Mar 6, 2015
 *      Author: zad
 */

#ifndef EPOLL_H_
#define EPOLL_H_

struct binder_state
{
    int fd;
    void *mapped;
    unsigned mapsize;
};

#endif /* EPOLL_H_ */
