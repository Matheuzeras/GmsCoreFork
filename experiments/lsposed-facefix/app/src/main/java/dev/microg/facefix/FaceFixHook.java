package dev.microg.facefix;

import android.graphics.PointF;
import android.graphics.Rect;
import android.content.Context;
import android.util.Log;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.ArrayList;
import java.util.List;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * EXPERIMENTAL post-processor for the ML Kit face detector in microG 0.3.17.
 * Does not alter identity checks, detection confidence or financial apps.
 */
public final class FaceFixHook implements IXposedHookLoadPackage {
    private static final String GMS = "com.google.android.gms";
    private static final String MP = "com.mercadopago.wallet";
    private static final String ML = "com.mercadolibre.android";
    private static final String CLASS = "org.microg.gms.vision.face.mlkit.FaceDetector";
    private static final String TAG = "MicroGFaceFix";
    private final AtomicBoolean installed = new AtomicBoolean(false);
    private static final AtomicBoolean reported = new AtomicBoolean(false);
    private final List<XC_MethodHook.Unhook> loadClassHooks = new ArrayList<>();

    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        // The face detector is loaded inside the client process, NOT only in GmsCore.
        if (!MP.equals(lpparam.packageName) && !ML.equals(lpparam.packageName)) return;
        report("v0.2 in process " + lpparam.processName);
        tryInstall(lpparam.classLoader, "app loader");
        // microG provides the detector using a remote package context.
        try {
            XposedHelpers.findAndHookMethod("android.app.ContextImpl", null,
                    "createPackageContext", String.class, int.class, new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            if (installed.get() || p.hasThrowable() || p.args == null ||
                                p.args.length < 1 || !GMS.equals(p.args[0]) ||
                                !(p.getResult() instanceof Context)) return;
                            tryInstall(((Context)p.getResult()).getClassLoader(), "remote GmsCore context");
                        }
                    });
        } catch (Throwable t) {
            report("remote context watcher unavailable: " + t.getClass().getSimpleName());
        }
        // Fallback for classes loaded later by a DexClassLoader.
        if (!installed.get()) {
            try {
                Set<XC_MethodHook.Unhook> handles =
                    XposedBridge.hookAllMethods(ClassLoader.class, "loadClass", new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            if (installed.get() || p.hasThrowable() || p.args == null ||
                                p.args.length == 0 || !CLASS.equals(p.args[0])) return;
                            if (p.getResult() instanceof Class) {
                                attach((Class<?>)p.getResult(), "late class loading");
                            }
                        }
                    });
                synchronized (loadClassHooks) {
                    loadClassHooks.addAll(handles);
                    if (installed.get()) unwatch();
                }
                report("v0.2 waiting for detector in remote class loader");
            } catch (Throwable t) {
                report("late class watcher unavailable: " + t.getClass().getSimpleName());
            }
        }
    }

    private void tryInstall(ClassLoader loader, String from) {
        if (installed.get() || loader == null) return;
        try {
            Class<?> cls = XposedHelpers.findClassIfExists(CLASS, loader);
            if (cls != null) attach(cls, from);
        } catch (Throwable t) {
            report("class lookup unavailable: " + t.getClass().getSimpleName());
        }
    }

    private synchronized void attach(Class<?> cls, String from) {
        if (installed.get() || !CLASS.equals(cls.getName())) return;
        try {
            Set<XC_MethodHook.Unhook> methods =
                XposedBridge.hookAllMethods(cls, "detectFaces", new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        try { fixResult(p); }
                        catch (Throwable t) {
                            report("coordinate processing failed: " + t.getClass().getSimpleName());
                        }
                    }
                });
            if (methods.isEmpty()) {
                report("detector class has no detectFaces method");
                return;
            }
            installed.set(true);
            report("v0.2 hooked ML Kit detector in " + from);
            unwatch();
        } catch (Throwable t) {
            report("detector hook failed: " + t.getClass().getSimpleName());
        }
    }

    private void unwatch() {
        synchronized (loadClassHooks) {
            for (XC_MethodHook.Unhook hook : loadClassHooks) {
                try { hook.unhook(); } catch (Throwable ignored) {}
            }
            loadClassHooks.clear();
        }
    }

    private static void report(String message) {
        Log.i(TAG, message);
        XposedBridge.log(TAG + ": " + message);
    }

    private static void fixResult(XC_MethodHook.MethodHookParam p) {
        if (p.args == null || p.args.length < 2 || p.args[1] == null || !(p.getResult() instanceof List)) return;
        Object m = p.args[1];
        int w = XposedHelpers.getIntField(m, "width");
        int h = XposedHelpers.getIntField(m, "height");
        int r = XposedHelpers.getIntField(m, "rotation");
        if (w < 2 || h < 2 || w > 16384 || h > 16384) return;
        for (Object face : (List<?>) p.getResult()) {
            if (face == null) continue;
            try { fixFace(face, r, w, h);
                if (reported.compareAndSet(false, true)) report("v0.2 applied coordinate fix to face results"); }
            catch (Throwable t) { report("face result skipped: " + t.getClass().getSimpleName()); }
        }
    }

    private static void fixFace(Object face, int r, int w, int h) {
        Rect dst = (Rect) XposedHelpers.getObjectField(face, "boundingBox");
        if (dst == null) return;
        Rect src = new Rect(dst);
        @SuppressWarnings("unchecked")
        List<Object> landmarks = (List<Object>) XposedHelpers.getObjectField(face, "landmarkParcelList");
        @SuppressWarnings("unchecked")
        List<Object> contours = (List<Object>) XposedHelpers.getObjectField(face, "contourParcelList");
        if (landmarks == null || contours == null) return;

        // Compute first, then update existing final-reference parcel fields in place.
        List<PointF> mappedLandmarks = new ArrayList<>();
        for (Object landmark : landmarks) {
            PointF pos = (PointF) XposedHelpers.getObjectField(landmark, "position");
            mappedLandmarks.add(map(pos, r, w, h));
        }
        List<List<PointF>> mappedContours = new ArrayList<>();
        for (Object contour : contours) {
            // Only remap original contour points; do not synthesize additional data.
            @SuppressWarnings("unchecked")
            List<PointF> points = (List<PointF>) XposedHelpers.getObjectField(contour, "pointsList");
            List<PointF> resized = points == null ? new ArrayList<PointF>() : points;
            List<PointF> mapped = new ArrayList<>();
            for (PointF pt : resized) mapped.add(map(pt, r, w, h));
            mappedContours.add(mapped);
        }
        Rect corrected = mapRect(src, r, w, h);
        for (int i = 0; i < landmarks.size(); i++) {
            PointF target = (PointF) XposedHelpers.getObjectField(landmarks.get(i), "position");
            target.set(mappedLandmarks.get(i));
        }
        for (int i = 0; i < contours.size(); i++) {
            @SuppressWarnings("unchecked")
            List<PointF> target = (List<PointF>) XposedHelpers.getObjectField(contours.get(i), "pointsList");
            target.clear();
            target.addAll(mappedContours.get(i));
        }
        dst.set(corrected);
    }

    private static PointF map(PointF p, int r, int w, int h) {
        return new PointF(FaceMapping.clamp(FaceMapping.x(p.x, p.y, r, w, h), w),
            FaceMapping.clamp(FaceMapping.y(p.x, p.y, r, w, h), h));
    }

    private static Rect mapRect(Rect box, int r, int w, int h) {
        PointF a=map(new PointF(box.left,box.top),r,w,h);
        PointF b=map(new PointF(box.right,box.top),r,w,h);
        PointF c=map(new PointF(box.left,box.bottom),r,w,h);
        PointF d=map(new PointF(box.right,box.bottom),r,w,h);
        return new Rect(Math.round(Math.min(Math.min(a.x,b.x),Math.min(c.x,d.x))),
            Math.round(Math.min(Math.min(a.y,b.y),Math.min(c.y,d.y))),
            Math.round(Math.max(Math.max(a.x,b.x),Math.max(c.x,d.x))),
            Math.round(Math.max(Math.max(a.y,b.y),Math.max(c.y,d.y))));
    }

}
