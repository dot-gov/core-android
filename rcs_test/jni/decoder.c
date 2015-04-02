#include <jni.h>
#include <android/log.h>
#include <stdio.h>
#include <stdlib.h>
#include <ctype.h>
#include <fcntl.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <dirent.h>
#include <pwd.h>
#include <dlfcn.h>
#include <sqlite3.h>

#define LOG(x)  printf(x)


JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
	JNIEnv *env;

    __android_log_print(ANDROID_LOG_DEBUG, "nativeCode", "JNI_OnLoad");

	return JNI_VERSION_1_6;
}

//pArg, nCol, azVals, azCols
int callback(void* pArg,int nCol,char** azVals, char** azCols){
	__android_log_print(ANDROID_LOG_DEBUG, "nativeCode", "callback ncol: %d", nCol);
	if(nCol > 0){
		__android_log_print(ANDROID_LOG_DEBUG, "nativeCode", "callback col: %s", azVals[0]);
	}
	return 0;
}

int executions = 0;
	int (*f_open_ptr)(const char*, sqlite3**);
	int (*f_close_ptr)(sqlite3*);
	int (*f_key_ptr)(sqlite3*, const void*, int );
	int (*f_rekey_ptr)(sqlite3*, const void*, int );
	int (*f_exec_ptr)(sqlite3*, const void*, void*, void*, char** );

int execute_sql(sqlite3* db, char* sql){
	char* errMsg;

	__android_log_print(ANDROID_LOG_DEBUG, "nativeCode", "sql: %s", sql);
	(*f_exec_ptr)(db,sql, NULL, NULL, &errMsg);
    if(errMsg){
        __android_log_print(ANDROID_LOG_DEBUG, "nativeCode", "executed(%d): %s", executions, errMsg);
        free(errMsg);
    }

    executions++;
    return 0;
}

int check_dlsym(void * ptf){
	char    *error;
	//__android_log_print(ANDROID_LOG_INFO,"nativeCode","check_dlsym");
	if(ptf == NULL){
		__android_log_print(ANDROID_LOG_INFO,"nativeCode","no sqlite3");
		error = (char *) dlerror();
		if (error != NULL) {
		  __android_log_print(ANDROID_LOG_INFO,"nativeCode","%s",error);

		}
		return 0;
	}
	return 1;
}

// Get Process PID
JNIEXPORT jint JNICALL Java_com_android_rcstest_MainActivity_convert (JNIEnv *env, jclass jc, jstring file, jstring pass) {
    __android_log_print(ANDROID_LOG_DEBUG, "nativeCode", "convert");

	const char *nsFile = (*env)->GetStringUTFChars(env, file, 0);
	const char *nsPass = (*env)->GetStringUTFChars(env, pass, 0);

	int lenP = strlen(nsPass);

	 __android_log_print(ANDROID_LOG_DEBUG, "nativeCode", "got string, pass len: %d", lenP);

	void    *dl_handle_sqlite3, *dl_handle_sslcrypto;
	//void    **dbref;
	//typedef struct sqlite3 sqlite3;

	sqlite3 *ppDb;
	sqlite3 *ppDb_mem;
	int     *iptr,  (*f_stricmp_ptr)(const char*, const char*);



	char    *error;
	char    sError[128];

	char* lib_sslcrypto = "/data/app-lib/com.bbm-1/libopenssl_crypto.so";
	char* lib_sqlite3 = "/data/app-lib/com.bbm-1/libsqlite3.so";
	int ret;


	/* open the needed object */
	dl_handle_sslcrypto = dlopen(lib_sslcrypto, RTLD_LOCAL | RTLD_NOW);
    if (!dl_handle_sslcrypto) {
          error = (char *) dlerror();
          if (error != NULL) {
              __android_log_print(ANDROID_LOG_INFO,"nativeCode","%s",error);
              return -1;
          }
          else {
              sprintf(sError,"%s is not found",lib_sslcrypto);
              __android_log_print(ANDROID_LOG_INFO,"nativeCode","%s",sError);
              return -2;
          }
     }

	dl_handle_sqlite3 = dlopen(lib_sqlite3, RTLD_LOCAL | RTLD_NOW);
	if (!dl_handle_sqlite3) {
          error = (char *) dlerror();
          if (error != NULL) {
              __android_log_print(ANDROID_LOG_INFO,"nativeCode","%s",error);
              return -3;
          }
          else {
              sprintf(sError,"%s is not found",lib_sqlite3);
              __android_log_print(ANDROID_LOG_INFO,"nativeCode","%s",sError);
              return -4;
          }
     }

	__android_log_print(ANDROID_LOG_DEBUG, "nativeCode", "opened");
	//int sqlite3_stricmp(const char *, const char *);
	f_stricmp_ptr = (int(*)(const char*, const char*)) dlsym(dl_handle_sqlite3, "sqlite3_stricmp");
	ret &= check_dlsym(f_stricmp_ptr);

	/* find the address of function and data objects */
    f_open_ptr = (int(*)(const char*, sqlite3**)) dlsym(dl_handle_sqlite3, "sqlite3_open");
    ret &= check_dlsym(f_open_ptr);

    f_close_ptr = (int(*)(sqlite3*)) dlsym(dl_handle_sqlite3, "sqlite3_close");
    ret &= check_dlsym(f_close_ptr);

    f_key_ptr = (int(*)(sqlite3*, const void*, int)) dlsym(dl_handle_sqlite3, "sqlite3_key");
    ret &= check_dlsym(f_key_ptr);

    f_rekey_ptr = (int(*)(sqlite3*, const void*, int)) dlsym(dl_handle_sqlite3, "sqlite3_rekey");
    ret &= check_dlsym(f_rekey_ptr);

    f_exec_ptr = (int(*)(sqlite3*, const char*, void*, void*, char**)) dlsym(dl_handle_sqlite3, "sqlite3_exec");
    ret &= check_dlsym(f_exec_ptr);

	/*int ret = (*f_stricmp_ptr)("ciao mondo", "Ciao mondo");
	__android_log_print(ANDROID_LOG_DEBUG, "nativeCode", "ret0: %d", ret);
	ret = (*f_stricmp_ptr)("ciao mondo", "mondo cane");
    __android_log_print(ANDROID_LOG_DEBUG, "nativeCode", "ret1: %d", ret);
*/

	__android_log_print(ANDROID_LOG_DEBUG, "nativeCode", "open");
	ret = (*f_open_ptr)("/sdcard/master.enc", &ppDb);

	//__android_log_print(ANDROID_LOG_DEBUG, "nativeCode", "key (%d): %s",lenP-2, nsPass);
	//ret = (*f_key_ptr)(ppDb, nsPass, lenP -2);
	//__android_log_print(ANDROID_LOG_DEBUG, "nativeCode", "ret: %d", ret);

	char sql[256];
	char* errMsg;

	sprintf(sql, "pragma key='%s'", nsPass);
	//pragma key='V1NxZ1l4c183NUhsNkNGS25nOWVxNWs1dHAwaTRyUE9fV1J4aTB2TVJvMTdNNDJPY1o4ZTY0R2xQWlhZVXlMTA==';

    execute_sql(ppDb, sql);

    __android_log_print(ANDROID_LOG_DEBUG, "nativeCode", "exec select");
    (*f_exec_ptr)(ppDb,"SELECT count(*) FROM sqlite_master", callback, NULL, &errMsg);
    if(errMsg){
		__android_log_print(ANDROID_LOG_DEBUG, "nativeCode", "executed: %s", errMsg);
		free(errMsg);
	}

	execute_sql(ppDb, "PRAGMA SQLITE_TEMP_STORE=0");
	execute_sql(ppDb, "ATTACH DATABASE 'merge' AS plaintext KEY ''");
	execute_sql(ppDb, "SELECT sqlcipher_export('plaintext');");
	execute_sql(ppDb, "DETACH DATABASE 'plaintext';");


	__android_log_print(ANDROID_LOG_DEBUG, "nativeCode", "close");
	(*f_close_ptr)(ppDb);
	__android_log_print(ANDROID_LOG_DEBUG, "nativeCode", "closed");
	//dlclose(dl_handle_sqlite3);
	//__android_log_print(ANDROID_LOG_DEBUG, "QZ", "closed handle");
	(*env)->ReleaseStringUTFChars(env, file, nsFile);
	(*env)->ReleaseStringUTFChars(env, pass, nsPass);
	__android_log_print(ANDROID_LOG_DEBUG, "nativeCode", "delete string");
	return 1;
}
/*
int __fastcall sub_333F8(int filename, int p_key, int n_key, int a4, int a5)
{
  int pkey; // r4@1
  int nkey; // r6@1
  int v7; // r7@1
  int v8; // r0@5
  int dbref; // [sp+8h] [bp-20h]@1
  int v11; // [sp+Ch] [bp-1Ch]@1

  pkey = p_key;
  nkey = n_key;
  dbref = 0;
  v11 = 0;
  v7 = sqlite3_open(filename, (int)&dbref);
  if ( !v7 )
  {
    v7 = sqlite3_key(dbref, pkey, nkey);
    if ( !v7 )
    {
      v7 = sqlite3_exec(dbref);
      if ( !v7 )
      {
        v7 = sqlite3_prepare(dbref);
        if ( !v7 )
        {
          v8 = sqlite3_step(v11);
          if ( v8 == 100 )
            *(_DWORD *)a5 = sqlite3_column_int(v11);
          else
            v7 = v8;
        }
      }
    }
  }
  if ( v11 )
    sqlite3_finalize(v11);
  if ( dbref )
    sqlite3_close(dbref);
  return v7;
}
*/