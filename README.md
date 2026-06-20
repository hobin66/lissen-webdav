# WebDAV Player

### Features

  * WebDAV-first library browsing for audiobook collections
  * Stream books directly from your WebDAV server
  * Optional offline downloads and cache management
  * Clean player UI adapted for this standalone WebDAV player

### About

`webdav-player` is a standalone Android audiobook player focused on one backend only: WebDAV.

### Building

1. Clone the repository:
``` 
git clone <repository-url>
```

2. Setup the SDK into your local.properties file
```
nano local.properties
```

3. Open the project in Android Studio or build it manually
```
./gradlew assembleDebug # Debug Build
./gradlew assembleRelease # Release Build
```
5. Build and run the app on an Android device or emulator.

### ABI Packaging

This repository now ships split APKs for `arm64-v8a` and `x86_64` only.

Run the Node packaging script to bump the build number, build release APKs, and archive them:
```
node build-apks.js
```

Artifacts are copied into `packages/<version>/` with one APK per ABI.

### AI-Assisted Contributions

AI-assisted development is welcome in this project and can be very useful when applied thoughtfully.

However, any AI-generated changes that are not properly reviewed or tested will be discarded without hesitation.

AI is a great tool, but until it can reliably understand the code it produces and the consequences of its changes, all AI-generated contributions must remain under strict human review.

## License
This project is open-source and licensed under the MIT License. See the LICENSE file for more details.
