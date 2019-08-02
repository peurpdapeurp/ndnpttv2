# NDNPTTv2

This repository contains the source code of NDNPTTv2, an audio chat application created to demonstrate the benefits that NDN can provide to the public safety use case of push-to-talk communication.

## Build Instructions

The build instructions below are for a Linux Ubuntu 16.04.6 LTS (Xenial) environment.

### Create a build directory

First, create a build directory for ndnpttv2 with the following commands:

```Shell
mkdir ndnpttv2-env && cd ndnpttv2-env
export NDNPTTV2_ENV=`pwd`
```

### Prerequisites

Below are instructions on how to populate the NDNPTTv2 build directory with the prerequisites of NDNPTTv2, which are Android NDK 19, android-crew-staging.

First, install Android NDK 19 (the latest NDK should not be used due to issues related to the android-crew-staging prerequisite):

```Shell
cd $NDNPTTV2_ENV
wget https://dl.google.com/android/repository/android-ndk-r19-linux-x86_64.zip
unzip android-ndk-r19-linux-x86_64.zip
rm android-ndk-r19-linux-x86_64.zip
```

Next, clone the android-crew-staging repository:

```Shell
cd $NDNPTTV2_ENV
git clone https://github.com/named-data-mobile/android-crew-staging
sudo apt-get install curl tar ruby ruby-rugged
```

Next, use the crew tool in the android-crew-staging repository to install the library dependencies of NDNPTTv2 (ndnrtc, webrtc, ndn-cpp, openssl, boost, openfec).

```Shell
cd $NDNPTTV2_ENV/android-ndk-r19
export CREW_NDK_DIR=\`pwd\`
cd $NDNPTTV2_ENV/android-crew-staging
./crew install target/openssl
./crew install boost
./crew install ndn-cpp
./crew install webrtc
./crew install openfec
./crew install ndnrtc
```


