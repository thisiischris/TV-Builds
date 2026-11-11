package com.chris.shieldhome;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.media.tv.TvContract;
import java.text.SimpleDateFormat;
import java.util.*;

/** Watch Next, PreviewPrograms, Channels, apps, usage. No views. */
class TvData {
    final MainActivity a;
    static final Set<String> VIDEO_APPS = new HashSet<>(Arrays.asList("com.netflix.ninja","com.disney.disneyplus","com.hulu.livingroomplus","com.wbd.stream","com.amazon.amazonvideo.livingroom","com.amazon.amazonvideo.livingroom.nvidia","com.plexapp.android","com.bydeluxe.d3.android.program.starz","com.crunchyroll.crunchyroid","com.google.android.youtube.tv","org.xbmc.kodi","com.google.android.videos","com.apple.atve.androidtv.appletv","com.peacocktv.peacockandroid","com.cbs.ott","com.paramount.android.pplus"));

    TvData(MainActivity a) { this.a = a; }

    Map<String, Long> queryUsage() {
        Map<String, Long> u = new HashMap<>();
        try {
            UsageStatsManager um = (UsageStatsManager) a.getSystemService(MainActivity.USAGE_STATS_SERVICE);
            long now = System.currentTimeMillis();
            List<UsageStats> l = um.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 30L * 86400000L, now);
            if (l != null) for (UsageStats st : l) {
                Long v = u.get(st.getPackageName());
                u.put(st.getPackageName(), (v == null ? 0 : v) + st.getTotalTimeInForeground());
            }
        } catch (Exception e) { android.util.Log.w("ShieldHome", "usage " + e); }
        return u;
    }

    List<String[]> queryApps() {
        List<ResolveInfo> l = a.pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0);
        List<String[]> out = new ArrayList<>();
        String[] settings = null;
        for (ResolveInfo ri : l) {
            String pk = ri.activityInfo.packageName;
            if (pk.equals(a.getPackageName())) continue;
            String[] e = {pk, ri.activityInfo.name, ri.loadLabel(a.pm).toString()};
            if (pk.equals("com.android.tv.settings")) { e[2] = "Settings"; settings = e; }
            else out.add(e);
        }
        final List<String> pref = Arrays.asList("com.netflix.ninja","com.disney.disneyplus","com.hulu.livingroomplus","com.wbd.stream","com.amazon.amazonvideo.livingroom","com.plexapp.android","com.google.android.youtube.tv","com.spotify.tv.android","com.crunchyroll.crunchyroid","org.xbmc.kodi","com.bydeluxe.d3.android.program.starz");
        Collections.sort(out, (x, y) -> {
            int ia = pref.indexOf(x[0]), ib = pref.indexOf(y[0]);
            if (ia < 0) ia = 99; if (ib < 0) ib = 99;
            return ia != ib ? ia - ib : x[2].compareToIgnoreCase(y[2]);
        });
        if (settings != null) out.add(settings);
        return out;
    }

    List<Object[]> queryPreviewFeatured() {
        List<Object[]> all = new ArrayList<>();
        try (Cursor c = a.getContentResolver().query(TvContract.PreviewPrograms.CONTENT_URI, null, null, null, null)) {
            while (c != null && c.moveToNext()) {
                String pkg = s(c, "package_name");
                if (pkg == null || !VIDEO_APPS.contains(pkg)) continue;
                String art = landArt(s(c, "thumbnail_uri"), s(c, "poster_art_uri"));
                if (art == null) continue;
                Intent in = null;
                try {
                    String u = s(c, "intent_uri");
                    if (u != null) {
                        in = Intent.parseUri(u, Intent.URI_INTENT_SCHEME);
                        if (in.resolveActivity(a.pm) == null) in = null;
                    }
                } catch (Exception e) { in = null; }
                String t = s(c, "title");
                if (t == null) continue;
                String d = s(c, "short_description");
                if (d == null) d = s(c, "long_description");
                long avail = Math.max(l(c, "start_time_utc_millis"), parseRelease(s(c, "release_date")));
                all.add(new Object[]{pkg, t, d, art, in, avail});
            }
        } catch (Exception e) { android.util.Log.w("ShieldHome", "hero " + e); }
        return all;
    }

    List<Object[]> collectFeatured(List<Object[]> previewRows) {
        List<Object[]> all = new ArrayList<>();
        for (Object[] row : previewRows) {
            String pkg = (String) row[0];
            if (pkg == null || !VIDEO_APPS.contains(pkg)) continue;
            if (opensYouTube(pkg, (Intent) row[4])) continue; // YT recs live in Play Next only
            all.add(row);
        }
        Collections.sort(all, (x, y) -> Long.compare(a.grid.use((String) y[0]), a.grid.use((String) x[0])));
        Map<String, List<Object[]>> by = new LinkedHashMap<>();
        for (Object[] o : all) {
            String k = (String) o[0];
            if (!by.containsKey(k)) by.put(k, new ArrayList<>());
            if (by.get(k).size() < 3) by.get(k).add(o);
        }
        List<Object[]> out = new ArrayList<>();
        for (int r = 0; r < 3; r++)
            for (List<Object[]> l : by.values())
                if (l.size() > r && out.size() < 10) {
                    Object[] row = l.get(r);
                    out.add(new Object[]{row[0], row[1], row[2], row[3], row[4], "FEATURED", row.length > 5 ? row[5] : 0L});
                }
        android.util.Log.i("ShieldHome", "hero items=" + out.size() + " apps=" + by.keySet());
        return out;
    }

    static final String YT = "com.google.android.youtube.tv";
    static final int MAX_ITEMS = 20;
    static final float FINISHED_FRAC = 0.9f;
    static final int WN_CONTINUE = 0, WN_NEXT = 1, WN_NEW = 2; // TvContract watch_next_type
    /** Play Next pick slots: {pkg, caption, thumb, intent, progress (-1 = none), isNew, engagement, playback, subtitle, score}. Progress alone can't tell NEW: NEXT-type rows at position 0 have -1 too. */
    static final int PICK_IS_NEW = 5;
    static final int PICK_ENGAGEMENT = 6, PICK_PLAYBACK = 7, PICK_SUB = 8, PICK_SCORE = 9, PICK_LEN = 10;
    static final String SUB_RESUME = "Resume watching", SUB_NEW = "New", SUB_REC = "Recommended";

    // ---- v95 Play Next scoring: every candidate gets base(kind) × recency × app weight; the top MAX_ITEMS are interleaved by app. ----
    static final long DAY_MS = 86400000L;
    /** Base weight per kind: started-and-unfinished first, then next episode / new upload, then app recommendations. */
    static final double BASE_RESUME = 1.0, BASE_NEXT = 0.85, BASE_NEW = 0.8, BASE_REC = 0.6;
    /** Up to RESUME_MID_BONUS extra for an item halfway through (likeliest to be continued), tapering to 0 at the ends. */
    static final double RESUME_MID_BONUS = 0.3;
    /** Scores halve every HALF_LIFE_DAYS since last watched / published; items with no timestamp count as UNKNOWN_AGE_DAYS old. */
    static final double HALF_LIFE_DAYS = 7, UNKNOWN_AGE_DAYS = 7;
    /** Dropped outright: YouTube NEW uploads older than NEW_MAX_DAYS, resume/next rows untouched for RESUME_MAX_DAYS. */
    static final long NEW_MAX_DAYS = 14, RESUME_MAX_DAYS = 60;
    /** App weight = 1 + USAGE_K·ln(1 + hours used in the last 30 days). */
    static final double USAGE_K = 0.25;
    /** A recommendation's weight falls with its position in the app's own ranking (its first rows are its trending/top picks). */
    static final double REC_RANK_K = 0.35;
    /** At most YT_CAP YouTube tiles while other apps have candidates left; the row is always filled to MAX_ITEMS when there are enough. */
    static final int YT_CAP = 10;

    /** Play Next (video apps, ≤MAX_ITEMS): resume/next rows from every app, YouTube NEW uploads and (while the featured card is hidden) app
     *  recommendations, scored and interleaved so apps alternate. See the v95 constants above. */
    List<Object[]> collectWatchNext() {
        List<Object[]> rows = new ArrayList<>();
        try (Cursor c = a.getContentResolver().query(TvContract.WatchNextPrograms.CONTENT_URI, null, null, null, TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS + " DESC")) {
            int idx = 0;
            while (c != null && c.moveToNext()) {
                String pkg = s(c, "package_name");
                if (pkg == null || !VIDEO_APPS.contains(pkg) || newsRow(c, pkg) || !a.installed(pkg)) continue;
                Object[] o = prog(c, true);
                if (o == null || o[0] == null || o[2] == null) continue;
                long published = Math.max(l(c, "start_time_utc_millis"), parseRelease(s(c, "release_date")));
                rows.add(new Object[]{o, l(c, "last_engagement_time_utc_millis"), l(c, "last_playback_position_millis"), l(c, "duration_millis"), (int) l(c, "watch_next_type"), clean(s(c, "title")), idx++, uriOf(o[3]), published});
            }
        } catch (Exception e) { android.util.Log.w("ShieldHome", "wn " + e); }
        Collections.sort(rows, (x, y) -> {
            long ex = (Long) x[1], ey = (Long) y[1];
            if (ex != ey) return Long.compare(ey, ex);
            if (ex == 0) {
                long px = (Long) x[2], py = (Long) y[2];
                if (px != py) return Long.compare(py, px);
            }
            return Integer.compare((Integer) x[6], (Integer) y[6]);
        });
        // The featured card shows app recommendations only while the wallpaper is off; then Play Next keeps excluding them.
        // With the wallpaper on the card is hidden, so those recommendations join Play Next instead.
        boolean heroOn = !a.settings.wallOn();
        Set<String> feat = new HashSet<>();
        if (heroOn && a.featuredCache != null) for (Object[] f : a.featuredCache) {
            if (f[0] != null && f[1] != null) feat.add(f[0] + "|" + clean((String) f[1]));
            feat.add(uriOf(f[4]));
        }
        long now = System.currentTimeMillis();
        Set<String> seen = new HashSet<>();
        List<Object[]> cands = new ArrayList<>();
        int dropDone = 0, dropZero = 0, dropType = 0, dropOldNew = 0, dropStale = 0;
        for (Object[] r : rows) {
            Object[] o = (Object[]) r[0];
            String pkg = (String) o[0];
            long engaged = (Long) r[1], pos = (Long) r[2], dur = (Long) r[3], published = (Long) r[8];
            int type = (Integer) r[4];
            // Nearly finished: hidden; a show's next episode is its own NEXT-type row and shows instead.
            if (finished(pos, dur)) { dropDone++; continue; }
            boolean ytNewRow = type == WN_NEW && YT.equals(pkg);
            if (type != WN_CONTINUE && type != WN_NEXT && !ytNewRow) { dropType++; continue; }
            if (type == WN_CONTINUE && pos <= 0) { dropZero++; continue; }
            // NEW = new to you, not necessarily new: age it by its publish time when the app gives one, else by when it was added.
            long when = ytNewRow && published > 0 ? published : engaged;
            if (ytNewRow && when > 0 && now - when > NEW_MAX_DAYS * DAY_MS) { dropOldNew++; continue; }
            if (!ytNewRow && engaged > 0 && now - engaged > RESUME_MAX_DAYS * DAY_MS) { dropStale++; continue; }
            if (!fresh(pkg, (String) r[5], (String) r[7], feat, seen)) continue;
            float prog = (Float) o[4];
            double base = ytNewRow ? BASE_NEW : type == WN_NEXT ? BASE_NEXT : BASE_RESUME + (prog > 0 ? RESUME_MID_BONUS * (1 - Math.abs(prog - 0.5) * 2) : 0);
            cands.add(pickOf(o, ytNewRow, engaged, pos, ytNewRow ? SUB_NEW : SUB_RESUME, base * decay(when, now) * appWeight(pkg)));
        }
        int recs = heroOn ? 0 : addRecommendations(cands, seen);
        List<Object[]> pick = choose(cands);
        Map<String, Integer> byApp = new TreeMap<>(); for (Object[] p : pick) byApp.merge(shortPkg((String) p[0]) + ":" + p[PICK_SUB], 1, Integer::sum);
        android.util.Log.i("ShieldHome", "playnext picked=" + pick.size() + "/" + cands.size() + " recs=" + recs + " heroOn=" + heroOn + " mix=" + byApp
            + " droppedFinished=" + dropDone + " droppedNoProgress=" + dropZero + " droppedNotResume=" + dropType + " droppedOldNew=" + dropOldNew + " droppedStale=" + dropStale);
        return pick;
    }
    static Object[] pickOf(Object[] o, boolean isNew, long engaged, long pos, String sub, double score) {
        Object[] p = Arrays.copyOf(o, PICK_LEN);
        p[PICK_IS_NEW] = isNew; p[PICK_ENGAGEMENT] = engaged; p[PICK_PLAYBACK] = pos; p[PICK_SUB] = sub; p[PICK_SCORE] = score;
        return p;
    }
    /** 0.5^(age/HALF_LIFE_DAYS); unknown timestamps count as UNKNOWN_AGE_DAYS. */
    static double decay(long when, long now) {
        double days = when > 0 ? Math.max(0, now - when) / (double) DAY_MS : UNKNOWN_AGE_DAYS;
        return Math.pow(0.5, days / HALF_LIFE_DAYS);
    }
    /** Raw 30-day foreground time (not AppGridController.use(), which inflates YouTube for the grid order). */
    double appWeight(String pkg) {
        Long ms = a.usage == null ? null : a.usage.get(pkg);
        return 1 + USAGE_K * Math.log1p((ms == null ? 0 : ms) / 3600000.0);
    }
    /** Video apps' home-screen recommendations (PreviewPrograms), each app's rows in its own order (channel, then weight); rank lowers the score. */
    int addRecommendations(List<Object[]> cands, Set<String> seen) {
        int n = 0; Map<String, Integer> rank = new HashMap<>();
        String order = TvContract.PreviewPrograms.COLUMN_CHANNEL_ID + " ASC, " + TvContract.PreviewPrograms.COLUMN_WEIGHT + " DESC";
        try (Cursor c = a.getContentResolver().query(TvContract.PreviewPrograms.CONTENT_URI, null, null, null, order)) {
            while (c != null && c.moveToNext()) {
                String pkg = s(c, "package_name");
                if (pkg == null || YT.equals(pkg) || !VIDEO_APPS.contains(pkg) || newsRow(c, pkg) || !a.installed(pkg)) continue;
                Object[] o = prog(c, false);
                if (o == null || o[2] == null) continue;
                if (opensYouTube(pkg, (Intent) o[3])) continue;
                if (!fresh(pkg, clean(s(c, "title")), uriOf(o[3]), Collections.emptySet(), seen)) continue;
                int r = rank.merge(pkg, 1, Integer::sum) - 1;
                double popular = 1 + 0.05 * Math.log10(1 + Math.max(0, l(c, "interaction_count")));
                cands.add(pickOf(o, false, 0L, 0L, SUB_REC, BASE_REC / (1 + REC_RANK_K * r) * popular * appWeight(pkg)));
                n++;
            }
        } catch (Exception e) { android.util.Log.w("ShieldHome", "recs " + e); }
        return n;
    }
    /** Top MAX_ITEMS by score with YouTube held to YT_CAP while others remain (the row still fills from YouTube if they run out),
     *  then interleaved: each slot takes the best remaining item from a different app than the previous slot when one exists. */
    static List<Object[]> choose(List<Object[]> cands) {
        List<Object[]> sorted = new ArrayList<>(cands);
        Collections.sort(sorted, (x, y) -> Double.compare((Double) y[PICK_SCORE], (Double) x[PICK_SCORE]));
        List<Object[]> chosen = new ArrayList<>(), overflow = new ArrayList<>();
        int yt = 0;
        for (Object[] p : sorted) {
            if (chosen.size() >= MAX_ITEMS) break;
            if (YT.equals(p[0]) && yt >= YT_CAP) { overflow.add(p); continue; }
            if (YT.equals(p[0])) yt++;
            chosen.add(p);
        }
        for (Object[] p : overflow) { if (chosen.size() >= MAX_ITEMS) break; chosen.add(p); }
        List<Object[]> out = new ArrayList<>(); String last = null;
        while (!chosen.isEmpty()) {
            int at = 0; // chosen stays score-sorted (overflow items are lower than everything before them)
            for (int i = 0; i < chosen.size(); i++) if (!chosen.get(i)[0].equals(last)) { at = i; break; }
            Object[] p = chosen.remove(at); out.add(p); last = (String) p[0];
        }
        return out;
    }
    static String shortPkg(String pkg) { int i = pkg.lastIndexOf('.'); return i < 0 ? pkg : pkg.substring(i + 1); }
    static final String PLUTO = "tv.pluto.android";
    /** Pluto's latest unfinished watch-next row as a Play Next pick slot {pkg, caption, thumb, intent, progress}, or null if Pluto publishes none. */
    Object[] plutoResume() {
        try (Cursor c = a.getContentResolver().query(TvContract.WatchNextPrograms.CONTENT_URI, null, null, null, TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS + " DESC")) {
            int rows = 0;
            while (c != null && c.moveToNext()) {
                if (!PLUTO.equals(s(c, "package_name"))) continue; rows++;
                int type = (int) l(c, "watch_next_type");
                if ((type != WN_CONTINUE && type != WN_NEXT) || finished(l(c, "last_playback_position_millis"), l(c, "duration_millis"))) continue;
                Object[] o = prog(c, true);
                if (o != null && o[2] != null) { android.util.Log.i("ShieldHome", "pluto resume: watch-next row (" + rows + " pluto rows)"); return o; }
            }
            android.util.Log.i("ShieldHome", "pluto resume: none (" + rows + " pluto watch-next rows)");
        } catch (Exception e) { android.util.Log.w("ShieldHome", "pluto resume " + e); }
        return null;
    }
    /** True (and marks seen) when pkg|title and intent uri are not in the featured card or already in Play Next. */
    static boolean fresh(String pkg, String title, String uri, Set<String> feat, Set<String> seen) {
        String pkTitle = title == null ? null : pkg + "|" + title;
        if (pkTitle != null && (feat.contains(pkTitle) || seen.contains(pkTitle))) return false;
        if (!uri.isEmpty() && (feat.contains(uri) || seen.contains(uri))) return false;
        if (pkTitle != null) seen.add(pkTitle);
        if (!uri.isEmpty()) seen.add(uri);
        return true;
    }
    static String uriOf(Object intent) {
        try { return ((Intent) intent).toUri(Intent.URI_INTENT_SCHEME); } catch (Exception e) { return ""; }
    }
    /** YouTube content, whether published by YouTube itself or by another app (e.g. Google TV) whose intent opens YouTube. */
    static boolean opensYouTube(String pkg, Intent in) {
        if (YT.equals(pkg)) return true;
        if (in == null) return false;
        if (YT.equals(in.getPackage()) || (in.getComponent() != null && YT.equals(in.getComponent().getPackageName()))) return true;
        String host = in.getData() == null ? null : in.getData().getHost();
        return host != null && (host.equals("youtube.com") || host.endsWith(".youtube.com") || host.equals("youtu.be"));
    }
    static boolean finished(long pos, long dur) { return dur > 0 && pos >= dur * FINISHED_FRAC; }

    boolean newsRow(Cursor c, String pkg) {
        String genre = s(c, "canonical_genre");
        if (genre == null) genre = s(c, "genre");
        return isNews(pkg, appName(pkg), genre);
    }
    String appName(String pkg) {
        try { return a.pm.getApplicationLabel(a.pm.getApplicationInfo(pkg, 0)).toString(); }
        catch (Exception e) { return null; }
    }
    static boolean isNews(String pkg, String name, String genre) {
        if (genre != null && genre.toUpperCase(Locale.US).contains("NEWS")) return true;
        String b = ((pkg == null ? "" : pkg) + " " + (name == null ? "" : name)).toLowerCase(Locale.US);
        for (String k : new String[]{"news", "cnn", "msnbc", "haystack"})
            if (b.contains(k)) return true;
        return false;
    }
    Map<String, Long> queryPlutoIds() {
        Map<String, Long> plutoIds = new HashMap<>();
        try (Cursor c = a.getContentResolver().query(TvContract.Channels.CONTENT_URI, null, null, null, null)) {
            while (c != null && c.moveToNext()) {
                if (!"tv.pluto.android".equals(s(c, "package_name"))) continue;
                String nm = s(c, "display_name");
                if (nm != null) plutoIds.put(nm, l(c, "_id"));
            }
        } catch (Exception e) { android.util.Log.w("ShieldHome", "channels " + e); }
        return plutoIds;
    }

    Object[] prog(Cursor c, boolean watchNext) {
        String pkg = s(c, "package_name"), title = s(c, "title");
        String thumb = landArt(s(c, "thumbnail_uri"), s(c, "poster_art_uri"));
        Intent in;
        try {
            in = Intent.parseUri(s(c, "intent_uri"), Intent.URI_INTENT_SCHEME);
            if (in.resolveActivity(a.pm) == null) return null;
        } catch (Exception e) { return null; }
        long dur = l(c, "duration_millis"), pos = watchNext ? l(c, "last_playback_position_millis") : 0;
        String se = s(c, "season_display_number"), ep = s(c, "episode_display_number"), et = clean(s(c, "episode_title"));
        title = clean(title);
        boolean okSe = se != null && ep != null && se.matches("\\d+") && ep.matches("\\d+") && !se.equals("0") && !ep.equals("0");
        String cap;
        if (okSe) cap = "S" + se + " E" + ep + (et != null ? " \u00B7 " + et : (title != null ? " \u00B7 " + title : ""));
        else cap = title == null ? "" : title;
        String desc = s(c, "short_description");
        if ("com.netflix.ninja".equals(pkg) && desc != null && desc.matches("(?i)^continue watching\\s.*")) {
            String epn = desc.replaceFirst("(?i)^continue watching\\s+", "").trim();
            if (!epn.isEmpty()) cap = (title == null ? "" : title + " \u00B7 ") + epn;
            if (thumb != null) thumb += "#cwcrop";
        }
        if ("com.netflix.ninja".equals(pkg) && desc != null && desc.startsWith("Continue"))
            android.util.Log.i("ShieldHome", "nfx row wn=" + watchNext + " type=" + s(c, "type") + " title=" + s(c, "title") + " se=" + se + " ep=" + ep + " et=" + s(c, "episode_title") + " desc=" + s(c, "short_description") + " sTitle=" + s(c, "season_title") + " thumb=" + thumb);
        if (dur > 0 && pos > 0) cap += " \u00B7 " + Math.max(1, (dur - pos) / 60000) + " min left";
        return new Object[]{pkg, cap, thumb, in, (dur > 0 && pos > 0) ? (float) pos / dur : -1f};
    }

    static String clean(String t) {
        if (t == null) return null;
        t = t.replaceAll("(?i)\\s*[\\-\u00B7:|]?\\s*continue watching\\s*[\\-\u00B7:|]?\\s*", " ").trim();
        return t.isEmpty() ? null : t;
    }
    static String landArt(String thumb, String poster){ return thumb!=null?thumb:poster; }
    static long parseRelease(String s) {
        if (s == null || s.isEmpty()) return 0;
        s = s.trim();
        for (String f : new String[]{"yyyy-MM-dd", "yyyy-MM", "yyyy"})
            try { Date d = new SimpleDateFormat(f, Locale.US).parse(s); if (d != null) return d.getTime(); } catch (Exception e) {}
        return 0;
    }
    static String s(Cursor c, String col) { int i = c.getColumnIndex(col); return i < 0 || c.isNull(i) ? null : c.getString(i); }
    static long l(Cursor c, String col) { int i = c.getColumnIndex(col); return i < 0 || c.isNull(i) ? 0 : c.getLong(i); }
}
