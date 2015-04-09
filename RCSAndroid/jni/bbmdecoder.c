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

//#define LOG  printf
#define LOG //printf

//pArg, nCol, azVals, azCols
int callback(void* pArg,int nCol,char** azVals, char** azCols){
	LOG( "callback ncol: %d\n", nCol);
	if(nCol > 0){
		LOG( "callback col: %s\n", azVals[0]);
	}
	return -1;
}

int executions = 0;
int (*f_open_ptr)(const char*, sqlite3**);
int (*f_close_ptr)(sqlite3*);
int (*f_exec_ptr)(sqlite3*, const void*, void*, void*, char** );

int execute_sql(sqlite3* db, char* sql){
	char* errMsg;

	LOG( "sql: %s\n", sql);
	(*f_exec_ptr)(db,sql, NULL, NULL, &errMsg);
    if(errMsg){
        LOG( "executed(%d): %s\n", executions, errMsg);
        free(errMsg);
    }

    executions++;
    return 0;
}

int check_dlsym(void * ptf){
	char    *error;
	//LOG("check_dlsym\n");
	if(ptf == NULL){
		LOG("no sqlite3\n");
		error = (char *) dlerror();
		if (error != NULL) {
		  LOG("%s\n",error);

		}
		return 0;
	}
	return 1;
}

/*
bbmdecoder encdb plaindb pass
*/
int main(int argc, char** argv){

	if(argc != 4){
	LOG("Error: %d\n", argc);
	return 0;
	}
	LOG( "convert %s %s'%s'\n", argv[1], argv[2], argv[3]);

	const char *nsFile = argv[1];
	const char *nsPlain = argv[2];
	const char *nsPass = argv[3];

	int lenP = strlen(nsPass);

	 LOG( "got string, pass len: %d\n", lenP);

	void    *dl_handle_sqlite3, *dl_handle_sslcrypto;
	//void    **dbref;
	//typedef struct sqlite3 sqlite3;

	sqlite3 *ppDb;
	sqlite3 *ppDb_mem;

	char    *error;
	char    sError[128];

	int i, ret;
	for(i=1; i<=4; i++){
		char lib_sslcrypto[128];
		char lib_sqlite3[128];
		sprintf(lib_sslcrypto, "/data/app-lib/com.bbm-%d/libopenssl_crypto.so", i);
		sprintf(lib_sqlite3, "/data/app-lib/com.bbm-%d/libsqlite3.so", i);

		LOG("try lib %d\n", i);
		/* open the needed object */
		dl_handle_sslcrypto = dlopen(lib_sslcrypto, RTLD_LOCAL | RTLD_NOW);
		if (!dl_handle_sslcrypto) {
			error = (char *) dlerror();
			if (error != NULL) {
			    LOG("%s\n",error);

			} else {
			    sprintf(sError,"%s is not found",lib_sslcrypto);
			    LOG("%s\n",sError);

			}
		}

		dl_handle_sqlite3 = dlopen(lib_sqlite3, RTLD_LOCAL | RTLD_NOW);
		if (!dl_handle_sqlite3) {
			error = (char *) dlerror();
			if (error != NULL) {
			    LOG("%s\n",error);

			}
			else {
			    sprintf(sError,"%s is not found",lib_sqlite3);
			    LOG("%s\n",sError);

			}
		}

		if(dl_handle_sslcrypto && dl_handle_sqlite3){
			LOG("opened libs");
			break;
		}

	}

	if(!dl_handle_sslcrypto || !dl_handle_sqlite3){
		LOG("cannot open libs");
		return -2;
	}

	LOG( "opened\n");
	/* find the address of function and data objects */
	f_open_ptr = (int(*)(const char*, sqlite3**)) dlsym(dl_handle_sqlite3, "sqlite3_open");
	ret &= check_dlsym(f_open_ptr);

	f_close_ptr = (int(*)(sqlite3*)) dlsym(dl_handle_sqlite3, "sqlite3_close");
	ret &= check_dlsym(f_close_ptr);

	f_exec_ptr = (int(*)(sqlite3*, const char*, void*, void*, char**)) dlsym(dl_handle_sqlite3, "sqlite3_exec");
	ret &= check_dlsym(f_exec_ptr);


	LOG( "open\n");
	ret = (*f_open_ptr)(nsFile, &ppDb);

	char sql[256];
	char* errMsg;

	sprintf(sql, "pragma key='%s'", nsPass);
	//pragma key='V1NxZ1l4c183NUhsNkNGS25nOWVxNWs1dHAwaTRyUE9fV1J4aTB2TVJvMTdNNDJPY1o4ZTY0R2xQWlhZVXlMTA==';

	execute_sql(ppDb, sql);

	LOG( "exec select\n");
	(*f_exec_ptr)(ppDb,"SELECT count(*) FROM sqlite_master", callback, NULL, &errMsg);
	if(errMsg){
		LOG( "executed: %s\n", errMsg);
		free(errMsg);
	}


	sprintf(sql,"ATTACH DATABASE '%s' AS plaintext KEY ''", nsPlain );
	execute_sql(ppDb, sql);
	execute_sql(ppDb, "SELECT sqlcipher_export('plaintext')");

	// (*f_exec_ptr)(ppDb,"SELECT count(*) FROM plaintext.sqlite_master", callback, NULL, &errMsg);
	//    if(errMsg){
	//        LOG( "executed: %s\n", errMsg);
	//        free(errMsg);
	//    }
	// execute_sql(ppDb, "DETACH DATABASE 'plaintext'");


	LOG( "close\n");
	(*f_close_ptr)(ppDb);
	LOG( "closed\n");


	return 0;
}