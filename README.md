# RykoCollection Native — Strawhat integration

This repository contains the native Android application baseline copied from [akiraa14200/RykoCollection-](https://github.com/akiraa14200/RykoCollection-), branch `kotlin-native`, build #322 (commit `6da1579cd8007b7933e5f66272e9c2f453f66f71`).

## Integrated StreamGurd tab

The **Protect** tab opens StreamGurd as a native Android Activity. It includes the existing WebView browser, domain-based ad/tracker host filtering, redirect/popup protection, saved websites, history, and the source app's browser tools.

**Scope:** filtering applies to traffic handled inside StreamGurd's WebView. It is not a device-wide VPN or system-wide ad blocker, and cannot guarantee removal of first-party or in-stream advertisements.

## Firefox Sites launcher

The **Firefox Sites** launcher is a second app-drawer entry in the same APK. It embeds Mozilla GeckoView, the Gecko engine used by Firefox for Android, to open websites inside a dedicated app surface. Users can add, edit, remove, and launch their own HTTP/HTTPS website shortcuts. The current page can also be handed off to the installed Firefox app.

GeckoView is an embedded browser engine; it does not share Firefox for Android's signed-in profile, sync data, or installed browser extensions.

## Build

Use JDK 21, Android SDK 36, and Gradle 8.13:

```bash
gradle -p native-android :app:assembleDebug --no-daemon
```

GitHub Actions builds and uploads the debug APK on pushes to `integrated-native` and `firefox-site-launcher`, and on manual dispatch.
