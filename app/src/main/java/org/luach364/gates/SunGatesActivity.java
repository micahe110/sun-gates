package org.luach364.gates;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.SurfaceTexture;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Size;
import android.util.SizeF;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;

/**
 * שערי השמש: המצלמה פתוחה, ועליה האופק, ששת השערים (חנוך עב) בזריחה ובשקיעה, והשמש המחושבת.
 */
public class SunGatesActivity extends Activity implements SensorEventListener {
    static final int REQ = 72;

    FrameLayout root;
    TextureView texture;
    OverlayView overlay;
    TextView info;
    SensorManager sm;
    final GatesOverlay.Pose pose = new GatesOverlay.Pose();
    final float[] rTmp = new float[9];
    boolean haveR = false;
    GatesModel model;
    double lat, lon;
    double vfovDeg = 66;            // מעודכן מנתוני המצלמה
    SharedPreferences prefs;
    boolean askedLoc = false;

    CameraDevice camera;
    CameraCaptureSession session;
    Size previewSize;
    HandlerThread camThread;
    Handler camHandler;
    final Handler ui = new Handler(Looper.getMainLooper());

    float u() { return getResources().getDisplayMetrics().widthPixels / 1080f; }

    /** השכבה המצוירת מעל המצלמה. */
    class OverlayView extends View {
        OverlayView(Context c) { super(c); }
        @Override protected void onDraw(Canvas c) {
            if (model == null) return;
            int W = getWidth(), H = getHeight();
            pose.f = (float) ((H / 2.0) / Math.tan(Math.toRadians(vfovDeg / 2)));
            double[] sun = model.sunNow(Instant.now());
            GatesOverlay.draw(c, W, H, model, pose, sun, u());
        }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs = getSharedPreferences("gates", MODE_PRIVATE);
        pose.azOff = Double.longBitsToDouble(prefs.getLong("azOff", Double.doubleToLongBits(0)));
        pose.altOff = Double.longBitsToDouble(prefs.getLong("altOff", Double.doubleToLongBits(0)));

        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        texture = new TextureView(this);
        overlay = new OverlayView(this);
        root.addView(texture, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));

        info = new TextView(this);
        info.setTextColor(0xFFFFFFFF);
        info.setTextSize(15);
        info.setBackgroundColor(0x99000000);
        info.setPadding(dp(14), dp(34), dp(14), dp(10));
        info.setGravity(Gravity.CENTER);
        root.addView(info, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(0x99000000);
        bar.setPadding(dp(8), dp(8), dp(8), dp(20));
        Button cal = new Button(this);
        cal.setText("כייל לפי השמש");
        cal.setOnClickListener(v -> calibrate());
        Button reset = new Button(this);
        reset.setText("איפוס כיול");
        reset.setOnClickListener(v -> {
            pose.azOff = 0; pose.altOff = 0; saveCal();
            Toast.makeText(this, "הכיול אופס", Toast.LENGTH_SHORT).show();
        });
        bar.addView(cal, new LinearLayout.LayoutParams(0, -2, 1));
        bar.addView(reset, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(bar, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        setContentView(root);
        sm = (SensorManager) getSystemService(SENSOR_SERVICE);
    }

    int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    @Override
    protected void onResume() {
        super.onResume();
        boolean cam = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        boolean loc = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!cam || (!loc && !askedLoc)) {
            askedLoc = true;
            requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ);
        }
        setupLocationAndModel();
        Sensor rv = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        if (rv != null) sm.registerListener(this, rv, SensorManager.SENSOR_DELAY_GAME);
        else Toast.makeText(this, "אין במכשיר חיישן כיוון", Toast.LENGTH_LONG).show();
        ui.post(ticker);
        if (cam) startCamera();
    }

    @Override
    protected void onPause() {
        ui.removeCallbacks(ticker);
        sm.unregisterListener(this);
        stopCamera();
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        super.onRequestPermissionsResult(req, perms, res);
        // onResume ייקרא שוב וימשיך משם
    }

    // ===================== מיקום ונתוני השערים =====================
    void setupLocationAndModel() {
        lat = Double.longBitsToDouble(prefs.getLong("lat", Double.doubleToLongBits(31.668)));   // ברירת מחדל: אשקלון,
        lon = Double.longBitsToDouble(prefs.getLong("lon", Double.doubleToLongBits(34.574)));   // או המיקום האחרון שנמצא
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
                Location best = null;
                for (String prov : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER}) {
                    Location l = lm.getLastKnownLocation(prov);
                    if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
                }
                if (best != null) {
                    lat = best.getLatitude(); lon = best.getLongitude();
                    prefs.edit().putLong("lat", Double.doubleToLongBits(lat)).putLong("lon", Double.doubleToLongBits(lon)).apply();
                }
            } catch (SecurityException | IllegalArgumentException ignored) { }
        }
        GeomagneticField gf = new GeomagneticField((float) lat, (float) lon, 0f, System.currentTimeMillis());
        pose.decl = gf.getDeclination();
        model = GatesModel.build(LocalDate.now(), lat, lon);
    }

    final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (model != null && !model.today.equals(LocalDate.now())) model = GatesModel.build(LocalDate.now(), lat, lon);
            updateInfo();
            overlay.invalidate();
            ui.postDelayed(this, 1000);
        }
    };

    void updateInfo() {
        if (model == null) return;
        double[] sun = model.sunNow(Instant.now());
        double[] pt = GatesOverlay.pointing(pose);
        String dir = model.northbound ? "השמש נעה צפונה" : "השמש נעה דרומה";
        info.setText(bibTitle(model.z) + "\n"
                + "שער " + model.gate + " · יום " + model.dayInGate + " מתוך " + model.gateLen + " · " + dir + "\n"
                + String.format("השמש: אזימוט %.0f° · גובה %.0f°   |   המצלמה: %.0f° · %.0f°", sun[0], sun[1], pt[0], pt[1]));
    }

    static String bibTitle(Luach.Z z) {
        if (z.ic) return "שבוע העיבור · יום " + Luach.gem(z.icDay);
        return Luach.gem(z.day) + " לחודש " + Luach.MONTH_ORD[z.month - 1] + " · " + Luach.WEEKDAYS[z.wd];
    }

    // ===================== חיישן כיוון =====================
    @Override
    public void onSensorChanged(SensorEvent e) {
        SensorManager.getRotationMatrixFromVector(rTmp, e.values);
        float k = haveR ? 0.25f : 1f;                                // החלקה
        for (int i = 0; i < 9; i++) pose.R[i] += k * (rTmp[i] - pose.R[i]);
        haveR = true;
        overlay.invalidate();
    }

    @Override public void onAccuracyChanged(Sensor s, int a) { }

    /** מכוונים את השמש האמיתית לכוונת ולוחצים: מתקנים את סטיית המצפן והגובה. */
    void calibrate() {
        if (model == null || !haveR) return;
        double[] sun = model.sunNow(Instant.now());
        if (sun[1] < -1) {
            Toast.makeText(this, "השמש מתחת לאופק — אפשר לכייל רק כשהיא נראית", Toast.LENGTH_LONG).show();
            return;
        }
        GatesOverlay.Pose raw = new GatesOverlay.Pose();
        raw.R = pose.R.clone(); raw.decl = pose.decl;
        double[] pt = GatesOverlay.pointing(raw);                      // בלי הכיול הקודם
        // ההטלה מחסירה את ההיסט מהאזימוט — כך שהשמש המחושבת תשב בדיוק בכוונת
        double dAz = ((sun[0] - pt[0]) % 360 + 540) % 360 - 180;
        pose.azOff = dAz;
        pose.altOff = sun[1] - pt[1];
        saveCal();
        Toast.makeText(this, String.format("כויל: %.1f° אופקי, %.1f° אנכי", dAz, pose.altOff), Toast.LENGTH_LONG).show();
    }

    void saveCal() {
        prefs.edit().putLong("azOff", Double.doubleToLongBits(pose.azOff))
                .putLong("altOff", Double.doubleToLongBits(pose.altOff)).apply();
    }

    // ===================== מצלמה (Camera2) =====================
    void startCamera() {
        if (camThread == null) {
            camThread = new HandlerThread("cam");
            camThread.start();
            camHandler = new Handler(camThread.getLooper());
        }
        if (texture.isAvailable()) openCamera();
        else texture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) { openCamera(); }
            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) { }
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture st) { return true; }
            @Override public void onSurfaceTextureUpdated(SurfaceTexture st) { }
        });
    }

    void openCamera() {
        if (camera != null) return;
        CameraManager cm = (CameraManager) getSystemService(CAMERA_SERVICE);
        try {
            String id = null;
            CameraCharacteristics ch = null;
            for (String cid : cm.getCameraIdList()) {
                CameraCharacteristics c = cm.getCameraCharacteristics(cid);
                Integer facing = c.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) { id = cid; ch = c; break; }
            }
            if (id == null) { Toast.makeText(this, "לא נמצאה מצלמה אחורית", Toast.LENGTH_LONG).show(); return; }

            // שדה הראייה לאורך הצלע הארוכה של החיישן (= הגובה במסך לאורך)
            SizeF phys = ch.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
            float[] focals = ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
            if (phys != null && focals != null && focals.length > 0) {
                float longSide = Math.max(phys.getWidth(), phys.getHeight());
                vfovDeg = Math.toDegrees(2 * Math.atan(longSide / (2 * focals[0])));
            }

            StreamConfigurationMap map = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            DisplayMetrics dm = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getRealMetrics(dm);
            int sw = dm.widthPixels, sh = dm.heightPixels;
            previewSize = choose(map.getOutputSizes(SurfaceTexture.class), sh / (double) sw);

            // התצוגה ממלאת את המסך (חיתוך בצדדים), והשכבה בדיוק באותו גודל
            double portraitAspect = previewSize.getHeight() / (double) previewSize.getWidth();   // רוחב/גובה
            int vw, vh;
            if (sw / (double) sh > portraitAspect) { vw = sw; vh = (int) Math.round(sw / portraitAspect); }
            else { vh = sh; vw = (int) Math.round(sh * portraitAspect); }
            final int fvw = vw, fvh = vh;
            runOnUiThread(() -> {
                texture.setLayoutParams(new FrameLayout.LayoutParams(fvw, fvh, Gravity.CENTER));
                overlay.setLayoutParams(new FrameLayout.LayoutParams(fvw, fvh, Gravity.CENTER));
            });

            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return;
            cm.openCamera(id, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice cd) { camera = cd; startPreview(); }
                @Override public void onDisconnected(CameraDevice cd) { cd.close(); camera = null; }
                @Override public void onError(CameraDevice cd, int err) { cd.close(); camera = null; }
            }, camHandler);
        } catch (CameraAccessException | SecurityException e) {
            Toast.makeText(this, "שגיאה בפתיחת המצלמה", Toast.LENGTH_LONG).show();
        }
    }

    /** גודל תצוגה: היחס הקרוב ביותר למסך, עד 1920 פיקסלים. */
    static Size choose(Size[] sizes, double screenRatio) {
        Size best = sizes[0];
        double bestScore = 1e9;
        for (Size s : sizes) {
            if (s.getWidth() > 1920 || s.getHeight() > 1440) continue;
            double r = s.getWidth() / (double) s.getHeight();
            double score = Math.abs(r - screenRatio) * 10 - s.getWidth() / 1920.0;
            if (score < bestScore) { bestScore = score; best = s; }
        }
        return best;
    }

    void startPreview() {
        try {
            SurfaceTexture st = texture.getSurfaceTexture();
            if (st == null || camera == null) return;
            st.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            final Surface surface = new Surface(st);
            final CaptureRequest.Builder rb = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            rb.addTarget(surface);
            camera.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                @Override public void onConfigured(CameraCaptureSession s) {
                    if (camera == null) return;
                    session = s;
                    try {
                        rb.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                        s.setRepeatingRequest(rb.build(), null, camHandler);
                    } catch (CameraAccessException ignored) { }
                }
                @Override public void onConfigureFailed(CameraCaptureSession s) { }
            }, camHandler);
        } catch (CameraAccessException ignored) { }
    }

    void stopCamera() {
        if (session != null) { session.close(); session = null; }
        if (camera != null) { camera.close(); camera = null; }
        if (camThread != null) { camThread.quitSafely(); camThread = null; camHandler = null; }
    }
}
