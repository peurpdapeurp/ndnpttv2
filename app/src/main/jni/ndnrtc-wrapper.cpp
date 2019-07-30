
#include "ndnrtc-wrapper.hpp"

#include <android/log.h>

#include <algorithm>
#include <boost/make_shared.hpp>
#include <boost/thread/mutex.hpp>
#include <boost/thread.hpp>
#include <ndn-cpp/security/key-chain.hpp>
#include <ndn-cpp/security/identity/memory-private-key-storage.hpp>
#include <ndn-cpp/security/identity/memory-identity-storage.hpp>
#include <ndn-cpp/security/policy/no-verify-policy-manager.hpp>
#include <ndn-cpp/threadsafe-face.hpp>

#include <ndnrtc/params.hpp>
#include <ndnrtc/remote-stream.hpp>

#include <string.h>
#include <jni.h>

static std::map<std::string, std::string> g_params;

// the function below was taken from here: https://github.com/named-data-mobile/NFD-android/blob/master/app/src/main/jni/nfd-wrapper.cpp
std::map<std::string, std::string>
getParams(JNIEnv* env, jobject jParams)
{
  std::map<std::string, std::string> params;
  
  jclass jcMap = env->GetObjectClass(jParams);
  jclass jcSet = env->FindClass("java/util/Set");
  jclass jcIterator = env->FindClass("java/util/Iterator");
  jclass jcMapEntry = env->FindClass("java/util/Map$Entry");
  
  jmethodID jcMapEntrySet      = env->GetMethodID(jcMap,      "entrySet", "()Ljava/util/Set;");
  jmethodID jcSetIterator      = env->GetMethodID(jcSet,      "iterator", "()Ljava/util/Iterator;");
  jmethodID jcIteratorHasNext  = env->GetMethodID(jcIterator, "hasNext",  "()Z");
  jmethodID jcIteratorNext     = env->GetMethodID(jcIterator, "next",     "()Ljava/lang/Object;");
  jmethodID jcMapEntryGetKey   = env->GetMethodID(jcMapEntry, "getKey",   "()Ljava/lang/Object;");
  jmethodID jcMapEntryGetValue = env->GetMethodID(jcMapEntry, "getValue", "()Ljava/lang/Object;");
  
  jobject jParamsEntrySet = env->CallObjectMethod(jParams, jcMapEntrySet);
  jobject jParamsIterator = env->CallObjectMethod(jParamsEntrySet, jcSetIterator);
  jboolean bHasNext = env->CallBooleanMethod(jParamsIterator, jcIteratorHasNext);

  while (bHasNext) {
    jobject entry = env->CallObjectMethod(jParamsIterator, jcIteratorNext);
    
    jstring jKey = (jstring)env->CallObjectMethod(entry, jcMapEntryGetKey);
    jstring jValue = (jstring)env->CallObjectMethod(entry, jcMapEntryGetValue);
    
    const char* cKey = env->GetStringUTFChars(jKey, nullptr);
    const char* cValue = env->GetStringUTFChars(jValue, nullptr);
    
    params.insert(std::make_pair(cKey, cValue));
    
    env->ReleaseStringUTFChars(jKey, cKey);
    env->ReleaseStringUTFChars(jValue, cValue);
    
    bHasNext = env->CallBooleanMethod(jParamsIterator, jcIteratorHasNext);
  }
  
  return params;
}

// the function below was adapted from here: https://github.com/remap/ndnrtc/blob/4b2eb4001072ec00b96ab3d0c366149c9a536ac9/cpp/client/src/client.cpp
void runProcessLoop(boost::asio::io_service &io, int runTimeSec)
{
  boost::asio::deadline_timer runTimer(io);
  runTimer.expires_from_now(boost::posix_time::seconds(runTimeSec));
  runTimer.wait();
}

JNIEXPORT void JNICALL
Java_com_example_nrtpttv2_MainActivity_startNdnRtc(JNIEnv* env, jclass, jobject jParams) {

  __android_log_print(ANDROID_LOG_DEBUG, "NDNRTC-WRAPPER", "Got to the beginning of the startNdnRtc function.");
  
  int err = 0;

  g_params = getParams(env, jParams);
  ::setenv("HOME", g_params["homePath"].c_str(), true);

  __android_log_print(ANDROID_LOG_DEBUG, "NDNRTC-WRAPPER", "Finished setting the HOME variable.");
  
  boost::asio::io_service io;
  std::shared_ptr<boost::asio::io_service::work> work(std::make_shared<boost::asio::io_service::work>(io));
  boost::thread t([&io, &err]() {
      try
  	{
  	  io.run();
  	}
      catch (std::exception &e)
  	{
  	  __android_log_print(ANDROID_LOG_ERROR, "NDNRTC-WRAPPER", "Client caught exception while running: %s\n", e.what());
  	  err = 1;
  	}
    });

  __android_log_print(ANDROID_LOG_DEBUG, "NDNRTC-WRAPPER", "Finished adding work to and running the io object.");
  
  std::shared_ptr<ndn::Face> face(std::make_shared<ndn::ThreadsafeFace>(io));

  __android_log_print(ANDROID_LOG_DEBUG, "NDNRTC-WRAPPER", "Finished creating the face object.");
  
  auto keyChain = std::make_shared<ndn::KeyChain>();

  __android_log_print(ANDROID_LOG_DEBUG, "NDNRTC-WRAPPER", "Finished creating the keychain object.");
  
  ndnrtc::RemoteAudioStream remoteStream(io,
  					 face,
  					 keyChain,
  					 "/ndn/edu/ucla/remap/clientB",
  					 "/randomStream");

  __android_log_print(ANDROID_LOG_DEBUG, "NDNRTC-WRAPPER", "Finished creating the remote audio stream object.");
  
  remoteStream.start("pcmu");

  __android_log_print(ANDROID_LOG_DEBUG, "NDNRTC-WRAPPER", "Finished starting the remote audio stream object.");

  runProcessLoop(io, 5);

  __android_log_print(ANDROID_LOG_DEBUG, "NDNRTC-WRAPPER", "Process loop finished running.");

  remoteStream.stop();
  
  face->shutdown();
  face.reset();
  work.reset();
  t.join();
  io.stop();

  __android_log_print(ANDROID_LOG_DEBUG, "NDNRTC-WRAPPER", "Clean up finished.");  
  
  __android_log_print(ANDROID_LOG_DEBUG, "NDNRTC-WRAPPER", "Got to the end of the startNdnRtc function.");
  
}
