/*
 * binderz.h
 *
 *  Created on: Mar 6, 2015
 *      Author: zad
 */

#ifndef BINDERZ_H_
#define BINDERZ_H_
struct binder_state
{
    int fd;
    void *mapped;
    unsigned mapsize;
};
struct binder_io
{
    char *data;            /* pointer to read/write from */
    uint32_t *offs;        /* array of offsets */
    uint32_t data_avail;   /* bytes available in data buffer */
    uint32_t offs_avail;   /* entries available in offsets array */

    char *data0;           /* start of data buffer */
    uint32_t *offs0;       /* start of offsets buffer */
    uint32_t flags;
    uint32_t unused;
};

#endif /* BINDERZ_H_ */
