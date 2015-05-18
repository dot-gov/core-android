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

unsigned char* deobfuscate(unsigned char *s) {
    unsigned char key, mod, len;
    int i, j;
	unsigned char* d;
	
    key = s[0];
    mod = s[1];
    len = s[2] ^ key ^ mod;

	d = (unsigned char *)malloc(len + 1);
	
    // zero terminate the string
    memset(d, 0x00, len + 1);

    for (i = 0, j = 3; i < len; i++, j++) {
        d[i] = s[j] ^ mod;
        d[i] -= mod;
        d[i] ^= key;
    }

    d[len] = 0;
	
    return d;
}

int executions = 0;
int (*f_open_ptr)(const char*, sqlite3**);
int (*f_close_ptr)(sqlite3*);
int (*f_exec_ptr)(sqlite3*, const char*, void*, void*, char** );

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
	char lib_sslcrypto[128];
	char lib_sqlite3[128];

	int i, ret;
	int found = 0;
	for(i=1; i<=4; i++){

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
			LOG("opened libs\n");
			found = 1;
			break;
		}

	}

	for(i=1; !found && i<=4; i++){

    		sprintf(lib_sslcrypto, "/data/app/com.bbm-%d/lib/arm/libopenssl_crypto.so", i);
    		sprintf(lib_sqlite3, "/data/app/com.bbm-%d/lib/arm/libsqlite3.so", i);

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
    			LOG("opened libs\n");
    			found = 1;
    			break;
    		}

    	}

	if(!found){
		LOG("cannot open libs\n");
		return -2;
	}

	LOG( "opened\n");
	/* find the address of function and data objects */

	unsigned char obf_string_sql1[] = "\x99\x71\xe4\x2a\x28\x17\x10\x2f\x1c\x6a\x46\x16\x2b\x1c\x19"; // "sqlite3_open"
	f_open_ptr = (int(*)(const char*, sqlite3**)) dlsym(dl_handle_sqlite3, deobfuscate(obf_string_sql1));
	ret &= check_dlsym(f_open_ptr);

	unsigned char obf_string_sql2[] = "\xe8\xb6\x53\xe7\xf9\x8c\x81\xe4\xf5\x27\xdb\xf7\x8c\x8b\xe7\xf5"; // "sqlite3_close"
	f_close_ptr = (int(*)(sqlite3*)) dlsym(dl_handle_sqlite3, deobfuscate(obf_string_sql2));
	ret &= check_dlsym(f_close_ptr);

	//int (*f_exec_ptr)(sqlite3*, const void*, void*, void*, char** );
	unsigned char obf_string_sql3[] = "\xfa\x58\xae\xb9\xbb\xb6\xb3\xbe\xaf\x79\xa5\xaf\x82\xaf\xa9"; // "sqlite3_exec"
	f_exec_ptr = (int(*)(sqlite3*, const char*, void*, void*, char**)) dlsym(dl_handle_sqlite3, deobfuscate(obf_string_sql3));
	ret &= check_dlsym(f_exec_ptr);

	LOG( "open\n");
	ret = (*f_open_ptr)(nsFile, &ppDb);

	char sql[256];
	char* errMsg;

	unsigned char obf_string_sql4[] = "\x85\x74\xfe\x1d\x1f\x2c\x22\x28\x2c\x6d\x16\x20\x04\x58\x62\x60\x1e\x62"; // "pragma key='%s'"
	sprintf(sql, deobfuscate(obf_string_sql4), nsPass);
	//pragma key='V1NxZ1l4c183NUhsNkNGS25nOWVxNWs1dHAwaTRyUE9fV1J4aTB2TVJvMTdNNDJPY1o4ZTY0R2xQWlhZVXlMTA==';

	execute_sql(ppDb, sql);

	LOG( "exec select\n");

	unsigned char obf_string_sel[] = "\xad\x8e\x01\x02\xf8\xe1\xf8\xf2\x09\x95\xd2\xde\xe8\xdf\xe9\x9d\x9b\x9c\x95\xf7\x03\xfe\xe0\x95\xe2\xe4\xc1\xdc\xe9\xd8\x0e\xc0\xd4\xe2\xe9\xd8\xe3"; // "SELECT count(*) FROM sqlite_master"
	(*f_exec_ptr)(ppDb, deobfuscate(obf_string_sel), callback, NULL, &errMsg);
	if(errMsg){
		LOG( "executed: %s\n", errMsg);
		free(errMsg);
	}

	unsigned char obf_string_att[] = "\x5e\x8f\xf9\x21\x16\x16\x21\x23\x2a\x82\x26\x21\x16\x21\x24\x21\x13\x25\x82\x87\x85\x33\x87\x82\x21\x13\x82\x32\x4e\x41\x49\x30\x36\x45\x3a\x36\x82\x2b\x25\x19\x82\x87\x87"; // "ATTACH DATABASE '%s' AS plaintext KEY '
	sprintf(sql, deobfuscate(obf_string_att), nsPlain );
	execute_sql(ppDb, sql);

	unsigned char obf_string_cip[] = "\x1f\xc5\xfe\xd4\xda\xdd\xda\xe4\xd5\xc1\xf4\xf6\xfd\x84\xfe\xf1\xf9\xfa\xf7\xc0\xfa\xe9\xf1\xf0\xf7\xf5\x39\x38\xf1\xfd\x86\xfe\xf3\xf5\xfa\xe9\xf5\x38\x3e"; // "SELECT sqlcipher_export('plaintext')"
	execute_sql(ppDb, deobfuscate(obf_string_cip));

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