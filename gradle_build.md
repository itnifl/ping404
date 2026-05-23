# Gradle build setup

This file explains both what is set and why it is set.

## Main project files

- **build.gradle**
  - Uses Android Gradle Plugin `8.7.3`.
  - Why: this version works better with the current Android SDK tools and compile SDK level.

- **gradle.properties**
  - Uses `android.suppressUnsupportedCompileSdk=35`.
  - Why: reduces noisy warnings for this compile SDK setup.
  - Uses `android.useAndroidX=true`.
  - Why: ensures Android libraries use AndroidX, which is the current standard.

## Android files

- **android/build.gradle**
  - Uses `compileSdk = 35` and `targetSdkVersion 35`.
  - Why: builds against modern Android APIs and targets a current platform level.
  - Has a `lint` block with:
    - `checkReleaseBuilds false`
    - `abortOnError false`
  - Why: prevents release builds from stopping because of lint issues in this environment.
  - Contains a Gradle task rule that skips lint tasks.
  - Why: lint currently crashes with the Java runtime used here, so builds stay reliable.

- **android/AndroidManifest.xml**
  - Defines internet permission.
  - Why: the app needs network access.
  - Sets app label from `@string/app_name`.
  - Why: app name is stored in resources for easier management.
  - Sets theme from `@style/GdxTheme`.
  - Why: applies the game-style fullscreen theme on launch.
  - Starts `AndroidLauncher` as the main launcher activity.
  - Why: this is the Android entry point for the game.

- **android/res/values/strings.xml**
  - Defines `app_name` as `Ping404`.
  - Why: gives Android a display name for launcher and system UI.

- **android/res/values/styles.xml**
  - Defines `GdxTheme` using fullscreen black no-title style.
  - Why: matches the expected libGDX full-screen game presentation.

- **android/proguard-rules.pro**
  - Contains ProGuard rules for release/minify builds. ProGuard is a tool that shrinks and
    obfuscates the app before release. It removes unused code and renames classes to make
    the app smaller and harder to reverse-engineer. The rules file tells ProGuard what it is
    allowed to remove and what it must leave alone.
  - Used in: `android/build.gradle` under `buildTypes > release`, via `proguardFiles`.
  - Why: release builds with minification expect this file and use it to keep required classes.

## Local environment file
You have to create it yourself, it is ignore by gitignore since this is specific to your local environment.

- **local.properties**
  - Points to the local Android SDK path with `sdk.dir=...`.
  - Why: Gradle needs the SDK location to compile Android code.
  - This file is machine-specific.
  - Why: each developer machine can have a different SDK path.
