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
        for (Object[] o : out) android.util.Log.i("ShieldHome", "hero item src=preview pkg=" + o[0] + " title=" + o[1] + " target=" + uriOf(o[4]));
        return out;
    }

    static final String YT = "com.google.android.youtube.tv";
    static final int MAX_ITEMS = 20;
    static final float FINISHED_FRAC = 0.9f;
    static final int WN_CONTINUE = 0, WN_NEXT = 1, WN_NEW = 2; // TvContract watch_next_type

    /** Play Next (video apps, ≤MAX_ITEMS), each group by engagement: other apps' resume/NEXT rows, then YouTube NEW, then YouTube resume. No recs. */
    List<Object[]> collectWatchNext() {
        List<Object[]> rows = new ArrayList<>();
        try (Cursor c = a.getContentResolver().query(TvContract.WatchNextPrograms.CONTENT_URI, null, null, null, TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS + " DESC")) {
            int idx = 0;
            while (c != null && c.moveToNext()) {
                String pkg = s(c, "package_name");
                if (pkg == null || !VIDEO_APPS.contains(pkg) || newsRow(c, pkg) || !a.installed(pkg)) continue;
                Object[] o = prog(c, true);
                if (o == null || o[0] == null || o[2] == null) continue;
                rows.add(new Object[]{o, l(c, "last_engagement_time_utc_millis"), l(c, "last_playback_position_millis"), l(c, "duration_millis"), (int) l(c, "watch_next_type"), clean(s(c, "title")), idx++, uriOf(o[3])});
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
        Set<String> feat = new HashSet<>();
        if (a.featuredCache != null) for (Object[] f : a.featuredCache) {
            if (f[0] != null && f[1] != null) feat.add(f[0] + "|" + clean((String) f[1]));
            feat.add(uriOf(f[4]));
        }
        List<Object[]> pick = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        List<Object[]> other = new ArrayList<>(), ytNew = new ArrayList<>(), ytResume = new ArrayList<>();
        Map<String, Integer> ytTypes = new TreeMap<>();
        int dropDone = 0, dropZero = 0, dropType = 0;
        for (Object[] r : rows) {
            if (YT.equals(((Object[]) r[0])[0])) ytTypes.merge("t" + r[4], 1, Integer::sum);
            Object[] o = (Object[]) r[0];
            String pkg = (String) o[0];
            long pos = (Long) r[2], dur = (Long) r[3];
            int type = (Integer) r[4];
            // Nearly finished: hidden; a show's next episode is its own NEXT-type row and shows instead.
            if (finished(pos, dur)) { dropDone++; continue; }
            boolean ytNewRow = type == WN_NEW && YT.equals(pkg);
            if (type != WN_CONTINUE && type != WN_NEXT && !ytNewRow) { dropType++; continue; }
            if (type == WN_CONTINUE && pos <= 0) { dropZero++; continue; }
            if (!fresh(pkg, (String) r[5], (String) r[7], feat, seen)) continue;
            (ytNewRow ? ytNew : YT.equals(pkg) ? ytResume : other).add(o);
        }
        int[] shown = new int[3]; List<List<Object[]>> groups = Arrays.asList(other, ytNew, ytResume);
        for (int g = 0; g < groups.size(); g++) {
            List<Object[]> l = groups.get(g);
            shown[g] = Math.min(l.size(), MAX_ITEMS - pick.size());
            pick.addAll(l.subList(0, shown[g]));
        }
        android.util.Log.i("ShieldHome", "playnext other=" + shown[0] + "/" + other.size() + " ytNew=" + shown[1] + "/" + ytNew.size() + " ytResume=" + shown[2] + "/" + ytResume.size() + " wnRows=" + rows.size() + " ytTypes=" + ytTypes + " droppedFinished=" + dropDone + " droppedNoProgress=" + dropZero + " droppedNotResume=" + dropType);
        return mixOrder(pick, a.wnPickCache);
    }
    /** Random order, but keeps the previous order when the same items (by intent) are picked again, so tiles don't jump on reloads. */
    static List<Object[]> mixOrder(List<Object[]> pick, List<Object[]> prev) {
        Map<String, Integer> at = new HashMap<>();
        if (prev != null) for (Object[] o : prev) at.put(uriOf(o[3]), at.size());
        boolean same = prev != null && prev.size() == pick.size() && at.size() == pick.size();
        for (Object[] o : pick) same &= at.containsKey(uriOf(o[3]));
        if (same) Collections.sort(pick, (x, y) -> at.get(uriOf(x[3])) - at.get(uriOf(y[3])));
        else Collections.shuffle(pick);
        android.util.Log.i("ShieldHome", "playnext order=" + (same ? "kept" : "shuffled"));
        return pick;
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
