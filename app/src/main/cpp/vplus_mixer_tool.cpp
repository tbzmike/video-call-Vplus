#include <dlfcn.h>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <algorithm>
#include <cctype>

enum mixer_ctl_type { MIXER_CTL_TYPE_BOOL, MIXER_CTL_TYPE_INT, MIXER_CTL_TYPE_ENUM, MIXER_CTL_TYPE_BYTE, MIXER_CTL_TYPE_IEC958, MIXER_CTL_TYPE_INT64, MIXER_CTL_TYPE_UNKNOWN };
struct mixer; struct mixer_ctl;
struct Api {
 void* h=nullptr; mixer*(*open)(unsigned)=nullptr; void(*close)(mixer*)=nullptr; unsigned(*count)(const mixer*)=nullptr; mixer_ctl*(*get)(mixer*,unsigned)=nullptr;
 const char*(*name)(const mixer_ctl*)=nullptr; mixer_ctl_type(*type)(const mixer_ctl*)=nullptr; unsigned(*values)(const mixer_ctl*)=nullptr; int(*getv)(const mixer_ctl*,unsigned)=nullptr; int(*setv)(mixer_ctl*,unsigned,int)=nullptr; int(*min)(const mixer_ctl*)=nullptr; int(*max)(const mixer_ctl*)=nullptr;
 bool load(){ const char* p[]={"/vendor/lib64/libtinyalsa.so","/system/lib64/libtinyalsa.so","/vendor/lib/libtinyalsa.so","/system/lib/libtinyalsa.so"}; for(auto x:p){h=dlopen(x,RTLD_NOW);if(h)break;} if(!h)return false;
#define L(x) x=reinterpret_cast<decltype(x)>(dlsym(h,#x)); if(!x)return false
 L(open);L(close);L(count);L(get);L(name);L(type);L(values);L(getv);L(setv);L(min);L(max);return true; }
} a;
static bool cand(const char* raw){if(!raw)return false;std::string n(raw);std::transform(n.begin(),n.end(),n.begin(),[](unsigned char c){return (char)std::tolower(c);});if(n.find("rx")==std::string::npos||n.find("tx")!=std::string::npos||n.find("mic")!=std::string::npos||n.find("capture")!=std::string::npos||n.find("adc")!=std::string::npos)return false;return n.find("digital volume")!=std::string::npos||n.find("rx volume")!=std::string::npos||n.find("rx gain")!=std::string::npos;}
int main(int argc,char**argv){if(!a.load()){fprintf(stderr,"TinyALSA unavailable\n");return 2;}mixer*m=a.open(0);if(!m){fprintf(stderr,"mixer_open(0) failed\n");return 3;}unsigned n=a.count(m);const char*cmd=argc>1?argv[1]:"scan";
if(!strcmp(cmd,"scan")){printf("card=0 controls=%u\n",n);for(unsigned i=0;i<n;i++){auto*c=a.get(m,i);if(cand(a.name(c))&&a.type(c)==MIXER_CTL_TYPE_INT)printf("candidate=%s values=%u range=%d..%d current=%d\n",a.name(c),a.values(c),a.min(c),a.max(c),a.getv(c,0));}a.close(m);return 0;}
if(!strcmp(cmd,"snapshot")){for(unsigned i=0;i<n;i++){auto*c=a.get(m,i);if(!c||!cand(a.name(c))||a.type(c)!=MIXER_CTL_TYPE_INT)continue;for(unsigned v=0;v<a.values(c);v++)printf("%u|%u|%d|%s\n",i,v,a.getv(c,v),a.name(c));}a.close(m);return 0;}
if(!strcmp(cmd,"restore")&&argc>=3){FILE*f=fopen(argv[2],"r");if(!f){perror("snapshot");a.close(m);return 4;}char line[1024];unsigned ok=0;while(fgets(line,sizeof(line),f)){unsigned id=0,v=0;int value=0;if(sscanf(line,"%u|%u|%d",&id,&v,&value)==3){auto*c=a.get(m,id);if(c&&a.type(c)==MIXER_CTL_TYPE_INT&&v<a.values(c)&&a.setv(c,v,value)==0)ok++;}}fclose(f);printf("restored=%u\n",ok);a.close(m);return 0;}
if(!strcmp(cmd,"boost")&&argc>=3){int p=std::max(100,std::min(200,atoi(argv[2])));unsigned changed=0;for(unsigned i=0;i<n;i++){auto*c=a.get(m,i);if(!c||!cand(a.name(c))||a.type(c)!=MIXER_CTL_TYPE_INT||!a.values(c))continue;int lo=a.min(c),hi=a.max(c),cur=a.getv(c,0);if(hi<=lo)continue;int target=std::min(hi,cur+((hi-cur)*(p-100))/1000);if(a.setv(c,0,target)==0){printf("%s: %d -> %d\n",a.name(c),cur,target);changed++;}}printf("changed=%u\n",changed);a.close(m);return 0;}a.close(m);fprintf(stderr,"usage: scan|snapshot|restore FILE|boost 100..200\n");return 1;}
