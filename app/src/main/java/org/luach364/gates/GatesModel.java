package org.luach364.gates;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * הנתונים של "שערי השמש" (חנוך עב) — Java טהור.
 * השנתות = אזימוט הזריחה (ובמערב: השקיעה) בתחילת כל חודש מקראי, מהמקום שלך.
 * המעבר הנוכחי של השמש (צפונה או דרומה) מצויר כסרגל ראשי, והמעבר השני — כשנתות חלשות.
 */
public final class GatesModel {
    public double lat, lon;
    public LocalDate today;
    public Luach.Z z;
    public int gate, dayInGate, gateLen;     // השהות הנוכחית
    public boolean northbound;               // השמש נעה צפונה (חורף→קיץ)?
    /** 7 שנתות של המעבר הנוכחי: [i] = תחילת החודש i בסדר המעבר. */
    public double[] riseTicks = new double[7], setTicks = new double[7];
    public int[] bandGate = new int[6], bandDays = new int[6], bandMonth = new int[6];
    public LocalDate[] tickDate = new LocalDate[7];
    /** שנתות המעבר השני (חלשות). */
    public double[] otherRise = new double[7], otherSet = new double[7];
    public double todayRiseAz, todaySetAz;
    /** מסלולי היום: נקודות [אזימוט, גובה] מהזריחה עד השקיעה בלבד. */
    public List<double[]> sunPath = new ArrayList<>(), moonPath = new ArrayList<>();
    public ZonedDateTime moonRise, moonSet;
    public long pathsAt = 0;
    public int currentBand = -1;

    static final int[] NORTH = {10, 11, 12, 1, 2, 3, 4};     // חורף → קיץ
    static final int[] SOUTH = {4, 5, 6, 7, 8, 9, 10};       // קיץ → חורף

    public static GatesModel build(LocalDate today, double lat, double lon) {
        GatesModel m = new GatesModel();
        m.lat = lat; m.lon = lon; m.today = today;
        Luach.Z z = Luach.zadok(today);
        m.z = z;
        int[] st = Luach.gateStay(z);
        m.gate = st[0]; m.dayInGate = st[1]; m.gateLen = st[2];
        int month = z.ic ? 12 : z.month;
        int Y = z.ic ? z.cyc * 6 + 6 : z.Y;                    // מספר שנה רציף מהעוגן (2019 = 1)
        m.northbound = month >= 10 || month <= 3;

        // שנה של כל חודש במעבר הצפוני: 10–12 משנה אחת, 1–4 מהשנה שאחריה
        int nY10 = month >= 10 ? Y : Y - 1;
        int[] seq = m.northbound ? NORTH : SOUTH;
        int[] other = m.northbound ? SOUTH : NORTH;
        for (int i = 0; i < 7; i++) {
            int mm = seq[i];
            int yy = m.northbound ? (mm >= 10 ? nY10 : nY10 + 1) : Y;
            LocalDate d = monthStart(yy, mm);
            m.tickDate[i] = d;
            m.riseTicks[i] = Luach.riseSetAz(d, lat, lon, true);
            m.setTicks[i] = Luach.riseSetAz(d, lat, lon, false);
            // המעבר השני = המעבר הקודם בכיוון ההפוך
            int om = other[i];
            int oy;
            if (m.northbound) oy = nY10;                         // הקיץ→חורף שהסתיים ב-10 של nY10
            else oy = om >= 10 ? Y - 1 : Y;                      // החורף→קיץ שהסתיים ב-4 של Y
            LocalDate od = monthStart(oy, om);
            m.otherRise[i] = Luach.riseSetAz(od, lat, lon, true);
            m.otherSet[i] = Luach.riseSetAz(od, lat, lon, false);
        }
        for (int i = 0; i < 6; i++) {
            m.bandMonth[i] = seq[i];
            m.bandGate[i] = Luach.GATE[seq[i] - 1];
            m.bandDays[i] = Luach.monthLen(seq[i]);
            if (seq[i] == month) m.currentBand = i;
        }
        m.todayRiseAz = Luach.riseSetAz(today, lat, lon, true);
        m.todaySetAz = Luach.riseSetAz(today, lat, lon, false);
        return m;
    }

    /** תחילת חודש m בשנה הרציפה Y (Y=1 ← 2019). */
    static LocalDate monthStart(int Y, int m) {
        int cyc = Math.floorDiv(Y - 1, 6), year = Math.floorMod(Y - 1, 6) + 1;
        return Luach.gregOf(cyc, year, m, 1);
    }

    public double[] sunNow(Instant t) { return Luach.sunAzAlt(t, lat, lon); }

    public double[] moonNow(Instant t) { return Luach.moonAzAlt(t, lat, lon); }

    static final double HORIZON = -0.27;      // הקצה העליון של הדיסק נוגע באופק
    static final long STEP = 4 * 60_000L;     // דגימה כל 4 דקות

    /** מחשב מחדש את מסלולי השמש והירח (פעם בכמה דקות). */
    public void refreshPaths(Instant now, ZoneId zone) {
        pathsAt = now.toEpochMilli();
        // ---- השמש: היום (או מחר, אם כבר שקעה) ----
        LocalDate d = now.atZone(zone).toLocalDate();
        if (!now.isBefore(Luach.sunTime(d, lat, lon, false, zone).toInstant())) d = d.plusDays(1);
        Instant rise = Luach.sunTime(d, lat, lon, true, zone).toInstant();
        Instant set = Luach.sunTime(d, lat, lon, false, zone).toInstant();
        List<double[]> sp = new ArrayList<>();
        for (long t = rise.toEpochMilli(); t < set.toEpochMilli(); t += STEP) sp.add(Luach.sunAzAlt(Instant.ofEpochMilli(t), lat, lon));
        sp.add(Luach.sunAzAlt(set, lat, lon));
        sunPath = sp;

        // ---- הירח: המעבר הנוכחי מעל האופק, ואם הוא מתחת — המעבר הבא ----
        long t0 = now.toEpochMilli() - 26 * 3600_000L, t1 = now.toEpochMilli() + 30 * 3600_000L;
        long segStart = -1, chosenStart = -1, chosenEnd = -1;
        double prevAlt = Double.NaN; long prevT = 0;
        for (long t = t0; t <= t1; t += STEP) {
            double alt = Luach.moonAzAlt(Instant.ofEpochMilli(t), lat, lon)[1];
            if (!Double.isNaN(prevAlt)) {
                boolean wasUp = prevAlt >= HORIZON, isUp = alt >= HORIZON;
                if (!wasUp && isUp) segStart = cross(prevT, prevAlt, t, alt);
                if (wasUp && !isUp && segStart > 0) {
                    long segEnd = cross(prevT, prevAlt, t, alt);
                    if (segEnd > now.toEpochMilli()) { chosenStart = segStart; chosenEnd = segEnd; break; }
                    segStart = -1;
                }
            }
            prevAlt = alt; prevT = t;
        }
        List<double[]> mp = new ArrayList<>();
        moonRise = moonSet = null;
        if (chosenStart > 0) {
            for (long t = chosenStart; t < chosenEnd; t += STEP) mp.add(Luach.moonAzAlt(Instant.ofEpochMilli(t), lat, lon));
            mp.add(Luach.moonAzAlt(Instant.ofEpochMilli(chosenEnd), lat, lon));
            moonRise = Instant.ofEpochMilli(chosenStart).atZone(zone);
            moonSet = Instant.ofEpochMilli(chosenEnd).atZone(zone);
        }
        moonPath = mp;
    }

    /** הזמן שבו הגובה חוצה את האופק בין שתי דגימות (אינטרפולציה). */
    private static long cross(long ta, double aa, long tb, double ab) {
        double f = (HORIZON - aa) / (ab - aa);
        return ta + (long) ((tb - ta) * Math.max(0, Math.min(1, f)));
    }
}
