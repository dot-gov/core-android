#ifndef _HEADER_EXTERN_STUFF
#define _HEADER_EXTERN_STUFF
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

/*
 * Here are declared extern variables and function common to
 * all the function injected by this library
 */

// Extern variable
extern struct dexstuff_t d;
extern char *dumpPath;
extern char *dex;
extern char *process;
extern char *quite_needle;
extern char *dexFile_default;
extern char *dumpDir_default;
extern char *dumpDir;
extern long int and_maj;
extern long int and_min;
extern long int and_rel;

//Function and Struct
int load_dext(char * dext_path,char **classes);
void create_cnf(char *cnf_name);
char * who_am_i(unsigned int max_len);
void my_log(char *msg);
void my_log2(char *msg);
void get_android_version();
char * _fgetln(FILE *stream,size_t *len);
char * findLast(char *where, char *what);


struct hook_t eph_epoll_w;
struct hook_t eph_epoll_p;
struct hook_t eph_media_const;
struct hook_t eph_media_start;



#endif //#ifndef _HEADER_EXTERN_STUFF
