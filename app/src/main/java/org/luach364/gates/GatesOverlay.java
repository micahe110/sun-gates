package org.luach364.gates;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;

/**
 * ציור שכבת "שערי השמש" מעל תמונת המצלמה.
 * ההטלה: כיוון בעולם (אזימוט/גובה) → קואורדינטות המכשיר (מטריצת הסיבוב) → מסך (מצלמה אחורית, חור סיכה).
 */
public final class GatesOverlay {
    private GatesOverlay() {}

    /** מצב הצפייה ברגע הציור. */
    public static final class Pose {
        public float[] R = {1, 0, 0, 0, 0, -1, 0, 1, 0};  // מכשיר→עולם (ENU מגנטי). ברירת מחדל: מצלמה לצפון, אופקית
        public float f = 1000;          // מוקד בפיקסלים של השכבה
        public double decl = 0;         // סטייה מגנטית (מזרחה חיובי)
        public double azOff = 0, altOff = 0;   // כיול לפי השמש
    }

    static final int GOLD = 0xFFE9B949, GOLD_SOFT = 0x99E9B949, BLUE = 0xFF7FC4F2, WHITE = 0xFFFFFFFF,
            SHADOW = 0xAA000000, HILITE = 0x40F2C14E, SUN = 0xFFFFD54F, TODAY = 0xFFFF8A50;

    /** [x, y] במסך, או null אם הכיוון מאחורי המצלמה. */
    public static float[] project(double azTrue, double alt, Pose p, int W, int H) {
        double a = Math.toRadians(azTrue - p.decl - p.azOff), e = Math.toRadians(alt - p.altOff);
        double wx = Math.sin(a) * Math.cos(e), wy = Math.cos(a) * Math.cos(e), wz = Math.sin(e);
        float[] R = p.R;
        double dx = R[0] * wx + R[3] * wy + R[6] * wz;
        double dy = R[1] * wx + R[4] * wy + R[7] * wz;
        double dz = R[2] * wx + R[5] * wy + R[8] * wz;
        double fwd = -dz;                                    // המצלמה האחורית מסתכלת ל- -Z
        if (fwd < 0.05) return null;
        return new float[]{(float) (W / 2.0 + p.f * dx / fwd), (float) (H / 2.0 - p.f * dy / fwd)};
    }

    /** לאן המצלמה מכוונת (מרכז המסך): [אזימוט אמיתי, גובה]. */
    public static double[] pointing(Pose p) {
        float[] R = p.R;
        double wx = -R[2], wy = -R[5], wz = -R[8];
        double az = Math.toDegrees(Math.atan2(wx, wy)) + p.decl + p.azOff;
        double alt = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, wz)))) + p.altOff;
        return new double[]{(az % 360 + 360) % 360, alt};
    }

    public static void draw(Canvas c, int W, int H, GatesModel m, Pose p, double[] sun, float u) {
        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setStyle(Paint.Style.STROKE); line.setStrokeCap(Paint.Cap.ROUND);

        // ---- האופק ----
        line.setColor(0xCCFFFFFF); line.setStrokeWidth(2.2f * u);
        polyline(c, line, p, W, H, 0, 360, 0, 0, 1);

        // ---- הסרגלים: מזרח (זריחה) ומערב (שקיעה) ----
        drawRuler(c, m, p, W, H, u, m.riseTicks, m.otherRise, m.todayRiseAz, "מזרח · זריחה");
        drawRuler(c, m, p, W, H, u, m.setTicks, m.otherSet, m.todaySetAz, "מערב · שקיעה");

        // ---- השמש (מחושבת) ----
        if (sun != null) {
            float[] s = project(sun[0], sun[1], p, W, H);
            if (s != null) {
                float r = Math.max(26 * u, (float) (p.f * Math.tan(Math.toRadians(0.6))));
                Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG);
                sp.setStyle(Paint.Style.STROKE); sp.setColor(SUN); sp.setStrokeWidth(3.5f * u);
                c.drawCircle(s[0], s[1], r, sp);
                for (int k = 0; k < 8; k++) {
                    double a = Math.toRadians(k * 45);
                    c.drawLine(s[0] + (float) Math.cos(a) * r * 1.25f, s[1] + (float) Math.sin(a) * r * 1.25f,
                            s[0] + (float) Math.cos(a) * r * 1.6f, s[1] + (float) Math.sin(a) * r * 1.6f, sp);
                }
                label(c, sun[1] < 0 ? "השמש (מתחת לאופק)" : "השמש", s[0], s[1] + r * 1.7f + 30 * u, 34 * u, SUN, u);
            }
        }

        // ---- כוונת במרכז ----
        Paint ch = new Paint(Paint.ANTI_ALIAS_FLAG);
        ch.setStyle(Paint.Style.STROKE); ch.setColor(0xCCFFFFFF); ch.setStrokeWidth(2f * u);
        float cx = W / 2f, cy = H / 2f, a = 34 * u;
        c.drawCircle(cx, cy, a * 0.55f, ch);
        c.drawLine(cx - a, cy, cx - a * 0.75f, cy, ch); c.drawLine(cx + a * 0.75f, cy, cx + a, cy, ch);
        c.drawLine(cx, cy - a, cx, cy - a * 0.75f, ch); c.drawLine(cx, cy + a * 0.75f, cx, cy + a, ch);
    }

    /** סרגל שערים אחד, כמו בשרטוט: מסגרת, שנתות אנכיות, מספר השער ומספר הימים מעל. */
    private static void drawRuler(Canvas c, GatesModel m, Pose p, int W, int H, float u,
                                  double[] ticks, double[] other, double todayAz, String title) {
        double bottom = -1.5, top = 16, numAlt = 20.5, daysAlt = 18;
        // הדגשת השער הנוכחי
        if (m.currentBand >= 0) {
            double a1 = ticks[m.currentBand], a2 = ticks[m.currentBand + 1];
            Path band = new Path();
            boolean ok = true;
            float[][] pts = {project(a1, bottom, p, W, H), project(a2, bottom, p, W, H),
                    project(a2, top, p, W, H), project(a1, top, p, W, H)};
            for (float[] q : pts) if (q == null) ok = false;
            if (ok) {
                band.moveTo(pts[0][0], pts[0][1]);
                for (int i = 1; i < 4; i++) band.lineTo(pts[i][0], pts[i][1]);
                band.close();
                Paint hp = new Paint(Paint.ANTI_ALIAS_FLAG); hp.setColor(HILITE);
                c.drawPath(band, hp);
            }
        }
        Paint fr = new Paint(Paint.ANTI_ALIAS_FLAG);
        fr.setStyle(Paint.Style.STROKE); fr.setStrokeCap(Paint.Cap.ROUND);
        double lo = Math.min(ticks[0], ticks[6]), hi = Math.max(ticks[0], ticks[6]);
        // מסגרת: קו עליון וקו תחתון
        fr.setColor(GOLD); fr.setStrokeWidth(3f * u);
        polyline(c, fr, p, W, H, lo, hi, top, top, 0.5);
        polyline(c, fr, p, W, H, lo, hi, bottom, bottom, 0.5);
        // שנתות המעבר הנוכחי
        for (int i = 0; i < 7; i++) {
            fr.setColor(i == 0 || i == 6 ? GOLD : BLUE);
            fr.setStrokeWidth((i == 0 || i == 6 ? 3.4f : 3f) * u);
            seg(c, fr, p, W, H, ticks[i], bottom, ticks[i], top);
        }
        // שנתות המעבר השני — קצרות ודקות, מתחת לאופק
        fr.setColor(0x88FFFFFF); fr.setStrokeWidth(2f * u);
        for (int i = 0; i < 7; i++) seg(c, fr, p, W, H, other[i], bottom - 0.5, other[i], bottom - 3.5);
        // הזריחה/השקיעה של היום
        fr.setColor(TODAY); fr.setStrokeWidth(3.5f * u);
        seg(c, fr, p, W, H, todayAz, bottom - 1, todayAz, top + 1);
        float[] tq = project(todayAz, top + 2.2, p, W, H);
        if (tq != null) label(c, "היום", tq[0], tq[1], 28 * u, TODAY, u);
        // תאריך כל שנתה (לועזי) מתחת לסרגל
        for (int i = 0; i < 7; i++) {
            float[] q = project(ticks[i], bottom - 5.5, p, W, H);
            if (q != null && m.tickDate[i] != null)
                label(c, m.tickDate[i].getDayOfMonth() + "." + m.tickDate[i].getMonthValue(), q[0], q[1], 24 * u, 0xDDFFFFFF, u);
        }
        // מספרי השערים וימים
        for (int i = 0; i < 6; i++) {
            double mid = (ticks[i] + ticks[i + 1]) / 2;
            float[] q = project(mid, numAlt, p, W, H);
            if (q != null) {
                Paint np = new Paint(Paint.ANTI_ALIAS_FLAG);
                np.setTextAlign(Paint.Align.CENTER); np.setTextSize(58 * u); np.setFakeBoldText(true);
                np.setColor(i == m.currentBand ? 0xFFFFE082 : GOLD);
                np.setShadowLayer(6 * u, 0, 2 * u, SHADOW);
                c.drawText(String.valueOf(m.bandGate[i]), q[0], q[1], np);
            }
            float[] d = project(mid, daysAlt, p, W, H);
            if (d != null) label(c, m.bandDays[i] + " יום", d[0], d[1], 26 * u, WHITE, u);
        }
        // כותרת
        float[] t = project((lo + hi) / 2, numAlt + 6, p, W, H);
        if (t != null) label(c, title, t[0], t[1], 34 * u, WHITE, u);
    }

    private static void seg(Canvas c, Paint pt, Pose p, int W, int H, double az1, double alt1, double az2, double alt2) {
        float[] a = project(az1, alt1, p, W, H), b = project(az2, alt2, p, W, H);
        if (a != null && b != null) c.drawLine(a[0], a[1], b[0], b[1], pt);
    }

    /** קו לאורך אזימוטים (בגובה קבוע או משתנה), מנותק כשהנקודה מאחורי המצלמה. */
    private static void polyline(Canvas c, Paint pt, Pose p, int W, int H,
                                 double az1, double az2, double alt1, double alt2, double step) {
        int n = Math.max(2, (int) Math.ceil(Math.abs(az2 - az1) / step));
        Path path = new Path();
        boolean pen = false;
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n;
            float[] q = project(az1 + (az2 - az1) * t, alt1 + (alt2 - alt1) * t, p, W, H);
            if (q == null || Math.abs(q[0]) > 4 * W || Math.abs(q[1]) > 4 * H) { pen = false; continue; }
            if (!pen) { path.moveTo(q[0], q[1]); pen = true; } else path.lineTo(q[0], q[1]);
        }
        c.drawPath(path, pt);
    }

    static void label(Canvas c, String text, float cx, float cy, float size, int color, float u) {
        int w = (int) (size * 14);
        TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tp.setTextSize(size); tp.setColor(color); tp.setTypeface(Typeface.DEFAULT_BOLD);
        tp.setShadowLayer(5 * u, 0, 2.5f * u, 0xAA000000);
        StaticLayout l = StaticLayout.Builder.obtain(text, 0, text.length(), tp, w)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setTextDirection(TextDirectionHeuristics.RTL)
                .setIncludePad(false).build();
        c.save(); c.translate(cx - w / 2f, cy - l.getHeight() / 2f); l.draw(c); c.restore();
    }
}
