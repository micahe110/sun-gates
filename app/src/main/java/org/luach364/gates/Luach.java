package org.luach364.gates;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

/**
 * מנוע לוח 364 — Java טהור (בלי תלות באנדרואיד), כדי שאפשר יהיה לבדוק אותו בכל מחשב.
 * הכללים: עוגן רביעי 20.3.2019; מחזור 2191 יום; משמרת משבת עד שישי (בעיבור גמול);
 * ירח סכמטי בספירה רציפה מהעוגן (בלוקים של 1092); היום מתחיל בזריחה; חנוך עב.
 */
public final class Luach {
    private Luach() {}

    public static final String[] COURSES = {"יהויריב","ידעיה","חרים","שעורים","מלכיה","מימין","הקוץ","אביה",
            "ישוע","שכניהו","אלישיב","יקים","חפה","ישבאב","בלגה","אמר","חזיר","הפצץ","פתחיה","יחזקאל",
            "יכין","גמול","דליהו","מעזיהו"};
    public static final String[] MONTH_ORD = {"הראשון","השני","השלישי","הרביעי","החמישי","השישי","השביעי",
            "השמיני","התשיעי","העשירי","האחד עשר","השנים עשר"};
    public static final String[] WEEKDAYS = {"יום ראשון","יום שני","יום שלישי","יום רביעי","יום חמישי","יום שישי","שבת"};
    public static final String[] GREG = {"ינואר","פברואר","מרץ","אפריל","מאי","יוני","יולי","אוגוסט",
            "ספטמבר","אוקטובר","נובמבר","דצמבר"};
    public static final int[] GATE = {4,5,6,6,5,4,3,2,1,1,2,3};
    public static final int[] DPART = {10,11,12,11,10,9,8,7,6,7,8,9};

    static final LocalDate ANCHOR = LocalDate.of(2019, 3, 20);
    static final int CYCLE = 2191;

    static final Map<String, String> FEASTS = new HashMap<>();
    static final int[][] COUNT_FROM = {{1,26},{3,15},{5,3}};
    static final String[] COUNT_LABEL = {"לעומר","לספירת התירוש","לספירת היצהר"};
    static final boolean[] SX = new boolean[1093];
    static final boolean[] SD = new boolean[1093];

    static {
        String[][] f = {
            {"1-1","ראש השנה · יום זיכרון"},{"1-14","פסח"},
            {"1-15","חג המצות — א׳"},{"1-16","חג המצות — ב׳"},{"1-17","חג המצות — ג׳"},{"1-18","חג המצות — ד׳"},
            {"1-19","חג המצות — ה׳"},{"1-20","חג המצות — ו׳"},{"1-21","חג המצות — ז׳"},
            {"1-26","הנפת העומר · ראשית הקציר"},{"2-14","פסח שני"},{"3-15","חג השבועות · ביכורי חיטים"},
            {"4-1","יום זיכרון · ראש הקיץ"},{"5-3","מועד היין · ביכורי תירוש"},{"6-22","מועד השמן · ביכורי יצהר"},
            {"6-23","קרבן העצים — א׳"},{"6-24","קרבן העצים — ב׳"},{"6-25","קרבן העצים — ג׳"},
            {"6-26","קרבן העצים — ד׳"},{"6-27","קרבן העצים — ה׳"},{"6-28","קרבן העצים — ו׳"},
            {"7-1","יום תרועה · זיכרון תרועה"},{"7-10","יום הכיפורים"},
            {"7-15","חג הסוכות — א׳"},{"7-16","חג הסוכות — ב׳"},{"7-17","חג הסוכות — ג׳"},{"7-18","חג הסוכות — ד׳"},
            {"7-19","חג הסוכות — ה׳"},{"7-20","חג הסוכות — ו׳"},{"7-21","חג הסוכות — ז׳"},
            {"7-22","שמיני עצרת · יום ההקהל"},{"10-1","יום זיכרון · ראש החורף"}};
        for (String[] e : f) FEASTS.put(e[0], e[1]);
        series(1, 29, SX);      // X: 1, 30, 60, 89 … 1063
        series(17, 30, SD);     // דוקה: 17, 47, 76 … 1079
    }

    private static void series(int start, int first, boolean[] out) {
        int d = start, st = first;
        while (d < 1092) { out[d] = true; d += st; st = 59 - st; }   // אין אירוע ביום 1092
    }

    static int doyOf(int m, int d) { return (m - 1) * 30 + (m - 1) / 3 + d; }

    public static String gem(int n) {
        if (n == 15) return "ט״ו";
        if (n == 16) return "ט״ז";
        String[] o = {"","א","ב","ג","ד","ה","ו","ז","ח","ט"}, t = {"","י","כ","ל"};
        String s = t[n / 10] + o[n % 10];
        return s.length() > 1 ? s.substring(0, s.length() - 1) + "״" + s.substring(s.length() - 1) : s + "׳";
    }

    /** התאריך המקראי של יום לועזי (הקלט כבר מתוקן לזריחה). */
    public static final class Z {
        public long n; public int cyc, dic, wd;
        public String cin, cout;
        public boolean isX, isD; public int toX;
        public boolean ic; public int icDay, em;
        public int year, doy, month, day, Y;
        public String feast; public int countN; public String countLabel;
        public boolean shmita;
    }

    public static Z zadok(LocalDate g) {
        Z z = new Z();
        z.n = ChronoUnit.DAYS.between(ANCHOR, g);
        z.cyc = (int) Math.floorDiv(z.n, CYCLE);
        z.dic = (int) (z.n - (long) z.cyc * CYCLE);
        z.wd = (int) Math.floorMod(z.n + 3, 7);                 // 0=ראשון … 6=שבת
        if (z.dic >= 2184) { z.cin = z.cout = "גמול"; }
        else {
            z.cin = COURSES[(21 + (z.dic + 4) / 7) % 24];      // שבוע שבת–שישי
            z.cout = COURSES[(21 + (z.dic + 3) / 7) % 24];     // מי שסיים את השבוע (בשבת)
        }
        int dib = (int) Math.floorMod(z.n, 1092) + 1;
        z.isX = SX[dib]; z.isD = SD[dib];
        int nx = -1;
        for (int x = dib + 1; x < 1092; x++) if (SX[x]) { nx = x; break; }
        z.toX = nx > 0 ? nx - dib : 1 + 1092 - dib;
        if (z.dic >= 2184) { z.ic = true; z.icDay = z.dic - 2183; z.em = 12; return z; }
        z.year = z.dic / 364 + 1;
        z.doy = z.dic % 364 + 1;
        int q = (z.doy - 1) / 91, w = (z.doy - 1) % 91;
        int mi = w < 30 ? 0 : (w < 60 ? 1 : 2);
        z.month = q * 3 + mi + 1;
        z.day = w - new int[]{0, 30, 60}[mi] + 1;
        z.em = z.month;
        z.Y = z.cyc * 6 + z.year;
        z.feast = FEASTS.get(z.month + "-" + z.day);
        for (int i = 0; i < COUNT_FROM.length; i++) {
            int k = z.doy - doyOf(COUNT_FROM[i][0], COUNT_FROM[i][1]) + 1;
            if (k >= 1 && k <= 49) { z.countN = k; z.countLabel = COUNT_LABEL[i]; }
        }
        int yf = z.doy >= doyOf(7, 23) ? z.Y : z.Y - 1;   // שמיטה: 23/7 → 22/7
        z.shmita = yf > 0 && yf % 7 == 0;
        return z;
    }

    // ===== זריחה / שקיעה (NOAA, כמו ב-luach-364.html) =====
    private static final double RAD = Math.PI / 180;

    /** דקות UTC מחצות של התאריך. */
    public static double sunUtcMin(LocalDate d, double lat, double lon, boolean rise) {
        double jdn = d.toEpochDay() + 2440588.0;
        double jd = jdn - 0.5 + (rise ? 0.25 : 0.75), res = 0;
        for (int i = 0; i < 3; i++) {
            double t = (jd - 2451545) / 36525;
            double L0 = ((280.46646 + t * (36000.76983 + t * 0.0003032)) % 360 + 360) % 360;
            double M = 357.52911 + t * (35999.05029 - 0.0001537 * t);
            double e = 0.016708634 - t * (0.000042037 + 0.0000001267 * t);
            double C = Math.sin(M * RAD) * (1.914602 - t * (0.004817 + 0.000014 * t))
                    + Math.sin(2 * M * RAD) * (0.019993 - 0.000101 * t) + Math.sin(3 * M * RAD) * 0.000289;
            double om = 125.04 - 1934.136 * t;
            double app = L0 + C - 0.00569 - 0.00478 * Math.sin(om * RAD);
            double secs = 21.448 - t * (46.815 + t * (0.00059 - t * 0.001813));
            double obl = 23 + (26 + secs / 60) / 60 + 0.00256 * Math.cos(om * RAD);
            double decl = Math.asin(Math.sin(obl * RAD) * Math.sin(app * RAD)) / RAD;
            double y = Math.pow(Math.tan(obl * RAD / 2), 2);
            double eqt = 4 * (y * Math.sin(2 * L0 * RAD) - 2 * e * Math.sin(M * RAD)
                    + 4 * e * y * Math.sin(M * RAD) * Math.cos(2 * L0 * RAD)
                    - 0.5 * y * y * Math.sin(4 * L0 * RAD) - 1.25 * e * e * Math.sin(2 * M * RAD)) / RAD;
            double cosH = (Math.cos(90.833 * RAD) - Math.sin(lat * RAD) * Math.sin(decl * RAD))
                    / (Math.cos(lat * RAD) * Math.cos(decl * RAD));
            double H = Math.acos(Math.max(-1, Math.min(1, cosH))) / RAD;
            res = 720 - 4 * lon - eqt + (rise ? -4 * H : 4 * H);
            jd = jdn - 0.5 + res / 1440;
        }
        return res;
    }

    public static ZonedDateTime sunTime(LocalDate d, double lat, double lon, boolean rise, ZoneId zone) {
        long ms = d.toEpochDay() * 86400000L + Math.round(sunUtcMin(d, lat, lon, rise) * 60000);
        return Instant.ofEpochMilli(ms).atZone(zone);
    }

    // ===== המצב ברגע נתון =====
    public static final class State {
        public Z z;
        public LocalDate greg;
        public ZonedDateTime rise, set;
        public boolean beforeSunrise, isDay;
        public int dp, np, gate, nextGate, part, of;
        public ZonedDateTime nextChange;          // תחילת החלק הבא או הזריחה
        public double riseMin, setMin;            // דקות-ביום, לציור החוגה
        /** מפתח שמשתנה רק כשמשהו בתצוגה משתנה. */
        public String key() {
            return greg + "|" + z.n + "|" + (isDay ? "d" : "n") + part + "|" + rise.toLocalTime().withSecond(0).withNano(0)
                    + "|" + set.toLocalTime().withSecond(0).withNano(0);
        }
    }

    static double minOfDay(ZonedDateTime t) { return t.getHour() * 60 + t.getMinute() + t.getSecond() / 60.0; }

    public static State stateAt(ZonedDateTime now, double lat, double lon) {
        ZoneId zone = now.getZone();
        LocalDate today = now.toLocalDate();
        State s = new State();
        s.greg = today;
        s.rise = sunTime(today, lat, lon, true, zone);
        s.set = sunTime(today, lat, lon, false, zone);
        s.riseMin = minOfDay(s.rise); s.setMin = minOfDay(s.set);
        s.beforeSunrise = now.isBefore(s.rise);
        s.z = zadok(s.beforeSunrise ? today.minusDays(1) : today);
        s.dp = DPART[s.z.em - 1]; s.np = 18 - s.dp;
        s.gate = GATE[s.z.em - 1]; s.nextGate = GATE[s.z.em % 12];
        ZonedDateTime start, end; int n;
        if (!now.isBefore(s.rise) && now.isBefore(s.set)) {
            s.isDay = true; start = s.rise; end = s.set; n = s.dp;
        } else if (!now.isBefore(s.set)) {
            s.isDay = false; start = s.set; end = sunTime(today.plusDays(1), lat, lon, true, zone); n = s.np;
        } else {
            s.isDay = false; start = sunTime(today.minusDays(1), lat, lon, false, zone); end = s.rise; n = s.np;
        }
        long len = Duration.between(start, end).toMillis();
        long el = Duration.between(start, now).toMillis();
        s.part = (int) Math.min(n, el / (len / (double) n) + 1);
        s.of = n;
        s.nextChange = start.plusNanos((long) (len * 1_000_000L * (double) s.part / n));
        // חצות לועזי משנה את השורה הלועזית
        ZonedDateTime midnight = today.plusDays(1).atStartOfDay(zone);
        if (midnight.isBefore(s.nextChange)) s.nextChange = midnight;
        return s;
    }

    // ===== מיקום השמש בשמיים (אזימוט וגובה) — לשערי השמש =====

    /** [נטייה במעלות, משוואת הזמן בדקות] ליום יוליאני jd. */
    static double[] declEqt(double jd) {
        double t = (jd - 2451545) / 36525;
        double L0 = ((280.46646 + t * (36000.76983 + t * 0.0003032)) % 360 + 360) % 360;
        double M = 357.52911 + t * (35999.05029 - 0.0001537 * t);
        double e = 0.016708634 - t * (0.000042037 + 0.0000001267 * t);
        double C = Math.sin(M * RAD) * (1.914602 - t * (0.004817 + 0.000014 * t))
                + Math.sin(2 * M * RAD) * (0.019993 - 0.000101 * t) + Math.sin(3 * M * RAD) * 0.000289;
        double om = 125.04 - 1934.136 * t;
        double app = L0 + C - 0.00569 - 0.00478 * Math.sin(om * RAD);
        double secs = 21.448 - t * (46.815 + t * (0.00059 - t * 0.001813));
        double obl = 23 + (26 + secs / 60) / 60 + 0.00256 * Math.cos(om * RAD);
        double decl = Math.asin(Math.sin(obl * RAD) * Math.sin(app * RAD)) / RAD;
        double y = Math.pow(Math.tan(obl * RAD / 2), 2);
        double eqt = 4 * (y * Math.sin(2 * L0 * RAD) - 2 * e * Math.sin(M * RAD)
                + 4 * e * y * Math.sin(M * RAD) * Math.cos(2 * L0 * RAD)
                - 0.5 * y * y * Math.sin(4 * L0 * RAD) - 1.25 * e * e * Math.sin(2 * M * RAD)) / RAD;
        return new double[]{decl, eqt};
    }

    /** [אזימוט מהצפון עם כיוון השעון, גובה מעל האופק] במעלות, כולל שבירת אור בקירוב. */
    public static double[] sunAzAlt(Instant when, double lat, double lon) {
        double ms = when.toEpochMilli();
        double jd = ms / 86400000.0 + 2440587.5;
        double[] de = declEqt(jd);
        double decl = de[0], eqt = de[1];
        double utcMin = ((ms % 86400000.0) + 86400000.0) % 86400000.0 / 60000.0;
        double tst = utcMin + eqt + 4 * lon;                  // זמן שמש אמיתי (דקות)
        double ha = tst / 4 - 180;                            // זווית שעה (מעלות)
        double cz = Math.sin(lat * RAD) * Math.sin(decl * RAD) + Math.cos(lat * RAD) * Math.cos(decl * RAD) * Math.cos(ha * RAD);
        cz = Math.max(-1, Math.min(1, cz));
        double alt = 90 - Math.acos(cz) / RAD;
        if (alt > -1) alt += 1.02 / Math.tan((alt + 10.3 / (alt + 5.11)) * RAD) / 60;   // שבירה (Bennett)
        double az = Math.atan2(Math.sin(ha * RAD),
                Math.cos(ha * RAD) * Math.sin(lat * RAD) - Math.tan(decl * RAD) * Math.cos(lat * RAD)) / RAD + 180;
        return new double[]{(az % 360 + 360) % 360, alt};
    }

    /** אזימוט הזריחה (rise=true) או השקיעה ביום לועזי. */
    public static double riseSetAz(LocalDate d, double lat, double lon, boolean rise) {
        return sunAzAlt(sunTime(d, lat, lon, rise, ZoneId.of("UTC")).toInstant(), lat, lon)[0];
    }

    /** התאריך הלועזי של יום מקראי (מחזור, שנה 1..6, חודש, יום). */
    public static LocalDate gregOf(int cyc, int year, int month, int day) {
        return ANCHOR.plusDays((long) cyc * CYCLE + (long) (year - 1) * 364 + doyOf(month, day) - 1);
    }

    /** אורך חודש מקראי. */
    public static int monthLen(int m) { return m % 3 == 0 ? 31 : 30; }

    /** השהות הנוכחית בשער: [שער, היום בשהות, אורך השהות]. בשבוע העיבור — שער 3 (סוף החודש ה-12). */
    public static int[] gateStay(Z z) {
        if (z.ic) return new int[]{GATE[11], 31 + z.icDay, 31 + 7};
        int g = GATE[z.month - 1];
        int first = z.month, last = z.month;
        if (first > 1 && GATE[first - 2] == g) first--;
        if (last < 12 && GATE[last] == g) last++;
        int len = 0, before = 0;
        for (int m = first; m <= last; m++) { if (m < z.month) before += monthLen(m); len += monthLen(m); }
        return new int[]{g, before + z.day, len};
    }

    // ===== הירח: מיקום בשמיים (שיטת Schlyter, דיוק של כמה דקות קשת) =====

    private static double rev(double x) { return x - Math.floor(x / 360.0) * 360.0; }

    /** [אורך אקליפטי, רוחב אקליפטי, מרחק ברדיוסי כדור הארץ] של הירח, וגם אורך השמש. */
    static double[] moonEcl(double d) {
        double N = rev(125.1228 - 0.0529538083 * d), i = 5.1454, w = rev(318.0634 + 0.1643573223 * d);
        double a = 60.2666, e = 0.054900, M = rev(115.3654 + 13.0649929509 * d);
        double ws = 282.9404 + 4.70935E-5 * d, Ms = rev(356.0470 + 0.9856002585 * d);
        double E = M + e * (180 / Math.PI) * Math.sin(M * RAD) * (1 + e * Math.cos(M * RAD));
        for (int k = 0; k < 6; k++) E = E - (E - e * (180 / Math.PI) * Math.sin(E * RAD) - M) / (1 - e * Math.cos(E * RAD));
        double xv = a * (Math.cos(E * RAD) - e), yv = a * Math.sqrt(1 - e * e) * Math.sin(E * RAD);
        double v = Math.atan2(yv, xv) / RAD, r = Math.sqrt(xv * xv + yv * yv);
        double xh = r * (Math.cos(N * RAD) * Math.cos((v + w) * RAD) - Math.sin(N * RAD) * Math.sin((v + w) * RAD) * Math.cos(i * RAD));
        double yh = r * (Math.sin(N * RAD) * Math.cos((v + w) * RAD) + Math.cos(N * RAD) * Math.sin((v + w) * RAD) * Math.cos(i * RAD));
        double zh = r * Math.sin((v + w) * RAD) * Math.sin(i * RAD);
        double lon = Math.atan2(yh, xh) / RAD, lat = Math.atan2(zh, Math.sqrt(xh * xh + yh * yh)) / RAD;
        double Ls = rev(Ms + ws), Lm = rev(M + w + N), D = rev(Lm - Ls), F = rev(Lm - N);
        lon += -1.274 * sn(M - 2 * D) + 0.658 * sn(2 * D) - 0.186 * sn(Ms) - 0.059 * sn(2 * M - 2 * D)
                - 0.057 * sn(M - 2 * D + Ms) + 0.053 * sn(M + 2 * D) + 0.046 * sn(2 * D - Ms) + 0.041 * sn(M - Ms)
                - 0.035 * sn(D) - 0.031 * sn(M + Ms) - 0.015 * sn(2 * F - 2 * D) + 0.011 * sn(M - 4 * D);
        lat += -0.173 * sn(F - 2 * D) - 0.055 * sn(M - F - 2 * D) - 0.046 * sn(M + F - 2 * D) + 0.033 * sn(F + 2 * D) + 0.017 * sn(2 * M + F);
        r += -0.58 * Math.cos((M - 2 * D) * RAD) - 0.46 * Math.cos(2 * D * RAD);
        return new double[]{rev(lon), lat, r, Ls};
    }

    private static double sn(double deg) { return Math.sin(deg * RAD); }

    /** [אזימוט, גובה נראה] של הירח מהמקום, כולל היסט (פרלקסה) ושבירה. */
    public static double[] moonAzAlt(Instant when, double lat, double lon) {
        double jd = when.toEpochMilli() / 86400000.0 + 2440587.5;
        double d = jd - 2451543.5;
        double[] m = moonEcl(d);
        double L = m[0] * RAD, B = m[1] * RAD, r = m[2];
        double ecl = (23.4393 - 3.563E-7 * d) * RAD;
        double xg = Math.cos(L) * Math.cos(B), yg = Math.sin(L) * Math.cos(B), zg = Math.sin(B);
        double ye = yg * Math.cos(ecl) - zg * Math.sin(ecl), ze = yg * Math.sin(ecl) + zg * Math.cos(ecl);
        double ra = Math.atan2(ye, xg) / RAD, dec = Math.atan2(ze, Math.sqrt(xg * xg + ye * ye)) / RAD;
        double ut = ((when.toEpochMilli() % 86400000L) + 86400000L) % 86400000L / 3600000.0;
        double lst = rev(m[3] + 180 + ut * 15 + lon);
        double ha = rev(lst - ra);
        double sa = Math.sin(lat * RAD) * Math.sin(dec * RAD) + Math.cos(lat * RAD) * Math.cos(dec * RAD) * Math.cos(ha * RAD);
        double alt = Math.asin(Math.max(-1, Math.min(1, sa))) / RAD;
        double az = Math.atan2(Math.sin(ha * RAD), Math.cos(ha * RAD) * Math.sin(lat * RAD) - Math.tan(dec * RAD) * Math.cos(lat * RAD)) / RAD + 180;
        alt -= Math.asin(1 / r) / RAD * Math.cos(alt * RAD);                 // פרלקסה: הירח נראה נמוך יותר
        if (alt > -1) alt += 1.02 / Math.tan((alt + 10.3 / (alt + 5.11)) * RAD) / 60;
        return new double[]{rev(az), alt};
    }

    /** זווית בין הירח לשמש לאורך האקליפטיקה (0 = מולד, 180 = מילוא). */
    public static double moonElongation(Instant when) {
        double jd = when.toEpochMilli() / 86400000.0 + 2440587.5;
        double[] m = moonEcl(jd - 2451543.5);
        return rev(m[0] - m[3]);
    }

    public static String hhmm(ZonedDateTime t) {
        return t.getHour() + ":" + String.format("%02d", t.getMinute());
    }
}
