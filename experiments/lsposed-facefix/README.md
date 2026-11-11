# microG FaceFix Runtime — Experimental LSPosed module

**Unverified prototype.** The official-signed GmsCore 0.3.17.252432 is installed as an UPDATED_SYSTEM_APP at /data/app and the ROM baseline 0.3.16 at /product/priv-app. Its signing cert SHA256 is **9bd06727e62796c0130eb6dab39b73157451582cbd138e86c468acc395d14165**, which is not reproduced by the PR3899 GitHub debug APK.

The module attempts to correct coordinates returned from the rotated face detection frame and contour list lengths in the microG ML Kit path. It is not a replacement for upstream microG PR #3899, does not include OpenCV memory-leak fixes and is **not validated with Mercado Pago or Mercado Livre**.

## Build

Use JDK17 and Android SDK. From repository root:

    ./gradlew -p experiments/lsposed-facefix :app:assembleDebug

The Android APK is at app/build/outputs/apk/debug/app-debug.apk under the project. A separate GitHub Actions workflow publishes this APK.

## Installation / rollback

1. For KernelSU Next: configure a compatible Zygisk implementation such as ZygiskNext, then LSPosed or Vector.
2. Install the module APK with adb install (it is **NOT** a KernelSU ZIP).
3. Enable the module in the LSPosed manager with **only com.google.android.gms** selected as scope.
4. Restart to reload microG, then inspect LSPosed logs for "MicroGFaceFix".
5. If a crash persists or new errors appear: disable this module in LSPosed and reboot.

It never replaces GmsCore, modifies app data, grants authentication, or bypasses integrity checks. Detection contours are approximated, not a facial identity. The crash can remain if it has other causes. Test at your own risk.

Reference: https://github.com/microg/GmsCore/pull/3899
