package dev.microg.yuvdiag;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.Process;
import android.util.Log;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicInteger;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Passive logs only. No changes to frames, authentication, arguments, or results. */
public final class YuvDiagHook implements IXposedHookLoadPackage {
    private static final String TAG = "YuvFaceDiag";
    private static final AtomicInteger CALLS = new AtomicInteger();
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"com.mercadopago.wallet".equals(lpparam.packageName)) return;
        try {
            XposedHelpers.findAndHookMethod(YuvImage.class, "compressToJpeg",
                Rect.class, int.class, OutputStream.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam p) {
                        if (CALLS.incrementAndGet() > 100) return;
                        try {
                            YuvImage yuv = (YuvImage) p.thisObject;
                            int format = XposedHelpers.getIntField(yuv, "mFormat");
                            if (format != ImageFormat.NV21) return;
                            int w = XposedHelpers.getIntField(yuv, "mWidth");
                            int h = XposedHelpers.getIntField(yuv, "mHeight");
                            byte[] raw = (byte[]) XposedHelpers.getObjectField(yuv, "mData");
                            int[] strides = (int[]) XposedHelpers.getObjectField(yuv, "mStrides");
                            int yStride = strides == null || strides.length < 1 ? w : strides[0];
                            int uvStride = strides == null || strides.length < 2 ? w : strides[1];
                            long needed = (long)yStride * h + (long)uvStride * ((h+1L)/2L);
                            int actual = raw == null ? -1 : raw.length;
                            Rect crop = p.args != null && p.args.length > 0 && p.args[0] instanceof Rect
                                ? (Rect) p.args[0] : null;
                            boolean cropInside = crop != null && crop.left >= 0 && crop.top >= 0
                                && crop.right <= w && crop.bottom <= h && crop.width() > 0
                                && crop.height() > 0;
                            Log.i(TAG, "beforeJPEG pid=" + Process.myPid()
                                + " thread=" + Thread.currentThread().getName()
                                + " NV21=" + w + "x" + h
                                + " strides=" + yStride + "/" + uvStride
                                + " actualBytes=" + actual + " expectedPlaneBytes=" + needed
                                + " shortBuffer=" + (actual >= 0 && actual < needed)
                                + " crop=" + (crop == null ? "null" : crop.toShortString())
                                + " cropInside=" + cropInside);
                        } catch (Throwable t) {
                            Log.w(TAG, "probeSkipped: " + t.getClass().getSimpleName());
                        }
                    }
                });
            Log.i(TAG, "attached process=" + lpparam.processName + " pid=" + Process.myPid());
            XposedBridge.log(TAG + ": attached process=" + lpparam.processName);
        } catch (Throwable t) {
            Log.e(TAG, "hookUnavailable", t);
            XposedBridge.log(TAG + ": hookUnavailable: " + t.getClass().getSimpleName());
        }
    }
}
