#ifndef _HEADER_COMMON
#define _HEADER_COMMON
struct dalvik_cache_t
{
   // for the call inside the hijack
   jclass cls_h;
   jmethodID mid_h;
};
typedef int32_t     status_t;
#define RES_DO_NOTHING 1
#endif //#ifndef _HEADER_EXTERN_STUFF
