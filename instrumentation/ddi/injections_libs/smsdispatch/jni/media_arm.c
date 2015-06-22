#include "hook.h"
#include "dexstuff.h"
#include "dalvik_hook.h"
#include "extern.h"
#include "base.h"
#include "common.h"

extern int instrument_media(void );
status_t mediaRecorder_start(void);
status_t mediaRecorder_start_arm(void)
{
   log("mediaRecorder_start_arm start\n");
      return mediaRecorder_start();
}
int instrument_media_arm(void){
   log("instrument media arm\n");
   return instrument_media();
}
