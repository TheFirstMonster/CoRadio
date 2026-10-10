# Fork

This is a fork of the OpenRadio Android App by Yurii Chernyshov which was originally open-source.
The fork exists to preserve an ad-free and fully open-source version of the app after the original project was aquired. No new features will be added.

An archive of the original repo can be found on [Software Heritage](https://archive.softwareheritage.org/browse/origin/directory/?origin_url=https://github.com/ChernyshovYuriy/OpenRadio).

This project is not affiliated with or endorsed by the original project or its current maintainers.

## Changes

- Removed analytics
- Removed funding/support links

## Downloads

* [GitHub Releases](https://github.com/TheFirstMonster/CoRadio/releases)

# Original README #

### What is this ? ###

* **Open Radio** is the project which is use media framework of Android to provide live streaming of the world wide Radio Stations. Open Radio supported on Mobile, Android Auto, Android TV and Android Automotive.
* This project is use [Community Radio Browser's API](http://www.radio-browser.info) and [Web Radio](https://jcorporation.github.io/webradiodb) - services that provide a list of radio stations broadcasting their live stream on the Internet.
* Playlist parser is provided by [William Seemann](https://github.com/wseemann/JavaPlaylistParser)
* Playback powered by [Exo Player](https://github.com/google/ExoPlayer)
* Android requirements : Android 4.2 (API level 17) (new APIs for implementing audio playback that is compatible with Auto) or newer.

### Permissions used ###

* INTERNET - To access internet connection.
* ACCESS_NETWORK_STATE - To monitor Internet connection state, detect connect and reconnect states.
* WAKE_LOCK - To keep screen on while playing Radio Station.
* ACCESS_COARSE_LOCATION - On user's demand only - to select Country for user based on Location. This helps to navigate local Radio Stations.
* READ_EXTERNAL_STORAGE (Android 12 and older), READ_MEDIA_IMAGES (Android 13 and newer, on user's demand only) to read image from phone's memory when set it as image for Local Radio Station.
* FOREGROUND_SERVICE - To keep service active while playing stream.
* BLUETOOTH, BLUETOOTH_ADMIN, BLUETOOTH_CONNECT - On user's demand only - to handle connection with a Bluetooth device.
* RECORD_AUDIO - On user's demand only - to use voice search engine on Android TV.

**Application is fully compatible with vehicle's system.**

In order to run application just like it does on vehicle it is necessary to install [Android Auto for Mobile](https://play.google.com/store/apps/details?id=com.google.android.projection.gearhead&hl=en).

**Enjoy!**
