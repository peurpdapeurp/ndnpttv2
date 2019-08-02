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

Below are instructions on how install the prerequisites of NDNPTTv2, which are the Android SDK, Android NDK 19, and android-crew-staging.

First, install the Android SDK:

```Shell
sudo apt-get install android-sdk
```

Next, install Android NDK 19 (the latest NDK should not be used due to issues related to the android-crew-staging prerequisite):

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

### Build NDNPTTv2

Now, clone NDNPTTv2 into the build directory.

```Shell
cd $NDNPTTV2_ENV
git clone https://github.com/peurpdapeurp/ndnpttv2
```

Before building with the gradle tool, proper security configuration must be set up (source for keytool commands: https://coderwall.com/p/r09hoq/android-generate-release-debug-keystores):

```Shell
cd $NDNPTTV2_ENV/ndnpttv2
keytool -genkey -v -keystore my-key.keystore -alias alias_name -keyalg RSA -keysize 2048 -validity 10000
keytool -genkey -v -keystore debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000
echo "ndk.dir=$NDNPTTV2_ENV/android-ndk-r19
sdk.dir=/usr/lib/android-sdk
keystore=debug.keystore
keystore.password=android
keystore.key.alias=androiddebugkey
keystore.key.password=android
" >> local.properties
```

After security is properly configured, NDNPTTv2 can be built with the gradle tool:

```Shell
cd $NDNPTTV2_ENV/ndnpttv2
./gradlew assembleDebug
```

The resulting app-debug.apk file can be found in $NDNPTTV2_ENV/ndnpttv2/app/build/outputs/apk/debug.

The app-debug.apk file can be installed to your Android device through adb or a file sharing service like DropBox / Google Drive.
