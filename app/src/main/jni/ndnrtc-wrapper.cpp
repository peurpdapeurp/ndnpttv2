
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
#include <ndnrtc/local-stream.hpp>
#include <ndnrtc/simple-log.hpp>

#include <string.h>
#include <jni.h>

#define CLIENT_PRODUCER
// #define CLIENT_CONSUMER

static std::map<std::string, std::string> g_params;
static int g_run_time_sec = 5;

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

  __android_log_print(ANDROID_LOG_DEBUG, "NDNRTC-WRAPPER", "Finished setting the HOME variable: %s.", g_params["homePath"].c_str());
  __android_log_print(ANDROID_LOG_DEBUG, "NDNRTC-WRAPPER", "Got the directory of the application cache: %s.", g_params["cachePath"].c_str());

  ndnlog::new_api::Logger::initAsyncLogging();
  ndnlog::new_api::Logger::getLogger(g_params["cachePath"] + "/log.txt").setLogLevel(ndnlog::NdnLoggerDetailLevelAll);

  __android_log_print(ANDROID_LOG_DEBUG, "NDNRTC-WRAPPER", "Finished creating the logger object and setting up logging.");
  
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
    
  auto keyChain = std::make_shared<ndn::KeyChain>();
  auto defaultIdentity = keyChain->createIdentityV2(ndn::Name("/default/identity"));
  keyChain->setDefaultIdentity(*defaultIdentity);
  
  __android_log_print(ANDROID_LOG_DEBUG, "NDNRTC-WRAPPER", "Default identity of keychain object: %s.",
		      keyChain->getDefaultIdentity().toUri().c_str());
  
  __android_log_print(ANDROID_LOG_DEBUG, "NDNRTC-WRAPPER", "Finished creating the keychain object.");

  std::shared_ptr<ndn::Face> face(std::make_shared<ndn::ThreadsafeFace>(io));
  face->setCommandSigningInfo(*keyChain, keyChain->getDefaultCertificateName());
  
  __android_log_print(ANDROID_LOG_DEBUG, "NDNRTC-WRAPPER", "Finished creating the face object.");

#ifdef CLIENT_CONSUMER
  
  ndnrtc::RemoteAudioStream remoteStream(io,
  					 face,
  					 keyChain,
  					 "/ndnrtc",
  					 "nrtpttv2");
  remoteStream.setLogger(ndnlog::new_api::Logger::getLoggerPtr(g_params["cachePath"] + "/log.txt"));

  __android_log_print(ANDROID_LOG_DEBUG, "NDNRTC-WRAPPER", "Finished creating the remote audio stream object.");
  
  remoteStream.start("pcmu");

  __android_log_print(ANDROID_LOG_DEBUG, "NDNRTC-WRAPPER", "Finished starting the remote audio stream object.");

  runProcessLoop(io, g_run_time_sec);

  __android_log_print(ANDROID_LOG_DEBUG, "NDNRTC-WRAPPER", "Process loop finished running.");

  remoteStream.stop();
  
#endif
  
#ifdef CLIENT_PRODUCER
  
  face->registerPrefix(ndn::Name("/ndnrtc"),
		       [](const std::shared_ptr<const ndn::Name> &prefix,
			  const std::shared_ptr<const ndn::Interest> &interest,
			  ndn::Face &face, uint64_t, const std::shared_ptr<const ndn::InterestFilter> &) {
			 __android_log_print(ANDROID_LOG_DEBUG, "NDNRTC-WRAPPER", "Unexpected incoming interest %s.", interest->getName().toUri().c_str());
		       },
		       [](const std::shared_ptr<const ndn::Name> &p) {
			 __android_log_print(ANDROID_LOG_ERROR, "NDNRTC-WRAPPER", "Failed to register prefix %s.", p->toUri().c_str());
		       },
		       [](const std::shared_ptr<const ndn::Name> &p, uint64_t) {
			 __android_log_print(ANDROID_LOG_DEBUG, "NDNRTC-WRAPPER", "Successfully registered prefix %s.", p->toUri().c_str());
		       });
  
  ndnrtc::MediaStreamParams params;
  params.streamName_ = "nrtpttv2";
  params.synchronizedStreamName_ = "";
  params.captureDevice_.deviceId_ = 0;
  params.type_ = ndnrtc::MediaStreamParams::MediaStreamType::MediaStreamTypeAudio;
  params.producerParams_.segmentSize_ = 1000;

  ndnrtc::MediaStreamSettings settings(io, params);
  settings.face_ = face.get();
  settings.keyChain_ = keyChain.get();
  settings.params_.addMediaThread(ndnrtc::AudioThreadParams("sound"));

  ndnrtc::LocalAudioStream localStream("/ndnrtc", settings);
  localStream.setLogger(ndnlog::new_api::Logger::getLoggerPtr(g_params["cachePath"] + "/log.txt"));

  __android_log_print(ANDROID_LOG_DEBUG, "NDNRTC-WRAPPER", "Finished creating the local audio stream object.");
  
  localStream.start();
  
  __android_log_print(ANDROID_LOG_DEBUG, "NDNRTC-WRAPPER", "Finished starting the local audio stream object.");

  runProcessLoop(io, g_run_time_sec);

  __android_log_print(ANDROID_LOG_DEBUG, "NDNRTC-WRAPPER", "Process loop finished running.");

  localStream.stop();
  
#endif
  
  face->shutdown();
  face.reset();
  work.reset();
  t.join();
  io.stop();

  __android_log_print(ANDROID_LOG_DEBUG, "NDNRTC-WRAPPER", "Clean up finished.");  
  
  __android_log_print(ANDROID_LOG_DEBUG, "NDNRTC-WRAPPER", "Got to the end of the startNdnRtc function.");
  
}
