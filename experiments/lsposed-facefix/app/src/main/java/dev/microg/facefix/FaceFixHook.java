package dev.microg.facefix;

import android.graphics.PointF;
import android.graphics.Rect;
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
    private static final String PKG = "com.google.android.gms";
    private static final String CLASS = "org.microg.gms.vision.face.mlkit.FaceDetector";
    private static final String TAG = "MicroGFaceFix";

    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!PKG.equals(lpparam.packageName)) return;
        try {
            Class<?> detector = XposedHelpers.findClassIfExists(CLASS, lpparam.classLoader);
            if (detector == null) {
                XposedBridge.log(TAG + ": detector class not found");
                return;
            }
            XposedBridge.hookAllMethods(detector, "detectFaces", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try { fixResult(p); }
                    catch (Throwable t) {
                        XposedBridge.log(TAG + ": postprocessing failed: " + t.getClass().getSimpleName());
                    }
                }
            });
            XposedBridge.log(TAG + ": hooked ML Kit detector");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": could not register hook: " + t.getClass().getSimpleName());
        }
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
            try { fixFace(face, r, w, h); }
            catch (Throwable t) { XposedBridge.log(TAG + ": malformed face record; skipped"); }
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
            int type = XposedHelpers.getIntField(contour, "type");
            @SuppressWarnings("unchecked")
            List<PointF> points = (List<PointF>) XposedHelpers.getObjectField(contour, "pointsList");
            List<PointF> resized = resize(points, type, src);
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

    private static int expected(int type) {
        if (type==1) return 36;
        if (type>=2 && type<=5) return 5;
        if (type==6 || type==7) return 16;
        if (type==8) return 11;
        if (type>=9 && type<=11) return 9;
        if (type==12) return 2;
        if (type==13) return 3;
        if (type==14 || type==15) return 1;
        return 0;
    }

    /** Approximate contours only; not a substitute for precise facial geometry. */
    private static List<PointF> resize(List<PointF> points, int type, Rect box) {
        int want = expected(type);
        if (points == null || points.isEmpty() || want == 0) return new ArrayList<>();
        if (points.size() >= want) return new ArrayList<>(points);
        List<PointF> out = new ArrayList<>(want);
        if (type==1 || type==6 || type==7) {
            PointF center=type==1 ? new PointF(box.exactCenterX(),box.exactCenterY()) : points.get(0);
            float rx=Math.max(1,box.width()*(type==1 ? .5f : .09f));
            float ry=Math.max(1,box.height()*(type==1 ? .5f : .045f));
            for (int i=0;i<want;i++) {
                double a=2.0*Math.PI*i/want;
                out.add(new PointF(center.x+(float)Math.cos(a)*rx,center.y+(float)Math.sin(a)*ry));
            }
        } else if (points.size()==1) {
            PointF center=points.get(0);
            float rx=Math.max(1,box.width()*.07f);
            for (int i=0;i<want;i++) {
                float t=want==1?.5f:(float)i/(want-1);
                out.add(new PointF(center.x+(t-.5f)*2f*rx,center.y));
            }
        } else {
            for(int i=0;i<want;i++) {
                float pos=(float)i*(points.size()-1)/(want-1);
                int index=(int)Math.floor(pos), next=Math.min(index+1,points.size()-1);
                float t=pos-index;
                PointF a=points.get(index), b=points.get(next);
                out.add(new PointF(a.x+(b.x-a.x)*t,a.y+(b.y-a.y)*t));
            }
        }
        return out;
    }
}
