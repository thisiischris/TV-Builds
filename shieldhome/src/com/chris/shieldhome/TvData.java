package com.chris.shieldhome;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.media.tv.TvContract;
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
                String art = s(c, "thumbnail_uri");
                if (art == null) art = s(c, "poster_art_uri");
                if (art == null) continue;
                Intent in;
                try {
                    in = Intent.parseUri(s(c, "intent_uri"), Intent.URI_INTENT_SCHEME);
                    if (in.resolveActivity(a.pm) == null) continue;
                } catch (Exception e) { continue; }
                String t = s(c, "title");
                if (t == null) continue;
                String d = s(c, "short_description");
                if (d == null) d = s(c, "long_description");
                all.add(new Object[]{pkg, t, d, art, in});
            }
        } catch (Exception e) { android.util.Log.w("ShieldHome", "hero " + e); }
        return all;
    }

    List<Object[]> collectFeatured(List<Object[]> previewRows) {
        List<Object[]> all = new ArrayList<>();
        for (Object[] row : previewRows) {
            String pkg = (String) row[0];
            if (pkg == null || !VIDEO_APPS.contains(pkg)) continue;
            all.add(row);
        }
        Collections.sort(all, (x, y) -> Long.compare(a.use((String) y[0]), a.use((String) x[0])));
        Map<String, List<Object[]>> by = new LinkedHashMap<>();
        for (Object[] o : all) {
            String k = (String) o[0];
            if (!by.containsKey(k)) by.put(k, new ArrayList<>());
            if (by.get(k).size() < 3) by.get(k).add(o);
        }
        List<Object[]> out = new ArrayList<>();
        for (int r = 0; r < 3; r++)
            for (List<Object[]> l : by.values())
                if (l.size() > r && out.size() < 10)
                    out.add(new Object[]{l.get(r)[0], l.get(r)[1], l.get(r)[2], l.get(r)[3], l.get(r)[4], "FEATURED"});
        android.util.Log.i("ShieldHome", "hero items=" + out.size() + " apps=" + by.keySet());
        return out;
    }

    List<Object[]> collectWatchNext() {
        Map<String, List<Object[]>> wnBy = new LinkedHashMap<>(), pvBy = new LinkedHashMap<>();
        Set<String> histPkg = new LinkedHashSet<>(), histGenre = new HashSet<>();
        try (Cursor c = a.getContentResolver().query(TvContract.WatchNextPrograms.CONTENT_URI, null, null, null, TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS + " DESC")) {
            while (c != null && c.moveToNext()) {
                String pkg = s(c, "package_name");
                if (newsRow(c, pkg)) continue;
                Object[] o = prog(c, true);
                if (o == null || o[0] == null || o[2] == null) continue;
                String k = (String) o[0];
                histPkg.add(k);
                addGenres(histGenre, s(c, "canonical_genre"));
                addGenres(histGenre, s(c, "genre"));
                if (!wnBy.containsKey(k)) wnBy.put(k, new ArrayList<>());
                wnBy.get(k).add(o);
            }
        } catch (Exception e) { android.util.Log.w("ShieldHome", "wn " + e); }
        try (Cursor c = a.getContentResolver().query(TvContract.PreviewPrograms.CONTENT_URI, null, null, null, null)) {
            while (c != null && c.moveToNext()) {
                String pkg = s(c, "package_name");
                if (pkg == null || !VIDEO_APPS.contains(pkg) || newsRow(c, pkg)) continue;
                Object[] o = prog(c, false);
                if (o == null || o[0] == null || o[2] == null) continue;
                String k = (String) o[0];
                if (!pvBy.containsKey(k)) pvBy.put(k, new ArrayList<>());
                if (genreHit(histGenre, s(c, "canonical_genre")) || genreHit(histGenre, s(c, "genre")))
                    pvBy.get(k).add(0, o);
                else pvBy.get(k).add(o);
            }
        } catch (Exception e) { android.util.Log.w("ShieldHome", "prev " + e); }
        StringBuilder diag = new StringBuilder();
        Set<String> pk = new LinkedHashSet<>();
        pk.addAll(wnBy.keySet());
        pk.addAll(pvBy.keySet());
        Map<String, Deque<Object[]>> cw = new HashMap<>(), nw = new HashMap<>();
        for (String k : pk) {
            if (!a.installed(k)) continue;
            cw.put(k, new ArrayDeque<>(wnBy.containsKey(k) ? wnBy.get(k) : new ArrayList<Object[]>()));
            nw.put(k, new ArrayDeque<>(pvBy.containsKey(k) ? pvBy.get(k) : new ArrayList<Object[]>()));
            Long m = a.usage.get(k);
            diag.append(k).append(" wn=").append(wnBy.containsKey(k) ? wnBy.get(k).size() : 0).append(" prev=").append(pvBy.containsKey(k) ? pvBy.get(k).size() : 0).append(" use=").append(m == null ? 0 : m / 60000).append("m; ");
        }
        List<String> order = new ArrayList<>(cw.keySet());
        Collections.sort(order, (x, y) -> {
            int hx = histPkg.contains(x) ? 0 : 1, hy = histPkg.contains(y) ? 0 : 1;
            if (hx != hy) return hx - hy;
            Long ua = a.usage.get(x), ub = a.usage.get(y);
            return Long.compare(ub == null ? 0 : ub, ua == null ? 0 : ua);
        });
        long top = 1;
        for (String k : order) { Long m = a.usage.get(k); if (m != null) top = Math.max(top, m); }
        Set<String> seenT = new HashSet<>();
        List<Object[]> cwL = rr(order, cw, top, 12, seenT);
        List<Object[]> nwL = new ArrayList<>();
        for (Object[] o : mazeNew()) {
            if (o[0] == null || o[1] == null) continue;
            if (!seenT.add(o[0] + "|" + o[1])) continue;
            nwL.add(o);
        }
        nwL.addAll(rr(order, nw, top, 12, seenT));
        List<Object[]> pick = new ArrayList<>();
        final int MAX = 12;
        int wantNew = Math.min(MAX * 2 / 5, nwL.size()), wantCw = Math.min(MAX - wantNew, cwL.size());
        wantNew = Math.min(nwL.size(), MAX - wantCw);
        int ci = 0, ni = 0;
        int[] pattern = {0, 0, 1, 0, 1};
        for (int slot = 0; pick.size() < wantCw + wantNew; slot++) {
            boolean takeNew = pattern[slot % 5] == 1;
            if (takeNew && ni < wantNew) pick.add(tag(nwL.get(ni++)));
            else if (ci < wantCw) pick.add(cwL.get(ci++));
            else if (ni < wantNew) pick.add(tag(nwL.get(ni++)));
        }
        android.util.Log.i("ShieldHome", "playnext " + diag + "order=" + order + " picked=" + pick.size() + " cw=" + cwL.size() + " new=" + nwL.size() + " hist=" + histPkg);
        return pick;
    }

    boolean newsRow(Cursor c, String pkg) {
        String genre = s(c, "canonical_genre");
        if (genre == null) genre = s(c, "genre");
        return isNews(pkg, appName(pkg), genre);
    }
    String appName(String pkg) {
        try { return a.pm.getApplicationLabel(a.pm.getApplicationInfo(pkg, 0)).toString(); }
        catch (Exception e) { return null; }
    }
    List<Object[]> mazeNew() {
        List<Object[]> out = new ArrayList<>();
        try {
            org.json.JSONArray arr = new org.json.JSONArray(a.prefs().getString("wn_json", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject o = arr.getJSONObject(i);
                String pkg = o.getString("pkg"), show = o.optString("show"), img = o.optString("img");
                if (!a.installed(pkg) || img.isEmpty() || show.isEmpty()) continue;
                if (isNews(pkg, appName(pkg), null)) continue;
                Intent li = a.pm.getLeanbackLaunchIntentForPackage(pkg);
                if (li == null) li = a.pm.getLaunchIntentForPackage(pkg);
                if (li == null) continue;
                out.add(new Object[]{pkg, show, img, li, -1f});
            }
        } catch (Exception e) { android.util.Log.w("ShieldHome", "maze " + e); }
        return out;
    }
    static boolean isNews(String pkg, String name, String genre) {
        if (genre != null && genre.toUpperCase(Locale.US).contains("NEWS")) return true;
        String b = ((pkg == null ? "" : pkg) + " " + (name == null ? "" : name)).toLowerCase(Locale.US);
        for (String k : new String[]{"news", "cnn", "msnbc", "haystack"})
            if (b.contains(k)) return true;
        return false;
    }
    static void addGenres(Set<String> into, String g) {
        if (g == null || g.isEmpty()) return;
        for (String x : g.split("[,/|]")) { x = x.trim().toUpperCase(Locale.US); if (!x.isEmpty() && !x.equals("NEWS")) into.add(x); }
    }
    static boolean genreHit(Set<String> hist, String g) {
        if (g == null || hist.isEmpty()) return false;
        String u = g.toUpperCase(Locale.US);
        for (String h : hist) if (u.contains(h)) return true;
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
        String thumb = s(c, "thumbnail_uri");
        if (thumb == null) thumb = s(c, "poster_art_uri");
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

    List<Object[]> rr(List<String> order, Map<String, Deque<Object[]>> q, long top, int max, Set<String> seen) {
        List<Object[]> out = new ArrayList<>();
        while (out.size() < max) {
            boolean any = false;
            for (String k : order) {
                Long m = a.usage.get(k);
                int w = 1 + (int) Math.round(1.0 * (m == null ? 0 : m) / top);
                for (int r = 0; r < w && out.size() < max; r++) {
                    Object[] o;
                    do { o = q.get(k).poll(); } while (o != null && !seen.add(k + "|" + o[1]));
                    if (o == null) break;
                    out.add(o);
                    any = true;
                }
            }
            if (!any) break;
        }
        return out;
    }

    static Object[] tag(Object[] o) { return new Object[]{o[0], o[1], o[2], o[3], -1f, "NEW"}; }
    static String clean(String t) {
        if (t == null) return null;
        t = t.replaceAll("(?i)\\s*[\\-\u00B7:|]?\\s*continue watching\\s*[\\-\u00B7:|]?\\s*", " ").trim();
        return t.isEmpty() ? null : t;
    }
    static String s(Cursor c, String col) { int i = c.getColumnIndex(col); return i < 0 || c.isNull(i) ? null : c.getString(i); }
    static long l(Cursor c, String col) { int i = c.getColumnIndex(col); return i < 0 || c.isNull(i) ? 0 : c.getLong(i); }
}
