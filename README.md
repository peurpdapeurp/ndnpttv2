# NDNPTTv2

This repository contains the source code of NDNPTTv2, an audio chat application created to demonstrate the benefits that NDN can provide to the public safety use case of push-to-talk communication.

## Build Instructions

The build instructions below are for a Linux Ubuntu 16.04.6 LTS (Xenial) environment, to build a debug .apk file of the application that can be run on an Android phone.

### Create a build directory

First, create a build directory for ndnpttv2 with the following commands:

```Shell
mkdir ndnpttv2-env && cd ndnpttv2-env
export NDNPTTV2_ENV=`pwd`
```

### Prerequisites

Below are instructions on how install the prerequisites of NDNPTTv2, which are the Java SE Development Kit, Android SDK, Android NDK 19, and android-crew-staging.

First, make sure to run the following command:

```Shell
sudo apt-get update
```

Next, if some version of the JDK is not already installed on your machine, download the Java SE Development Kit 8 for Linux x64 from here: https://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html

Next, if some version of the JDK is not already installed on your machine, install Java SE Development Kit 8, and set the JAVA_HOME environment variable to the directory of your installation (the shell instructions below assume they are run in the same directory which you downloaded the "jdk-8u221-linux-x64.tar.gz" file to):

```Shell
sudo mkdir /usr/java
sudo mv jdk-8u221-linux-x64.tar.gz /usr/java/
cd /usr/java/
sudo tar -xvf jdk-8u221-linux-x64.tar.gz
sudo rm jdk-8u221-linux-x64.tar.gz
cd jdk1.8.0_221/
export JAVA_HOME=`pwd`
```

If some version of the JDK is already installed on your machine, then simply make sure that your JAVA_HOME environment variable is set to the directory of your JDK installation.

Next, install the Android SDK:

```Shell
sudo apt-get install android-sdk
```

Next, download the Android SDK tools for Linux from here: https://developer.android.com/studio

Next, use the Android SDK tools to accept the licenses for the Android SDK. The shell instructions below assume they are run in the same directory which you downloaded the Android SDK tools for Linux to. The name of the file may change in the future; if it does, simply replace the "sdk-tools-linux-4333796.zip" in the commands below which the name of the zip file containing the Android SDK tools for Linux which you downloaded:

```Shell
sudo mv sdk-tools-linux-4333796.zip /usr/java/
cd /usr/java/
sudo unzip sdk-tools-linux-4333796.zip
./tools/bin/sdkmanager --update
./tools/bin/sdkmanager --licenses
```

Note: If you run into an error related to your repositories.cfg file while trying to run the sdkmanager, follow the instructions here (https://askubuntu.com/questions/885658/android-sdk-repositories-cfg-could-not-be-loaded) to resolve it.

When prompted by the sdkmanager to accept licenses, press "y" until you have accepted all licenses, then move the licenses folder generated in /usr/lib/ to your android-sdk installation directory:

```Shell
cd /usr/java/
sudo mv licenses /usr/lib/android-sdk/
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
export CREW_NDK_DIR=`pwd`
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

Create a local.properties file that contains the Android SDK and Android NDK installation directories. Note that the commands below assume your Android SDK was installed with "sudo apt-get install android-sdk" to the default location; if you installed Android SDK by other means, your Android SDK installation directory may be different. The same goes for your Android NDK directory.

```Shell
cd $NDNPTTV2_ENV/ndnpttv2
echo "sdk.dir=/usr/lib/android-sdk
ndk.dir=$NDNPTTV2_ENV/android-ndk-r19" >> local.properties
```

Finally, NDNPTTv2 can be built with the gradle tool:

```Shell
cd $NDNPTTV2_ENV/ndnpttv2
./gradlew assembleDebug
```

The resulting app-debug.apk file can be found in $NDNPTTV2_ENV/ndnpttv2/app/build/outputs/apk/debug.

The app-debug.apk file can be installed to your Android device through adb or a file sharing service like DropBox / Google Drive.
