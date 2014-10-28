/*
 * =====================================================================================
 *
 *       Filename:  bson.cpp
 *
 *    Description:  library to parse and serialise bson stream
 *
 *        Version:  1.0
 *        Created:  15/10/2014 16:11:22
 *       Revision:  none
 *       Compiler:  gcc
 *
 *         Author:  zad
 *   Organization:  ht
 *
 * =====================================================================================
 */

#include "bson.h"
#include "libsonDefine.h"

#include "bson_obj.h"
#include <string>
#include <fstream>
#include <unistd.h>

#include <sys/stat.h>
#include <boost/filesystem/path.hpp>
#include <boost/filesystem.hpp>
#include <boost/regex.hpp>
#include <android/log.h>
char tag[256];
#define logd(...) {\
tag[0]=tag[1]=0;\
snprintf(tag,256,"libbsonJni:%s",__FUNCTION__);\
__android_log_print(ANDROID_LOG_DEBUG, tag , __VA_ARGS__);}
using namespace std;
using namespace bson;


/*
 * Function declarations
 */
string getTypeDir(int type);
string merge_fragment(string baseDir,int type,long int ts);
int check_dir(string dirPath);
int file_exist(string file);
string getFileName(string baseDir,int type,long int ts,int fragment);

/*
 * Returns: a filename for the type,ts,and fragment of a file.
 * empty sting in case of error
 */


string getFileName(string baseDir,int type,long int ts,int fragment)
{
  string filename ;
  logd("basedir.e=%d type=%d ts=%ld fragment=%d ",baseDir.empty(),type,ts,fragment);
  if(!baseDir.empty() && type>0 && ts>=0 && fragment>=FRAGMENT_VALID && !getTypeDir(type).empty()){
    string type_dir=  baseDir + "/" + getTypeDir(type);
    if( check_dir(type_dir)==0){
      if(fragment>=0){
        filename = type_dir + "/" + boost::lexical_cast<std::string>(ts) + "_" + boost::lexical_cast<std::string>(fragment);
      }else if(fragment==FRAGMENT_WILDCHAR){
        logd("FRAGMENT_WILDCHAR");
        filename = type_dir + "/" + boost::lexical_cast<std::string>(ts) + "_";
      }else{
        logd("FRAGMENT_STRIP");
        filename = type_dir + "/" + boost::lexical_cast<std::string>(ts);
      }
      logd("filename=%s",filename.c_str());
    }
  }
  return filename;
}

int regular_file(string file){
  if(!file.empty()){
    const char *file_s=file.c_str();
    struct stat s;
    if (!stat(file_s, &s))
    {
      if (S_ISREG(s.st_mode))
      {
        logd("regular %s\n", file_s);
        return 1;
      }else{
        logd("irregular %s\n", file_s);
      }
    }
    else
    {
      logd("Can't stat: %s\n", file_s);
    }
  }
  return 0;
}

int file_exist(string file)
{
  struct stat results;
  if (!file.empty() && stat(file.c_str(), &results) == 0){
    return 1;
  }
  return 0;
}

int check_dir(string dirPath)
{

  if(!dirPath.empty()){
    try{
      if (file_exist(dirPath)){
        return 0;
      }else
      {
        logd("Try to create %s",dirPath.c_str());
        boost::filesystem::path dir((const char *)dirPath.c_str());

        logd("path ok %s",dir.c_str());
        if(boost::filesystem::create_directory(dir))
        {
          logd("success %s",dirPath.c_str());
          return 0;
        }
        logd("failed %s",dirPath.c_str());
      }
    }catch(std::exception const&  ex)
    {
      logd("Can't create dir. %s", ex.what());
    }
  }


  return 1;
}

// ------------------------------------------------------------------
/*!
    Convert a hex string to a block of data
*/
void fromHex(
    const std::string &in,              //!< Input hex string
    void *const data                    //!< Data store
    )
{
    size_t          length      = in.length();
    unsigned char   *byteData   = reinterpret_cast<unsigned char*>(data);

    std::stringstream hexStringStream; hexStringStream >> std::hex;
    for(size_t strIndex = 0, dataIndex = 0; strIndex < length; ++dataIndex)
    {
        // Read out and convert the string two characters at a time
        const char tmpStr[3] = { in[strIndex++], in[strIndex++], 0 };

        // Reset and fill the string stream
        hexStringStream.clear();
        hexStringStream.str(tmpStr);

        // Do the conversion
        int tmpValue = 0;
        hexStringStream >> tmpValue;
        byteData[dataIndex] = static_cast<unsigned char>(tmpValue);
    }
}
/*
 * saves a file payload in the correct place
 *
 * returns: 1 in case of error
 * returns: 0 in case of success
 */

int save_payload(int type,string filename, char* payload, int payloadSize)
{
  int result=1;
  if(payload!=NULL && !filename.empty() && payloadSize>0)
  {
    ofstream file(filename.c_str(), ios::out | ios::binary);
    if(type!=TYPE_TEXT)
    {
      stringstream ss;
      ss >> std::hex;
      ss.str(payload);

      char *tmp= new char[2];
      while(payloadSize>0)
      {
        ss.read(tmp,2);
        char unsigned x = static_cast<char unsigned>(strtoul(tmp, NULL, 16));
        file << x;
        payloadSize-=2;
      }
    }else{
      file.write(payload,payloadSize);
    }
    file.close();
    result = !file_exist(filename);
  }
  return result;
}

int append_file(string dst,string src)
{
  std::ifstream ifile(src.c_str(), std::ios::in);
  std::ofstream ofile(dst.c_str(), std::ios::out | std::ios::app);

  //check to see that it exists:
  if (!ifile.is_open()) {
    logd("failed to open %s ",src.c_str());
    ofile.close();
    return 1;
  }
  else {
      ofile << ifile.rdbuf();
  }
  ifile.close();
  ofile.close();
  return 0;
}

string merge_fragment(string baseDir,int type,long int ts)
{
  bool error=false;

  const std::string target_path =  baseDir + "/" + getTypeDir(type);
  const string file_mask = getFileName(baseDir,type,ts,FRAGMENT_WILDCHAR);
  string newFile;
  if(!file_mask.empty()){
    try{
      std::vector< std::string > all_matching_files;
      boost::filesystem::directory_iterator end_itr; // Default ctor yields past-the-end


      for( boost::filesystem::directory_iterator i( target_path ); i != end_itr; ++i )
      {
        const char* tmp = i->path().string().c_str();
        const char* tmp_src=file_mask.c_str();
        logd("checking %s --> %s",tmp,tmp_src);
        // Skip if not a file

        if( !regular_file( tmp ) ){
          logd("not a file");
          continue;
        }

        logd("against  %s",tmp_src);
        // Skip if no match
        if(strlen(tmp)>=strlen(tmp_src)){
          if (strncmp(tmp,tmp_src,strlen(tmp_src)) == 0)
          {
            logd("adding %s",tmp );
            all_matching_files.push_back( i->path().string());
          }
        }
      }
      newFile=getFileName(baseDir,type,ts,FRAGMENT_STRIP);
      boost::filesystem::path newFilePath(newFile.c_str());
      boost::filesystem::remove(newFilePath);
      sort (all_matching_files.begin(), all_matching_files.end(), less<string>());
      for(std::vector<string>::iterator it = all_matching_files.begin(); it != all_matching_files.end(); ++it) {
        const char* tmpFile = (*it).c_str();
        logd("appending %s-->%s", tmpFile,newFile.c_str());
        if(append_file(newFile,*it)){
          error=true;
        }
        if(boost::filesystem::remove(*it)){
          logd("removed %s",tmpFile);
        }else{
          logd("failed to remove %s",tmpFile);
          error=true;
        }
      }
    } catch(std::exception const&  ex)
    {
      logd("Can't merge file %s", ex.what());
    }
  }else{
    logd("empty file mask");
    error = true;
  }
  if(!error)
    return newFile;
  return "Error";
}

string getTypeDir(int type)
{
  string dirName ;
  switch(type){
  case TYPE_TEXT:
    return TYPE_TEXT_DIR;
  case TYPE_AUDIO:
    return TYPE_AUDIO_DIR;
  case TYPE_IMGL:
    return TYPE_IMG_DIR;
  case TYPE_VIDEO:
    return TYPE_VIDEO_DIR;
  case TYPE_HTML:
    return TYPE_HTML_DIR;
  default:
    break;
  }
  return dirName;
}



typedef struct _file_signature
{
  const char * signature;
  const char * h_name;
}file_signature;

file_signature images[]={
    {"FFD8FFE0","JPG"},
    {"49492A","TIFF"},
    {"424D","BMP"},
    {"474946","GIF"},
    {"89504E470D0A1A0A","PNG"},
    {NULL,NULL},
};

int isImage(char * payload,file_signature* fs)
{
  int i=0;
  logd("Start");
  while( fs->h_name!=NULL && payload!=NULL){
    logd("checking %d %p %s",i,fs,fs->h_name);
    if(strlen(fs->signature)<=strlen(payload)){
      if (strncasecmp(fs->signature,payload,strlen(fs->signature)) == 0)
      {
        logd("found %s",fs->h_name);
        return 1;
      }
    }
    fs++;
    i++;
  }
  return 0;
}

int check_filebytype(int type, char *payload)
{
  int res=1;
  switch(type){
   case TYPE_TEXT:
     res=0;
     break;
   case TYPE_AUDIO:
     res=0;
     break;
   case TYPE_IMGL:
     return !isImage(payload, images);
     break;
     res=0;
     break;
   case TYPE_VIDEO:

   case TYPE_HTML:
     break;
   default:
     break;
   }
  return res;
}

/*
 * saves the payload in the correct place and format
 *
 * returns: 1 in case of error
 * returns: 0 in case of success
 */

string save_payload_type(string baseDir,int type,long int ts,int fragment,char* payload, int payloadSize)
{
  string result;
  logd("payload %p basedir.e?=%d payloadSize=%d",payload,baseDir.empty(),payloadSize);
  if(payload!=NULL && !baseDir.empty() && payloadSize>0){
      if(save_payload(type,getFileName(baseDir,type,ts,fragment),payload,payloadSize)==0){
        result = getFileName(baseDir,type,ts,fragment);
      }
      if(fragment==0){
        result = merge_fragment(baseDir,type,ts);
        if(check_filebytype(type,payload)==0){
          logd("file ok");
        }
      }

  }
  logd("result=%s",result.c_str());
  return result;
}


void GetJStringContent(JNIEnv *AEnv, jstring AStr, std::string &ARes)
{
  if (!AStr) {
    ARes.clear();
    return;
  }
  const char *s = AEnv->GetStringUTFChars(AStr,NULL);
  ARes=s;
  AEnv->ReleaseStringUTFChars(AStr,s);
}



//char bson_s[] = { 0x16,0x00,0x00,0x00,0x05,'s','a','l','u','t','o',0x00,0x04,0x00,0x00,0x00,0x00,'c','i','a','o',0x0 };
/*
0x35 0x0 0x0 0x0
0x5
0x63 0x6d 0x64 0x0
0x4 0x0 0x0 0x0
0x0
0x73 0x61 0x76 0x65
0x10
0x74 0x79 0x70 0x65 0x0
0x1 0x0 0x0 0x0
0x5
0x70 0x61 0x79 0x6c 0x6f 0x61 0x64 0x0
0xa 0x0 0x0 0x0
0x0
0x63 0x69 0x61 0x6f 0x20 0x6d 0x6f 0x6e 0x64 0x6f 0x0
*/
JNIEXPORT jstring JNICALL Java_org_benews_BsonBridge_serialize(JNIEnv *env, jclass obj, jstring basedir, jobject bson_s)
{
  logd("serialize called");
  jstring resS=NULL;
  //jbyte* arry = env->GetByteArrayElements(bson_s,NULL);
  char *arry = (char *)env->GetDirectBufferAddress(bson_s);
  if(bson_s!=NULL){
    jsize lengthOfArray = env->GetDirectBufferCapacity(bson_s);

    //for (int i=0 ; i < lengthOfArray; i++){
    //logd("->0x%x",(char *)a[i]);
    //}
    if(lengthOfArray>4){
      logd("converted %d:%s",lengthOfArray,arry);
      bo y = BSONObj((char *)arry);
      logd("obj retrived");
     // env->ReleaseByteArrayElements(bson_s,arry, JNI_ABORT);
      be *ptr;
      string res ;
      int element=0;
      string value;
      be ts=y.getField("ts");
      logd("got elemets");
      if(ts.size()>0 && ts.type()==NumberLong){
        ptr=&ts;
        logd("got ts");
        value = boost::lexical_cast<std::string>(ptr->Long());
        res += boost::lexical_cast<std::string>(ptr->fieldName())  + "=" + value + "\n";
        logd("result %s",res.c_str());
        element++;
      }
      be type=y.getField("type");
      if(type.size()>0 && type.type()==NumberInt){
        ptr=&type;
        logd("got type");
        value = boost::lexical_cast<std::string>(ptr->Int());
        res += boost::lexical_cast<std::string>(ptr->fieldName())  + "=" + value + "\n";
        logd("result %s",res.c_str());
        element++;
      }
      be frag=y.getField("frag");
      if(frag.size()>0 && frag.type()==NumberInt){
        ptr=&frag;
        logd("got frag");
        value = boost::lexical_cast<std::string>(ptr->Int());
        res += boost::lexical_cast<std::string>(ptr->fieldName())  + "=" + value + "\n";
        logd("result %s",res.c_str());
        element++;
      }
      be payload=y.getField("payload");
      if(payload.size()>0){
        ptr=&payload;
        logd("got payload");
        //int a;
        //value = boost::lexical_cast<std::string>(ptr->binData(a));
        //value = value.substr(0,a);
        //res += boost::lexical_cast<std::string>(ptr->fieldName())  + "=" + value + "\n";
        //logd("result %s",res.c_str());
        element++;
      }
      ptr=NULL;

      if(element==ELEMENT2PROCESS){
        string basedir_str;
        GetJStringContent(env,basedir,basedir_str);
        logd("returning %s",res.c_str());
        int a;
        const char *payloadArray=payload.binData(a);
        res=save_payload_type(basedir_str,type.Int(),ts.Long(),frag.Int(),(char *)payloadArray,a);
        resS = env->NewStringUTF(res.c_str());
      }
    }else{
     // env->ReleaseByteArrayElements(bson_s,arry, JNI_ABORT);
    }
  }
  if(resS==NULL){
    resS = env->NewStringUTF("Fails");
  }
  return  resS;
}



JNIEXPORT jbyteArray JNICALL Java_org_benews_BsonBridge_getToken(JNIEnv * env, jclass, jint imei, jint ts)
{
  bob bson;
  bson.append("imei",imei);
  bson.append("ts",ts);
  bo ret =bson.obj();
  jbyteArray arr = env->NewByteArray(ret.objsize());
  env->SetByteArrayRegion(arr,0,ret.objsize(), (jbyte*)ret.objdata());
  return arr;
}
