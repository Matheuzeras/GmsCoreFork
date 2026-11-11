# YUV Face Diagnostics (passive) for Mercado Pago

This module observes Android YuvImage.compressToJpeg calls inside com.mercadopago.wallet. It records frame dimensions, NV21 format, input byte-array length, strides, requested crop rectangle and if the byte array appears too short. It never stores pixels, photos, biometrics, or authentication decisions and it never modifies data.

Only install for a brief troubleshooting test. It is a diagnostic module for LSPosed/Vector, not a KernelSU ZIP and not a GmsCore update.

Usage in Windows CMD:
1. Disable the older MicroG FaceFix Runtime v0.2 LSPosed hook, which caused an extra crash. Keep updated GmsCore as it is.
2. Install the APK artifact, enable YUV Face Diagnostics for Mercado Pago only, then reboot.
3. Execute: adb logcat -c
4. Reproduce a crash in Mercado Pago once.
5. Execute: adb logcat -d -v time | findstr /I /C:"YuvFaceDiag" /C:"FaceDetection" > yuvdiag.txt
6. Execute: adb logcat -b crash -v time -d > crash-yuvdiag.txt
7. Disable this module after collection.

If YuvFaceDiag reports a short buffer, the app is passing insufficient NV21 bytes to the framework. If the probe does not log, the hook did not execute or the crash is on a different path. A full-sized buffer does not rule out format/stride bugs.

A third-party financial app may block hooking or interfere with its initialization. If any new error arises, disable this diagnostic module and reboot.
