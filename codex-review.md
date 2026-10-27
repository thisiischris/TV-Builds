# Codex review (codex-cli 0.165.0, gpt-6-luna, read-only sandbox) of TV-Builds Shield_AppleStyle @ 8a1b3db

## Efficiency

- [MainActivity.java:570](/tmp/codexreview/shieldhome/src/com/chris/shieldhome/MainActivity.java:570) — **low** — `HeroDots.onDraw()` creates a new `Paint` on every redraw. Keep one `Paint` as a view field and update its color as needed.
- [MainActivity.java:530](/tmp/codexreview/shieldhome/src/com/chris/shieldhome/MainActivity.java:530) — **low** — `SegBar.dispatchDraw()` allocates `Paint` objects during every draw. Cache the paints as fields; similarly reuse the shadow paint at [MainActivity.java:539](/tmp/codexreview/shieldhome/src/com/chris/shieldhome/MainActivity.java:539).
- [MainActivity.java:604](/tmp/codexreview/shieldhome/src/com/chris/shieldhome/MainActivity.java:604) — **med** — Each focus change creates a `DecelerateInterpolator` and `ValueAnimator`, without cancelling the previous glow animator. Keep the animator per `GlowFrame`, cancel or retarget it, and reuse a view-level interpolator.
- [MainActivity.java:382](/tmp/codexreview/shieldhome/src/com/chris/shieldhome/MainActivity.java:382) — **low** — `PillView.onFocusChanged()` constructs an interpolator on each focus change. Reuse one.
- [MainActivity.java:1697](/tmp/codexreview/shieldhome/shieldhome/src/com/chris/shieldhome/MainActivity.java:1697) — **med** — `useWhatsNew()` repeatedly constructs date formatters and parses the current date inside the item loop. Create the formatters and compute today’s date once per call; parse each item date once and reuse it for `avail`.
- [FocusController.java:156](/tmp/codexreview/shieldhome/src/com/chris/shieldhome/FocusController.java:156) — **low** — `secRoot()` builds a section list just to find an ancestor, then `indexOf()` rebuilds the same list in `remember()`/navigation. Resolve the section child directly and avoid repeated list creation on key events.
- [MainActivity.java:1380](/tmp/codexreview/shieldhome/src/com/chris/shieldhome/MainActivity.java:1380) — **med** — `addCard()` calls `setLayoutParams()` even when an existing card already has identical dimensions and margins, triggering layout work. Compare parameters first or only update when the card’s position/size changed.

## Structure

- [MainActivity.java:18](/tmp/codexreview/shieldhome/src/com/chris/shieldhome/MainActivity.java:18) — **high** — `MainActivity` is an 1,804-line god class combining view construction/drawing, focus and input, app-grid editing, card binding, Pluto/network data, hero rotation, search, and dimming. Extract cohesive controllers, starting with card/live-TV data and binding, then hero UI/rotation and search/dimmer behavior; keep the activity as lifecycle and coordination glue.
- [MainActivity.java:1503](/tmp/codexreview/shieldhome/shieldhome/src/com/chris/shieldhome/MainActivity.java:1503) — **med** — Pluto state is stored in parallel maps and `List<Object[]>` records, making relationships and index meanings fragile. Use typed channel/card model classes and move fetch/parse/cache logic into a Pluto data component.
- [TvData.java:55](/tmp/codexreview/shieldhome/shieldhome/src/com/chris/shieldhome/TvData.java:55) — **med** — TV rows are represented as positional `Object[]` values throughout the data pipeline. Replace them with named model types so callers don’t rely on numeric indexes and casts.
- [FocusController.java:156](/tmp/codexreview/shieldhome/shieldhome/src/com/chris/shieldhome/FocusController.java:156) — **low** — `secItems()` repeats visibility/focusability filtering for card and app sections. A shared helper would make section enumeration easier to maintain.

## Cleanup

- [MainActivity.java:385](/tmp/codexreview/shieldhome/src/com/chris/shieldhome/MainActivity.java:385) — **low** — `lit` is computed but never used; remove it.
- [MainActivity.java:600](/tmp/codexreview/shieldhome/shieldhome/src/com/chris/shieldhome/MainActivity.java:600) — **low** — The `base` parameter to both `focusable()` overloads is unused. Remove it and update call sites.
- [MainActivity.java:796](/tmp/codexreview/shieldhome/shieldhome/src/com/chris/shieldhome/MainActivity.java:796) — **low** — `homeGrid.setLayoutParams()` is called after every rebuild even if the row count and height are unchanged. Skip the update when the computed height matches.
- [MainActivity.java:1484](/tmp/codexreview/shieldhome/shieldhome/src/com/chris/shieldhome/MainActivity.java:1484) — **low** — After the preceding fallback logic, this loop only runs for the live tab, so the `tab==0 ? PLAY_PKGS : LIVE_PKGS` branch is redundant.
- [NetData.java:62](/tmp/codexreview/shieldhome/shieldhome/src/com/chris/shieldhome/NetData.java:62) — **low** — The date offsets and fetch/cache durations are embedded literals across the data code. Name the schedule offsets and timing values as constants to make their intent and tuning clear.
- [MainActivity.java:1241](/tmp/codexreview/shieldhome/shieldhome/src/com/chris/shieldhome/MainActivity.java:1241) — **low** — `16000` is repeated for hero rotation scheduling. Define a named rotation interval; likewise name repeated animation durations and card/grid sizing values where they represent shared design decisions.
---
## Full log
Reading additional input from stdin...
OpenAI Codex v0.165.0
--------
workdir: /tmp/codexreview
model: gpt-6-luna
provider: openai
approval: never
sandbox: read-only
reasoning effort: none
reasoning summaries: none
session id: 01a17f1c-9756-7a00-9453-43eb2228880d
--------
user
READ-ONLY code review. Do NOT edit, create, commit or push any files. Review the Android TV launcher in shieldhome/src/com/chris/shieldhome/*.java (Java, Android 11, NVIDIA Shield). Find: (1) inefficient code: allocations in onDraw/animation/focus paths, redundant layout passes, repeated lookups; (2) duplicated logic that should be a helper; (3) god-class/giant-method 'ball of mud' structure in MainActivity and a sensible split; (4) magic numbers that should be named constants; (5) dead code; (6) better idioms. Give concrete findings as a prioritized list, each with file:line, severity (high/med/low), and a suggested fix. Group under Efficiency, Structure, Cleanup. Be concise.
codex
I’ll inspect the Java sources read-only, focusing on the requested hotspots and reporting only actionable findings with line references.
exec
/bin/bash -lc "rg --files shieldhome/src/com/chris/shieldhome -g '*.java'" in /tmp/codexreview
 succeeded in 0ms:
shieldhome/src/com/chris/shieldhome/FocusController.java
shieldhome/src/com/chris/shieldhome/ImageLoader.java
shieldhome/src/com/chris/shieldhome/NetData.java
shieldhome/src/com/chris/shieldhome/TvData.java
shieldhome/src/com/chris/shieldhome/MainActivity.java
shieldhome/src/com/chris/shieldhome/DimService.java

exec
/bin/bash -lc "wc -l shieldhome/src/com/chris/shieldhome/*.java && rg -n \"onDraw|invalidate|requestLayout|new |findViewById|get[A-Z]|TODO|FIXME|static final|while \\(|for \\(\" shieldhome/src/com/chris/shieldhome/*.java" in /tmp/codexreview
 succeeded in 0ms:
    34 shieldhome/src/com/chris/shieldhome/DimService.java
   256 shieldhome/src/com/chris/shieldhome/FocusController.java
   105 shieldhome/src/com/chris/shieldhome/ImageLoader.java
  1804 shieldhome/src/com/chris/shieldhome/MainActivity.java
   143 shieldhome/src/com/chris/shieldhome/NetData.java
   259 shieldhome/src/com/chris/shieldhome/TvData.java
  2601 total
shieldhome/src/com/chris/shieldhome/NetData.java:9:    final Map<String, String> httpCache = new java.util.concurrent.ConcurrentHashMap<>();
shieldhome/src/com/chris/shieldhome/NetData.java:10:    static final String[][] WEB_APPS = {{"Netflix","com.netflix.ninja"},{"Hulu","com.hulu.livingroomplus"},{"Disney+","com.disney.disneyplus"},{"Prime Video","com.amazon.amazonvideo.livingroom"},{"Amazon","com.amazon.amazonvideo.livingroom"},{"HBO Max","com.wbd.stream"},{"Max","com.wbd.stream"},{"Crunchyroll","com.crunchyroll.crunchyroid"},{"STARZ","com.bydeluxe.d3.android.program.starz"},{"Starz","com.bydeluxe.d3.android.program.starz"},{"Paramount+","com.cbs.ott"},{"Peacock","com.peacocktv.peacockandroid"},{"Apple TV+","com.apple.atve.androidtv.appletv"},{"YouTube","com.google.android.youtube.tv"}};
shieldhome/src/com/chris/shieldhome/NetData.java:16:        for (String[] w : WEB_APPS)
shieldhome/src/com/chris/shieldhome/NetData.java:26:        String cached = a.prefs().getString("wn_json", null);
shieldhome/src/com/chris/shieldhome/NetData.java:27:        long at = a.prefs().getLong("wn_at", 0);
shieldhome/src/com/chris/shieldhome/NetData.java:32:            try { js = fetchWhatsNew(); } catch (Exception e) { android.util.Log.w("ShieldHome", "whatsnew " + e); }
shieldhome/src/com/chris/shieldhome/NetData.java:42:        java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
shieldhome/src/com/chris/shieldhome/NetData.java:46:        try (java.io.InputStream in = c.getInputStream()) {
shieldhome/src/com/chris/shieldhome/NetData.java:47:            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
shieldhome/src/com/chris/shieldhome/NetData.java:48:            byte[] b = new byte[8192];
shieldhome/src/com/chris/shieldhome/NetData.java:50:            while ((n = in.read(b)) > 0) bo.write(b, 0, n);
shieldhome/src/com/chris/shieldhome/NetData.java:58:        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
shieldhome/src/com/chris/shieldhome/NetData.java:59:        Map<String, List<org.json.JSONObject>> byPkg = new LinkedHashMap<>();
shieldhome/src/com/chris/shieldhome/NetData.java:60:        Set<Integer> seen = new HashSet<>();
shieldhome/src/com/chris/shieldhome/NetData.java:62:        for (int dd : new int[]{0, 1, -1, 2, 3, -2}) {
shieldhome/src/com/chris/shieldhome/NetData.java:63:            String day = df.format(new Date(now + dd * 86400000L));
shieldhome/src/com/chris/shieldhome/NetData.java:64:            org.json.JSONArray arr1 = new org.json.JSONArray(httpGet("https://api.tvmaze.com/schedule/web?date=" + day + "&country=US"));
shieldhome/src/com/chris/shieldhome/NetData.java:65:            org.json.JSONArray arr2 = new org.json.JSONArray(httpGet("https://api.tvmaze.com/schedule/web?date=" + day));
shieldhome/src/com/chris/shieldhome/NetData.java:66:            for (org.json.JSONArray arr : new org.json.JSONArray[]{arr1, arr2})
shieldhome/src/com/chris/shieldhome/NetData.java:67:                for (int i = 0; i < arr.length(); i++) {
shieldhome/src/com/chris/shieldhome/NetData.java:68:                    org.json.JSONObject ep = arr.getJSONObject(i);
shieldhome/src/com/chris/shieldhome/NetData.java:78:                    org.json.JSONObject o = new org.json.JSONObject();
shieldhome/src/com/chris/shieldhome/NetData.java:81:                            .put("weight", sh.optJSONObject("rating") != null && !sh.getJSONObject("rating").isNull("average") ? sh.getJSONObject("rating").optDouble("average", 0) : 0)
shieldhome/src/com/chris/shieldhome/NetData.java:82:                            .put("img", sh.optJSONObject("image") != null ? sh.getJSONObject("image").optString("original") : "");
shieldhome/src/com/chris/shieldhome/NetData.java:83:                    if (!byPkg.containsKey(pkg)) byPkg.put(pkg, new ArrayList<>());
shieldhome/src/com/chris/shieldhome/NetData.java:87:            for (List<org.json.JSONObject> l : byPkg.values()) total += l.size();
shieldhome/src/com/chris/shieldhome/NetData.java:90:        for (List<org.json.JSONObject> l : byPkg.values())
shieldhome/src/com/chris/shieldhome/NetData.java:92:        List<org.json.JSONObject> pick = new ArrayList<>();
shieldhome/src/com/chris/shieldhome/NetData.java:93:        for (int r = 0; r < 4 && pick.size() < 10; r++)
shieldhome/src/com/chris/shieldhome/NetData.java:94:            for (List<org.json.JSONObject> l : byPkg.values())
shieldhome/src/com/chris/shieldhome/NetData.java:96:        org.json.JSONArray out = new org.json.JSONArray();
shieldhome/src/com/chris/shieldhome/NetData.java:97:        for (org.json.JSONObject o : pick) {
shieldhome/src/com/chris/shieldhome/NetData.java:99:                org.json.JSONArray im = new org.json.JSONArray(httpGet("https://api.tvmaze.com/shows/" + o.getInt("id") + "/images"));
shieldhome/src/com/chris/shieldhome/NetData.java:102:                for (int i = 0; i < im.length(); i++) {
shieldhome/src/com/chris/shieldhome/NetData.java:103:                    org.json.JSONObject x = im.getJSONObject(i);
shieldhome/src/com/chris/shieldhome/NetData.java:115:        android.util.Log.i("ShieldHome", "whatsnew items=" + out.length() + " apps=" + byPkg.keySet());
shieldhome/src/com/chris/shieldhome/NetData.java:126:                    org.json.JSONObject j = new org.json.JSONObject(httpGet("https://ipapi.co/json/"));
shieldhome/src/com/chris/shieldhome/NetData.java:127:                    lat = j.getDouble("latitude");
shieldhome/src/com/chris/shieldhome/NetData.java:128:                    lon = j.getDouble("longitude");
shieldhome/src/com/chris/shieldhome/NetData.java:131:                    org.json.JSONObject j = new org.json.JSONObject(httpGet("https://ipwho.is/"));
shieldhome/src/com/chris/shieldhome/NetData.java:132:                    lat = j.getDouble("latitude");
shieldhome/src/com/chris/shieldhome/NetData.java:133:                    lon = j.getDouble("longitude");
shieldhome/src/com/chris/shieldhome/NetData.java:136:                org.json.JSONObject cw = new org.json.JSONObject(httpGet("https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon + "&current_weather=true&temperature_unit=fahrenheit")).getJSONObject("current_weather");
shieldhome/src/com/chris/shieldhome/NetData.java:137:                a.prefs().edit().putInt("wx_t", (int) Math.round(cw.getDouble("temperature"))).putInt("wx_code", cw.getInt("weathercode")).putInt("wx_day", cw.optInt("is_day", 1)).putString("wx_city", city).putLong("wx_at", System.currentTimeMillis()).apply();
shieldhome/src/com/chris/shieldhome/NetData.java:138:                android.util.Log.i("ShieldHome", "weather ok code=" + cw.getInt("weathercode"));
shieldhome/src/com/chris/shieldhome/TvData.java:15:    static final Set<String> VIDEO_APPS = new HashSet<>(Arrays.asList("com.netflix.ninja","com.disney.disneyplus","com.hulu.livingroomplus","com.wbd.stream","com.amazon.amazonvideo.livingroom","com.amazon.amazonvideo.livingroom.nvidia","com.plexapp.android","com.bydeluxe.d3.android.program.starz","com.crunchyroll.crunchyroid","com.google.android.youtube.tv","org.xbmc.kodi","com.google.android.videos","com.apple.atve.androidtv.appletv","com.peacocktv.peacockandroid","com.cbs.ott","com.paramount.android.pplus"));
shieldhome/src/com/chris/shieldhome/TvData.java:20:        Map<String, Long> u = new HashMap<>();
shieldhome/src/com/chris/shieldhome/TvData.java:22:            UsageStatsManager um = (UsageStatsManager) a.getSystemService(MainActivity.USAGE_STATS_SERVICE);
shieldhome/src/com/chris/shieldhome/TvData.java:25:            if (l != null) for (UsageStats st : l) {
shieldhome/src/com/chris/shieldhome/TvData.java:26:                Long v = u.get(st.getPackageName());
shieldhome/src/com/chris/shieldhome/TvData.java:27:                u.put(st.getPackageName(), (v == null ? 0 : v) + st.getTotalTimeInForeground());
shieldhome/src/com/chris/shieldhome/TvData.java:34:        List<ResolveInfo> l = a.pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0);
shieldhome/src/com/chris/shieldhome/TvData.java:35:        List<String[]> out = new ArrayList<>();
shieldhome/src/com/chris/shieldhome/TvData.java:37:        for (ResolveInfo ri : l) {
shieldhome/src/com/chris/shieldhome/TvData.java:39:            if (pk.equals(a.getPackageName())) continue;
shieldhome/src/com/chris/shieldhome/TvData.java:55:        List<Object[]> all = new ArrayList<>();
shieldhome/src/com/chris/shieldhome/TvData.java:56:        try (Cursor c = a.getContentResolver().query(TvContract.PreviewPrograms.CONTENT_URI, null, null, null, null)) {
shieldhome/src/com/chris/shieldhome/TvData.java:57:            while (c != null && c.moveToNext()) {
shieldhome/src/com/chris/shieldhome/TvData.java:75:                all.add(new Object[]{pkg, t, d, art, in, avail});
shieldhome/src/com/chris/shieldhome/TvData.java:82:        List<Object[]> all = new ArrayList<>();
shieldhome/src/com/chris/shieldhome/TvData.java:83:        for (Object[] row : previewRows) {
shieldhome/src/com/chris/shieldhome/TvData.java:90:        Map<String, List<Object[]>> by = new LinkedHashMap<>();
shieldhome/src/com/chris/shieldhome/TvData.java:91:        for (Object[] o : all) {
shieldhome/src/com/chris/shieldhome/TvData.java:93:            if (!by.containsKey(k)) by.put(k, new ArrayList<>());
shieldhome/src/com/chris/shieldhome/TvData.java:96:        List<Object[]> out = new ArrayList<>();
shieldhome/src/com/chris/shieldhome/TvData.java:97:        for (int r = 0; r < 3; r++)
shieldhome/src/com/chris/shieldhome/TvData.java:98:            for (List<Object[]> l : by.values())
shieldhome/src/com/chris/shieldhome/TvData.java:101:                    out.add(new Object[]{row[0], row[1], row[2], row[3], row[4], "FEATURED", row.length > 5 ? row[5] : 0L});
shieldhome/src/com/chris/shieldhome/TvData.java:109:        List<Object[]> rows = new ArrayList<>();
shieldhome/src/com/chris/shieldhome/TvData.java:110:        try (Cursor c = a.getContentResolver().query(TvContract.WatchNextPrograms.CONTENT_URI, null, null, null, TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS + " DESC")) {
shieldhome/src/com/chris/shieldhome/TvData.java:112:            while (c != null && c.moveToNext()) {
shieldhome/src/com/chris/shieldhome/TvData.java:119:                rows.add(new Object[]{o, l(c, "last_engagement_time_utc_millis"), l(c, "last_playback_position_millis"), l(c, "duration_millis"), (int) l(c, "watch_next_type"), clean(s(c, "title")), idx++, uri});
shieldhome/src/com/chris/shieldhome/TvData.java:131:        Set<String> feat = new HashSet<>();
shieldhome/src/com/chris/shieldhome/TvData.java:132:        if (a.featuredCache != null) for (Object[] f : a.featuredCache) {
shieldhome/src/com/chris/shieldhome/TvData.java:136:        Map<String, Intent> next = new HashMap<>();
shieldhome/src/com/chris/shieldhome/TvData.java:137:        for (Object[] r : rows) {
shieldhome/src/com/chris/shieldhome/TvData.java:141:        List<Object[]> pick = new ArrayList<>();
shieldhome/src/com/chris/shieldhome/TvData.java:142:        Set<String> seen = new HashSet<>();
shieldhome/src/com/chris/shieldhome/TvData.java:144:        for (Object[] r : rows) {
shieldhome/src/com/chris/shieldhome/TvData.java:160:            for (int i = pick.size() - 1; i >= 0 && ytN >= 4; i--)
shieldhome/src/com/chris/shieldhome/TvData.java:172:        try (Cursor c = a.getContentResolver().query(TvContract.PreviewPrograms.CONTENT_URI, null, null, null, null)) {
shieldhome/src/com/chris/shieldhome/TvData.java:173:            while (c != null && c.moveToNext()) {
shieldhome/src/com/chris/shieldhome/TvData.java:195:        try { return a.pm.getApplicationLabel(a.pm.getApplicationInfo(pkg, 0)).toString(); }
shieldhome/src/com/chris/shieldhome/TvData.java:201:        for (String k : new String[]{"news", "cnn", "msnbc", "haystack"})
shieldhome/src/com/chris/shieldhome/TvData.java:206:        Map<String, Long> plutoIds = new HashMap<>();
shieldhome/src/com/chris/shieldhome/TvData.java:207:        try (Cursor c = a.getContentResolver().query(TvContract.Channels.CONTENT_URI, null, null, null, null)) {
shieldhome/src/com/chris/shieldhome/TvData.java:208:            while (c != null && c.moveToNext()) {
shieldhome/src/com/chris/shieldhome/TvData.java:241:        return new Object[]{pkg, cap, thumb, in, (dur > 0 && pos > 0) ? (float) pos / dur : -1f};
shieldhome/src/com/chris/shieldhome/TvData.java:253:        for (String f : new String[]{"yyyy-MM-dd", "yyyy-MM", "yyyy"})
shieldhome/src/com/chris/shieldhome/TvData.java:254:            try { Date d = new SimpleDateFormat(f, Locale.US).parse(s); if (d != null) return d.getTime(); } catch (Exception e) {}
shieldhome/src/com/chris/shieldhome/TvData.java:257:    static String s(Cursor c, String col) { int i = c.getColumnIndex(col); return i < 0 || c.isNull(i) ? null : c.getString(i); }
shieldhome/src/com/chris/shieldhome/TvData.java:258:    static long l(Cursor c, String col) { int i = c.getColumnIndex(col); return i < 0 || c.isNull(i) ? 0 : c.getLong(i); }
shieldhome/src/com/chris/shieldhome/FocusController.java:12:    final int[] lastIdx = new int[16];
shieldhome/src/com/chris/shieldhome/FocusController.java:22:            savedSec = a.prefs().getInt("f_sec", -1);
shieldhome/src/com/chris/shieldhome/FocusController.java:23:            savedIdx = a.prefs().getInt("f_idx", 0);
shieldhome/src/com/chris/shieldhome/FocusController.java:24:            int ft = a.prefs().getInt("f_tab", 0);
shieldhome/src/com/chris/shieldhome/FocusController.java:39:            View f = a.getCurrentFocus();
shieldhome/src/com/chris/shieldhome/FocusController.java:41:            for (View x = f; x != null; x = x.getParent() instanceof View ? (View) x.getParent() : null)
shieldhome/src/com/chris/shieldhome/FocusController.java:50:            if (sc == 3 && a.cardScroll != null) lastCardX = a.cardScroll.getScrollX();
shieldhome/src/com/chris/shieldhome/FocusController.java:84:        int k = e.getKeyCode();
shieldhome/src/com/chris/shieldhome/FocusController.java:90:        View f = a.getCurrentFocus();
shieldhome/src/com/chris/shieldhome/FocusController.java:93:        if (e.getAction() != KeyEvent.ACTION_DOWN) return false; // ponytail: let ACTION_UP through
shieldhome/src/com/chris/shieldhome/FocusController.java:109:            if (t.getParent() == a.homeGrid){
shieldhome/src/com/chris/shieldhome/FocusController.java:132:        if (e.getRepeatCount() == 0) { e.startTracking(); backLong = false; }
shieldhome/src/com/chris/shieldhome/FocusController.java:140:        try { a.startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception x) {}
shieldhome/src/com/chris/shieldhome/FocusController.java:156:        List<View> o = new ArrayList<>();
shieldhome/src/com/chris/shieldhome/FocusController.java:161:            else if (a.searchV != null && a.searchV.getVisibility() == View.VISIBLE) o.add(a.searchV);
shieldhome/src/com/chris/shieldhome/FocusController.java:163:        else if (sec == 2) { if (a.tabPlay.getVisibility()==View.VISIBLE) o.add(a.tabPlay); o.add(a.tabLive); }
shieldhome/src/com/chris/shieldhome/FocusController.java:165:            for (int i = 0; i < a.cardRow.getChildCount(); i++) {
shieldhome/src/com/chris/shieldhome/FocusController.java:166:                View c = a.cardRow.getChildAt(i);
shieldhome/src/com/chris/shieldhome/FocusController.java:167:                if (c.isFocusable() && c.getVisibility() == View.VISIBLE) o.add(c);
shieldhome/src/com/chris/shieldhome/FocusController.java:171:            for (int i = r * 6; i < Math.min(r * 6 + 6, a.homeGrid.getChildCount()); i++) {
shieldhome/src/com/chris/shieldhome/FocusController.java:172:                View c = a.homeGrid.getChildAt(i);
shieldhome/src/com/chris/shieldhome/FocusController.java:173:                if (c.isFocusable() && c.getVisibility() == View.VISIBLE) o.add(c);
shieldhome/src/com/chris/shieldhome/FocusController.java:179:    int nSecs() { return 4 + (a.homeGrid.getChildCount() + 5) / 6; }
shieldhome/src/com/chris/shieldhome/FocusController.java:184:        while (x != null) {
shieldhome/src/com/chris/shieldhome/FocusController.java:186:            x = x.getParent() instanceof View ? (View) x.getParent() : null;
shieldhome/src/com/chris/shieldhome/FocusController.java:193:        for (View x = f; x != null; x = x.getParent() instanceof View ? (View) x.getParent() : null) {
shieldhome/src/com/chris/shieldhome/FocusController.java:197:            if (x.getParent() == a.cardRow) return 3;
shieldhome/src/com/chris/shieldhome/FocusController.java:198:            if (x.getParent() == a.homeGrid) return 4 + a.homeGrid.indexOfChild(x) / 6;
shieldhome/src/com/chris/shieldhome/FocusController.java:216:        while (it.isEmpty() && s2 < nSecs() - 1) it = secItems(++s2);
shieldhome/src/com/chris/shieldhome/FocusController.java:220:    View tabV() { return a.tab == 0 && a.tabPlay.getVisibility()==View.VISIBLE ? a.tabPlay : a.tabLive; }
shieldhome/src/com/chris/shieldhome/FocusController.java:221:    View topTarget() { return a.searchOpen && a.speakV!=null ? a.speakV : (a.searchV!=null && a.searchV.getVisibility()==View.VISIBLE ? a.searchV : a.gear); }
shieldhome/src/com/chris/shieldhome/FocusController.java:228:        if (sec == 3 && a.cardScroll != null) lastCardX = a.cardScroll.getScrollX();
shieldhome/src/com/chris/shieldhome/FocusController.java:230:        for (int s2 = sec + (up ? -1 : 1); s2 >= 0 && s2 < nSecs(); s2 += up ? -1 : 1) {
shieldhome/src/com/chris/shieldhome/FocusController.java:252:        if (v.getParent() == a.cardRow) return "card" + a.cardRow.indexOfChild(v);
shieldhome/src/com/chris/shieldhome/FocusController.java:253:        if (v.getParent() == a.homeGrid) return "app" + a.homeGrid.indexOfChild(v);
shieldhome/src/com/chris/shieldhome/FocusController.java:254:        return v.getClass().getSimpleName();
shieldhome/src/com/chris/shieldhome/ImageLoader.java:22:        this.ctx = ctx.getApplicationContext();
shieldhome/src/com/chris/shieldhome/ImageLoader.java:25:        int kb = (int) (Runtime.getRuntime().maxMemory() / 1024 / 8);
shieldhome/src/com/chris/shieldhome/ImageLoader.java:26:        cache = new LruCache<String, Bitmap>(Math.max(1024, kb)) {
shieldhome/src/com/chris/shieldhome/ImageLoader.java:27:            protected int sizeOf(String k, Bitmap b) { return Math.max(1, b.getByteCount() / 1024); }
shieldhome/src/com/chris/shieldhome/ImageLoader.java:41:                if (bm == null || iv.getWindowToken() == null || !uri0.equals(iv.getTag())) return;
shieldhome/src/com/chris/shieldhome/ImageLoader.java:48:        if (iv.getScaleType() != ImageView.ScaleType.FIT_END)
shieldhome/src/com/chris/shieldhome/ImageLoader.java:61:            BitmapFactory.Options bounds = new BitmapFactory.Options();
shieldhome/src/com/chris/shieldhome/ImageLoader.java:70:                while (bounds.outWidth / ss / 2 >= tw && bounds.outHeight / ss / 2 >= th) ss *= 2;
shieldhome/src/com/chris/shieldhome/ImageLoader.java:73:            BitmapFactory.Options o = new BitmapFactory.Options();
shieldhome/src/com/chris/shieldhome/ImageLoader.java:77:                int nh = Math.max(1, Math.round(b0.getHeight() * 0.80f));
shieldhome/src/com/chris/shieldhome/ImageLoader.java:78:                Bitmap cut = Bitmap.createBitmap(b0, 0, 0, b0.getWidth(), nh);
shieldhome/src/com/chris/shieldhome/ImageLoader.java:93:            HttpURLConnection c = (HttpURLConnection) new java.net.URL(uri).openConnection();
shieldhome/src/com/chris/shieldhome/ImageLoader.java:96:            return c.getInputStream();
shieldhome/src/com/chris/shieldhome/ImageLoader.java:98:        return ctx.getContentResolver().openInputStream(Uri.parse(uri));
shieldhome/src/com/chris/shieldhome/MainActivity.java:21:    SegBar tabBar; TextView tabPlay, tabLive; int tab = 0; Handler h = new Handler(Looper.getMainLooper());
shieldhome/src/com/chris/shieldhome/MainActivity.java:22:    static final int TILE = Color.argb(34, 255, 255, 255), FOCUS = Color.rgb(64, 156, 255), BG = Color.rgb(8, 8, 16);
shieldhome/src/com/chris/shieldhome/MainActivity.java:23:    static final int HERO_SCRIM_RGB=0x050507; // darker than BG 0x080810 so the wash doesn't read as wallpaper
shieldhome/src/com/chris/shieldhome/MainActivity.java:24:    static final float HERO_LEFT_FADE=0.24f;
shieldhome/src/com/chris/shieldhome/MainActivity.java:25:    static final int TILE_R = 11;
shieldhome/src/com/chris/shieldhome/MainActivity.java:26:    static final float APP_FOCUS=1.13f, APP_HOLD=1.16f, APP_W=142.5f, APP_H=78.5f, APP_HG=19.8f, APP_VG=21f, APP_GRID_Y=467.5f;
shieldhome/src/com/chris/shieldhome/MainActivity.java:27:    static final String[] PLAY_PKGS = {"com.google.android.youtube.tv","com.netflix.ninja","com.disney.disneyplus","com.hulu.livingroomplus","com.wbd.stream","com.amazon.amazonvideo.livingroom","com.plexapp.android"};
shieldhome/src/com/chris/shieldhome/MainActivity.java:28:    static final String[] LIVE_PKGS = {"tv.pluto.android","com.cnbc.client","com.haystack.android","com.plexapp.android"};
shieldhome/src/com/chris/shieldhome/MainActivity.java:30:    FrameLayout.LayoutParams lp(int w,int h,int g,int l,int t,int r,int b){ FrameLayout.LayoutParams x=new FrameLayout.LayoutParams(w,h,g); x.setMargins(l,t,r,b); return x; }
shieldhome/src/com/chris/shieldhome/MainActivity.java:34:    List<String[]> appsCache, fullOrder=new ArrayList<>();
shieldhome/src/com/chris/shieldhome/MainActivity.java:36:    Map<String,Long> plutoIdsCache=new HashMap<>();
shieldhome/src/com/chris/shieldhome/MainActivity.java:38:    final Object heroLock=new Object(); Object[] heroOnScreen;
shieldhome/src/com/chris/shieldhome/MainActivity.java:41:        images.load(iv, uri0, fit, iv.getWidth()>0?iv.getWidth():p(184.4f), iv.getHeight()>0?iv.getHeight():p(112));
shieldhome/src/com/chris/shieldhome/MainActivity.java:48:        pm = getPackageManager();
shieldhome/src/com/chris/shieldhome/MainActivity.java:49:        images = new ImageLoader(this, pool, h);
shieldhome/src/com/chris/shieldhome/MainActivity.java:50:        focus = new FocusController(this);
shieldhome/src/com/chris/shieldhome/MainActivity.java:51:        tv = new TvData(this);
shieldhome/src/com/chris/shieldhome/MainActivity.java:52:        net = new NetData(this);
shieldhome/src/com/chris/shieldhome/MainActivity.java:54:        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
shieldhome/src/com/chris/shieldhome/MainActivity.java:58:        IntentFilter pf = new IntentFilter(); pf.addAction(Intent.ACTION_PACKAGE_REMOVED); pf.addAction(Intent.ACTION_PACKAGE_ADDED); pf.addAction(Intent.ACTION_PACKAGE_CHANGED); pf.addDataScheme("package");
shieldhome/src/com/chris/shieldhome/MainActivity.java:61:    final BroadcastReceiver pkgRx = new BroadcastReceiver(){ @Override public void onReceive(Context c, Intent i){
shieldhome/src/com/chris/shieldhome/MainActivity.java:62:        String pkg = i.getData()==null?null:i.getData().getSchemeSpecificPart(); boolean repl = i.getBooleanExtra(Intent.EXTRA_REPLACING,false);
shieldhome/src/com/chris/shieldhome/MainActivity.java:63:        android.util.Log.i("ShieldHome","pkg "+i.getAction()+" "+pkg+" repl="+repl);
shieldhome/src/com/chris/shieldhome/MainActivity.java:64:        final boolean removed = Intent.ACTION_PACKAGE_REMOVED.equals(i.getAction()) && !repl && pkg!=null;
shieldhome/src/com/chris/shieldhome/MainActivity.java:76:    void DisplayMetricsHolder(){ android.util.DisplayMetrics m = new android.util.DisplayMetrics(); getWindowManager().getDefaultDisplay().getRealMetrics(m); u = m.widthPixels/1024f; }
shieldhome/src/com/chris/shieldhome/MainActivity.java:87:    boolean heroShown(){ return heroG!=null && heroG.getVisibility()==View.VISIBLE; }
shieldhome/src/com/chris/shieldhome/MainActivity.java:97:        if (editMode && dragIdx<0 && homeGrid!=null && (e.getKeyCode()==KeyEvent.KEYCODE_DPAD_CENTER||e.getKeyCode()==KeyEvent.KEYCODE_ENTER||e.getKeyCode()==KeyEvent.KEYCODE_NUMPAD_ENTER)){
shieldhome/src/com/chris/shieldhome/MainActivity.java:98:            View f=getCurrentFocus();
shieldhome/src/com/chris/shieldhome/MainActivity.java:99:            if (f!=null && f.getParent()==homeGrid){
shieldhome/src/com/chris/shieldhome/MainActivity.java:100:                if (e.getAction()==KeyEvent.ACTION_DOWN && (e.getRepeatCount()>0 || e.isLongPress())){ editLp=true; return true; }
shieldhome/src/com/chris/shieldhome/MainActivity.java:101:                if (e.getAction()==KeyEvent.ACTION_UP && editLp){ editLp=false; f.setPressed(false); f.cancelLongPress(); android.util.Log.i("ShieldHome","edit long-press ignored"); return true; }
shieldhome/src/com/chris/shieldhome/MainActivity.java:102:                if (e.getAction()==KeyEvent.ACTION_DOWN) editLp=false;
shieldhome/src/com/chris/shieldhome/MainActivity.java:111:        View f=getCurrentFocus();
shieldhome/src/com/chris/shieldhome/MainActivity.java:112:        if (f!=null && cardRow!=null && f.getParent()==cardRow){
shieldhome/src/com/chris/shieldhome/MainActivity.java:114:            if (i>0 || (cardScroll!=null && cardScroll.getScrollX()>0)){ resetCardRow(); android.util.Log.i("ShieldHome","back -> card0"); return; }
shieldhome/src/com/chris/shieldhome/MainActivity.java:122:        for (int i=0;i<homeGrid.getChildCount();i++){
shieldhome/src/com/chris/shieldhome/MainActivity.java:123:            View c=homeGrid.getChildAt(i);
shieldhome/src/com/chris/shieldhome/MainActivity.java:124:            if (c!=null && c.isFocusable() && c.getVisibility()==View.VISIBLE){ c.requestFocus(); android.util.Log.i("ShieldHome","back -> "+name(c)); return; }
shieldhome/src/com/chris/shieldhome/MainActivity.java:132:            new Intent(Intent.ACTION_ASSIST).setPackage("com.google.android.katniss"),
shieldhome/src/com/chris/shieldhome/MainActivity.java:133:            new Intent("android.speech.action.WEB_SEARCH").setPackage("com.google.android.katniss"),
shieldhome/src/com/chris/shieldhome/MainActivity.java:134:            new Intent(Intent.ACTION_SEARCH_LONG_PRESS).setPackage("com.google.android.katniss")
shieldhome/src/com/chris/shieldhome/MainActivity.java:136:        for (Intent i: tries){ try { if (i.resolveActivity(pm)!=null){ startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return; } } catch (Exception e) {} }
shieldhome/src/com/chris/shieldhome/MainActivity.java:147:        android.view.animation.Interpolator ip=new android.view.animation.OvershootInterpolator(1.2f);
shieldhome/src/com/chris/shieldhome/MainActivity.java:148:        for (PillView pv: new PillView[]{speakV,typeV}){ pv.animate().cancel(); pv.setScaleX(0.86f); pv.setScaleY(0.86f); pv.setAlpha(0.35f); }
shieldhome/src/com/chris/shieldhome/MainActivity.java:160:        android.view.inputmethod.InputMethodManager imm=(android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
shieldhome/src/com/chris/shieldhome/MainActivity.java:169:        android.view.inputmethod.InputMethodManager imm=(android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
shieldhome/src/com/chris/shieldhome/MainActivity.java:171:        imm.hideSoftInputFromWindow(searchField.getWindowToken(), 0);
shieldhome/src/com/chris/shieldhome/MainActivity.java:175:            new Intent("android.search.action.GLOBAL_SEARCH").setPackage("com.google.android.katniss").putExtra(android.app.SearchManager.QUERY, q),
shieldhome/src/com/chris/shieldhome/MainActivity.java:176:            new Intent("android.speech.action.WEB_SEARCH").setPackage("com.google.android.katniss").putExtra(android.app.SearchManager.QUERY, q),
shieldhome/src/com/chris/shieldhome/MainActivity.java:177:            new Intent(Intent.ACTION_VIEW, Uri.parse("https://assistant.google.com/search?q="+Uri.encode(q))).setPackage("com.google.android.katniss")
shieldhome/src/com/chris/shieldhome/MainActivity.java:179:        for (Intent i: tries){ try { if (i.resolveActivity(pm)!=null){ startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); closeSearchField(); return; } } catch (Exception e) {} }
shieldhome/src/com/chris/shieldhome/MainActivity.java:199:        if (searchSplit==null || searchSplit.getVisibility()!=View.VISIBLE){
shieldhome/src/com/chris/shieldhome/MainActivity.java:204:        android.view.animation.Interpolator ip=new android.view.animation.DecelerateInterpolator();
shieldhome/src/com/chris/shieldhome/MainActivity.java:218:    void tick(){ setPill(timePill, new SimpleDateFormat("h:mm a", Locale.US).format(new Date())); h.postDelayed(this::tick, 15000); }
shieldhome/src/com/chris/shieldhome/MainActivity.java:220:        boolean cta=v==heroBtn; Paint pt=new Paint(); pt.setTextSize(p(cta?14:12)); pt.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
shieldhome/src/com/chris/shieldhome/MainActivity.java:222:        if (cta && ("Play Now".equals(s) || "More Info".equals(s))) extra += (-pt.getFontMetrics().ascent)*0.78f + p(10);
shieldhome/src/com/chris/shieldhome/MainActivity.java:225:    void setPill(PillView v, String s){ if (v==null) return; v.label=s; ViewGroup.LayoutParams lp=v.getLayoutParams(); if (lp!=null){ lp.width=pillTextW(v,s); if (v==heroBtn) lp.height=p(34); v.setLayoutParams(lp); } v.invalidate(); if (v==heroBtn) placeDots(); }
shieldhome/src/com/chris/shieldhome/MainActivity.java:228:        int dw=heroDots.getWidth(), sw=heroG!=null?heroG.getWidth():(root!=null?root.getWidth():0);
shieldhome/src/com/chris/shieldhome/MainActivity.java:233:    void place(View v, float x, float y, float w, float hh){ FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w<0?-2:p(w), hh<0?-2:p(hh)); lp.leftMargin=p(x); lp.topMargin=p(y); root.addView(v, lp); }
shieldhome/src/com/chris/shieldhome/MainActivity.java:234:    TextView text(String s, float size, int color, String font){ TextView t=new TextView(this); t.setText(s); ts(t,size); t.setTextColor(color); t.setTypeface(Typeface.create(font, Typeface.NORMAL)); t.setIncludeFontPadding(false); return t; }
shieldhome/src/com/chris/shieldhome/MainActivity.java:237:        root = new FrameLayout(this);
shieldhome/src/com/chris/shieldhome/MainActivity.java:241:        wxPill = new PillView(this, "", 4, false); wxPill.setFocusable(false); wxPill.setClickable(false);
shieldhome/src/com/chris/shieldhome/MainActivity.java:242:        timePill = new PillView(this, "", 4, false); timePill.setFocusable(false); timePill.setClickable(false);
shieldhome/src/com/chris/shieldhome/MainActivity.java:243:        LinearLayout tr = new LinearLayout(this); tr.setGravity(Gravity.CENTER_VERTICAL);
shieldhome/src/com/chris/shieldhome/MainActivity.java:244:        tr.addView(wxPill, new LinearLayout.LayoutParams(p(28), p(28)));
shieldhome/src/com/chris/shieldhome/MainActivity.java:245:        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(p(8), p(28));
shieldhome/src/com/chris/shieldhome/MainActivity.java:246:        tr.addView(new View(this), gap);
shieldhome/src/com/chris/shieldhome/MainActivity.java:247:        tr.addView(timePill, new LinearLayout.LayoutParams(p(88), p(28)));
shieldhome/src/com/chris/shieldhome/MainActivity.java:252:        gear = new PillView(this, "", 5, false); gear.setId(View.generateViewId());
shieldhome/src/com/chris/shieldhome/MainActivity.java:256:        PillView search = searchV = new PillView(this, "Search", 3, false);
shieldhome/src/com/chris/shieldhome/MainActivity.java:259:        searchSplit = new LinearLayout(this); searchSplit.setOrientation(LinearLayout.HORIZONTAL); searchSplit.setGravity(Gravity.CENTER_VERTICAL);
shieldhome/src/com/chris/shieldhome/MainActivity.java:261:        speakV = new PillView(this, "", 6, false); typeV = new PillView(this, "", 7, false);
shieldhome/src/com/chris/shieldhome/MainActivity.java:263:        speakV.setNextFocusRightId(typeV.getId()); typeV.setNextFocusLeftId(speakV.getId());
shieldhome/src/com/chris/shieldhome/MainActivity.java:269:        searchSplit.addView(speakV, new LinearLayout.LayoutParams(p(49), p(28)));
shieldhome/src/com/chris/shieldhome/MainActivity.java:270:        searchSplit.addView(new View(this), new LinearLayout.LayoutParams(p(6), p(28)));
shieldhome/src/com/chris/shieldhome/MainActivity.java:271:        searchSplit.addView(typeV, new LinearLayout.LayoutParams(p(49), p(28)));
shieldhome/src/com/chris/shieldhome/MainActivity.java:273:        searchField = new EditText(this);
shieldhome/src/com/chris/shieldhome/MainActivity.java:288:        GradientDrawable fieldBg=new GradientDrawable();
shieldhome/src/com/chris/shieldhome/MainActivity.java:296:            String q=v.getText().toString().trim();
shieldhome/src/com/chris/shieldhome/MainActivity.java:302:        dimBar = new PillView(this, "", 5, false);
shieldhome/src/com/chris/shieldhome/MainActivity.java:307:        FrameLayout hero = new FrameLayout(this); hero.setClipChildren(false);
shieldhome/src/com/chris/shieldhome/MainActivity.java:308:        FrameLayout artHost = new FrameLayout(this); artHost.setClipChildren(true);
shieldhome/src/com/chris/shieldhome/MainActivity.java:309:        ImageView art = new ImageView(this); art.setImageResource(R.drawable.hero); art.setScaleType(ImageView.ScaleType.FIT_XY);
shieldhome/src/com/chris/shieldhome/MainActivity.java:310:        heroArt2 = new ImageView(this); heroArt2.setScaleType(ImageView.ScaleType.FIT_XY); heroArt2.setAlpha(0f);
shieldhome/src/com/chris/shieldhome/MainActivity.java:311:        artHost.addView(art, new FrameLayout.LayoutParams(-1,-1));
shieldhome/src/com/chris/shieldhome/MainActivity.java:312:        artHost.addView(heroArt2, new FrameLayout.LayoutParams(-1,-1));
shieldhome/src/com/chris/shieldhome/MainActivity.java:314:        LinearLayout meta = new LinearLayout(this); meta.setOrientation(LinearLayout.VERTICAL);
shieldhome/src/com/chris/shieldhome/MainActivity.java:316:        heroBadge = new ImageView(this); heroBadge.setScaleType(ImageView.ScaleType.FIT_START); heroBadge.setAlpha(0.7f);
shieldhome/src/com/chris/shieldhome/MainActivity.java:327:        heroFocus = new View(this); heroFocus.setBackgroundColor(Color.WHITE); heroFocus.setAlpha(1f);
shieldhome/src/com/chris/shieldhome/MainActivity.java:328:        meta.addView(heroBadge, new LinearLayout.LayoutParams(p(80), p(22)));
shieldhome/src/com/chris/shieldhome/MainActivity.java:329:        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(-2,-2); ml.topMargin=p(6); meta.addView(heroLabel, ml);
shieldhome/src/com/chris/shieldhome/MainActivity.java:330:        LinearLayout.LayoutParams mt = new LinearLayout.LayoutParams(-1,-2); mt.topMargin=p(10); meta.addView(heroTitle, mt);
shieldhome/src/com/chris/shieldhome/MainActivity.java:331:        LinearLayout.LayoutParams mf = new LinearLayout.LayoutParams(p(40), Math.max(2,p(1.4f))); mf.topMargin=p(6); meta.addView(heroFocus, mf);
shieldhome/src/com/chris/shieldhome/MainActivity.java:332:        LinearLayout.LayoutParams ms = new LinearLayout.LayoutParams(-1,-2); ms.topMargin=p(8); meta.addView(heroSub, ms);
shieldhome/src/com/chris/shieldhome/MainActivity.java:335:        heroBtn = new PillView(this, "Play Now", 4, true);
shieldhome/src/com/chris/shieldhome/MainActivity.java:339:        heroDots = new HeroDots();
shieldhome/src/com/chris/shieldhome/MainActivity.java:344:        art.post(()->{ int w=art.getWidth(), h=art.getHeight(); if (w>0){ Drawable d=art.getDrawable(); if (d!=null){ int iw=Math.max(1,d.getIntrinsicWidth()), ih=Math.max(1,d.getIntrinsicHeight()); Bitmap src=Bitmap.createBitmap(iw,ih,Bitmap.Config.ARGB_8888); Canvas cv=new Canvas(src); d.setBounds(0,0,iw,ih); d.draw(cv); art.setImageBitmap(maskHero("hero", src, w, h)); } } });
shieldhome/src/com/chris/shieldhome/MainActivity.java:349:        tabBar = new SegBar(); tabPlay=tabBar.play; tabLive=tabBar.live;
shieldhome/src/com/chris/shieldhome/MainActivity.java:353:        cardWrap = new FrameLayout(this);
shieldhome/src/com/chris/shieldhome/MainActivity.java:355:        cardScroll = new HorizontalScrollView(this){
shieldhome/src/com/chris/shieldhome/MainActivity.java:362:        cardRow = new LinearLayout(this); cardRow.setClipChildren(false); cardRow.setClipToPadding(false);
shieldhome/src/com/chris/shieldhome/MainActivity.java:363:        cardScroll.addView(cardRow, new ViewGroup.LayoutParams(-2,-1));
shieldhome/src/com/chris/shieldhome/MainActivity.java:364:        cardWrap.addView(cardScroll, new FrameLayout.LayoutParams(-1,-1));
shieldhome/src/com/chris/shieldhome/MainActivity.java:366:        cardScroll.getViewTreeObserver().addOnScrollChangedListener(this::updateCardAlphas);
shieldhome/src/com/chris/shieldhome/MainActivity.java:368:        homeGrid = new GridLayout(this); homeGrid.setColumnCount(6); homeGrid.setClipChildren(false); homeGrid.setClipToPadding(false);
shieldhome/src/com/chris/shieldhome/MainActivity.java:374:        h.post(()->{ if (tabPlay.getVisibility()==View.VISIBLE) tabPlay.requestFocus(); else tabLive.requestFocus(); });
shieldhome/src/com/chris/shieldhome/MainActivity.java:379:        String label; int icon; boolean on; Drawable asst; Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);
shieldhome/src/com/chris/shieldhome/MainActivity.java:381:        void setOn(boolean o){ on=o; invalidate(); }
shieldhome/src/com/chris/shieldhome/MainActivity.java:382:        @Override protected void onFocusChanged(boolean f, int d, Rect r){ super.onFocusChanged(f,d,r); if (this==heroBtn) return; animate().scaleX(f?1.07f:1f).scaleY(f?1.07f:1f).setDuration(160).setInterpolator(new android.view.animation.DecelerateInterpolator()).start(); setElevation(f?p(4):0); invalidate(); }
shieldhome/src/com/chris/shieldhome/MainActivity.java:383:        @Override protected void onDraw(Canvas c){
shieldhome/src/com/chris/shieldhome/MainActivity.java:384:            float w=getWidth(), hh=getHeight(), r=this==heroBtn?p(14):hh/2;
shieldhome/src/com/chris/shieldhome/MainActivity.java:395:                if (icon==5 && !on){ pt.setShader(new LinearGradient(0,0,0,hh,Color.argb(70,255,255,255),Color.argb(0,255,255,255),Shader.TileMode.CLAMP)); c.drawRoundRect(0,0,w,hh,r,r,pt); pt.setShader(null); }
shieldhome/src/com/chris/shieldhome/MainActivity.java:406:                Paint.FontMetrics fm=pt.getFontMetrics();
shieldhome/src/com/chris/shieldhome/MainActivity.java:414:                if (asst==null) asst = getContext().getDrawable(R.drawable.assistant);
shieldhome/src/com/chris/shieldhome/MainActivity.java:420:                for (int i=0;i<8;i++){ double a=Math.toRadians(i*45); float s=sr+p(1.5f), e=sr+p(3.3f); c.drawLine(gx+(float)Math.cos(a)*s, cy+(float)Math.sin(a)*s, gx+(float)Math.cos(a)*e, cy+(float)Math.sin(a)*e, pt); }
shieldhome/src/com/chris/shieldhome/MainActivity.java:423:                if (asst==null) asst = getContext().getDrawable(R.drawable.assistant);
shieldhome/src/com/chris/shieldhome/MainActivity.java:431:                for (int row=0; row<2; row++) for (int col=0; col<5; col++) c.drawCircle(kl+col*gapx, kt+row*gapy, kd, pt);
shieldhome/src/com/chris/shieldhome/MainActivity.java:436:            else if (icon!=4) { pt.setStyle(Paint.Style.FILL); Path tr=new Path(); tr.moveTo(cx-p(3),cy-p(5)); tr.lineTo(cx+p(5),cy); tr.lineTo(cx-p(3),cy+p(5)); tr.close(); c.drawPath(tr,pt); }
shieldhome/src/com/chris/shieldhome/MainActivity.java:438:            Paint.FontMetrics fm = pt.getFontMetrics();
shieldhome/src/com/chris/shieldhome/MainActivity.java:442:                    pt.setPathEffect(new CornerPathEffect(Math.max(1, cap*0.12f)));
shieldhome/src/com/chris/shieldhome/MainActivity.java:443:                    Path tr=new Path(); tr.moveTo(x0, cy-th/2); tr.lineTo(x0+twt, cy); tr.lineTo(x0, cy+th/2); tr.close();
shieldhome/src/com/chris/shieldhome/MainActivity.java:451:                    Paint.FontMetrics ifm=pt.getFontMetrics();
shieldhome/src/com/chris/shieldhome/MainActivity.java:471:            int sw=Math.max(Math.round(play.getPaint().measureText("Play Next")), Math.round(live.getPaint().measureText("Live TV")))+p(36);
shieldhome/src/com/chris/shieldhome/MainActivity.java:472:            play.setLayoutParams(new LinearLayout.LayoutParams(sw,-1));
shieldhome/src/com/chris/shieldhome/MainActivity.java:473:            live.setLayoutParams(new LinearLayout.LayoutParams(sw,-1));
shieldhome/src/com/chris/shieldhome/MainActivity.java:478:            play.setNextFocusRightId(live.getId()); live.setNextFocusLeftId(play.getId());
shieldhome/src/com/chris/shieldhome/MainActivity.java:508:            pa.setInterpolator(new android.view.animation.OvershootInterpolator(1.1f));
shieldhome/src/com/chris/shieldhome/MainActivity.java:509:            pa.addUpdateListener(a->{ hs=(Float)a.getAnimatedValue(); invalidate(); });
shieldhome/src/com/chris/shieldhome/MainActivity.java:512:        void showPlay(boolean on){ play.setVisibility(on?VISIBLE:GONE); play.setFocusable(on); requestLayout(); }
shieldhome/src/com/chris/shieldhome/MainActivity.java:514:            TextView s=(tab==0 && play.getVisibility()==VISIBLE)?play:live;
shieldhome/src/com/chris/shieldhome/MainActivity.java:515:            float nx=s.getLeft(), nw=s.getWidth();
shieldhome/src/com/chris/shieldhome/MainActivity.java:517:            if (!anim){ hx=nx; hw=nw; invalidate(); return; }
shieldhome/src/com/chris/shieldhome/MainActivity.java:521:            ha.setInterpolator(new android.view.animation.PathInterpolator(0.16f,1f,0.3f,1f));
shieldhome/src/com/chris/shieldhome/MainActivity.java:522:            ha.addUpdateListener(a->{ float u=(Float)a.getAnimatedValue(); hx=x0+(nx-x0)*u; hw=w0+(nw-w0)*u; invalidate(); });
shieldhome/src/com/chris/shieldhome/MainActivity.java:531:            float w=getWidth(), hh=getHeight(), rr=hh/2;
shieldhome/src/com/chris/shieldhome/MainActivity.java:532:            Paint pt=new Paint(Paint.ANTI_ALIAS_FLAG);
shieldhome/src/com/chris/shieldhome/MainActivity.java:539:                if (sb!=null){ Paint sp=new Paint(Paint.ANTI_ALIAS_FLAG); sp.setAlpha(Math.max(0,Math.min(130,(int)(130*(hs-1f)/0.04f)))); int pad=p(48); c.drawBitmap(sb, cx-sw/2-pad, cy-sh/2-pad+p(19), sp); }
shieldhome/src/com/chris/shieldhome/MainActivity.java:551:        void snap(int i){ cur=i; ax=slot(i); invalidate(); }
shieldhome/src/com/chris/shieldhome/MainActivity.java:554:            int h=MeasureSpec.getMode(hms)==MeasureSpec.EXACTLY?MeasureSpec.getSize(hms):p(28);
shieldhome/src/com/chris/shieldhome/MainActivity.java:559:            if (nn2!=n) requestLayout();
shieldhome/src/com/chris/shieldhome/MainActivity.java:562:            if (!anim || getAlpha()<0.05f || n<=1 || wrap){ snap(i); return; }
shieldhome/src/com/chris/shieldhome/MainActivity.java:566:            an.setInterpolator(new android.view.animation.PathInterpolator(0.16f,1f,0.3f,1f));
shieldhome/src/com/chris/shieldhome/MainActivity.java:567:            an.addUpdateListener(a->{ float u=(Float)a.getAnimatedValue(); ax=x0+(to-x0)*u; invalidate(); });
shieldhome/src/com/chris/shieldhome/MainActivity.java:570:        @Override protected void onDraw(Canvas c){
shieldhome/src/com/chris/shieldhome/MainActivity.java:572:            float d=p(5), g=p(7), row=(n-1)*(d+g)+d, hh=getHeight(), rr=hh/2, x0=(getWidth()-row)/2f, cy=hh/2f;
shieldhome/src/com/chris/shieldhome/MainActivity.java:573:            Paint pt=new Paint(Paint.ANTI_ALIAS_FLAG);
shieldhome/src/com/chris/shieldhome/MainActivity.java:575:            c.drawRoundRect(0,0,getWidth(),hh,rr,rr,pt);
shieldhome/src/com/chris/shieldhome/MainActivity.java:577:            for (int i=0;i<n;i++) c.drawCircle(x0+slot(i)+d/2, cy, d/2, pt);
shieldhome/src/com/chris/shieldhome/MainActivity.java:585:        FrameLayout inner; View badgeV; float radius; Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG); float glow=0, fade=1; Bitmap shBm; int shPad;
shieldhome/src/com/chris/shieldhome/MainActivity.java:588:            addView(in, new FrameLayout.LayoutParams(-1,-1));
shieldhome/src/com/chris/shieldhome/MainActivity.java:591:        void syncHalo(){ invalidate(); }
shieldhome/src/com/chris/shieldhome/MainActivity.java:595:            if (g>0.02f){ if (shBm==null) shBm=shadowBmp(getWidth(),getHeight(),radius); if (shBm!=null){ sp.setAlpha((int)(160*g)); c.drawBitmap(shBm,-shPad,-shPad+p(19),sp); } }
shieldhome/src/com/chris/shieldhome/MainActivity.java:597:            if (badgeV!=null && badgeV.getAlpha()>0.01f){ c.saveLayerAlpha(badgeV.getLeft(), badgeV.getTop(), badgeV.getRight(), badgeV.getBottom(), Math.round(255*badgeV.getAlpha())); c.translate(badgeV.getLeft(), badgeV.getTop()); badgeV.draw(c); c.restore(); }
shieldhome/src/com/chris/shieldhome/MainActivity.java:602:        final GlowFrame g = new GlowFrame(this, v, p(radius));
shieldhome/src/com/chris/shieldhome/MainActivity.java:605:            if (x.getParent()==null) return;
shieldhome/src/com/chris/shieldhome/MainActivity.java:609:            x.animate().scaleX(f?sc:1f).scaleY(f?sc:1f).translationX(0).translationY(0).rotation(0).setDuration(220).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
shieldhome/src/com/chris/shieldhome/MainActivity.java:612:            va.addUpdateListener(a->{ g.glow=(Float)a.getAnimatedValue(); g.syncHalo(); }); va.start();
shieldhome/src/com/chris/shieldhome/MainActivity.java:617:    final Map<String,Bitmap> glowBmps = new HashMap<>();
shieldhome/src/com/chris/shieldhome/MainActivity.java:623:        Canvas c=new Canvas(bm);
shieldhome/src/com/chris/shieldhome/MainActivity.java:624:        Paint bl=new Paint(Paint.ANTI_ALIAS_FLAG);
shieldhome/src/com/chris/shieldhome/MainActivity.java:625:        bl.setColor(Color.BLACK); bl.setMaskFilter(new BlurMaskFilter(p(32), BlurMaskFilter.Blur.NORMAL));
shieldhome/src/com/chris/shieldhome/MainActivity.java:630:        FrameLayout f = new FrameLayout(this); f.setClipToOutline(true);
shieldhome/src/com/chris/shieldhome/MainActivity.java:631:        GradientDrawable bg = new GradientDrawable(); bg.setCornerRadius(p(TILE_R)); bg.setColor(TILE); bg.setStroke(1, Color.argb(30,255,255,255)); f.setBackground(bg);
shieldhome/src/com/chris/shieldhome/MainActivity.java:632:        ImageView iv = new ImageView(this);
shieldhome/src/com/chris/shieldhome/MainActivity.java:633:        Drawable banner = null; ComponentName cn = new ComponentName(pkg, cls);
shieldhome/src/com/chris/shieldhome/MainActivity.java:634:        try { banner = pm.getActivityBanner(cn); if (banner==null) banner = pm.getApplicationBanner(pkg); } catch (Exception e) {}
shieldhome/src/com/chris/shieldhome/MainActivity.java:639:            f.addView(iv, new FrameLayout.LayoutParams(-1,-1));
shieldhome/src/com/chris/shieldhome/MainActivity.java:640:            View sheen = new View(this); sheen.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{Color.argb(28,255,255,255), Color.TRANSPARENT, Color.argb(40,0,0,0)}));
shieldhome/src/com/chris/shieldhome/MainActivity.java:641:            f.addView(sheen, new FrameLayout.LayoutParams(-1,-1));
shieldhome/src/com/chris/shieldhome/MainActivity.java:643:            Drawable icon = null; try { icon = pm.getActivityIcon(cn);} catch(Exception e){}
shieldhome/src/com/chris/shieldhome/MainActivity.java:650:        final Intent li = new Intent(Intent.ACTION_MAIN).setComponent(cn).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
shieldhome/src/com/chris/shieldhome/MainActivity.java:658:            Bitmap bm = Bitmap.createBitmap(64, 36, Bitmap.Config.ARGB_8888); Canvas c = new Canvas(bm); d.setBounds(0,0,64,36); d.draw(c);
shieldhome/src/com/chris/shieldhome/MainActivity.java:660:                for (int x=0;x<64;x++) for (int y: new int[]{0,1,34,35}){ int px=bm.getPixel(x,y); if (Color.alpha(px)<128) continue; r+=Color.red(px); g+=Color.green(px); b+=Color.blue(px); n++; }
shieldhome/src/com/chris/shieldhome/MainActivity.java:661:                for (int y=0;y<36;y++) for (int x: new int[]{0,1,62,63}){ int px=bm.getPixel(x,y); if (Color.alpha(px)<128) continue; r+=Color.red(px); g+=Color.green(px); b+=Color.blue(px); n++; }
shieldhome/src/com/chris/shieldhome/MainActivity.java:664:            Map<Integer,Integer> hist = new HashMap<>(); int best=0, bestN=0;
shieldhome/src/com/chris/shieldhome/MainActivity.java:665:            for (int x=0;x<64;x++) for (int y=0;y<36;y++){ int px=bm.getPixel(x,y); if (Color.alpha(px)<200) continue; float[] hsv=new float[3]; Color.colorToHSV(px,hsv); if (hsv[1]<0.25f||hsv[2]<0.2f) continue;
shieldhome/src/com/chris/shieldhome/MainActivity.java:667:            if (bestN==0) return TILE; float[] hsv=new float[3]; Color.colorToHSV(best,hsv); hsv[2]=Math.min(hsv[2],0.45f); hsv[1]=Math.min(hsv[1],0.75f); return Color.HSVToColor(hsv);
shieldhome/src/com/chris/shieldhome/MainActivity.java:672:    Map<String,Long> usage = new HashMap<>();
shieldhome/src/com/chris/shieldhome/MainActivity.java:676:    final android.view.animation.Interpolator dragIp=new android.view.animation.PathInterpolator(0.16f,1f,0.3f,1f);
shieldhome/src/com/chris/shieldhome/MainActivity.java:677:    final android.view.animation.Interpolator drawerIp=new android.view.animation.PathInterpolator(0.4f,0f,0.2f,1f);
shieldhome/src/com/chris/shieldhome/MainActivity.java:679:    final android.view.Choreographer.FrameCallback glideCb=new android.view.Choreographer.FrameCallback(){
shieldhome/src/com/chris/shieldhome/MainActivity.java:683:            for (int i=0;i<tiles.length;i++){
shieldhome/src/com/chris/shieldhome/MainActivity.java:685:                float x=v.getTranslationX(), y=v.getTranslationY();
shieldhome/src/com/chris/shieldhome/MainActivity.java:690:            if (more) android.view.Choreographer.getInstance().postFrameCallback(this);
shieldhome/src/com/chris/shieldhome/MainActivity.java:695:    android.content.SharedPreferences prefs(){ return getSharedPreferences("home", MODE_PRIVATE); }
shieldhome/src/com/chris/shieldhome/MainActivity.java:697:        List<String[]> all = new ArrayList<>(apps());
shieldhome/src/com/chris/shieldhome/MainActivity.java:698:        String saved = prefs().getString("order", null);
shieldhome/src/com/chris/shieldhome/MainActivity.java:711:        List<String[]> out=new ArrayList<>(); Set<String> seen=new HashSet<>();
shieldhome/src/com/chris/shieldhome/MainActivity.java:712:        for (String key: saved.split(",")){
shieldhome/src/com/chris/shieldhome/MainActivity.java:714:            for (String[] a: all) if ((a[0]+"/"+a[1]).equals(key) && seen.add(key)) out.add(a);
shieldhome/src/com/chris/shieldhome/MainActivity.java:716:        for (String[] a: all) if (seen.add(a[0]+"/"+a[1])) out.add(a);
shieldhome/src/com/chris/shieldhome/MainActivity.java:719:    void saveHome(List<String[]> l){ StringBuilder sb=new StringBuilder(); for (String[] a: l){ if (a==null) continue; if (sb.length()>0) sb.append(','); sb.append(a[0]).append('/').append(a[1]); } prefs().edit().putString("order", sb.toString()).commit(); }
shieldhome/src/com/chris/shieldhome/MainActivity.java:720:    GridLayout.LayoutParams gridLp(int i){ GridLayout.LayoutParams gl = new GridLayout.LayoutParams(); gl.width=p(APP_W); gl.height=p(APP_H); gl.rightMargin = (i%6==5)?0:p(APP_HG); gl.bottomMargin = p(APP_VG); return gl; }
shieldhome/src/com/chris/shieldhome/MainActivity.java:722:    View[] slideViews(){ return new View[]{clk, gear, searchV, searchSplit, searchField, dimBar, heroG, tabBar, cardWrap, homeGrid}; }
shieldhome/src/com/chris/shieldhome/MainActivity.java:726:        for (View v: new View[]{clk, gear, searchV, searchSplit, searchField, dimBar}){
shieldhome/src/com/chris/shieldhome/MainActivity.java:741:        int n=homeGrid.getChildCount(), rows=Math.max(1,(n+5)/6);
shieldhome/src/com/chris/shieldhome/MainActivity.java:742:        for (int row=2; row<rows; row++){ // row 1 is the peek; leave it visible when closed
shieldhome/src/com/chris/shieldhome/MainActivity.java:744:            for (int c=0;c<6;c++){
shieldhome/src/com/chris/shieldhome/MainActivity.java:746:                View t=homeGrid.getChildAt(i); t.animate().cancel();
shieldhome/src/com/chris/shieldhome/MainActivity.java:755:        int r=0; View f=getCurrentFocus();
shieldhome/src/com/chris/shieldhome/MainActivity.java:756:        if (f!=null && homeGrid!=null && f.getParent()==homeGrid) r=Math.max(0, (editMode?tileIx(f):homeGrid.indexOfChild(f))/6);
shieldhome/src/com/chris/shieldhome/MainActivity.java:761:            for (View v: new View[]{tabBar, cardWrap, homeGrid}) if (v!=null) v.bringToFront();
shieldhome/src/com/chris/shieldhome/MainActivity.java:771:        for (View v: slideViews()){
shieldhome/src/com/chris/shieldhome/MainActivity.java:777:            if (v==homeGrid) an.withEndAction(()->{ if (!editMode && getCurrentFocus()==null && focus!=null) focus.restore(); });
shieldhome/src/com/chris/shieldhome/MainActivity.java:790:        for (String[] a: all) sig += a[0]+"/"+a[1]+",";
shieldhome/src/com/chris/shieldhome/MainActivity.java:791:        if (focusIdx<0 && sig.equals(homeBindSig) && homeGrid.getChildCount()==all.size()) return;
shieldhome/src/com/chris/shieldhome/MainActivity.java:794:        for (int i=0;i<all.size();i++) homeGrid.addView(homeTile(all.get(i), i), gridLp(i));
shieldhome/src/com/chris/shieldhome/MainActivity.java:795:        ViewGroup.LayoutParams lp=homeGrid.getLayoutParams();
shieldhome/src/com/chris/shieldhome/MainActivity.java:797:        if (focusIdx>=0 && homeGrid.getChildCount()>0){
shieldhome/src/com/chris/shieldhome/MainActivity.java:798:            final int f=Math.min(focusIdx, homeGrid.getChildCount()-1);
shieldhome/src/com/chris/shieldhome/MainActivity.java:799:            homeGrid.getChildAt(f).requestFocus();
shieldhome/src/com/chris/shieldhome/MainActivity.java:800:            h.post(()->{ if (f<homeGrid.getChildCount()) homeGrid.getChildAt(f).requestFocus(); });
shieldhome/src/com/chris/shieldhome/MainActivity.java:811:                if (e.getAction()!=KeyEvent.ACTION_DOWN) return dpad||ok;
shieldhome/src/com/chris/shieldhome/MainActivity.java:812:                if (ok){ if (e.getRepeatCount()==0) drop(ix); return true; }
shieldhome/src/com/chris/shieldhome/MainActivity.java:822:                if (e.getAction()==KeyEvent.ACTION_DOWN && e.getRepeatCount()==0 && !e.isLongPress()){ pressHold(g); return false; }
shieldhome/src/com/chris/shieldhome/MainActivity.java:823:                if (e.getAction()==KeyEvent.ACTION_DOWN && (e.isLongPress() || e.getRepeatCount()>=3)){ appMenu(g, a, ix); return true; }
shieldhome/src/com/chris/shieldhome/MainActivity.java:824:                if (e.getAction()==KeyEvent.ACTION_UP){
shieldhome/src/com/chris/shieldhome/MainActivity.java:836:        if (fullOrder!=null) for (String[] a: fullOrder) sig += a[0]+"/"+a[1]+",";
shieldhome/src/com/chris/shieldhome/MainActivity.java:840:        if (tiles!=null) for (int i=0;i<tiles.length;i++) if (tiles[i]==v) return i;
shieldhome/src/com/chris/shieldhome/MainActivity.java:848:        tgtX[i]=slotX(i)-v.getLeft();
shieldhome/src/com/chris/shieldhome/MainActivity.java:849:        tgtY[i]=slotY(i)-v.getTop()+(v==dragV?-liftY:0);
shieldhome/src/com/chris/shieldhome/MainActivity.java:850:        if (v.getLayerType()!=View.LAYER_TYPE_HARDWARE) layer(v, true);
shieldhome/src/com/chris/shieldhome/MainActivity.java:853:        if (!glideOn){ glideOn=true; android.view.Choreographer.getInstance().postFrameCallback(glideCb); }
shieldhome/src/com/chris/shieldhome/MainActivity.java:864:        if (v instanceof GlowFrame){ GlowFrame g=(GlowFrame)v; g.glow=foc?1f:0f; g.syncHalo(); g.invalidate(); }
shieldhome/src/com/chris/shieldhome/MainActivity.java:868:        for (int j=0;j<homeGrid.getChildCount();j++){
shieldhome/src/com/chris/shieldhome/MainActivity.java:869:            View c=homeGrid.getChildAt(j);
shieldhome/src/com/chris/shieldhome/MainActivity.java:877:        for (int i=0;i<tiles.length;i++){
shieldhome/src/com/chris/shieldhome/MainActivity.java:882:        for (int i=0;i<tiles.length;i++) homeGrid.addView(tiles[i], gridLp(i));
shieldhome/src/com/chris/shieldhome/MainActivity.java:888:        int n=homeGrid.getChildCount();
shieldhome/src/com/chris/shieldhome/MainActivity.java:889:        tiles=new View[n]; tgtX=new float[n]; tgtY=new float[n];
shieldhome/src/com/chris/shieldhome/MainActivity.java:891:        for (int j=0;j<n;j++){ tiles[j]=homeGrid.getChildAt(j); tgtX[j]=0; tgtY[j]=0; }
shieldhome/src/com/chris/shieldhome/MainActivity.java:895:        for (int j=0;j<n;j++){
shieldhome/src/com/chris/shieldhome/MainActivity.java:900:                float from=c.getScaleX();
shieldhome/src/com/chris/shieldhome/MainActivity.java:918:        View keep=tiles!=null && i>=0 && i<tiles.length? tiles[i] : (homeGrid!=null && i>=0 && i<homeGrid.getChildCount()? homeGrid.getChildAt(i):null);
shieldhome/src/com/chris/shieldhome/MainActivity.java:931:        dimV=new View(this); dimV.setBackgroundColor(Color.argb(150,4,4,10)); dimV.setAlpha(0f);
shieldhome/src/com/chris/shieldhome/MainActivity.java:935:        root.addView(dimV, at<0?0:at, new FrameLayout.LayoutParams(-1,-1));
shieldhome/src/com/chris/shieldhome/MainActivity.java:948:        View tv=null; for (int i=0;i<g.getChildCount();i++) if (pkg.equals(g.getChildAt(i).getTag())) { tv=g.getChildAt(i); break; }
shieldhome/src/com/chris/shieldhome/MainActivity.java:952:        t.animate().alpha(0f).scaleX(0.85f).scaleY(0.85f).setDuration(250).setInterpolator(new android.view.animation.AccelerateInterpolator()).withEndAction(()->{ if (after!=null) after.run(); }).start();
shieldhome/src/com/chris/shieldhome/MainActivity.java:955:        try { int f=pm.getApplicationInfo(pkg,0).flags; return (f&(ApplicationInfo.FLAG_SYSTEM|ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))!=0; }
shieldhome/src/com/chris/shieldhome/MainActivity.java:958:    static final int[] MENU_ICONS={R.drawable.ic_menu_edit,R.drawable.ic_menu_stop,R.drawable.ic_menu_info,R.drawable.ic_menu_delete};
shieldhome/src/com/chris/shieldhome/MainActivity.java:962:        v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(ViewConfiguration.getLongPressTimeout()).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
shieldhome/src/com/chris/shieldhome/MainActivity.java:981:        android.view.animation.Interpolator ip=new android.view.animation.OvershootInterpolator(1.3f);
shieldhome/src/com/chris/shieldhome/MainActivity.java:989:            va.addUpdateListener(a->{ g.glow=Math.max(0f, Math.min(1f,(Float)a.getAnimatedValue())); g.syncHalo(); });
shieldhome/src/com/chris/shieldhome/MainActivity.java:998:        LinearLayout p=new LinearLayout(this); p.setOrientation(LinearLayout.VERTICAL);
shieldhome/src/com/chris/shieldhome/MainActivity.java:999:        GradientDrawable bg=new GradientDrawable(); bg.setColor(Color.argb(235,28,28,32)); bg.setCornerRadius(p(14)); bg.setStroke(Math.max(1,p(1)), Color.argb(51,255,255,255));
shieldhome/src/com/chris/shieldhome/MainActivity.java:1004:        java.util.ArrayList<View> rows=new java.util.ArrayList<>();
shieldhome/src/com/chris/shieldhome/MainActivity.java:1005:        for (int n=0;n<labs.length;n++){
shieldhome/src/com/chris/shieldhome/MainActivity.java:1008:            LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(p(10),0,p(10),0);
shieldhome/src/com/chris/shieldhome/MainActivity.java:1010:            final ImageView li=new ImageView(this); li.setImageResource(MENU_ICONS[n]); li.setScaleType(ImageView.ScaleType.FIT_CENTER);
shieldhome/src/com/chris/shieldhome/MainActivity.java:1013:            row.addView(li, new LinearLayout.LayoutParams(p(18),p(18)));
shieldhome/src/com/chris/shieldhome/MainActivity.java:1015:            LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-2,-2); tp.leftMargin=p(10);
shieldhome/src/com/chris/shieldhome/MainActivity.java:1018:                GradientDrawable pill=new GradientDrawable(); pill.setCornerRadius(p(8));
shieldhome/src/com/chris/shieldhome/MainActivity.java:1025:            row.setOnKeyListener((v,k,e)->{ if (k==KeyEvent.KEYCODE_BACK && e.getAction()==KeyEvent.ACTION_DOWN){ closeMenu(); return true; } return false; });
shieldhome/src/com/chris/shieldhome/MainActivity.java:1026:            p.addView(row, new LinearLayout.LayoutParams(p(200), p(36)));
shieldhome/src/com/chris/shieldhome/MainActivity.java:1029:        for (int i=0;i<rows.size();i++){
shieldhome/src/com/chris/shieldhome/MainActivity.java:1031:            r.setNextFocusLeftId(r.getId()); r.setNextFocusRightId(r.getId());
shieldhome/src/com/chris/shieldhome/MainActivity.java:1032:            r.setNextFocusUpId(i==0?r.getId():rows.get(i-1).getId());
shieldhome/src/com/chris/shieldhome/MainActivity.java:1033:            r.setNextFocusDownId(i==rows.size()-1?r.getId():rows.get(i+1).getId());
shieldhome/src/com/chris/shieldhome/MainActivity.java:1036:        int mw=p.getMeasuredWidth(), mh=p.getMeasuredHeight();
shieldhome/src/com/chris/shieldhome/MainActivity.java:1037:        int[] loc=new int[2]; anchor.getLocationInWindow(loc);
shieldhome/src/com/chris/shieldhome/MainActivity.java:1038:        int sw=root.getWidth()>0?root.getWidth():p(1024), sh=root.getHeight()>0?root.getHeight():p(576);
shieldhome/src/com/chris/shieldhome/MainActivity.java:1039:        int x=loc[0]+anchor.getWidth()+p(10), y=loc[1];
shieldhome/src/com/chris/shieldhome/MainActivity.java:1044:        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(mw, mh);
shieldhome/src/com/chris/shieldhome/MainActivity.java:1047:        p.setPivotY(Math.max(0, Math.min(mh, loc[1]+anchor.getHeight()/2f-y)));
shieldhome/src/com/chris/shieldhome/MainActivity.java:1051:        p.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(new android.view.animation.OvershootInterpolator(0.9f)).start();
shieldhome/src/com/chris/shieldhome/MainActivity.java:1056:        int k=e.getKeyCode();
shieldhome/src/com/chris/shieldhome/MainActivity.java:1059:            if (e.getAction()==KeyEvent.ACTION_UP){ dimSlideIgnore=false; return true; }
shieldhome/src/com/chris/shieldhome/MainActivity.java:1060:            if (e.getAction()!=KeyEvent.ACTION_DOWN) return true;
shieldhome/src/com/chris/shieldhome/MainActivity.java:1061:            if (dimSlideIgnore && e.getRepeatCount()>0) return true;
shieldhome/src/com/chris/shieldhome/MainActivity.java:1064:        if (e.getAction()!=KeyEvent.ACTION_DOWN) return true;
shieldhome/src/com/chris/shieldhome/MainActivity.java:1072:        int k=e.getKeyCode();
shieldhome/src/com/chris/shieldhome/MainActivity.java:1073:        if (k==KeyEvent.KEYCODE_BACK){ if (e.getAction()==KeyEvent.ACTION_DOWN) closeMenu(); return true; }
shieldhome/src/com/chris/shieldhome/MainActivity.java:1076:            if (e.getAction()==KeyEvent.ACTION_DOWN) menuMove(k==KeyEvent.KEYCODE_DPAD_DOWN);
shieldhome/src/com/chris/shieldhome/MainActivity.java:1083:        java.util.ArrayList<View> items=new java.util.ArrayList<>();
shieldhome/src/com/chris/shieldhome/MainActivity.java:1084:        for (int i=0;i<appMenu.getChildCount();i++){ View c=appMenu.getChildAt(i); if (c.isFocusable()) items.add(c); }
shieldhome/src/com/chris/shieldhome/MainActivity.java:1086:        View f=getCurrentFocus(); int ix=items.indexOf(f);
shieldhome/src/com/chris/shieldhome/MainActivity.java:1093:        for (int i=0;i<root.getChildCount();i++){
shieldhome/src/com/chris/shieldhome/MainActivity.java:1094:            View c=root.getChildAt(i);
shieldhome/src/com/chris/shieldhome/MainActivity.java:1113:        if (id==1 || id==2){ try { startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:"+a[0])).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception e){} return; }
shieldhome/src/com/chris/shieldhome/MainActivity.java:1114:        if (id==3 && !systemApp(a[0])){ try { startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:"+a[0])).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception e){} }
shieldhome/src/com/chris/shieldhome/MainActivity.java:1124:        m.animate().alpha(0f).scaleX(0.3f).scaleY(0.3f).setDuration(140).setInterpolator(new android.view.animation.AccelerateInterpolator()).withEndAction(()->{ if (m.getParent()!=null) root.removeView(m); }).start();
shieldhome/src/com/chris/shieldhome/MainActivity.java:1128:    void appInfo(String pkg){ try { startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:"+pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception e){} }
shieldhome/src/com/chris/shieldhome/MainActivity.java:1129:    Bitmap vigMask; final Map<String,Bitmap> heroMasked=new HashMap<>();
shieldhome/src/com/chris/shieldhome/MainActivity.java:1131:        if (vigMask!=null && vigMask.getWidth()==w && vigMask.getHeight()==h) return vigMask;
shieldhome/src/com/chris/shieldhome/MainActivity.java:1133:        Canvas c=new Canvas(bm);
shieldhome/src/com/chris/shieldhome/MainActivity.java:1135:        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.DITHER_FLAG); p.setDither(true);
shieldhome/src/com/chris/shieldhome/MainActivity.java:1136:        p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
shieldhome/src/com/chris/shieldhome/MainActivity.java:1138:        p.setShader(new LinearGradient(0,0,leftEnd,0, new int[]{Color.TRANSPARENT,Color.TRANSPARENT,Color.WHITE}, new float[]{0, leftEnd<=0?0:artL/leftEnd, 1}, Shader.TileMode.CLAMP));
shieldhome/src/com/chris/shieldhome/MainActivity.java:1140:        p.setShader(new LinearGradient(0,h,0,botEnd, new int[]{Color.TRANSPARENT,Color.TRANSPARENT,Color.WHITE}, new float[]{0, botRange<=0?0:(h-artB)/botRange, 1}, Shader.TileMode.CLAMP));
shieldhome/src/com/chris/shieldhome/MainActivity.java:1142:        p.setShader(new LinearGradient(0,0,0,h*0.064f, Color.argb(240,255,255,255), Color.WHITE, Shader.TileMode.CLAMP));
shieldhome/src/com/chris/shieldhome/MainActivity.java:1145:        p.setShader(new RadialGradient(artL, artB, rad, new int[]{Color.TRANSPARENT, Color.argb(50,255,255,255), Color.WHITE}, new float[]{0, 0.5f, 1}, Shader.TileMode.CLAMP));
shieldhome/src/com/chris/shieldhome/MainActivity.java:1152:        if (hit!=null && !hit.isRecycled() && hit.getWidth()==tw && hit.getHeight()==th) return hit;
shieldhome/src/com/chris/shieldhome/MainActivity.java:1154:        Canvas c=new Canvas(out);
shieldhome/src/com/chris/shieldhome/MainActivity.java:1156:        float s=Math.max(artW/src.getWidth(), artH/src.getHeight());
shieldhome/src/com/chris/shieldhome/MainActivity.java:1157:        float dw=src.getWidth()*s, dh=src.getHeight()*s, cx=artL+artW/2f, cy=artH/2f;
shieldhome/src/com/chris/shieldhome/MainActivity.java:1158:        c.drawBitmap(src, null, new RectF(cx-dw/2,cy-dh/2,cx+dw/2,cy+dh/2), null);
shieldhome/src/com/chris/shieldhome/MainActivity.java:1159:        Paint xp=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.DITHER_FLAG); xp.setDither(true);
shieldhome/src/com/chris/shieldhome/MainActivity.java:1160:        xp.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
shieldhome/src/com/chris/shieldhome/MainActivity.java:1164:        xp.setShader(new LinearGradient(0,0,leftEnd,0, new int[]{0xE6000000|HERO_SCRIM_RGB,0xC2000000|HERO_SCRIM_RGB,HERO_SCRIM_RGB}, new float[]{0, leftEnd<=0?0:artL/leftEnd, 1}, Shader.TileMode.CLAMP));
shieldhome/src/com/chris/shieldhome/MainActivity.java:1169:    ImageView heroArt, heroBadge; TextView heroTitle, heroSub; LinearLayout heroMeta; List<Object[]> heroItems = new ArrayList<>(); int heroIdx=0;
shieldhome/src/com/chris/shieldhome/MainActivity.java:1173:        if (heroTitle.getPaint().measureText(heroTitle.getText().toString())>p(360)) ts(heroTitle, 17);
shieldhome/src/com/chris/shieldhome/MainActivity.java:1189:        for (int i=0;i<n;i++){
shieldhome/src/com/chris/shieldhome/MainActivity.java:1194:            int b=i; while (b>0 && Character.isLetter(s.charAt(b-1))) b--;
shieldhome/src/com/chris/shieldhome/MainActivity.java:1214:        android.view.animation.Interpolator ease=new android.view.animation.PathInterpolator(0.16f,1f,0.3f,1f);
shieldhome/src/com/chris/shieldhome/MainActivity.java:1227:                heroDots.animate().alpha(0f).setDuration(120).setInterpolator(new android.view.animation.AccelerateInterpolator()).withEndAction(()->{ if (heroDots!=null && (heroBtn==null||!heroBtn.isFocused())) heroDots.setVisibility(View.GONE); }).start();
shieldhome/src/com/chris/shieldhome/MainActivity.java:1241:    final Runnable heroRotate = new Runnable(){ public void run(){ synchronized(heroLock){ if (heroItems.size()>1) heroIdx=(heroIdx+1)%heroItems.size(); else { h.postDelayed(this, 16000); return; } } showHero(); h.postDelayed(this, 16000); } };
shieldhome/src/com/chris/shieldhome/MainActivity.java:1256:        StringBuilder b=new StringBuilder();
shieldhome/src/com/chris/shieldhome/MainActivity.java:1257:        for (Object[] o: l) b.append(o[0]).append('|').append(o[1]).append('|').append(o[3]).append(',');
shieldhome/src/com/chris/shieldhome/MainActivity.java:1278:        final int tw=heroG!=null&&heroG.getWidth()>0?heroG.getWidth():p(1024), th=heroG!=null&&heroG.getHeight()>0?heroG.getHeight():p(400), g=++heroLoadGen;
shieldhome/src/com/chris/shieldhome/MainActivity.java:1286:        android.view.animation.Interpolator ease = new android.view.animation.PathInterpolator(0.4f,0f,0.2f,1f);
shieldhome/src/com/chris/shieldhome/MainActivity.java:1296:        android.view.animation.Interpolator outIp=new android.view.animation.AccelerateInterpolator();
shieldhome/src/com/chris/shieldhome/MainActivity.java:1297:        for (View t: texts) t.animate().alpha(0f).translationX(-p(10)).setDuration(450).setInterpolator(outIp).start();
shieldhome/src/com/chris/shieldhome/MainActivity.java:1302:            try { Drawable bd = pm.getApplicationBanner((String)o[0]); if (bd==null) bd = pm.getApplicationIcon((String)o[0]); heroBadge.setImageBitmap(glyph((String)o[0], bd));} catch(Exception e){}
shieldhome/src/com/chris/shieldhome/MainActivity.java:1304:            if (heroDots!=null && heroDots.getVisibility()==View.VISIBLE){ int n; synchronized(heroLock){ n=heroItems.size(); } heroDots.bind(n, heroIdx, true); }
shieldhome/src/com/chris/shieldhome/MainActivity.java:1305:            int d=200; for (View t: texts){ t.setTranslationX(p(16)); t.animate().alpha(t==heroBadge?0.7f:1f).translationX(0).setStartDelay(d).setDuration(900).setInterpolator(new android.view.animation.DecelerateInterpolator(2f)).start(); d+=90; }
shieldhome/src/com/chris/shieldhome/MainActivity.java:1306:            if (ctaOn && heroBtn!=null && heroBtn.isFocused()){ heroBtn.animate().cancel(); heroBtn.setAlpha(0f); heroBtn.animate().alpha(1f).setStartDelay(200).setDuration(900).setInterpolator(new android.view.animation.DecelerateInterpolator(2f)).start(); }
shieldhome/src/com/chris/shieldhome/MainActivity.java:1310:    Map<String,Bitmap> glyphs=new HashMap<>();
shieldhome/src/com/chris/shieldhome/MainActivity.java:1314:        int W=320, H=Math.max(1,Math.round(320f*d.getIntrinsicHeight()/Math.max(1,d.getIntrinsicWidth()))); if (d.getIntrinsicWidth()<=0){ H=180; }
shieldhome/src/com/chris/shieldhome/MainActivity.java:1315:        Bitmap src=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888); android.graphics.Canvas cv=new android.graphics.Canvas(src); d.setBounds(0,0,W,H); d.draw(cv);
shieldhome/src/com/chris/shieldhome/MainActivity.java:1316:        int[] px=new int[W*H]; src.getPixels(px,0,W,0,0,W,H);
shieldhome/src/com/chris/shieldhome/MainActivity.java:1318:        for (int i=0;i<px.length;i++){ int x=i%W, y=i/W, q=o[i]; int a=Color.alpha(q);
shieldhome/src/com/chris/shieldhome/MainActivity.java:1320:            for (int r: refs){ float dd; if (Color.alpha(r)<40) dd=a/255f; else dd=(Math.abs(Color.red(q)-Color.red(r))+Math.abs(Color.green(q)-Color.green(r))+Math.abs(Color.blue(q)-Color.blue(r)))/(3*255f)*(a/255f); dist=Math.min(dist,dd); }
shieldhome/src/com/chris/shieldhome/MainActivity.java:1326:        int pad=8; Bitmap out=Bitmap.createBitmap(w.getWidth()+pad*2,w.getHeight()+pad*2,Bitmap.Config.ARGB_8888); android.graphics.Canvas oc=new android.graphics.Canvas(out);
shieldhome/src/com/chris/shieldhome/MainActivity.java:1327:        android.graphics.Paint sh=new android.graphics.Paint(); sh.setMaskFilter(new android.graphics.BlurMaskFilter(5, android.graphics.BlurMaskFilter.Blur.NORMAL));
shieldhome/src/com/chris/shieldhome/MainActivity.java:1328:        int[] off=new int[2]; Bitmap a=w.extractAlpha(sh,off); android.graphics.Paint sp=new android.graphics.Paint(); sp.setColor(Color.argb(150,0,0,0)); oc.drawBitmap(a,pad+off[0],pad+off[1]+2,sp);
shieldhome/src/com/chris/shieldhome/MainActivity.java:1342:            StringBuilder sb=new StringBuilder(); List<String> keys=new ArrayList<>(usage.keySet()); Collections.sort(keys,(a,b)->Long.compare(use(b),use(a)));
shieldhome/src/com/chris/shieldhome/MainActivity.java:1343:            for (int i=0;i<Math.min(15,keys.size());i++) sb.append(keys.get(i)).append('=').append(use(keys.get(i))/60000).append("m ");
shieldhome/src/com/chris/shieldhome/MainActivity.java:1372:        StringBuilder b=new StringBuilder(tab==0?"P|":"L|");
shieldhome/src/com/chris/shieldhome/MainActivity.java:1373:        if (tab==0){ if (wnPickCache!=null) for (Object[] o: wnPickCache) b.append(o[0]).append('|').append(o[1]).append('|').append(o[2]).append(','); }
shieldhome/src/com/chris/shieldhome/MainActivity.java:1381:        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(p(184.4f), p(112)); lp.bottomMargin=p(8); lp.leftMargin = i==0?0:p(8);
shieldhome/src/com/chris/shieldhome/MainActivity.java:1382:        if (v.getParent()==cardRow){ v.setLayoutParams(lp); return; }
shieldhome/src/com/chris/shieldhome/MainActivity.java:1383:        if (v.getParent()!=null) ((ViewGroup)v.getParent()).removeView(v);
shieldhome/src/com/chris/shieldhome/MainActivity.java:1384:        if (!(v.getLayoutParams() instanceof LinearLayout.LayoutParams)){
shieldhome/src/com/chris/shieldhome/MainActivity.java:1385:            final View.OnFocusChangeListener base = v.getOnFocusChangeListener();
shieldhome/src/com/chris/shieldhome/MainActivity.java:1394:        int x=snapX(cardScroll.getScrollX(), idx, p(184.4f), p(8), p(954), cardRow.getChildCount());
shieldhome/src/com/chris/shieldhome/MainActivity.java:1395:        if (x!=cardScroll.getScrollX()) cardScroll.smoothScrollTo(x,0); else updateCardAlphas();
shieldhome/src/com/chris/shieldhome/MainActivity.java:1400:        if (cardRow!=null && cardRow.getChildCount()>0 && cardRow.indexOfChild(getCurrentFocus())>=0) cardRow.getChildAt(0).requestFocus();
shieldhome/src/com/chris/shieldhome/MainActivity.java:1405:        int sx=cardScroll==null?0:cardScroll.getScrollX(), stride=p(184.4f)+p(8);
shieldhome/src/com/chris/shieldhome/MainActivity.java:1406:        for (int i=0;i<cardRow.getChildCount();i++){
shieldhome/src/com/chris/shieldhome/MainActivity.java:1407:            View c=cardRow.getChildAt(i); float a=tileAlpha(i,sx,stride,5);
shieldhome/src/com/chris/shieldhome/MainActivity.java:1409:                GlowFrame g=(GlowFrame)c; g.fade=a; g.setAlpha(1f); g.inner.setAlpha(a); g.syncHalo(); g.invalidate();
shieldhome/src/com/chris/shieldhome/MainActivity.java:1411:                    View iv=g.inner.getChildAt(0);
shieldhome/src/com/chris/shieldhome/MainActivity.java:1412:                    if (iv instanceof ImageView){ Object tg=iv.getTag();
shieldhome/src/com/chris/shieldhome/MainActivity.java:1443:        FrameLayout f = new FrameLayout(this); f.setClipToOutline(true);
shieldhome/src/com/chris/shieldhome/MainActivity.java:1444:        GradientDrawable bg = new GradientDrawable(); bg.setCornerRadius(p(TILE_R)); bg.setColor(Color.argb(96,190,185,220)); bg.setStroke(Math.max(1,p(.6f)), Color.argb(60,255,255,255)); f.setBackground(bg);
shieldhome/src/com/chris/shieldhome/MainActivity.java:1446:        ImageView iv = new ImageView(this); iv.setScaleType(ImageView.ScaleType.CENTER_CROP); if (art!=null) iv.setImageDrawable(art); if (fitArt){ iv.setScaleType(ImageView.ScaleType.FIT_CENTER); iv.setPadding(p(30),p(18),p(30),p(30)); } loadInto(iv, artUri, fitArt);
shieldhome/src/com/chris/shieldhome/MainActivity.java:1447:        f.addView(iv, new FrameLayout.LayoutParams(-1,-1));
shieldhome/src/com/chris/shieldhome/MainActivity.java:1448:        View shade = new View(this); shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{Color.TRANSPARENT, Color.argb(70,0,0,0), Color.argb(160,0,0,0), Color.argb(204,0,0,0)}));
shieldhome/src/com/chris/shieldhome/MainActivity.java:1449:        if (caption!=null || progress>=0) f.addView(shade, new FrameLayout.LayoutParams(-1, p(56), Gravity.BOTTOM));
shieldhome/src/com/chris/shieldhome/MainActivity.java:1451:            ImageView b = new ImageView(this); b.setScaleType(ImageView.ScaleType.CENTER_CROP); b.setClipToOutline(true);
shieldhome/src/com/chris/shieldhome/MainActivity.java:1452:            GradientDrawable bb = new GradientDrawable(); bb.setCornerRadius(p(4)); bb.setColor(Color.BLACK); b.setBackground(bb);
shieldhome/src/com/chris/shieldhome/MainActivity.java:1453:            try { Drawable d = pm.getApplicationBanner(badgePkg); if (d==null) d = pm.getApplicationIcon(badgePkg); b.setImageDrawable(d);} catch(Exception e){}
shieldhome/src/com/chris/shieldhome/MainActivity.java:1461:            FrameLayout bar = new FrameLayout(this); bar.setBackgroundColor(Color.argb(90,255,255,255));
shieldhome/src/com/chris/shieldhome/MainActivity.java:1462:            View fill = new View(this); fill.setBackgroundColor(Color.rgb(229,9,20));
shieldhome/src/com/chris/shieldhome/MainActivity.java:1463:            bar.addView(fill, new FrameLayout.LayoutParams(Math.round(p(166)*progress), -1));
shieldhome/src/com/chris/shieldhome/MainActivity.java:1476:        if (next.equals(cardsBindSig) && cardRow.getChildCount()>0) return;
shieldhome/src/com/chris/shieldhome/MainActivity.java:1484:        for (final String pk : (tab==0?PLAY_PKGS:LIVE_PKGS)){
shieldhome/src/com/chris/shieldhome/MainActivity.java:1486:            Drawable art=null; try { art = pm.getApplicationBanner(pk); if (art==null){ Intent li=pm.getLeanbackLaunchIntentForPackage(pk); if(li!=null) art=pm.getActivityBanner(li);} if(art==null) art=pm.getApplicationIcon(pk);} catch(Exception e){}
shieldhome/src/com/chris/shieldhome/MainActivity.java:1497:        for (Object[] o: pick){ final Intent fi=new Intent((Intent)o[3]).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); final String pk2=(String)o[0]; boolean isNew=o.length>5;
shieldhome/src/com/chris/shieldhome/MainActivity.java:1503:    List<Object[]> plutoCaps = new ArrayList<>(); Map<String,String> plutoNow = new HashMap<>(); Map<String,String> plutoArt = new HashMap<>(); Map<String,String> plutoLogo = new HashMap<>(); final Map<String,String> plutoSlug = new java.util.concurrent.ConcurrentHashMap<>(); long plutoAt=0;
shieldhome/src/com/chris/shieldhome/MainActivity.java:1504:    List<View> liveCache; final Set<String> liveSlugs = new LinkedHashSet<>();
shieldhome/src/com/chris/shieldhome/MainActivity.java:1509:            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US); f.setTimeZone(TimeZone.getTimeZone("UTC"));
shieldhome/src/com/chris/shieldhome/MainActivity.java:1511:            java.net.HttpURLConnection c=(java.net.HttpURLConnection)new java.net.URL("https://api.pluto.tv/v2/channels?start="+f.format(new Date(now))+"&stop="+f.format(new Date(now+60000))).openConnection();
shieldhome/src/com/chris/shieldhome/MainActivity.java:1513:            java.io.InputStream in = c.getInputStream(); java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream(); byte[] buf=new byte[65536]; int r; while ((r=in.read(buf))>0) bo.write(buf,0,r); in.close();
shieldhome/src/com/chris/shieldhome/MainActivity.java:1514:            org.json.JSONArray a = new org.json.JSONArray(bo.toString("UTF-8")); final Map<String,String> m = new HashMap<>(), art = new HashMap<>(), logos = new HashMap<>();
shieldhome/src/com/chris/shieldhome/MainActivity.java:1515:            Set<String> ban = new HashSet<>(); Map<String,Integer> shared = new HashMap<>();
shieldhome/src/com/chris/shieldhome/MainActivity.java:1516:            for (int i=0;i<a.length();i++){ org.json.JSONObject ch=a.getJSONObject(i);
shieldhome/src/com/chris/shieldhome/MainActivity.java:1521:                if (tl!=null && tl.length()>0){ String first=plutoGuideArt(tl.getJSONObject(0), ban); if (first!=null){ String b=bareUrl(first); shared.put(b, (shared.containsKey(b)?shared.get(b):0)+1); } }
shieldhome/src/com/chris/shieldhome/MainActivity.java:1523:            for (Map.Entry<String,Integer> e: shared.entrySet()) if (e.getValue()>=4) ban.add(e.getKey());
shieldhome/src/com/chris/shieldhome/MainActivity.java:1524:            for (int i=0;i<a.length();i++){ org.json.JSONObject ch=a.getJSONObject(i); org.json.JSONArray tl=ch.optJSONArray("timelines");
shieldhome/src/com/chris/shieldhome/MainActivity.java:1525:                if (tl!=null && tl.length()>0){ org.json.JSONObject t0=tl.getJSONObject(0); m.put(ch.optString("name"), t0.optString("title")); String img=plutoGuideArt(t0, ban); if (img!=null) art.put(ch.optString("name"), img); }
shieldhome/src/com/chris/shieldhome/MainActivity.java:1537:        for (String u: cand) if (!genericArt(u, ban)) return u;
shieldhome/src/com/chris/shieldhome/MainActivity.java:1549:    static final String[] LIVE_SLUGS = {"cnn-headlines","nbc-news-now","abc-news-live","cbs-news"};
shieldhome/src/com/chris/shieldhome/MainActivity.java:1551:    Intent plutoIntent(String slug){ return new Intent(Intent.ACTION_VIEW, Uri.parse("https://pluto.tv/live-tv"+(slug==null?"":"/"+slug))).setPackage("tv.pluto.android").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); }
shieldhome/src/com/chris/shieldhome/MainActivity.java:1555:        for (Map.Entry<String,Long> e: ids.entrySet()){ String k=slugify(e.getKey()); if (k.equals(slug)||k.equals(slugify(nm))) return e.getValue(); }
shieldhome/src/com/chris/shieldhome/MainActivity.java:1560:        liveCache=new ArrayList<>(); liveSlugs.clear(); plutoCaps.clear();
shieldhome/src/com/chris/shieldhome/MainActivity.java:1564:            for (String slug: LIVE_SLUGS){
shieldhome/src/com/chris/shieldhome/MainActivity.java:1576:        try { ban=pm.getApplicationBanner("tv.pluto.android"); ic=pm.getApplicationIcon("tv.pluto.android"); } catch (Exception e){}
shieldhome/src/com/chris/shieldhome/MainActivity.java:1580:        LinkedHashMap<String,String> slugName=new LinkedHashMap<>();
shieldhome/src/com/chris/shieldhome/MainActivity.java:1581:        Map<String,Long> plutoIds=plutoIdsCache!=null?plutoIdsCache:new HashMap<>();
shieldhome/src/com/chris/shieldhome/MainActivity.java:1582:        for (String slug: LIVE_SLUGS){
shieldhome/src/com/chris/shieldhome/MainActivity.java:1583:            String nm=null; for (Map.Entry<String,String> en: plutoSlug.entrySet()) if (slug.equals(en.getValue())) nm=en.getKey();
shieldhome/src/com/chris/shieldhome/MainActivity.java:1587:        for (Map.Entry<String,Long> e: plutoIds.entrySet()){ String slug=slugify(e.getKey()); if (!slugName.containsKey(slug)) slugName.put(slug, e.getKey()); }
shieldhome/src/com/chris/shieldhome/MainActivity.java:1588:        for (Map.Entry<String,String> e: plutoSlug.entrySet()) if (!slugName.containsKey(e.getValue())) slugName.put(e.getValue(), e.getKey());
shieldhome/src/com/chris/shieldhome/MainActivity.java:1593:        if (cardRow.getChildCount()==liveCache.size() && cardRow.getChildCount()>0 && cardRow.getChildAt(0)==liveCache.get(0)){ applyLiveNow(); return; }
shieldhome/src/com/chris/shieldhome/MainActivity.java:1595:        int i=0; for (View v: liveCache){ if (v.getParent()!=null) ((ViewGroup)v.getParent()).removeView(v); addCard(v, i++); }
shieldhome/src/com/chris/shieldhome/MainActivity.java:1599:        Map<String,Long> plutoIds=plutoIdsCache!=null?plutoIdsCache:new HashMap<>();
shieldhome/src/com/chris/shieldhome/MainActivity.java:1602:        ImageView big=(ImageView)((GlowFrame)v).inner.getChildAt(0);
shieldhome/src/com/chris/shieldhome/MainActivity.java:1606:        for (int ci=0; ci<((GlowFrame)v).inner.getChildCount(); ci++){ View cv=((GlowFrame)v).inner.getChildAt(ci); if (cv instanceof TextView && cv!=ov) cap=(TextView)cv; }
shieldhome/src/com/chris/shieldhome/MainActivity.java:1614:        plutoCaps.add(new Object[]{cap, nm, big, ov, id});
shieldhome/src/com/chris/shieldhome/MainActivity.java:1620:        ov.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Color.argb(160,0,0,0), Color.argb(70,0,0,0), Color.TRANSPARENT}));
shieldhome/src/com/chris/shieldhome/MainActivity.java:1628:        for (Object[] o: plutoCaps){
shieldhome/src/com/chris/shieldhome/MainActivity.java:1649:        Object old=iv.getTag(); if (uri.equals(old) || ("pend:"+uri).equals(old) || ("pendf:"+uri).equals(old)) return;
shieldhome/src/com/chris/shieldhome/MainActivity.java:1652:        if (now || (tab==1 && iv.getAlpha()>0)) loadInto(iv, uri, fit); else iv.setTag((fit?"pendf:":"pend:")+uri);
shieldhome/src/com/chris/shieldhome/MainActivity.java:1657:        try (Cursor c=getContentResolver().query(TvContract.buildProgramsUriForChannel(id, now, now+60000), null, null, null, null)){
shieldhome/src/com/chris/shieldhome/MainActivity.java:1659:                int i=c.getColumnIndex("poster_art_uri"); String a=i>=0&&!c.isNull(i)?c.getString(i):null;
shieldhome/src/com/chris/shieldhome/MainActivity.java:1660:                if (a==null){ i=c.getColumnIndex("thumbnail_uri"); a=i>=0&&!c.isNull(i)?c.getString(i):null; }
shieldhome/src/com/chris/shieldhome/MainActivity.java:1667:        try (Cursor c=getContentResolver().query(TvContract.Channels.CONTENT_URI, null, null, null, null)){
shieldhome/src/com/chris/shieldhome/MainActivity.java:1668:            while (c!=null && c.moveToNext()){
shieldhome/src/com/chris/shieldhome/MainActivity.java:1669:                int pi=c.getColumnIndex("package_name"); String pkg=pi>=0&&!c.isNull(pi)?c.getString(pi):null;
shieldhome/src/com/chris/shieldhome/MainActivity.java:1671:                int i=c.getColumnIndex("_id"); if (i<0) continue;
shieldhome/src/com/chris/shieldhome/MainActivity.java:1672:                String art=tifNowArt(c.getLong(i)); if (art!=null) return art;
shieldhome/src/com/chris/shieldhome/MainActivity.java:1677:    void playFreeplay(){ try { Intent b=new Intent("com.google.android.tvlauncher.action.VIEW", Uri.parse("tv://freeplay")); b.setComponent(new android.content.ComponentName("com.google.android.tvlauncher","com.google.android.tvlauncher.TvContentIntentReceiver")); b.addFlags(Intent.FLAG_RECEIVER_FOREGROUND); sendBroadcast(b); android.util.Log.i("ShieldHome","freeplay broadcast sent"); } catch(Exception e){ android.util.Log.w("ShieldHome","freeplay "+e); } }
shieldhome/src/com/chris/shieldhome/MainActivity.java:1680:        Drawable ic=null; if (art==null) try { android.content.res.Resources r=pm.getResourcesForApplication("com.google.android.tvlauncher"); int id=r.getIdentifier("freeplay_icon","drawable","com.google.android.tvlauncher"); if (id!=0) ic=r.getDrawable(id,null); } catch(Exception e){}
shieldhome/src/com/chris/shieldhome/MainActivity.java:1682:        TextView tg=text("LIVE", 7, Color.WHITE, "sans-serif-medium"); tg.setLetterSpacing(.08f); tg.setPadding(p(5),p(2),p(5),p(2)); GradientDrawable gb=new GradientDrawable(); gb.setCornerRadius(p(3)); gb.setColor(Color.rgb(200,30,40)); tg.setBackground(gb);
shieldhome/src/com/chris/shieldhome/MainActivity.java:1687:    boolean installed(String p){ try { pm.getApplicationInfo(p,0); return true; } catch(Exception e){ return false; } }
shieldhome/src/com/chris/shieldhome/MainActivity.java:1688:    void launchPkg(String pkg){ Intent i = pm.getLeanbackLaunchIntentForPackage(pkg); if (i==null) i = pm.getLaunchIntentForPackage(pkg); if (i!=null) startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
shieldhome/src/com/chris/shieldhome/MainActivity.java:1693:        List<Object[]> nl = new ArrayList<>();
shieldhome/src/com/chris/shieldhome/MainActivity.java:1694:        try { org.json.JSONArray a = new org.json.JSONArray(js==null?"[]":js);
shieldhome/src/com/chris/shieldhome/MainActivity.java:1695:            for (int i=0;i<a.length();i++){ org.json.JSONObject o=a.getJSONObject(i); String pkg=o.getString("pkg"); if (!installed(pkg) || o.optString("img").isEmpty()) continue;
shieldhome/src/com/chris/shieldhome/MainActivity.java:1697:                try { Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(o.getString("date")); long diff = Math.round((d.getTime() - new SimpleDateFormat("yyyy-MM-dd",Locale.US).parse(new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date())).getTime())/86400000.0);
shieldhome/src/com/chris/shieldhome/MainActivity.java:1698:                    when = diff==0?"New today":diff==1?"Coming tomorrow":diff==-1?"Added yesterday":diff<0?"Added "+new SimpleDateFormat("EEE",Locale.US).format(d):"Coming "+new SimpleDateFormat("EEEE",Locale.US).format(d); } catch (Exception e){ when="New"; }
shieldhome/src/com/chris/shieldhome/MainActivity.java:1701:                Intent li = pm.getLeanbackLaunchIntentForPackage(pkg); if (li==null) li = pm.getLaunchIntentForPackage(pkg);
shieldhome/src/com/chris/shieldhome/MainActivity.java:1702:                long avail=0; try { Date ad=new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(o.getString("date")); if (ad!=null && ad.getTime()>System.currentTimeMillis()) avail=ad.getTime(); } catch (Exception e){}
shieldhome/src/com/chris/shieldhome/MainActivity.java:1703:                nl.add(new Object[]{pkg, o.getString("show"), when+" on "+o.optString("net")+ep+"\n"+sum, o.getString("img"), li, "NEW", avail});
shieldhome/src/com/chris/shieldhome/MainActivity.java:1705:        } catch (Exception e){ android.util.Log.w("ShieldHome","whatsnew parse "+e); }
shieldhome/src/com/chris/shieldhome/MainActivity.java:1707:        List<Object[]> mix = new ArrayList<>(); for (Object[] o: heroItems) if (o.length>5 && "FEATURED".equals(o[5])) mix.add(o);
shieldhome/src/com/chris/shieldhome/MainActivity.java:1708:        int nf = mix.size(); Collections.shuffle(nl); for (int i=0;i<Math.min(8,nl.size());i++) mix.add(nl.get(i));
shieldhome/src/com/chris/shieldhome/MainActivity.java:1719:    int dimPct(){ return Math.max(0, Math.min(90, prefs().getInt("dim_pct",0))); }
shieldhome/src/com/chris/shieldhome/MainActivity.java:1723:        if (!anim){ dimFill=to; if (gear!=null) gear.invalidate(); if (dimBar!=null) dimBar.invalidate(); return; }
shieldhome/src/com/chris/shieldhome/MainActivity.java:1726:        dimFillAnim.addUpdateListener(a->{ dimFill=(Float)a.getAnimatedValue(); if (gear!=null) gear.invalidate(); if (dimBar!=null) dimBar.invalidate(); });
shieldhome/src/com/chris/shieldhome/MainActivity.java:1732:        android.view.animation.Interpolator ease=new android.view.animation.DecelerateInterpolator();
shieldhome/src/com/chris/shieldhome/MainActivity.java:1747:        android.view.animation.Interpolator ease=new android.view.animation.DecelerateInterpolator();
shieldhome/src/com/chris/shieldhome/MainActivity.java:1760:        gear.setNextFocusLeftId(gear.getId()); gear.setNextFocusRightId(gear.getId());
shieldhome/src/com/chris/shieldhome/MainActivity.java:1775:        Intent si = new Intent(this, DimService.class).putExtra("pct", pct);
shieldhome/src/com/chris/shieldhome/MainActivity.java:1777:        if (inAppDim==null){ inAppDim = new View(this); inAppDim.setBackgroundColor(Color.BLACK); inAppDim.setClickable(false); inAppDim.setFocusable(false); root.addView(inAppDim, new FrameLayout.LayoutParams(-1,-1)); inAppDim.setTranslationZ(p(60)); }
shieldhome/src/com/chris/shieldhome/MainActivity.java:1784:        long age = System.currentTimeMillis()-prefs().getLong("wx_at",0);
shieldhome/src/com/chris/shieldhome/MainActivity.java:1790:        int code=prefs().getInt("wx_code",0); boolean day=prefs().getInt("wx_day",1)==1;
shieldhome/src/com/chris/shieldhome/MainActivity.java:1802:        setPill(wxPill, ic+"  "+prefs().getInt("wx_t",0)+"\u00B0F");
shieldhome/src/com/chris/shieldhome/DimService.java:14:        int pct = i==null ? getSharedPreferences("home",MODE_PRIVATE).getInt("dimpct",0) : i.getIntExtra("pct", getSharedPreferences("home",MODE_PRIVATE).getInt("dimpct",0));
shieldhome/src/com/chris/shieldhome/DimService.java:15:        getSharedPreferences("home",MODE_PRIVATE).edit().putInt("dimpct",pct).apply();
shieldhome/src/com/chris/shieldhome/DimService.java:17:        NotificationManager nm = getSystemService(NotificationManager.class);
shieldhome/src/com/chris/shieldhome/DimService.java:18:        nm.createNotificationChannel(new NotificationChannel("dim","Screen dimmer",NotificationManager.IMPORTANCE_MIN));
shieldhome/src/com/chris/shieldhome/DimService.java:19:        startForeground(1, new Notification.Builder(this,"dim").setContentTitle("Screen dimmer on").setSmallIcon(android.R.drawable.ic_menu_view).build());
shieldhome/src/com/chris/shieldhome/DimService.java:20:        wm = getSystemService(WindowManager.class);
shieldhome/src/com/chris/shieldhome/DimService.java:22:            v = new View(this); v.setBackgroundColor(Color.BLACK);
shieldhome/src/com/chris/shieldhome/DimService.java:23:            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,

exec
/bin/bash -lc "rg -n \"onDraw|class .*View|new Runnable|postDelayed|ValueAnimator|ObjectAnimator|requestLayout|setLayoutParams|setVisibility|new SimpleDateFormat|containsKey\\(|getColumnIndex\\(|new int\\[\\]|new String\\[\\]\" shieldhome/src/com/chris/shieldhome/*.java" in /tmp/codexreview
 succeeded in 0ms:
shieldhome/src/com/chris/shieldhome/TvData.java:93:            if (!by.containsKey(k)) by.put(k, new ArrayList<>());
shieldhome/src/com/chris/shieldhome/TvData.java:201:        for (String k : new String[]{"news", "cnn", "msnbc", "haystack"})
shieldhome/src/com/chris/shieldhome/TvData.java:253:        for (String f : new String[]{"yyyy-MM-dd", "yyyy-MM", "yyyy"})
shieldhome/src/com/chris/shieldhome/TvData.java:254:            try { Date d = new SimpleDateFormat(f, Locale.US).parse(s); if (d != null) return d.getTime(); } catch (Exception e) {}
shieldhome/src/com/chris/shieldhome/TvData.java:257:    static String s(Cursor c, String col) { int i = c.getColumnIndex(col); return i < 0 || c.isNull(i) ? null : c.getString(i); }
shieldhome/src/com/chris/shieldhome/TvData.java:258:    static long l(Cursor c, String col) { int i = c.getColumnIndex(col); return i < 0 || c.isNull(i) ? 0 : c.getLong(i); }
shieldhome/src/com/chris/shieldhome/NetData.java:58:        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
shieldhome/src/com/chris/shieldhome/NetData.java:62:        for (int dd : new int[]{0, 1, -1, 2, 3, -2}) {
shieldhome/src/com/chris/shieldhome/NetData.java:83:                    if (!byPkg.containsKey(pkg)) byPkg.put(pkg, new ArrayList<>());
shieldhome/src/com/chris/shieldhome/FocusController.java:107:            if (t == a.heroBtn) { t.setVisibility(View.VISIBLE); t.setFocusable(true); }
shieldhome/src/com/chris/shieldhome/MainActivity.java:69:        h.postDelayed(()->{ appsCache=null; kickData(true); }, 150); } };
shieldhome/src/com/chris/shieldhome/MainActivity.java:143:        searchV.setFocusable(false); searchV.setAlpha(1f); searchV.setVisibility(View.INVISIBLE);
shieldhome/src/com/chris/shieldhome/MainActivity.java:144:        searchSplit.setVisibility(View.VISIBLE); searchSplit.setAlpha(1f);
shieldhome/src/com/chris/shieldhome/MainActivity.java:185:        if (searchSplit!=null) searchSplit.setVisibility(View.GONE);
shieldhome/src/com/chris/shieldhome/MainActivity.java:186:        if (searchV!=null){ searchV.setVisibility(View.INVISIBLE); searchV.setFocusable(false); }
shieldhome/src/com/chris/shieldhome/MainActivity.java:187:        searchField.setText(""); searchField.setVisibility(View.VISIBLE); searchField.bringToFront();
shieldhome/src/com/chris/shieldhome/MainActivity.java:193:        if (searchField!=null){ searchField.setVisibility(View.GONE); searchField.setText(""); }
shieldhome/src/com/chris/shieldhome/MainActivity.java:194:        if (!dimSlide && searchV!=null){ searchV.setVisibility(View.VISIBLE); searchV.setFocusable(!drawerOpen); }
shieldhome/src/com/chris/shieldhome/MainActivity.java:200:            if (!dimSlide && searchV!=null){ searchV.setVisibility(View.VISIBLE); searchV.setAlpha(1f); searchV.setFocusable(!drawerOpen); }
shieldhome/src/com/chris/shieldhome/MainActivity.java:209:            searchSplit.setVisibility(View.GONE);
shieldhome/src/com/chris/shieldhome/MainActivity.java:212:                searchV.setVisibility(View.VISIBLE); searchV.setFocusable(!drawerOpen);
shieldhome/src/com/chris/shieldhome/MainActivity.java:218:    void tick(){ setPill(timePill, new SimpleDateFormat("h:mm a", Locale.US).format(new Date())); h.postDelayed(this::tick, 15000); }
shieldhome/src/com/chris/shieldhome/MainActivity.java:225:    void setPill(PillView v, String s){ if (v==null) return; v.label=s; ViewGroup.LayoutParams lp=v.getLayoutParams(); if (lp!=null){ lp.width=pillTextW(v,s); if (v==heroBtn) lp.height=p(34); v.setLayoutParams(lp); } v.invalidate(); if (v==heroBtn) placeDots(); }
shieldhome/src/com/chris/shieldhome/MainActivity.java:260:        searchSplit.setVisibility(View.GONE);
shieldhome/src/com/chris/shieldhome/MainActivity.java:293:        searchField.setVisibility(View.GONE);
shieldhome/src/com/chris/shieldhome/MainActivity.java:303:        dimBar.setFocusable(false); dimBar.setClickable(false); dimBar.setVisibility(View.GONE);
shieldhome/src/com/chris/shieldhome/MainActivity.java:326:        heroLabel.setVisibility(View.GONE);
shieldhome/src/com/chris/shieldhome/MainActivity.java:378:    class PillView extends View {
shieldhome/src/com/chris/shieldhome/MainActivity.java:383:        @Override protected void onDraw(Canvas c){
shieldhome/src/com/chris/shieldhome/MainActivity.java:465:        TextView play, live; float hx, hw, hs=1f; android.animation.ValueAnimator ha, pa;
shieldhome/src/com/chris/shieldhome/MainActivity.java:472:            play.setLayoutParams(new LinearLayout.LayoutParams(sw,-1));
shieldhome/src/com/chris/shieldhome/MainActivity.java:473:            live.setLayoutParams(new LinearLayout.LayoutParams(sw,-1));
shieldhome/src/com/chris/shieldhome/MainActivity.java:507:            pa=android.animation.ValueAnimator.ofFloat(hs, to); pa.setDuration(340);
shieldhome/src/com/chris/shieldhome/MainActivity.java:512:        void showPlay(boolean on){ play.setVisibility(on?VISIBLE:GONE); play.setFocusable(on); requestLayout(); }
shieldhome/src/com/chris/shieldhome/MainActivity.java:520:            ha=android.animation.ValueAnimator.ofFloat(0,1); ha.setDuration(250);
shieldhome/src/com/chris/shieldhome/MainActivity.java:547:    class HeroDots extends View {
shieldhome/src/com/chris/shieldhome/MainActivity.java:548:        int n, cur; float ax; android.animation.ValueAnimator an;
shieldhome/src/com/chris/shieldhome/MainActivity.java:549:        HeroDots(){ super(MainActivity.this); setAlpha(0f); setVisibility(GONE); setFocusable(false); }
shieldhome/src/com/chris/shieldhome/MainActivity.java:559:            if (nn2!=n) requestLayout();
shieldhome/src/com/chris/shieldhome/MainActivity.java:565:            an=android.animation.ValueAnimator.ofFloat(0,1); an.setDuration(250);
shieldhome/src/com/chris/shieldhome/MainActivity.java:570:        @Override protected void onDraw(Canvas c){
shieldhome/src/com/chris/shieldhome/MainActivity.java:611:            android.animation.ValueAnimator va = android.animation.ValueAnimator.ofFloat(g.glow, f?1f:0f); va.setDuration(220);
shieldhome/src/com/chris/shieldhome/MainActivity.java:640:            View sheen = new View(this); sheen.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{Color.argb(28,255,255,255), Color.TRANSPARENT, Color.argb(40,0,0,0)}));
shieldhome/src/com/chris/shieldhome/MainActivity.java:660:                for (int x=0;x<64;x++) for (int y: new int[]{0,1,34,35}){ int px=bm.getPixel(x,y); if (Color.alpha(px)<128) continue; r+=Color.red(px); g+=Color.green(px); b+=Color.blue(px); n++; }
shieldhome/src/com/chris/shieldhome/MainActivity.java:661:                for (int y=0;y<36;y++) for (int x: new int[]{0,1,62,63}){ int px=bm.getPixel(x,y); if (Color.alpha(px)<128) continue; r+=Color.red(px); g+=Color.green(px); b+=Color.blue(px); n++; }
shieldhome/src/com/chris/shieldhome/MainActivity.java:666:                int k = ((int)(hsv[0]/20)); int v=(hist.containsKey(k)?hist.get(k):0)+1; hist.put(k,v); if (v>bestN){bestN=v; best=px;} }
shieldhome/src/com/chris/shieldhome/MainActivity.java:731:            v.setVisibility(View.VISIBLE);
shieldhome/src/com/chris/shieldhome/MainActivity.java:732:            v.animate().alpha(a).setDuration(600).setInterpolator(drawerIp).withEndAction(()->{ if (drawerOpen) v.setVisibility(View.INVISIBLE); }).start();
shieldhome/src/com/chris/shieldhome/MainActivity.java:796:        if (lp!=null){ lp.height=rowH()*Math.max(1,(all.size()+5)/6); homeGrid.setLayoutParams(lp); }
shieldhome/src/com/chris/shieldhome/MainActivity.java:932:        dimV.setVisibility(View.INVISIBLE); dimV.setClickable(false); dimV.setFocusable(false);
shieldhome/src/com/chris/shieldhome/MainActivity.java:944:        if (on){ stackEditDim(); dimV.setVisibility(View.VISIBLE); dimV.animate().alpha(1f).setDuration(160).setInterpolator(dragIp).start(); }
shieldhome/src/com/chris/shieldhome/MainActivity.java:945:        else dimV.animate().alpha(0f).setDuration(140).setInterpolator(dragIp).withEndAction(()->dimV.setVisibility(View.INVISIBLE)).start();
shieldhome/src/com/chris/shieldhome/MainActivity.java:987:            android.animation.ValueAnimator va=android.animation.ValueAnimator.ofFloat(g.glow, glowTo);
shieldhome/src/com/chris/shieldhome/MainActivity.java:1138:        p.setShader(new LinearGradient(0,0,leftEnd,0, new int[]{Color.TRANSPARENT,Color.TRANSPARENT,Color.WHITE}, new float[]{0, leftEnd<=0?0:artL/leftEnd, 1}, Shader.TileMode.CLAMP));
shieldhome/src/com/chris/shieldhome/MainActivity.java:1140:        p.setShader(new LinearGradient(0,h,0,botEnd, new int[]{Color.TRANSPARENT,Color.TRANSPARENT,Color.WHITE}, new float[]{0, botRange<=0?0:(h-artB)/botRange, 1}, Shader.TileMode.CLAMP));
shieldhome/src/com/chris/shieldhome/MainActivity.java:1145:        p.setShader(new RadialGradient(artL, artB, rad, new int[]{Color.TRANSPARENT, Color.argb(50,255,255,255), Color.WHITE}, new float[]{0, 0.5f, 1}, Shader.TileMode.CLAMP));
shieldhome/src/com/chris/shieldhome/MainActivity.java:1164:        xp.setShader(new LinearGradient(0,0,leftEnd,0, new int[]{0xE6000000|HERO_SCRIM_RGB,0xC2000000|HERO_SCRIM_RGB,HERO_SCRIM_RGB}, new float[]{0, leftEnd<=0?0:artL/leftEnd, 1}, Shader.TileMode.CLAMP));
shieldhome/src/com/chris/shieldhome/MainActivity.java:1182:        if (heroLabel!=null){ heroLabel.setText(meta); heroLabel.setVisibility(meta.isEmpty()?View.GONE:View.VISIBLE); }
shieldhome/src/com/chris/shieldhome/MainActivity.java:1224:                heroDots.setVisibility(View.VISIBLE);
shieldhome/src/com/chris/shieldhome/MainActivity.java:1227:                heroDots.animate().alpha(0f).setDuration(120).setInterpolator(new android.view.animation.AccelerateInterpolator()).withEndAction(()->{ if (heroDots!=null && (heroBtn==null||!heroBtn.isFocused())) heroDots.setVisibility(View.GONE); }).start();
shieldhome/src/com/chris/shieldhome/MainActivity.java:1241:    final Runnable heroRotate = new Runnable(){ public void run(){ synchronized(heroLock){ if (heroItems.size()>1) heroIdx=(heroIdx+1)%heroItems.size(); else { h.postDelayed(this, 16000); return; } } showHero(); h.postDelayed(this, 16000); } };
shieldhome/src/com/chris/shieldhome/MainActivity.java:1252:        if (!heroItems.isEmpty()){ showHero(); h.postDelayed(heroRotate, 16000); }
shieldhome/src/com/chris/shieldhome/MainActivity.java:1270:        h.postDelayed(heroRotate, 16000);
shieldhome/src/com/chris/shieldhome/MainActivity.java:1300:        h.postDelayed(()->{
shieldhome/src/com/chris/shieldhome/MainActivity.java:1382:        if (v.getParent()==cardRow){ v.setLayoutParams(lp); return; }
shieldhome/src/com/chris/shieldhome/MainActivity.java:1448:        View shade = new View(this); shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{Color.TRANSPARENT, Color.argb(70,0,0,0), Color.argb(160,0,0,0), Color.argb(204,0,0,0)}));
shieldhome/src/com/chris/shieldhome/MainActivity.java:1509:            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US); f.setTimeZone(TimeZone.getTimeZone("UTC"));
shieldhome/src/com/chris/shieldhome/MainActivity.java:1521:                if (tl!=null && tl.length()>0){ String first=plutoGuideArt(tl.getJSONObject(0), ban); if (first!=null){ String b=bareUrl(first); shared.put(b, (shared.containsKey(b)?shared.get(b):0)+1); } }
shieldhome/src/com/chris/shieldhome/MainActivity.java:1587:        for (Map.Entry<String,Long> e: plutoIds.entrySet()){ String slug=slugify(e.getKey()); if (!slugName.containsKey(slug)) slugName.put(slug, e.getKey()); }
shieldhome/src/com/chris/shieldhome/MainActivity.java:1588:        for (Map.Entry<String,String> e: plutoSlug.entrySet()) if (!slugName.containsKey(e.getValue())) slugName.put(e.getValue(), e.getKey());
shieldhome/src/com/chris/shieldhome/MainActivity.java:1612:        ov.setVisibility(art!=null||logo!=null?View.VISIBLE:View.GONE);
shieldhome/src/com/chris/shieldhome/MainActivity.java:1620:        ov.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Color.argb(160,0,0,0), Color.argb(70,0,0,0), Color.TRANSPARENT}));
shieldhome/src/com/chris/shieldhome/MainActivity.java:1633:                if (ov!=null) ov.setVisibility(View.VISIBLE);
shieldhome/src/com/chris/shieldhome/MainActivity.java:1637:                if (ov!=null) ov.setVisibility(View.VISIBLE);
shieldhome/src/com/chris/shieldhome/MainActivity.java:1641:                if (ov!=null) ov.setVisibility(View.GONE);
shieldhome/src/com/chris/shieldhome/MainActivity.java:1659:                int i=c.getColumnIndex("poster_art_uri"); String a=i>=0&&!c.isNull(i)?c.getString(i):null;
shieldhome/src/com/chris/shieldhome/MainActivity.java:1660:                if (a==null){ i=c.getColumnIndex("thumbnail_uri"); a=i>=0&&!c.isNull(i)?c.getString(i):null; }
shieldhome/src/com/chris/shieldhome/MainActivity.java:1669:                int pi=c.getColumnIndex("package_name"); String pkg=pi>=0&&!c.isNull(pi)?c.getString(pi):null;
shieldhome/src/com/chris/shieldhome/MainActivity.java:1671:                int i=c.getColumnIndex("_id"); if (i<0) continue;
shieldhome/src/com/chris/shieldhome/MainActivity.java:1697:                try { Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(o.getString("date")); long diff = Math.round((d.getTime() - new SimpleDateFormat("yyyy-MM-dd",Locale.US).parse(new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date())).getTime())/86400000.0);
shieldhome/src/com/chris/shieldhome/MainActivity.java:1698:                    when = diff==0?"New today":diff==1?"Coming tomorrow":diff==-1?"Added yesterday":diff<0?"Added "+new SimpleDateFormat("EEE",Locale.US).format(d):"Coming "+new SimpleDateFormat("EEEE",Locale.US).format(d); } catch (Exception e){ when="New"; }
shieldhome/src/com/chris/shieldhome/MainActivity.java:1702:                long avail=0; try { Date ad=new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(o.getString("date")); if (ad!=null && ad.getTime()>System.currentTimeMillis()) avail=ad.getTime(); } catch (Exception e){}
shieldhome/src/com/chris/shieldhome/MainActivity.java:1713:            showHero(); h.postDelayed(heroRotate, 16000);
shieldhome/src/com/chris/shieldhome/MainActivity.java:1718:    float dimFill; android.animation.ValueAnimator dimFillAnim;
shieldhome/src/com/chris/shieldhome/MainActivity.java:1725:        dimFillAnim=android.animation.ValueAnimator.ofFloat(dimFill, to); dimFillAnim.setDuration(200);
shieldhome/src/com/chris/shieldhome/MainActivity.java:1734:            if (searchTyping){ searchTyping=false; hideKb(); if (searchField!=null){ searchField.setVisibility(View.GONE); searchField.setText(""); } }
shieldhome/src/com/chris/shieldhome/MainActivity.java:1737:            if (searchSplit!=null) searchSplit.setVisibility(View.GONE);
shieldhome/src/com/chris/shieldhome/MainActivity.java:1739:            searchV.animate().alpha(0f).setDuration(180).setInterpolator(ease).withEndAction(()->{ if (dimSlide) searchV.setVisibility(View.INVISIBLE); }).start();
shieldhome/src/com/chris/shieldhome/MainActivity.java:1741:            searchV.setVisibility(View.VISIBLE); searchV.setFocusable(!drawerOpen);
shieldhome/src/com/chris/shieldhome/MainActivity.java:1750:            dimBar.setVisibility(View.VISIBLE);
shieldhome/src/com/chris/shieldhome/MainActivity.java:1754:            dimBar.animate().alpha(0f).scaleX(0.88f).scaleY(0.88f).setDuration(140).setInterpolator(ease).withEndAction(()->{ if (!dimSlide) dimBar.setVisibility(View.GONE); }).start();
shieldhome/src/com/chris/shieldhome/MainActivity.java:1778:        inAppDim.setAlpha(overlay?0f:pct/100f); inAppDim.setVisibility(!overlay && pct>0 ? View.VISIBLE : View.GONE);
shieldhome/src/com/chris/shieldhome/MainActivity.java:1786:        h.postDelayed(wxRun, Math.max(60000L, 30*60*1000L-Math.min(age,30*60*1000L)+5000));

exec
/bin/bash -lc "sed -n '1,250p' shieldhome/src/com/chris/shieldhome/MainActivity.java; sed -n '378,620p' shieldhome/src/com/chris/shieldhome/MainActivity.java; sed -n '1,280p' shieldhome/src/com/chris/shieldhome/FocusController.java; sed -n '1,280p' shieldhome/src/com/chris/shieldhome/ImageLoader.java" in /tmp/codexreview
 succeeded in 0ms:
package com.chris.shieldhome;

import android.app.Activity;
import android.content.*;
import android.content.pm.*;
import android.database.Cursor;
import android.graphics.*;
import android.graphics.drawable.*;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.*;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    // All geometry is in mockup pixels (1024x576 reference), scaled to the real screen.
    float u; PackageManager pm; FrameLayout root; LinearLayout cardRow; HorizontalScrollView cardScroll; FrameLayout cardWrap; View clk;
    SegBar tabBar; TextView tabPlay, tabLive; int tab = 0; Handler h = new Handler(Looper.getMainLooper());
    static final int TILE = Color.argb(34, 255, 255, 255), FOCUS = Color.rgb(64, 156, 255), BG = Color.rgb(8, 8, 16);
    static final int HERO_SCRIM_RGB=0x050507; // darker than BG 0x080810 so the wash doesn't read as wallpaper
    static final float HERO_LEFT_FADE=0.24f;
    static final int TILE_R = 11;
    static final float APP_FOCUS=1.13f, APP_HOLD=1.16f, APP_W=142.5f, APP_H=78.5f, APP_HG=19.8f, APP_VG=21f, APP_GRID_Y=467.5f;
    static final String[] PLAY_PKGS = {"com.google.android.youtube.tv","com.netflix.ninja","com.disney.disneyplus","com.hulu.livingroomplus","com.wbd.stream","com.amazon.amazonvideo.livingroom","com.plexapp.android"};
    static final String[] LIVE_PKGS = {"tv.pluto.android","com.cnbc.client","com.haystack.android","com.plexapp.android"};

    FrameLayout.LayoutParams lp(int w,int h,int g,int l,int t,int r,int b){ FrameLayout.LayoutParams x=new FrameLayout.LayoutParams(w,h,g); x.setMargins(l,t,r,b); return x; }
    java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(4);
    ImageLoader images; FocusController focus; TvData tv; NetData net;
    java.util.concurrent.Future<?> dataFut; int dataGen, heroLoadGen;
    List<String[]> appsCache, fullOrder=new ArrayList<>();
    List<Object[]> featuredCache, wnPickCache;
    Map<String,Long> plutoIdsCache=new HashMap<>();
    String homeBindSig="", cardsBindSig="", featuredBoundSig="";
    final Object heroLock=new Object(); Object[] heroOnScreen;
    void loadInto(final ImageView iv, String uri0, final boolean fit){
        if (images==null||uri0==null) return;
        images.load(iv, uri0, fit, iv.getWidth()>0?iv.getWidth():p(184.4f), iv.getHeight()>0?iv.getHeight():p(112));
    }
    int p(float v){ return Math.round(v*u); }
    void ts(TextView t, float v){ t.setTextSize(TypedValue.COMPLEX_UNIT_PX, v*u); }

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        pm = getPackageManager();
        images = new ImageLoader(this, pool, h);
        focus = new FocusController(this);
        tv = new TvData(this);
        net = new NetData(this);
        prefs().edit().remove("heromode").remove("featured").apply(); // v9: card mode removed
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        DisplayMetricsHolder();
        kickData(false);
        build();
        IntentFilter pf = new IntentFilter(); pf.addAction(Intent.ACTION_PACKAGE_REMOVED); pf.addAction(Intent.ACTION_PACKAGE_ADDED); pf.addAction(Intent.ACTION_PACKAGE_CHANGED); pf.addDataScheme("package");
        registerReceiver(pkgRx, pf);
    }
    final BroadcastReceiver pkgRx = new BroadcastReceiver(){ @Override public void onReceive(Context c, Intent i){
        String pkg = i.getData()==null?null:i.getData().getSchemeSpecificPart(); boolean repl = i.getBooleanExtra(Intent.EXTRA_REPLACING,false);
        android.util.Log.i("ShieldHome","pkg "+i.getAction()+" "+pkg+" repl="+repl);
        final boolean removed = Intent.ACTION_PACKAGE_REMOVED.equals(i.getAction()) && !repl && pkg!=null;
        if (removed){
            Runnable home = ()->{ appsCache=null; kickData(true); };
            if (!editMode && dragIdx<0) fadeRemove(homeGrid, pkg, home); else home.run();
            return; }
        h.postDelayed(()->{ appsCache=null; kickData(true); }, 150); } };
    @Override protected void onDestroy(){
        dataGen++; heroLoadGen++;
        try { unregisterReceiver(pkgRx); } catch (Exception e){}
        pool.shutdownNow();
        super.onDestroy();
    }
    void DisplayMetricsHolder(){ android.util.DisplayMetrics m = new android.util.DisplayMetrics(); getWindowManager().getDefaultDisplay().getRealMetrics(m); u = m.widthPixels/1024f; }
    @Override protected void onResume(){
        super.onResume();
        focus.prepare();
        if (appsCache==null){ if (dataFut==null) kickData(false); awaitData(); }
        bindFromCache();
        if (!editMode) resetAllLifts(false, null);
        kickData(true);
        tick(); showWeather(); weatherTick(); applyDim();
    }
    @Override protected void onPause(){ if (editMode) exitEdit(); super.onPause(); focus.save(); h.removeCallbacks(this::tick); h.removeCallbacks(heroRotate); h.removeCallbacks(wxRun); }
    boolean heroShown(){ return heroG!=null && heroG.getVisibility()==View.VISIBLE; }
    View tabV(){ return focus!=null?focus.tabV():(tab==0?tabPlay:tabLive); }
    String name(View v){ return focus!=null?focus.name(v):"?"; }
    @Override protected void onNewIntent(Intent i){ super.onNewIntent(i); focus.restoreNow(); }
    boolean editLp=false;
    @Override public void onWindowFocusChanged(boolean has){ super.onWindowFocusChanged(has); if (!has && dimSlide) closeDimSlide(); }
    @Override public boolean dispatchKeyEvent(KeyEvent e){
        if (dimSlide) return dimSlideKey(e);
        if (appMenu!=null) return menuKey(e);
        // v17: in edit mode a long-press on a home row tile does nothing (no drag, no click on release)
        if (editMode && dragIdx<0 && homeGrid!=null && (e.getKeyCode()==KeyEvent.KEYCODE_DPAD_CENTER||e.getKeyCode()==KeyEvent.KEYCODE_ENTER||e.getKeyCode()==KeyEvent.KEYCODE_NUMPAD_ENTER)){
            View f=getCurrentFocus();
            if (f!=null && f.getParent()==homeGrid){
                if (e.getAction()==KeyEvent.ACTION_DOWN && (e.getRepeatCount()>0 || e.isLongPress())){ editLp=true; return true; }
                if (e.getAction()==KeyEvent.ACTION_UP && editLp){ editLp=false; f.setPressed(false); f.cancelLongPress(); android.util.Log.i("ShieldHome","edit long-press ignored"); return true; }
                if (e.getAction()==KeyEvent.ACTION_DOWN) editLp=false;
            }
        }
        if (focus!=null && focus.dispatch(e)) return true;
        return super.dispatchKeyEvent(e);
    }
    @Override public void onBackPressed(){ back(); }
    void back(){
        if (focus!=null && focus.closeOverlay()) return;
        View f=getCurrentFocus();
        if (f!=null && cardRow!=null && f.getParent()==cardRow){
            int i=cardRow.indexOfChild(f);
            if (i>0 || (cardScroll!=null && cardScroll.getScrollX()>0)){ resetCardRow(); android.util.Log.i("ShieldHome","back -> card0"); return; }
        }
        focusHomeApp();
    }
    void focusHomeApp(){
        if (homeGrid==null) return;
        if (drawerOpen) setDrawer(false);
        if (focus!=null) focus.lastIdx[4]=0;
        for (int i=0;i<homeGrid.getChildCount();i++){
            View c=homeGrid.getChildAt(i);
            if (c!=null && c.isFocusable() && c.getVisibility()==View.VISIBLE){ c.requestFocus(); android.util.Log.i("ShieldHome","back -> "+name(c)); return; }
        }
    }
    @Override public boolean onKeyDown(int k, KeyEvent e){ if (focus.keyDown(k,e)) return true; return super.onKeyDown(k,e); }
    @Override public boolean onKeyLongPress(int k, KeyEvent e){ if (focus.keyLong(k,e)) return true; return super.onKeyLongPress(k,e); }
    @Override public boolean onKeyUp(int k, KeyEvent e){ if (focus.keyUp(k,e)) return true; return super.onKeyUp(k,e); }
    void openAssistant(){
        Intent[] tries = {
            new Intent(Intent.ACTION_ASSIST).setPackage("com.google.android.katniss"),
            new Intent("android.speech.action.WEB_SEARCH").setPackage("com.google.android.katniss"),
            new Intent(Intent.ACTION_SEARCH_LONG_PRESS).setPackage("com.google.android.katniss")
        };
        for (Intent i: tries){ try { if (i.resolveActivity(pm)!=null){ startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return; } } catch (Exception e) {} }
    }
    boolean searchOpen, searchTyping;
    void expandSearch(){
        if (searchOpen || dimSlide || searchTyping || searchSplit==null) return;
        searchOpen=true;
        searchV.animate().cancel();
        searchV.setFocusable(false); searchV.setAlpha(1f); searchV.setVisibility(View.INVISIBLE);
        searchSplit.setVisibility(View.VISIBLE); searchSplit.setAlpha(1f);
        speakV.setFocusable(true); typeV.setFocusable(true);
        float mid=p(28);
        android.view.animation.Interpolator ip=new android.view.animation.OvershootInterpolator(1.2f);
        for (PillView pv: new PillView[]{speakV,typeV}){ pv.animate().cancel(); pv.setScaleX(0.86f); pv.setScaleY(0.86f); pv.setAlpha(0.35f); }
        speakV.setTranslationX(mid); typeV.setTranslationX(-mid);
        speakV.animate().translationX(0).scaleX(1f).scaleY(1f).alpha(1f).setDuration(300).setInterpolator(ip).start();
        typeV.animate().translationX(0).scaleX(1f).scaleY(1f).alpha(1f).setDuration(300).setInterpolator(ip).start();
        speakV.requestFocus();
    }
    void maybeCollapseSearch(){
        if (searchTyping || !searchOpen || dimSlide) return;
        if (speakV!=null && (speakV.isFocused()||typeV.isFocused())) return;
        collapseSearch();
    }
    void showKb(){
        android.view.inputmethod.InputMethodManager imm=(android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
        if (imm==null || searchField==null) return;
        searchField.post(()->{
            searchField.requestFocus();
            if (!imm.showSoftInput(searchField, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT))
                imm.showSoftInput(searchField, android.view.inputmethod.InputMethodManager.SHOW_FORCED);
        });
    }
    void hideKb(){
        android.view.inputmethod.InputMethodManager imm=(android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
        if (imm==null || searchField==null) return;
        imm.hideSoftInputFromWindow(searchField.getWindowToken(), 0);
    }
    void launchSearch(String q){
        Intent[] tries = {
            new Intent("android.search.action.GLOBAL_SEARCH").setPackage("com.google.android.katniss").putExtra(android.app.SearchManager.QUERY, q),
            new Intent("android.speech.action.WEB_SEARCH").setPackage("com.google.android.katniss").putExtra(android.app.SearchManager.QUERY, q),
            new Intent(Intent.ACTION_VIEW, Uri.parse("https://assistant.google.com/search?q="+Uri.encode(q))).setPackage("com.google.android.katniss")
        };
        for (Intent i: tries){ try { if (i.resolveActivity(pm)!=null){ startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); closeSearchField(); return; } } catch (Exception e) {} }
    }
    void openSearchField(){
        if (searchTyping || dimSlide || searchField==null) return;
        searchTyping=true; searchOpen=false;
        if (speakV!=null){ speakV.animate().cancel(); typeV.animate().cancel(); speakV.setFocusable(false); typeV.setFocusable(false); }
        if (searchSplit!=null) searchSplit.setVisibility(View.GONE);
        if (searchV!=null){ searchV.setVisibility(View.INVISIBLE); searchV.setFocusable(false); }
        searchField.setText(""); searchField.setVisibility(View.VISIBLE); searchField.bringToFront();
        searchField.requestFocus(); showKb();
    }
    void closeSearchField(){
        if (!searchTyping) return;
        searchTyping=false; hideKb();
        if (searchField!=null){ searchField.setVisibility(View.GONE); searchField.setText(""); }
        if (!dimSlide && searchV!=null){ searchV.setVisibility(View.VISIBLE); searchV.setFocusable(!drawerOpen); }
    }
    void collapseSearch(){
        searchOpen=false;
        if (speakV!=null){ speakV.setFocusable(false); typeV.setFocusable(false); }
        if (searchSplit==null || searchSplit.getVisibility()!=View.VISIBLE){
            if (!dimSlide && searchV!=null){ searchV.setVisibility(View.VISIBLE); searchV.setAlpha(1f); searchV.setFocusable(!drawerOpen); }
            return;
        }
        float mid=p(28);
        android.view.animation.Interpolator ip=new android.view.animation.DecelerateInterpolator();
        speakV.animate().cancel(); typeV.animate().cancel();
        speakV.animate().translationX(mid).scaleX(0.86f).scaleY(0.86f).alpha(0f).setDuration(280).setInterpolator(ip).start();
        typeV.animate().translationX(-mid).scaleX(0.86f).scaleY(0.86f).alpha(0f).setDuration(280).setInterpolator(ip).withEndAction(()->{
            if (searchOpen) return;
            searchSplit.setVisibility(View.GONE);
            if (!dimSlide && searchV!=null){
                searchV.animate().cancel();
                searchV.setVisibility(View.VISIBLE); searchV.setFocusable(!drawerOpen);
                searchV.setAlpha(0f); searchV.animate().alpha(1f).setDuration(160).start();
            }
        }).start();
    }

    void tick(){ setPill(timePill, new SimpleDateFormat("h:mm a", Locale.US).format(new Date())); h.postDelayed(this::tick, 15000); }
    int pillTextW(PillView v, String s){
        boolean cta=v==heroBtn; Paint pt=new Paint(); pt.setTextSize(p(cta?14:12)); pt.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        float extra=cta?p(39):p(24);
        if (cta && ("Play Now".equals(s) || "More Info".equals(s))) extra += (-pt.getFontMetrics().ascent)*0.78f + p(10);
        return Math.max(p(cta?34:28), Math.round(pt.measureText(s)+extra));
    }
    void setPill(PillView v, String s){ if (v==null) return; v.label=s; ViewGroup.LayoutParams lp=v.getLayoutParams(); if (lp!=null){ lp.width=pillTextW(v,s); if (v==heroBtn) lp.height=p(34); v.setLayoutParams(lp); } v.invalidate(); if (v==heroBtn) placeDots(); }
    void placeDots(){
        if (heroDots==null) return;
        int dw=heroDots.getWidth(), sw=heroG!=null?heroG.getWidth():(root!=null?root.getWidth():0);
        if (dw<=0 || sw<=0) return;
        heroDots.setTranslationX((sw-dw)/2f);
    }

    void place(View v, float x, float y, float w, float hh){ FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w<0?-2:p(w), hh<0?-2:p(hh)); lp.leftMargin=p(x); lp.topMargin=p(y); root.addView(v, lp); }
    TextView text(String s, float size, int color, String font){ TextView t=new TextView(this); t.setText(s); ts(t,size); t.setTextColor(color); t.setTypeface(Typeface.create(font, Typeface.NORMAL)); t.setIncludeFontPadding(false); return t; }

    void build(){
        root = new FrameLayout(this);
        root.setBackgroundResource(R.drawable.bg);
        root.setClipChildren(false);

        wxPill = new PillView(this, "", 4, false); wxPill.setFocusable(false); wxPill.setClickable(false);
        timePill = new PillView(this, "", 4, false); timePill.setFocusable(false); timePill.setClickable(false);
        LinearLayout tr = new LinearLayout(this); tr.setGravity(Gravity.CENTER_VERTICAL);
        tr.addView(wxPill, new LinearLayout.LayoutParams(p(28), p(28)));
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(p(8), p(28));
        tr.addView(new View(this), gap);
        tr.addView(timePill, new LinearLayout.LayoutParams(p(88), p(28)));
        clk = tr;
        root.addView(clk, lp(-2,-2,Gravity.TOP|Gravity.END,0,p(17),p(46),0));

    class PillView extends View {
        String label; int icon; boolean on; Drawable asst; Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);
        PillView(Context c, String l, int ic, boolean o){ super(c); label=l; icon=ic; on=o; setFocusable(true); setClickable(true); }
        void setOn(boolean o){ on=o; invalidate(); }
        @Override protected void onFocusChanged(boolean f, int d, Rect r){ super.onFocusChanged(f,d,r); if (this==heroBtn) return; animate().scaleX(f?1.07f:1f).scaleY(f?1.07f:1f).setDuration(160).setInterpolator(new android.view.animation.DecelerateInterpolator()).start(); setElevation(f?p(4):0); invalidate(); }
        @Override protected void onDraw(Canvas c){
            float w=getWidth(), hh=getHeight(), r=this==heroBtn?p(14):hh/2;
            boolean lit = on || (icon==2 && isFocused());
            pt.setStyle(Paint.Style.FILL);
            boolean fo = isFocused();
            boolean bare = this==gear && !fo;
            if (!bare){
                if (icon==2) pt.setColor(fo?Color.WHITE:Color.rgb(225,224,228));
                else if (on) pt.setColor(fo?Color.WHITE:Color.rgb(229,227,228));
                else if (icon==5) pt.setColor(fo?Color.argb(155,170,170,191):Color.argb(95,162,162,183));
                else pt.setColor(fo?Color.argb(185,102,100,119):Color.argb(140,60,58,75));
                c.drawRoundRect(0,0,w,hh,r,r,pt);
                if (icon==5 && !on){ pt.setShader(new LinearGradient(0,0,0,hh,Color.argb(70,255,255,255),Color.argb(0,255,255,255),Shader.TileMode.CLAMP)); c.drawRoundRect(0,0,w,hh,r,r,pt); pt.setShader(null); }
                if (!on && icon!=2){ pt.setStyle(Paint.Style.STROKE); pt.setStrokeWidth(1); pt.setColor(Color.argb(40,255,255,255)); c.drawRoundRect(.5f,.5f,w-.5f,hh-.5f,r,r,pt); }
            }
            int fg = (icon==2||on) ? Color.rgb(22,22,30) : Color.rgb(232,232,240);
            if (this==dimBar){
                pt.setStyle(Paint.Style.FILL); pt.setTextSize(p(12)); pt.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                float labW=pt.measureText("90%"), pad=p(12), gap=p(8), th=p(6), tw=w-pad*2-gap-labW, ty=(hh-th)/2, rr=th/2, t=Math.max(0, Math.min(1, dimFill/0.9f));
                pt.setColor(Color.argb(50, Color.red(fg), Color.green(fg), Color.blue(fg)));
                c.drawRoundRect(pad, ty, pad+tw, ty+th, rr, rr, pt);
                if (t>0){ pt.setColor(fg); c.drawRoundRect(pad, ty, pad+Math.max(t*tw, th), ty+th, rr, rr, pt); }
                pt.setColor(fg); pt.setTextAlign(Paint.Align.LEFT);
                Paint.FontMetrics fm=pt.getFontMetrics();
                c.drawText(dimPct()+"%", pad+tw+gap, hh/2f-(fm.ascent+fm.descent)/2, pt);
                return;
            }
            pt.setColor(fg); pt.setStrokeWidth(p(1.1f)); pt.setStyle(Paint.Style.STROKE); pt.setStrokeCap(Paint.Cap.ROUND);
            float cx = icon==3 ? p(19) : p(icon==2?18:28) - (icon==2?p(4):0), cy = hh/2;
            if (icon==3){ // magnifier + mic
                c.drawCircle(cx-p(1),cy-p(1),p(4.2f),pt); c.drawLine(cx+p(2),cy+p(2),cx+p(5),cy+p(5),pt);
                if (asst==null) asst = getContext().getDrawable(R.drawable.assistant);
                int ax=(int)(cx+p(8)); asst.setBounds(ax, (int)(cy-p(7.5f)), ax+p(15), (int)(cy+p(7.5f))); asst.draw(c); }
            else if (icon==5){ float gx=w/2f, sr=p(4.0f);
                pt.setStrokeWidth(p(1.5f));
                c.drawCircle(gx, cy, sr, pt);
                pt.setStrokeWidth(p(1.3f));
                for (int i=0;i<8;i++){ double a=Math.toRadians(i*45); float s=sr+p(1.5f), e=sr+p(3.3f); c.drawLine(gx+(float)Math.cos(a)*s, cy+(float)Math.sin(a)*s, gx+(float)Math.cos(a)*e, cy+(float)Math.sin(a)*e, pt); }
                if (dimFill>0.01f){ c.save(); c.clipRect(gx+sr-2*sr*dimFill, cy-sr-1, gx+sr+1, cy+sr+1); pt.setStyle(Paint.Style.FILL); c.drawCircle(gx, cy, sr-p(0.3f), pt); pt.setStyle(Paint.Style.STROKE); c.restore(); } }
            else if (icon==6){
                if (asst==null) asst = getContext().getDrawable(R.drawable.assistant);
                int ax=(int)(w/2f-p(7.5f)); asst.setBounds(ax, (int)(cy-p(7.5f)), ax+p(15), (int)(cy+p(7.5f))); asst.draw(c); }
            else if (icon==7){
                float kx=w/2f, l=kx-p(8), rr=kx+p(8), t=cy-p(5.2f), b=cy+p(5.2f);
                pt.setStyle(Paint.Style.STROKE); pt.setStrokeWidth(p(1.1f)); pt.setStrokeJoin(Paint.Join.ROUND);
                c.drawRoundRect(l, t, rr, b, p(2.2f), p(2.2f), pt);
                pt.setStyle(Paint.Style.FILL);
                float inset=p(2.4f), kl=l+inset, kr=rr-inset, kt=t+inset, gapx=(kr-kl)/4f, gapy=p(2.35f), kd=p(0.85f);
                for (int row=0; row<2; row++) for (int col=0; col<5; col++) c.drawCircle(kl+col*gapx, kt+row*gapy, kd, pt);
                float by=kt+2*gapy, sh=p(1.15f);
                c.drawCircle(kl, by, kd, pt); c.drawCircle(kr, by, kd, pt);
                c.drawRoundRect(kl+gapx, by-sh/2, kr-gapx, by+sh/2, p(0.6f), p(0.6f), pt);
            }
            else if (icon!=4) { pt.setStyle(Paint.Style.FILL); Path tr=new Path(); tr.moveTo(cx-p(3),cy-p(5)); tr.lineTo(cx+p(5),cy); tr.lineTo(cx-p(3),cy+p(5)); tr.close(); c.drawPath(tr,pt); }
            pt.setStyle(Paint.Style.FILL); pt.setTextSize(p(this==heroBtn?14:icon==2?12.5f:12)); pt.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            Paint.FontMetrics fm = pt.getFontMetrics();
            if (this==heroBtn && ("Play Now".equals(label) || "More Info".equals(label))){
                float tw=pt.measureText(label), cap=-fm.ascent, th=cap, twt=cap*0.78f, gap=p(10), x0=(w-twt-gap-tw)/2;
                if ("Play Now".equals(label)){
                    pt.setPathEffect(new CornerPathEffect(Math.max(1, cap*0.12f)));
                    Path tr=new Path(); tr.moveTo(x0, cy-th/2); tr.lineTo(x0+twt, cy); tr.lineTo(x0, cy+th/2); tr.close();
                    c.drawPath(tr, pt); pt.setPathEffect(null);
                } else {
                    float icx=x0+twt/2;
                    pt.setStyle(Paint.Style.STROKE); pt.setStrokeWidth(Math.max(1, th*0.12f));
                    c.drawCircle(icx, cy, th/2, pt);
                    pt.setStyle(Paint.Style.FILL); pt.setTextAlign(Paint.Align.CENTER);
                    pt.setTextSize(th*0.72f);
                    Paint.FontMetrics ifm=pt.getFontMetrics();
                    c.drawText("i", icx, cy-(ifm.ascent+ifm.descent)/2, pt);
                    pt.setTextSize(p(14));
                }
                pt.setTextAlign(Paint.Align.LEFT);
                c.drawText(label, x0+twt+gap, cy-(fm.ascent+fm.descent)/2, pt);
                return;
            }
            if (icon==4) pt.setTextAlign(Paint.Align.CENTER); else pt.setTextAlign(Paint.Align.LEFT);
            if (label.length()>0) c.drawText(label, icon==4? w/2f : p(icon==2?24:icon==3?46:43), cy-(fm.ascent+fm.descent)/2, pt);
        }
    }

    class SegBar extends LinearLayout {
        TextView play, live; float hx, hw, hs=1f; android.animation.ValueAnimator ha, pa;
        SegBar(){
            super(MainActivity.this);
            setOrientation(HORIZONTAL); setGravity(Gravity.CENTER_VERTICAL);
            setPadding(0,0,0,0); setWillNotDraw(false); setClipChildren(false); setClipToPadding(false);
            play=mk("Play Next"); live=mk("Live TV");
            int sw=Math.max(Math.round(play.getPaint().measureText("Play Next")), Math.round(live.getPaint().measureText("Live TV")))+p(36);
            play.setLayoutParams(new LinearLayout.LayoutParams(sw,-1));
            live.setLayoutParams(new LinearLayout.LayoutParams(sw,-1));
            addView(play); addView(live);
            play.setOnFocusChangeListener((v,f)-> tabFocus(0,f));
            live.setOnFocusChangeListener((v,f)-> tabFocus(1,f));
            play.setId(View.generateViewId()); live.setId(View.generateViewId());
            play.setNextFocusRightId(live.getId()); live.setNextFocusLeftId(play.getId());
            play.setNextFocusDownId(View.NO_ID); live.setNextFocusDownId(View.NO_ID);
            play.setNextFocusUpId(View.NO_ID); live.setNextFocusUpId(View.NO_ID);
        }
        TextView mk(String s){
            TextView t=text(s,12,Color.rgb(232,232,240),"sans-serif");
            t.setFocusable(true); t.setClickable(true); t.setGravity(Gravity.CENTER);
            t.setPadding(0,0,0,0); t.setSingleLine(true);
            t.setDefaultFocusHighlightEnabled(false);
            return t;
        }
        void tabFocus(int t, boolean f){
            if (f){ pick(t); lift(true); }
            else post(()->{ if (!play.isFocused() && !live.isFocused()) lift(false); });
        }
        void pick(int t){
            boolean ch=t!=tab;
            if (ch){ tab=t; refreshCards(); resetCardRow(); }
            slide(hw>0); color();
        }
        void color(){
            int d=Color.rgb(22,22,30), l=Color.rgb(232,232,240);
            play.setTextColor(tab==0?d:l); live.setTextColor(tab==1?d:l);
        }
        void lift(boolean on){
            float to=on?1.18f:1f;
            if (on && hs>1.02f) return;
            if (Math.abs(hs-to)<0.005f) return;
            if (pa!=null) pa.cancel();
            pa=android.animation.ValueAnimator.ofFloat(hs, to); pa.setDuration(340);
            pa.setInterpolator(new android.view.animation.OvershootInterpolator(1.1f));
            pa.addUpdateListener(a->{ hs=(Float)a.getAnimatedValue(); invalidate(); });
            pa.start();
        }
        void showPlay(boolean on){ play.setVisibility(on?VISIBLE:GONE); play.setFocusable(on); requestLayout(); }
        void slide(boolean anim){
            TextView s=(tab==0 && play.getVisibility()==VISIBLE)?play:live;
            float nx=s.getLeft(), nw=s.getWidth();
            if (nw<=0){ post(()->slide(false)); return; }
            if (!anim){ hx=nx; hw=nw; invalidate(); return; }
            if (ha!=null) ha.cancel();
            float x0=hx, w0=hw;
            ha=android.animation.ValueAnimator.ofFloat(0,1); ha.setDuration(250);
            ha.setInterpolator(new android.view.animation.PathInterpolator(0.16f,1f,0.3f,1f));
            ha.addUpdateListener(a->{ float u=(Float)a.getAnimatedValue(); hx=x0+(nx-x0)*u; hw=w0+(nw-w0)*u; invalidate(); });
            ha.start();
        }
        @Override protected void onLayout(boolean ch, int l, int t, int r, int b){
            super.onLayout(ch,l,t,r,b);
            if (ha==null || !ha.isRunning()) slide(false);
            color();
        }
        @Override protected void dispatchDraw(Canvas c){
            float w=getWidth(), hh=getHeight(), rr=hh/2;
            Paint pt=new Paint(Paint.ANTI_ALIAS_FLAG);
            pt.setColor(Color.argb(140,60,58,75));
            c.drawRoundRect(0,0,w,hh,rr,rr,pt);
            pt.setStyle(Paint.Style.FILL);
            if (hw>0){
                float cx=hx+hw/2, cy=hh/2, sw=hw*hs, sh=hh*hs, sr=sh/2;
                Bitmap sb=shadowBmp(Math.max(1,(int)sw), Math.max(1,(int)sh), sr);
                if (sb!=null){ Paint sp=new Paint(Paint.ANTI_ALIAS_FLAG); sp.setAlpha(Math.max(0,Math.min(130,(int)(130*(hs-1f)/0.04f)))); int pad=p(48); c.drawBitmap(sb, cx-sw/2-pad, cy-sh/2-pad+p(19), sp); }
                pt.setColor(Color.rgb(229,227,228));
                c.drawRoundRect(cx-sw/2, cy-sh/2, cx+sw/2, cy+sh/2, sr, sr, pt);
            }
            super.dispatchDraw(c);
        }
    }

    class HeroDots extends View {
        int n, cur; float ax; android.animation.ValueAnimator an;
        HeroDots(){ super(MainActivity.this); setAlpha(0f); setVisibility(GONE); setFocusable(false); }
        float slot(int i){ return i*(p(5)+p(7)); }
        void snap(int i){ cur=i; ax=slot(i); invalidate(); }
        int pillW(){ float d=p(5), g=p(7), row=n<=1?d:(n-1)*(d+g)+d; return Math.round(row+p(24)); }
        @Override protected void onMeasure(int wms, int hms){
            int h=MeasureSpec.getMode(hms)==MeasureSpec.EXACTLY?MeasureSpec.getSize(hms):p(28);
            setMeasuredDimension(pillW(), h);
        }
        void bind(int nn, int i, boolean anim){
            int nn2=Math.max(0,nn); i=nn2==0?0:Math.max(0, Math.min(i,nn2-1));
            if (nn2!=n) requestLayout();
            n=nn2;
            boolean wrap=n>2 && ((cur==n-1 && i==0) || (cur==0 && i==n-1));
            if (!anim || getAlpha()<0.05f || n<=1 || wrap){ snap(i); return; }
            if (an!=null) an.cancel();
            float to=slot(i), x0=ax; cur=i;
            an=android.animation.ValueAnimator.ofFloat(0,1); an.setDuration(250);
            an.setInterpolator(new android.view.animation.PathInterpolator(0.16f,1f,0.3f,1f));
            an.addUpdateListener(a->{ float u=(Float)a.getAnimatedValue(); ax=x0+(to-x0)*u; invalidate(); });
            an.start();
        }
        @Override protected void onDraw(Canvas c){
            if (n<=1) return;
            float d=p(5), g=p(7), row=(n-1)*(d+g)+d, hh=getHeight(), rr=hh/2, x0=(getWidth()-row)/2f, cy=hh/2f;
            Paint pt=new Paint(Paint.ANTI_ALIAS_FLAG);
            pt.setColor(Color.argb(88,60,58,75));
            c.drawRoundRect(0,0,getWidth(),hh,rr,rr,pt);
            pt.setColor(Color.argb(89,255,255,255));
            for (int i=0;i<n;i++) c.drawCircle(x0+slot(i)+d/2, cy, d/2, pt);
            pt.setColor(Color.WHITE);
            c.drawCircle(x0+ax+d/2, cy, d/2, pt);
        }
    }

    // ---------- Focus helper: soft blue glow drawn outside the tile + scale ----------
    class GlowFrame extends FrameLayout {
        FrameLayout inner; View badgeV; float radius; Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG); float glow=0, fade=1; Bitmap shBm; int shPad;
        GlowFrame(Context c, FrameLayout in, float r){ super(c); inner=in; radius=r; setClipChildren(false); setClipToPadding(false); setClipToOutline(false); setWillNotDraw(false); setLayerType(View.LAYER_TYPE_NONE, null); forceHasOverlappingRendering(false);
            shPad=p(48);
            addView(in, new FrameLayout.LayoutParams(-1,-1));
            in.setLayerType(View.LAYER_TYPE_HARDWARE, null); }
        void warm(int w, int h){ if (w<=0||h<=0) return; shBm=shadowBmp(w,h,radius); }
        void syncHalo(){ invalidate(); }
        @Override protected void onSizeChanged(int w, int h, int ow, int oh){ super.onSizeChanged(w,h,ow,oh); shBm=null; if (w>0&&h>0) warm(w,h); }
        @Override public void draw(Canvas c){
            float g=glow*fade;
            if (g>0.02f){ if (shBm==null) shBm=shadowBmp(getWidth(),getHeight(),radius); if (shBm!=null){ sp.setAlpha((int)(160*g)); c.drawBitmap(shBm,-shPad,-shPad+p(19),sp); } }
            super.draw(c);
            if (badgeV!=null && badgeV.getAlpha()>0.01f){ c.saveLayerAlpha(badgeV.getLeft(), badgeV.getTop(), badgeV.getRight(), badgeV.getBottom(), Math.round(255*badgeV.getAlpha())); c.translate(badgeV.getLeft(), badgeV.getTop()); badgeV.draw(c); c.restore(); }
        }
    }
    View focusable(final FrameLayout v, final GradientDrawable base, float radius, final Runnable click){ return focusable(v, base, radius, 1.13f, click); }
    View focusable(final FrameLayout v, final GradientDrawable base, float radius, final float sc, final Runnable click){
        final GlowFrame g = new GlowFrame(this, v, p(radius));
        g.setFocusable(true); g.setClickable(true);
        g.setOnFocusChangeListener((x,f)->{
            if (x.getParent()==null) return;
            if (editMode){ if (x==dragV) x.setTranslationZ(p(18)); return; }
            if (x==menuOwner && !f) return;
            if (x==menuPop){ if (f) menuPop=null; return; }
            x.animate().scaleX(f?sc:1f).scaleY(f?sc:1f).translationX(0).translationY(0).rotation(0).setDuration(220).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
            x.setTranslationZ(f?p(12):0);
            android.animation.ValueAnimator va = android.animation.ValueAnimator.ofFloat(g.glow, f?1f:0f); va.setDuration(220);
            va.addUpdateListener(a->{ g.glow=(Float)a.getAnimatedValue(); g.syncHalo(); }); va.start();
        });
        g.setOnClickListener(x->click.run());
        return g;
    }
    final Map<String,Bitmap> glowBmps = new HashMap<>();
    Bitmap shadowBmp(int w, int h, float r){
        String k="sh:"+w+"x"+h+":"+(int)r;
        Bitmap hit=glowBmps.get(k); if (hit!=null && !hit.isRecycled()) return hit;
package com.chris.shieldhome;

import android.content.Intent;
import android.view.KeyEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

/** D-pad sections + one BACK path. ACTION_UP is not swallowed. */
class FocusController {
    final MainActivity a;
    final int[] lastIdx = new int[16];
    int lastCardX;
    int savedSec = -1, savedIdx = 0;
    boolean backLong, pendingRestore;

    FocusController(MainActivity a) { this.a = a; }

    void prepare() {
        pendingRestore = true;
        if (savedSec < 0) {
            savedSec = a.prefs().getInt("f_sec", -1);
            savedIdx = a.prefs().getInt("f_idx", 0);
            int ft = a.prefs().getInt("f_tab", 0);
            if (a.wnPickCache != null && a.wnPickCache.isEmpty()) ft = 1;
            if (ft != a.tab) a.tab = ft;
        }
    }

    void afterData() {
        if (!pendingRestore) return;
        pendingRestore = false;
        restoreNow();
    }

    /** Remember focused section+index before any overlay/transition. */
    void remember() {
        try {
            View f = a.getCurrentFocus();
            if (f == null) return;
            for (View x = f; x != null; x = x.getParent() instanceof View ? (View) x.getParent() : null)
                if (x == a.appMenu) return;
            int sc = secOf(f);
            if (sc < 0) return;
            View root = secRoot(f, sc);
            int idx = sc == 0 ? topIdx(root) : secItems(sc).indexOf(root);
            if (idx < 0) return;
            savedSec = sc;
            savedIdx = idx;
            if (sc == 3 && a.cardScroll != null) lastCardX = a.cardScroll.getScrollX();
        } catch (Exception e) {}
    }

    /** Posted requestFocus after layout. */
    void restore() {
        if (savedSec < 0 || a.appMenu != null) return;
        final View t = savedView();
        if (t == null) return;
        t.setFocusable(true);
        t.post(() -> {
            if (savedSec == 3 && a.cardScroll != null) a.cardScroll.scrollTo(lastCardX, 0);
            t.requestFocus();
            android.util.Log.i("ShieldHome", "focus restored " + name(t));
        });
    }

    void save() {
        remember();
        if (savedSec < 0) return;
        try {
            a.prefs().edit().putInt("f_sec", savedSec).putInt("f_idx", savedIdx).putInt("f_tab", a.tab).apply();
            android.util.Log.i("ShieldHome", "focus saved sec=" + savedSec + " idx=" + savedIdx);
        } catch (Exception e) {}
    }

    void restoreNow() {
        if (savedSec < 0) return;
        if (a.appMenu != null || a.editMode) return;
        if (savedSec >= 5) a.setDrawer(true);
        restore();
    }

    boolean dispatch(KeyEvent e) {
        int k = e.getKeyCode();
        boolean v = k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN;
        boolean hz = k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT;
        if (!(v || hz) || a.homeGrid == null || a.appMenu != null || a.editMode || a.dragIdx >= 0)
            return false;
        if (a.searchTyping && hz) return false;
        View f = a.getCurrentFocus();
        int sec = f == null ? -1 : secOf(f);
        if (sec < 0) return false;
        if (e.getAction() != KeyEvent.ACTION_DOWN) return false; // ponytail: let ACTION_UP through
        if (sec == 1 && hz) { a.heroStep(k == KeyEvent.KEYCODE_DPAD_RIGHT); return true; }
        if (k == KeyEvent.KEYCODE_DPAD_DOWN && sec >= 4 && sec == nSecs() - 1)
            return true;
        View t;
        if (v) t = navTarget(f, sec, k == KeyEvent.KEYCODE_DPAD_UP);
        else {
            List<View> it = secItems(sec);
            int ix = it.indexOf(secRoot(f, sec));
            int ni = ix + (k == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
            t = (ix >= 0 && ni >= 0 && ni < it.size()) ? it.get(ni) : f;
            if (t != f && ni >= 0 && sec >= 0 && sec < lastIdx.length) lastIdx[sec] = ni;
        }
        if (t != null && t != f) {
            if (t == a.heroBtn) { t.setVisibility(View.VISIBLE); t.setFocusable(true); }
            t.requestFocus();
            if (t.getParent() == a.homeGrid){
                int i = a.homeGrid.indexOfChild(t);
                if (a.drawerOpen && i < 6) a.setDrawer(false);
                else a.slideHome();
            }
            android.util.Log.i("ShieldHome", "nav " + name(f) + " -> " + name(t));
        }
        return true;
    }

    /** Overlays close themselves. Returns true if consumed (not the home short/long path). */
    boolean closeOverlay() {
        if (a.appMenu != null) { a.closeMenu(); return true; }
        if (a.searchTyping) { a.closeSearchField(); if (a.searchV!=null) a.searchV.requestFocus(); return true; }
        if (a.dimSlide) { a.closeDimSlide(); return true; }
        if (a.editMode || a.dragIdx >= 0) { a.exitEdit(); return true; }
        if (a.drawerOpen) { a.setDrawer(false); a.focusHomeApp(); return true; }
        return false;
    }

    boolean keyDown(int k, KeyEvent e) {
        if (k != KeyEvent.KEYCODE_BACK) return false;
        if (closeOverlay()) return true;
        if (e.getRepeatCount() == 0) { e.startTracking(); backLong = false; }
        return true;
    }

    boolean keyLong(int k, KeyEvent e) {
        if (k != KeyEvent.KEYCODE_BACK || closeOverlay()) return k == KeyEvent.KEYCODE_BACK;
        backLong = true;
        android.util.Log.i("ShieldHome", "back long -> settings");
        try { a.startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception x) {}
        return true;
    }

    boolean keyUp(int k, KeyEvent e) {
        if (k != KeyEvent.KEYCODE_BACK) return false;
        if (e.isTracking()) {
            if (!backLong && !e.isCanceled() && !closeOverlay()) a.back();
            backLong = false;
            return true;
        }
        if (backLong) { backLong = false; return true; }
        return false;
    }

    List<View> secItems(int sec) {
        List<View> o = new ArrayList<>();
        if (sec == 0) {
            if (a.gear != null) o.add(a.gear);
            if (a.searchOpen){ if (a.speakV!=null) o.add(a.speakV); if (a.typeV!=null) o.add(a.typeV); }
            else if (a.searchTyping && a.searchField!=null) o.add(a.searchField);
            else if (a.searchV != null && a.searchV.getVisibility() == View.VISIBLE) o.add(a.searchV);
        } else if (sec == 1) { if (a.heroShown() && a.heroBtn != null) o.add(a.heroBtn); }
        else if (sec == 2) { if (a.tabPlay.getVisibility()==View.VISIBLE) o.add(a.tabPlay); o.add(a.tabLive); }
        else if (sec == 3) {
            for (int i = 0; i < a.cardRow.getChildCount(); i++) {
                View c = a.cardRow.getChildAt(i);
                if (c.isFocusable() && c.getVisibility() == View.VISIBLE) o.add(c);
            }
        } else {
            int r = sec - 4;
            for (int i = r * 6; i < Math.min(r * 6 + 6, a.homeGrid.getChildCount()); i++) {
                View c = a.homeGrid.getChildAt(i);
                if (c.isFocusable() && c.getVisibility() == View.VISIBLE) o.add(c);
            }
        }
        return o;
    }

    int nSecs() { return 4 + (a.homeGrid.getChildCount() + 5) / 6; }

    View secRoot(View f, int sec) {
        List<View> it = secItems(sec);
        View x = f;
        while (x != null) {
            if (it.contains(x)) return x;
            x = x.getParent() instanceof View ? (View) x.getParent() : null;
        }
        return f;
    }

    int secOf(View f) {
        if (a.homeGrid == null || a.cardRow == null) return -1;
        for (View x = f; x != null; x = x.getParent() instanceof View ? (View) x.getParent() : null) {
            if (x == a.gear || x == a.searchV || x == a.speakV || x == a.typeV || x == a.searchField) return 0;
            if (x == a.heroG) return 1;
            if (x == a.tabPlay || x == a.tabLive) return 2;
            if (x.getParent() == a.cardRow) return 3;
            if (x.getParent() == a.homeGrid) return 4 + a.homeGrid.indexOfChild(x) / 6;
        }
        return -1;
    }

    int topIdx(View v) {
        if (v == a.gear) return 0;
        if (v == a.searchV || v == a.speakV || v == a.searchField) return 1;
        if (v == a.typeV) return 2;
        return -1;
    }
    View savedView() {
        if (savedSec == 0) {
            if (savedIdx == 0) return a.gear;
            return a.searchV;
        }
        List<View> it = secItems(savedSec);
        int s2 = savedSec;
        while (it.isEmpty() && s2 < nSecs() - 1) it = secItems(++s2);
        if (it.isEmpty()) return null;
        return it.get(Math.min(savedIdx, it.size() - 1));
    }
    View tabV() { return a.tab == 0 && a.tabPlay.getVisibility()==View.VISIBLE ? a.tabPlay : a.tabLive; }
    View topTarget() { return a.searchOpen && a.speakV!=null ? a.speakV : (a.searchV!=null && a.searchV.getVisibility()==View.VISIBLE ? a.searchV : a.gear); }

    View navTarget(View f, int sec, boolean up) {
        View fr = secRoot(f, sec);
        List<View> cur = secItems(sec);
        int ix = Math.max(0, cur.indexOf(fr));
        if (sec >= 0 && sec < lastIdx.length) lastIdx[sec] = ix;
        if (sec == 3 && a.cardScroll != null) lastCardX = a.cardScroll.getScrollX();
        boolean multi = sec >= 3;
        for (int s2 = sec + (up ? -1 : 1); s2 >= 0 && s2 < nSecs(); s2 += up ? -1 : 1) {
            List<View> it = secItems(s2);
            if (it.isEmpty()) continue;
            if (s2 == 0) return (sec == 1) ? a.gear : topTarget();
            if (s2 == 2) return tabV();
            int ti = s2 == 3 ? lastIdx[3] : (s2 == 4 && sec == 3) ? 0 : (multi ? ix : (s2 < lastIdx.length ? lastIdx[s2] : 0));
            View dest = it.get(Math.max(0, Math.min(ti, it.size() - 1)));
            if (s2 == 3 && a.cardScroll != null) a.cardScroll.scrollTo(lastCardX, 0);
            if (s2 >= 5) a.setDrawer(true);
            return dest;
        }
        return f;
    }

    String name(View v) {
        if (v == a.gear) return "gear";
        if (v == a.searchV || v == a.searchField) return "search";
        if (v == a.speakV) return "speak";
        if (v == a.typeV) return "type";
        if (v == a.heroG || v == a.heroBtn) return "hero";
        if (v == a.tabPlay) return "tabPlay";
        if (v == a.tabLive) return "tabLive";
        if (v.getParent() == a.cardRow) return "card" + a.cardRow.indexOfChild(v);
        if (v.getParent() == a.homeGrid) return "app" + a.homeGrid.indexOfChild(v);
        return v.getClass().getSimpleName();
    }
}
package com.chris.shieldhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.concurrent.ExecutorService;

/** Sampled decode + LruCache. Detached views are ignored. */
class ImageLoader {
    final Context ctx;
    final ExecutorService pool;
    final Handler h;
    final LruCache<String, Bitmap> cache;

    ImageLoader(Context ctx, ExecutorService pool, Handler h) {
        this.ctx = ctx.getApplicationContext();
        this.pool = pool;
        this.h = h;
        int kb = (int) (Runtime.getRuntime().maxMemory() / 1024 / 8);
        cache = new LruCache<String, Bitmap>(Math.max(1024, kb)) {
            protected int sizeOf(String k, Bitmap b) { return Math.max(1, b.getByteCount() / 1024); }
        };
    }

    void load(ImageView iv, String uri0, boolean fit, int tw, int th) {
        if (uri0 == null || iv == null) return;
        iv.setTag(uri0);
        String k = key(uri0, tw, th);
        Bitmap hit = cache.get(k);
        if (hit != null) { apply(iv, hit, fit); return; }
        pool.execute(() -> {
            Bitmap bm = decode(uri0, tw, th);
            if (bm != null) cache.put(k, bm);
            h.post(() -> {
                if (bm == null || iv.getWindowToken() == null || !uri0.equals(iv.getTag())) return;
                apply(iv, bm, fit);
            });
        });
    }

    static void apply(ImageView iv, Bitmap bm, boolean fit) {
        if (iv.getScaleType() != ImageView.ScaleType.FIT_END)
            iv.setScaleType(fit ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.CENTER_CROP);
        iv.setImageBitmap(bm);
    }

    static String key(String u, int w, int h) { return u + "|" + w + "x" + h; }

    Bitmap decode(String uri0, int tw, int th) {
        boolean crop = uri0.endsWith("#cwcrop");
        String uri = crop ? uri0.substring(0, uri0.length() - 7) : uri0;
        InputStream in = null;
        HttpURLConnection conn = null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            in = open(uri);
            if (in == null) return null;
            BitmapFactory.decodeStream(in, null, bounds);
            close(in, null);
            in = null;
            int ss = 1;
            if (tw > 0 && th > 0 && bounds.outWidth > 0 && bounds.outHeight > 0)
                while (bounds.outWidth / ss / 2 >= tw && bounds.outHeight / ss / 2 >= th) ss *= 2;
            in = open(uri);
            if (in == null) return null;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = ss;
            Bitmap b0 = BitmapFactory.decodeStream(in, null, o);
            if (crop && b0 != null) {
                int nh = Math.max(1, Math.round(b0.getHeight() * 0.80f));
                Bitmap cut = Bitmap.createBitmap(b0, 0, 0, b0.getWidth(), nh);
                if (cut != b0) b0.recycle();
                b0 = cut;
            }
            return b0;
        } catch (Exception e) {
            android.util.Log.w("ShieldHome", "img " + uri + " " + e);
            return null;
        } finally {
            close(in, conn);
        }
    }

    InputStream open(String uri) throws Exception {
        if (uri.startsWith("http")) {
            HttpURLConnection c = (HttpURLConnection) new java.net.URL(uri).openConnection();
            c.setConnectTimeout(5000);
            c.setReadTimeout(8000);
            return c.getInputStream();
        }
        return ctx.getContentResolver().openInputStream(Uri.parse(uri));
    }

    static void close(InputStream in, HttpURLConnection c) {
        try { if (in != null) in.close(); } catch (Exception ignored) {}
        if (c != null) c.disconnect();
    }
}

codex
The main review pattern is clear: drawing paths are mostly lightweight, but some custom views allocate paints/animators during frequent redraw or focus events. I’m checking the surrounding methods and unused members now so the findings distinguish real hot-path costs from one-time setup.
exec
/bin/bash -lc "nl -ba shieldhome/src/com/chris/shieldhome/MainActivity.java | sed -n '535,630p;970,1005p;1120,1180p;1480,1540p;1580,1655p;1688,1715p'; rg -n \"\\b(clk|featuredCache|wnPickCache|plutoNow|plutoAt|base|lastIdx|heroOnScreen|homeBindSig|featuredBoundSig|plutoLogo)\\b\" shieldhome/src/com/chris/shieldhome/*.java" in /tmp/codexreview
 succeeded in 0ms:
   535	            pt.setStyle(Paint.Style.FILL);
   536	            if (hw>0){
   537	                float cx=hx+hw/2, cy=hh/2, sw=hw*hs, sh=hh*hs, sr=sh/2;
   538	                Bitmap sb=shadowBmp(Math.max(1,(int)sw), Math.max(1,(int)sh), sr);
   539	                if (sb!=null){ Paint sp=new Paint(Paint.ANTI_ALIAS_FLAG); sp.setAlpha(Math.max(0,Math.min(130,(int)(130*(hs-1f)/0.04f)))); int pad=p(48); c.drawBitmap(sb, cx-sw/2-pad, cy-sh/2-pad+p(19), sp); }
   540	                pt.setColor(Color.rgb(229,227,228));
   541	                c.drawRoundRect(cx-sw/2, cy-sh/2, cx+sw/2, cy+sh/2, sr, sr, pt);
   542	            }
   543	            super.dispatchDraw(c);
   544	        }
   545	    }
   546	
   547	    class HeroDots extends View {
   548	        int n, cur; float ax; android.animation.ValueAnimator an;
   549	        HeroDots(){ super(MainActivity.this); setAlpha(0f); setVisibility(GONE); setFocusable(false); }
   550	        float slot(int i){ return i*(p(5)+p(7)); }
   551	        void snap(int i){ cur=i; ax=slot(i); invalidate(); }
   552	        int pillW(){ float d=p(5), g=p(7), row=n<=1?d:(n-1)*(d+g)+d; return Math.round(row+p(24)); }
   553	        @Override protected void onMeasure(int wms, int hms){
   554	            int h=MeasureSpec.getMode(hms)==MeasureSpec.EXACTLY?MeasureSpec.getSize(hms):p(28);
   555	            setMeasuredDimension(pillW(), h);
   556	        }
   557	        void bind(int nn, int i, boolean anim){
   558	            int nn2=Math.max(0,nn); i=nn2==0?0:Math.max(0, Math.min(i,nn2-1));
   559	            if (nn2!=n) requestLayout();
   560	            n=nn2;
   561	            boolean wrap=n>2 && ((cur==n-1 && i==0) || (cur==0 && i==n-1));
   562	            if (!anim || getAlpha()<0.05f || n<=1 || wrap){ snap(i); return; }
   563	            if (an!=null) an.cancel();
   564	            float to=slot(i), x0=ax; cur=i;
   565	            an=android.animation.ValueAnimator.ofFloat(0,1); an.setDuration(250);
   566	            an.setInterpolator(new android.view.animation.PathInterpolator(0.16f,1f,0.3f,1f));
   567	            an.addUpdateListener(a->{ float u=(Float)a.getAnimatedValue(); ax=x0+(to-x0)*u; invalidate(); });
   568	            an.start();
   569	        }
   570	        @Override protected void onDraw(Canvas c){
   571	            if (n<=1) return;
   572	            float d=p(5), g=p(7), row=(n-1)*(d+g)+d, hh=getHeight(), rr=hh/2, x0=(getWidth()-row)/2f, cy=hh/2f;
   573	            Paint pt=new Paint(Paint.ANTI_ALIAS_FLAG);
   574	            pt.setColor(Color.argb(88,60,58,75));
   575	            c.drawRoundRect(0,0,getWidth(),hh,rr,rr,pt);
   576	            pt.setColor(Color.argb(89,255,255,255));
   577	            for (int i=0;i<n;i++) c.drawCircle(x0+slot(i)+d/2, cy, d/2, pt);
   578	            pt.setColor(Color.WHITE);
   579	            c.drawCircle(x0+ax+d/2, cy, d/2, pt);
   580	        }
   581	    }
   582	
   583	    // ---------- Focus helper: soft blue glow drawn outside the tile + scale ----------
   584	    class GlowFrame extends FrameLayout {
   585	        FrameLayout inner; View badgeV; float radius; Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG); float glow=0, fade=1; Bitmap shBm; int shPad;
   586	        GlowFrame(Context c, FrameLayout in, float r){ super(c); inner=in; radius=r; setClipChildren(false); setClipToPadding(false); setClipToOutline(false); setWillNotDraw(false); setLayerType(View.LAYER_TYPE_NONE, null); forceHasOverlappingRendering(false);
   587	            shPad=p(48);
   588	            addView(in, new FrameLayout.LayoutParams(-1,-1));
   589	            in.setLayerType(View.LAYER_TYPE_HARDWARE, null); }
   590	        void warm(int w, int h){ if (w<=0||h<=0) return; shBm=shadowBmp(w,h,radius); }
   591	        void syncHalo(){ invalidate(); }
   592	        @Override protected void onSizeChanged(int w, int h, int ow, int oh){ super.onSizeChanged(w,h,ow,oh); shBm=null; if (w>0&&h>0) warm(w,h); }
   593	        @Override public void draw(Canvas c){
   594	            float g=glow*fade;
   595	            if (g>0.02f){ if (shBm==null) shBm=shadowBmp(getWidth(),getHeight(),radius); if (shBm!=null){ sp.setAlpha((int)(160*g)); c.drawBitmap(shBm,-shPad,-shPad+p(19),sp); } }
   596	            super.draw(c);
   597	            if (badgeV!=null && badgeV.getAlpha()>0.01f){ c.saveLayerAlpha(badgeV.getLeft(), badgeV.getTop(), badgeV.getRight(), badgeV.getBottom(), Math.round(255*badgeV.getAlpha())); c.translate(badgeV.getLeft(), badgeV.getTop()); badgeV.draw(c); c.restore(); }
   598	        }
   599	    }
   600	    View focusable(final FrameLayout v, final GradientDrawable base, float radius, final Runnable click){ return focusable(v, base, radius, 1.13f, click); }
   601	    View focusable(final FrameLayout v, final GradientDrawable base, float radius, final float sc, final Runnable click){
   602	        final GlowFrame g = new GlowFrame(this, v, p(radius));
   603	        g.setFocusable(true); g.setClickable(true);
   604	        g.setOnFocusChangeListener((x,f)->{
   605	            if (x.getParent()==null) return;
   606	            if (editMode){ if (x==dragV) x.setTranslationZ(p(18)); return; }
   607	            if (x==menuOwner && !f) return;
   608	            if (x==menuPop){ if (f) menuPop=null; return; }
   609	            x.animate().scaleX(f?sc:1f).scaleY(f?sc:1f).translationX(0).translationY(0).rotation(0).setDuration(220).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
   610	            x.setTranslationZ(f?p(12):0);
   611	            android.animation.ValueAnimator va = android.animation.ValueAnimator.ofFloat(g.glow, f?1f:0f); va.setDuration(220);
   612	            va.addUpdateListener(a->{ g.glow=(Float)a.getAnimatedValue(); g.syncHalo(); }); va.start();
   613	        });
   614	        g.setOnClickListener(x->click.run());
   615	        return g;
   616	    }
   617	    final Map<String,Bitmap> glowBmps = new HashMap<>();
   618	    Bitmap shadowBmp(int w, int h, float r){
   619	        String k="sh:"+w+"x"+h+":"+(int)r;
   620	        Bitmap hit=glowBmps.get(k); if (hit!=null && !hit.isRecycled()) return hit;
   621	        int pad=p(48);
   622	        Bitmap bm=Bitmap.createBitmap(Math.max(1,w+pad*2), Math.max(1,h+pad*2), Bitmap.Config.ARGB_8888);
   623	        Canvas c=new Canvas(bm);
   624	        Paint bl=new Paint(Paint.ANTI_ALIAS_FLAG);
   625	        bl.setColor(Color.BLACK); bl.setMaskFilter(new BlurMaskFilter(p(32), BlurMaskFilter.Blur.NORMAL));
   626	        c.drawRoundRect(pad, pad, pad+w, pad+h, r, r, bl);
   627	        glowBmps.put(k, bm); return bm;
   628	    }
   629	    View appTile(final String pkg, String cls, String label){
   630	        FrameLayout f = new FrameLayout(this); f.setClipToOutline(true);
   970	        pressHeld=false;
   971	        if (v==null) return;
   972	        menuPop=v;
   973	        tileSpring(v, APP_HOLD, 1f);
   974	    }
   975	    void pressSettle(View v){
   976	        if (v==null) return;
   977	        menuPop=v;
   978	        tileSpring(v, APP_FOCUS, 1f);
   979	    }
   980	    void tileSpring(View v, float sc, float glowTo){
   981	        android.view.animation.Interpolator ip=new android.view.animation.OvershootInterpolator(1.3f);
   982	        v.animate().cancel();
   983	        v.setTranslationZ(sc>1f?p(8):0);
   984	        v.animate().scaleX(sc).scaleY(sc).setDuration(420).setInterpolator(ip).start();
   985	        if (v instanceof GlowFrame){
   986	            GlowFrame g=(GlowFrame)v;
   987	            android.animation.ValueAnimator va=android.animation.ValueAnimator.ofFloat(g.glow, glowTo);
   988	            va.setDuration(420); va.setInterpolator(ip);
   989	            va.addUpdateListener(a->{ g.glow=Math.max(0f, Math.min(1f,(Float)a.getAnimatedValue())); g.syncHalo(); });
   990	            va.start();
   991	        }
   992	    }
   993	    void appMenu(View anchor, String[] a, int idx){
   994	        if (editMode || appMenu!=null) return;
   995	        if (focus!=null) focus.remember();
   996	        menuOwner=anchor;
   997	        pressPop(anchor);
   998	        LinearLayout p=new LinearLayout(this); p.setOrientation(LinearLayout.VERTICAL);
   999	        GradientDrawable bg=new GradientDrawable(); bg.setColor(Color.argb(235,28,28,32)); bg.setCornerRadius(p(14)); bg.setStroke(Math.max(1,p(1)), Color.argb(51,255,255,255));
  1000	        p.setBackground(bg); p.setPadding(p(8),p(8),p(8),p(8)); p.setElevation(p(18));
  1001	        String[] labs={"Edit Home Screen","Force Stop","App Info","Uninstall"};
  1002	        boolean sys=systemApp(a[0]);
  1003	        View first=null;
  1004	        java.util.ArrayList<View> rows=new java.util.ArrayList<>();
  1005	        for (int n=0;n<labs.length;n++){
  1120	        lockBehind(false);
  1121	        View owner=menuOwner; menuOwner=null;
  1122	        final View m=appMenu; appMenu=null;
  1123	        m.animate().cancel();
  1124	        m.animate().alpha(0f).scaleX(0.3f).scaleY(0.3f).setDuration(140).setInterpolator(new android.view.animation.AccelerateInterpolator()).withEndAction(()->{ if (m.getParent()!=null) root.removeView(m); }).start();
  1125	        if (owner!=null) pressSettle(owner);
  1126	        if (back && focus!=null) focus.restore();
  1127	    }
  1128	    void appInfo(String pkg){ try { startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:"+pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception e){} }
  1129	    Bitmap vigMask; final Map<String,Bitmap> heroMasked=new HashMap<>();
  1130	    Bitmap alphaMask(int w, int h){
  1131	        if (vigMask!=null && vigMask.getWidth()==w && vigMask.getHeight()==h) return vigMask;
  1132	        Bitmap bm=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
  1133	        Canvas c=new Canvas(bm);
  1134	        c.drawColor(Color.WHITE);
  1135	        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.DITHER_FLAG); p.setDither(true);
  1136	        p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
  1137	        float artW=Math.min(w,p(758)), artH=Math.min(h,p(360)), artL=Math.max(0,w-artW), artB=artH, leftEnd=artL+artW*HERO_LEFT_FADE, botEnd=artB-artH*0.40f, botRange=h-botEnd;
  1138	        p.setShader(new LinearGradient(0,0,leftEnd,0, new int[]{Color.TRANSPARENT,Color.TRANSPARENT,Color.WHITE}, new float[]{0, leftEnd<=0?0:artL/leftEnd, 1}, Shader.TileMode.CLAMP));
  1139	        c.drawRect(0,0,w,h,p);
  1140	        p.setShader(new LinearGradient(0,h,0,botEnd, new int[]{Color.TRANSPARENT,Color.TRANSPARENT,Color.WHITE}, new float[]{0, botRange<=0?0:(h-artB)/botRange, 1}, Shader.TileMode.CLAMP));
  1141	        c.drawRect(0,0,w,h,p);
  1142	        p.setShader(new LinearGradient(0,0,0,h*0.064f, Color.argb(240,255,255,255), Color.WHITE, Shader.TileMode.CLAMP));
  1143	        c.drawRect(0,0,w,h,p);
  1144	        float rad=artH*0.37f;
  1145	        p.setShader(new RadialGradient(artL, artB, rad, new int[]{Color.TRANSPARENT, Color.argb(50,255,255,255), Color.WHITE}, new float[]{0, 0.5f, 1}, Shader.TileMode.CLAMP));
  1146	        c.drawRect(artL, artB-rad, Math.min(w, artL+rad), artB, p);
  1147	        vigMask=bm; return bm;
  1148	    }
  1149	    Bitmap maskHero(String key, Bitmap src, int tw, int th){
  1150	        if (tw<=0||th<=0||src==null) return src;
  1151	        Bitmap hit=heroMasked.get(key);
  1152	        if (hit!=null && !hit.isRecycled() && hit.getWidth()==tw && hit.getHeight()==th) return hit;
  1153	        Bitmap out=Bitmap.createBitmap(tw,th,Bitmap.Config.ARGB_8888);
  1154	        Canvas c=new Canvas(out);
  1155	        float artW=Math.min(tw,p(758)), artH=Math.min(th,p(360)), artL=tw-artW;
  1156	        float s=Math.max(artW/src.getWidth(), artH/src.getHeight());
  1157	        float dw=src.getWidth()*s, dh=src.getHeight()*s, cx=artL+artW/2f, cy=artH/2f;
  1158	        c.drawBitmap(src, null, new RectF(cx-dw/2,cy-dh/2,cx+dw/2,cy+dh/2), null);
  1159	        Paint xp=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.DITHER_FLAG); xp.setDither(true);
  1160	        xp.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
  1161	        c.drawBitmap(alphaMask(tw,th),0,0,xp);
  1162	        float leftEnd=artL+artW*HERO_LEFT_FADE;
  1163	        xp.setXfermode(null);
  1164	        xp.setShader(new LinearGradient(0,0,leftEnd,0, new int[]{0xE6000000|HERO_SCRIM_RGB,0xC2000000|HERO_SCRIM_RGB,HERO_SCRIM_RGB}, new float[]{0, leftEnd<=0?0:artL/leftEnd, 1}, Shader.TileMode.CLAMP));
  1165	        c.drawRect(0,0,leftEnd,th,xp);
  1166	        heroMasked.put(key, out); return out;
  1167	    }
  1168	    // ---------- Featured hero ----------
  1169	    ImageView heroArt, heroBadge; TextView heroTitle, heroSub; LinearLayout heroMeta; List<Object[]> heroItems = new ArrayList<>(); int heroIdx=0;
  1170	    void fitHeroMeta(){
  1171	        if (heroTitle==null) return;
  1172	        ts(heroTitle, 23); ts(heroSub, 14);
  1173	        if (heroTitle.getPaint().measureText(heroTitle.getText().toString())>p(360)) ts(heroTitle, 17);
  1174	    }
  1175	    void bindHeroCopy(Object[] o){
  1176	        String raw=o[2]==null?"":(String)o[2];
  1177	        String tag=o.length>5?(String)o[5]:"";
  1178	        String meta="", desc=raw;
  1179	        int nl=raw.indexOf('\n');
  1180	        if ("NEW".equals(tag) && nl>=0){ meta=raw.substring(0,nl).trim(); desc=raw.substring(nl+1); }
  1480	        int n = tab==0 ? loadWatchNext() : loadLive();
  1481	        if (n>0){ h.post(this::updateCardAlphas); return; }
  1482	        if (tab==0){ applyTabs(); if (tab==1){ loadLive(); h.post(this::updateCardAlphas); } return; }
  1483	        int i=0;
  1484	        for (final String pk : (tab==0?PLAY_PKGS:LIVE_PKGS)){
  1485	            if (!installed(pk)) continue;
  1486	            Drawable art=null; try { art = pm.getApplicationBanner(pk); if (art==null){ Intent li=pm.getLeanbackLaunchIntentForPackage(pk); if(li!=null) art=pm.getActivityBanner(li);} if(art==null) art=pm.getApplicationIcon(pk);} catch(Exception e){}
  1487	            String cap = tab==1 && pk.equals("com.google.android.tv") ? "Free channels" : null;
  1488	            View v=card(art, null, false, null, cap, -1, ()->launchPkg(pk)); addCard(v, i++);
  1489	        }
  1490	        h.post(this::updateCardAlphas);
  1491	    }
  1492	
  1493	    int loadWatchNext(){
  1494	        List<Object[]> pick=wnPickCache;
  1495	        if (pick==null||pick.isEmpty()) return 0;
  1496	        int n=0;
  1497	        for (Object[] o: pick){ final Intent fi=new Intent((Intent)o[3]).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); final String pk2=(String)o[0]; boolean isNew=o.length>5;
  1498	            View v = card(null,(String)o[2],false,pk2,(String)o[1], isNew?-1f:(Float)o[4], ()->{try{startActivity(fi);}catch(Exception e){ launchPkg(pk2);} });
  1499	            addCard(v, n++); }
  1500	        android.util.Log.i("ShieldHome","playnext shown="+n);
  1501	        return n;
  1502	    }
  1503	    List<Object[]> plutoCaps = new ArrayList<>(); Map<String,String> plutoNow = new HashMap<>(); Map<String,String> plutoArt = new HashMap<>(); Map<String,String> plutoLogo = new HashMap<>(); final Map<String,String> plutoSlug = new java.util.concurrent.ConcurrentHashMap<>(); long plutoAt=0;
  1504	    List<View> liveCache; final Set<String> liveSlugs = new LinkedHashSet<>();
  1505	    // Pluto doesn't publish programs to TvContract (0 readable rows), so read what's on now from Pluto's public guide.
  1506	    void fillPlutoNow(final List<Object[]> caps){
  1507	        if (System.currentTimeMillis()-plutoAt < 10*60000L && !plutoNow.isEmpty()){ applyLiveNow(); return; }
  1508	        pool.execute(()->{ try {
  1509	            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US); f.setTimeZone(TimeZone.getTimeZone("UTC"));
  1510	            long now = System.currentTimeMillis();
  1511	            java.net.HttpURLConnection c=(java.net.HttpURLConnection)new java.net.URL("https://api.pluto.tv/v2/channels?start="+f.format(new Date(now))+"&stop="+f.format(new Date(now+60000))).openConnection();
  1512	            c.setConnectTimeout(6000); c.setReadTimeout(20000);
  1513	            java.io.InputStream in = c.getInputStream(); java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream(); byte[] buf=new byte[65536]; int r; while ((r=in.read(buf))>0) bo.write(buf,0,r); in.close();
  1514	            org.json.JSONArray a = new org.json.JSONArray(bo.toString("UTF-8")); final Map<String,String> m = new HashMap<>(), art = new HashMap<>(), logos = new HashMap<>();
  1515	            Set<String> ban = new HashSet<>(); Map<String,Integer> shared = new HashMap<>();
  1516	            for (int i=0;i<a.length();i++){ org.json.JSONObject ch=a.getJSONObject(i);
  1517	                addBan(ban, ch.optJSONObject("featuredImage")); addBan(ban, ch.optJSONObject("logo")); addBan(ban, ch.optJSONObject("thumbnail")); addBan(ban, ch.optJSONObject("tile"));
  1518	                String logo=jsonPath(ch.optJSONObject("colorLogoPNG")); if (logo==null) logo=jsonPath(ch.optJSONObject("logo"));
  1519	                if (logo!=null) logos.put(ch.optString("name"), logo);
  1520	                org.json.JSONArray tl=ch.optJSONArray("timelines");
  1521	                if (tl!=null && tl.length()>0){ String first=plutoGuideArt(tl.getJSONObject(0), ban); if (first!=null){ String b=bareUrl(first); shared.put(b, (shared.containsKey(b)?shared.get(b):0)+1); } }
  1522	            }
  1523	            for (Map.Entry<String,Integer> e: shared.entrySet()) if (e.getValue()>=4) ban.add(e.getKey());
  1524	            for (int i=0;i<a.length();i++){ org.json.JSONObject ch=a.getJSONObject(i); org.json.JSONArray tl=ch.optJSONArray("timelines");
  1525	                if (tl!=null && tl.length()>0){ org.json.JSONObject t0=tl.getJSONObject(0); m.put(ch.optString("name"), t0.optString("title")); String img=plutoGuideArt(t0, ban); if (img!=null) art.put(ch.optString("name"), img); }
  1526	                String sl=ch.optString("slug",null); if (sl!=null) plutoSlug.put(ch.optString("name"), sl); }
  1527	            android.util.Log.i("ShieldHome","pluto now: "+m.size()+" channels art="+art.size()+" logos="+logos.size());
  1528	            h.post(()->{ plutoNow.clear(); plutoNow.putAll(m); plutoArt.clear(); plutoArt.putAll(art); plutoLogo.clear(); plutoLogo.putAll(logos); plutoAt=System.currentTimeMillis(); applyLiveNow(); });
  1529	        } catch (Exception e){ android.util.Log.w("ShieldHome","pluto guide "+e); } });
  1530	    }
  1531	    static String plutoGuideArt(org.json.JSONObject t0, Set<String> ban){
  1532	        org.json.JSONObject ep=t0.optJSONObject("episode"); if (ep==null) return null;
  1533	        org.json.JSONObject se=ep.optJSONObject("series");
  1534	        String[] cand={ jsonPath(ep.optJSONObject("poster16_9")), jsonPath(ep.optJSONObject("thumbnail")),
  1535	            se==null?null:jsonPath(se.optJSONObject("tile")), se==null?null:jsonPath(se.optJSONObject("featuredImage")),
  1536	            jsonPath(ep.optJSONObject("featuredImage")) };
  1537	        for (String u: cand) if (!genericArt(u, ban)) return u;
  1538	        return null;
  1539	    }
  1540	    static void addBan(Set<String> ban, org.json.JSONObject o){ String u=jsonPath(o); if (u!=null){ ban.add(u); ban.add(bareUrl(u)); } }
  1580	        LinkedHashMap<String,String> slugName=new LinkedHashMap<>();
  1581	        Map<String,Long> plutoIds=plutoIdsCache!=null?plutoIdsCache:new HashMap<>();
  1582	        for (String slug: LIVE_SLUGS){
  1583	            String nm=null; for (Map.Entry<String,String> en: plutoSlug.entrySet()) if (slug.equals(en.getValue())) nm=en.getKey();
  1584	            if (nm==null) nm=slug.equals("cnn-headlines")?"CNN Headlines":slug.equals("nbc-news-now")?"NBC News NOW":slug.equals("abc-news-live")?"ABC News Live":"CBS News 24/7";
  1585	            slugName.put(slug, nm);
  1586	        }
  1587	        for (Map.Entry<String,Long> e: plutoIds.entrySet()){ String slug=slugify(e.getKey()); if (!slugName.containsKey(slug)) slugName.put(slug, e.getKey()); }
  1588	        for (Map.Entry<String,String> e: plutoSlug.entrySet()) if (!slugName.containsKey(e.getValue())) slugName.put(e.getValue(), e.getKey());
  1589	        return slugName;
  1590	    }
  1591	    void putLive(View v, String slug){ if (slug!=null) liveSlugs.add(slug); liveCache.add(v); addCard(v, liveCache.size()-1); }
  1592	    void showLiveCache(){
  1593	        if (cardRow.getChildCount()==liveCache.size() && cardRow.getChildCount()>0 && cardRow.getChildAt(0)==liveCache.get(0)){ applyLiveNow(); return; }
  1594	        cardRow.removeAllViews();
  1595	        int i=0; for (View v: liveCache){ if (v.getParent()!=null) ((ViewGroup)v.getParent()).removeView(v); addCard(v, i++); }
  1596	        applyLiveNow();
  1597	    }
  1598	    View plutoTile(String slug, String nm, boolean loadArt){
  1599	        Map<String,Long> plutoIds=plutoIdsCache!=null?plutoIdsCache:new HashMap<>();
  1600	        final Intent in=plutoIntent(slug); Long id=plutoId(plutoIds, nm, slug);
  1601	        View v=card(null, null, false, null, nm, -1, ()->startSafe(in, "tv.pluto.android"));
  1602	        ImageView big=(ImageView)((GlowFrame)v).inner.getChildAt(0);
  1603	        TextView ov=chName(nm);
  1604	        ((GlowFrame)v).inner.addView(ov, lp(-2,-2,Gravity.TOP|Gravity.START,0,0,0,0));
  1605	        TextView cap=null;
  1606	        for (int ci=0; ci<((GlowFrame)v).inner.getChildCount(); ci++){ View cv=((GlowFrame)v).inner.getChildAt(ci); if (cv instanceof TextView && cv!=ov) cap=(TextView)cv; }
  1607	        String art=liveArt(nm, id), logo=plutoLogo.get(nm);
  1608	        if (art!=null && loadArt) setLiveArt(big, art, true, false);
  1609	        else if (logo!=null && loadArt) setLiveArt(big, logo, true, true);
  1610	        else if (art!=null) big.setTag("pend:"+art);
  1611	        else if (logo!=null) big.setTag("pendf:"+logo);
  1612	        ov.setVisibility(art!=null||logo!=null?View.VISIBLE:View.GONE);
  1613	        if (plutoNow.get(nm)!=null && cap!=null) cap.setText(plutoNow.get(nm));
  1614	        plutoCaps.add(new Object[]{cap, nm, big, ov, id});
  1615	        return v;
  1616	    }
  1617	    TextView chName(String nm){
  1618	        TextView ov=text(nm, 7, Color.WHITE, "sans-serif-medium");
  1619	        ov.setPadding(p(6),p(3),p(10),p(4));
  1620	        ov.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Color.argb(160,0,0,0), Color.argb(70,0,0,0), Color.TRANSPARENT}));
  1621	        return ov;
  1622	    }
  1623	    String liveArt(String nm, Long id){
  1624	        String art=plutoArt.get(nm); if (!genericArt(art,null)) return art;
  1625	        art=tifNowArt(id); return genericArt(art,null)?null:art;
  1626	    }
  1627	    void applyLiveNow(){
  1628	        for (Object[] o: plutoCaps){
  1629	            String nm=(String)o[1]; TextView cap=(TextView)o[0]; ImageView iv=o.length>2?(ImageView)o[2]:null; TextView ov=o.length>3?(TextView)o[3]:null;
  1630	            Long id=o.length>4?(Long)o[4]:null;
  1631	            String t=plutoNow.get(nm), art=liveArt(nm,id), logo=plutoLogo.get(nm);
  1632	            if (art!=null && !art.isEmpty()){
  1633	                if (ov!=null) ov.setVisibility(View.VISIBLE);
  1634	                if (cap!=null) cap.setText(t!=null?t:nm);
  1635	                setLiveArt(iv, art, false, false);
  1636	            } else if (logo!=null){
  1637	                if (ov!=null) ov.setVisibility(View.VISIBLE);
  1638	                if (cap!=null) cap.setText(t!=null?t:nm);
  1639	                setLiveArt(iv, logo, false, true);
  1640	            } else {
  1641	                if (ov!=null) ov.setVisibility(View.GONE);
  1642	                if (cap!=null) cap.setText(t!=null?nm+" \u00B7 "+t:nm);
  1643	            }
  1644	        }
  1645	        if (tab==1) updateCardAlphas();
  1646	    }
  1647	    void setLiveArt(ImageView iv, String uri, boolean now, boolean fit){
  1648	        if (iv==null || uri==null) return;
  1649	        Object old=iv.getTag(); if (uri.equals(old) || ("pend:"+uri).equals(old) || ("pendf:"+uri).equals(old)) return;
  1650	        iv.setPadding(fit?p(22):0, fit?p(16):0, fit?p(22):0, fit?p(16):0);
  1651	        iv.setScaleType(fit?ImageView.ScaleType.FIT_CENTER:ImageView.ScaleType.CENTER_CROP);
  1652	        if (now || (tab==1 && iv.getAlpha()>0)) loadInto(iv, uri, fit); else iv.setTag((fit?"pendf:":"pend:")+uri);
  1653	    }
  1654	    String tifNowArt(Long id){
  1655	        if (id==null) return null;
  1688	    void launchPkg(String pkg){ Intent i = pm.getLeanbackLaunchIntentForPackage(pkg); if (i==null) i = pm.getLaunchIntentForPackage(pkg); if (i!=null) startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
  1689	
  1690	    // ---------- v6: quick settings, featured toggle, dimmer, weather ----------
  1691	    PillView searchV, speakV, typeV; LinearLayout searchSplit; EditText searchField; TextView heroLabel;
  1692	    void useWhatsNew(String js){
  1693	        List<Object[]> nl = new ArrayList<>();
  1694	        try { org.json.JSONArray a = new org.json.JSONArray(js==null?"[]":js);
  1695	            for (int i=0;i<a.length();i++){ org.json.JSONObject o=a.getJSONObject(i); String pkg=o.getString("pkg"); if (!installed(pkg) || o.optString("img").isEmpty()) continue;
  1696	                int dd=o.optInt("dd"); String when;
  1697	                try { Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(o.getString("date")); long diff = Math.round((d.getTime() - new SimpleDateFormat("yyyy-MM-dd",Locale.US).parse(new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date())).getTime())/86400000.0);
  1698	                    when = diff==0?"New today":diff==1?"Coming tomorrow":diff==-1?"Added yesterday":diff<0?"Added "+new SimpleDateFormat("EEE",Locale.US).format(d):"Coming "+new SimpleDateFormat("EEEE",Locale.US).format(d); } catch (Exception e){ when="New"; }
  1699	                String ep = o.optInt("season")>0 ? "  \u00B7  S"+o.optInt("season")+(o.optInt("num")>0?" E"+o.optInt("num"):"") : "";
  1700	                String sum = firstSentence(o.optString("summary"));
  1701	                Intent li = pm.getLeanbackLaunchIntentForPackage(pkg); if (li==null) li = pm.getLaunchIntentForPackage(pkg);
  1702	                long avail=0; try { Date ad=new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(o.getString("date")); if (ad!=null && ad.getTime()>System.currentTimeMillis()) avail=ad.getTime(); } catch (Exception e){}
  1703	                nl.add(new Object[]{pkg, o.getString("show"), when+" on "+o.optString("net")+ep+"\n"+sum, o.getString("img"), li, "NEW", avail});
  1704	            }
  1705	        } catch (Exception e){ android.util.Log.w("ShieldHome","whatsnew parse "+e); }
  1706	        // v9: one hero, Featured + What's New randomly mixed in the same rotation
  1707	        List<Object[]> mix = new ArrayList<>(); for (Object[] o: heroItems) if (o.length>5 && "FEATURED".equals(o[5])) mix.add(o);
  1708	        int nf = mix.size(); Collections.shuffle(nl); for (int i=0;i<Math.min(8,nl.size());i++) mix.add(nl.get(i));
  1709	        android.util.Log.i("ShieldHome","hero mix featured="+nf+" new="+(mix.size()-nf));
  1710	        if (mix.size()!=nf){
  1711	            Collections.shuffle(mix);
  1712	            synchronized(heroLock){ h.removeCallbacks(heroRotate); heroItems.clear(); heroItems.addAll(mix); heroIdx=0; }
  1713	            showHero(); h.postDelayed(heroRotate, 16000);
  1714	        }
  1715	    }
shieldhome/src/com/chris/shieldhome/FocusController.java:12:    final int[] lastIdx = new int[16];
shieldhome/src/com/chris/shieldhome/FocusController.java:25:            if (a.wnPickCache != null && a.wnPickCache.isEmpty()) ft = 1;
shieldhome/src/com/chris/shieldhome/FocusController.java:104:            if (t != f && ni >= 0 && sec >= 0 && sec < lastIdx.length) lastIdx[sec] = ni;
shieldhome/src/com/chris/shieldhome/FocusController.java:227:        if (sec >= 0 && sec < lastIdx.length) lastIdx[sec] = ix;
shieldhome/src/com/chris/shieldhome/FocusController.java:235:            int ti = s2 == 3 ? lastIdx[3] : (s2 == 4 && sec == 3) ? 0 : (multi ? ix : (s2 < lastIdx.length ? lastIdx[s2] : 0));
shieldhome/src/com/chris/shieldhome/MainActivity.java:20:    float u; PackageManager pm; FrameLayout root; LinearLayout cardRow; HorizontalScrollView cardScroll; FrameLayout cardWrap; View clk;
shieldhome/src/com/chris/shieldhome/MainActivity.java:35:    List<Object[]> featuredCache, wnPickCache;
shieldhome/src/com/chris/shieldhome/MainActivity.java:37:    String homeBindSig="", cardsBindSig="", featuredBoundSig="";
shieldhome/src/com/chris/shieldhome/MainActivity.java:38:    final Object heroLock=new Object(); Object[] heroOnScreen;
shieldhome/src/com/chris/shieldhome/MainActivity.java:121:        if (focus!=null) focus.lastIdx[4]=0;
shieldhome/src/com/chris/shieldhome/MainActivity.java:248:        clk = tr;
shieldhome/src/com/chris/shieldhome/MainActivity.java:249:        root.addView(clk, lp(-2,-2,Gravity.TOP|Gravity.END,0,p(17),p(46),0));
shieldhome/src/com/chris/shieldhome/MainActivity.java:600:    View focusable(final FrameLayout v, final GradientDrawable base, float radius, final Runnable click){ return focusable(v, base, radius, 1.13f, click); }
shieldhome/src/com/chris/shieldhome/MainActivity.java:601:    View focusable(final FrameLayout v, final GradientDrawable base, float radius, final float sc, final Runnable click){
shieldhome/src/com/chris/shieldhome/MainActivity.java:673:    long use(String p){ Long v = p==null?null:usage.get(p); long base = v==null?0:v; if ("com.google.android.youtube.tv".equals(p)) base = base*3+1; return base; }
shieldhome/src/com/chris/shieldhome/MainActivity.java:722:    View[] slideViews(){ return new View[]{clk, gear, searchV, searchSplit, searchField, dimBar, heroG, tabBar, cardWrap, homeGrid}; }
shieldhome/src/com/chris/shieldhome/MainActivity.java:726:        for (View v: new View[]{clk, gear, searchV, searchSplit, searchField, dimBar}){
shieldhome/src/com/chris/shieldhome/MainActivity.java:763:            if (clk!=null) clk.bringToFront();
shieldhome/src/com/chris/shieldhome/MainActivity.java:791:        if (focusIdx<0 && sig.equals(homeBindSig) && homeGrid.getChildCount()==all.size()) return;
shieldhome/src/com/chris/shieldhome/MainActivity.java:792:        homeBindSig=sig;
shieldhome/src/com/chris/shieldhome/MainActivity.java:837:        homeBindSig=sig;
shieldhome/src/com/chris/shieldhome/MainActivity.java:1232:        Object[] o=heroOnScreen;
shieldhome/src/com/chris/shieldhome/MainActivity.java:1245:        List<Object[]> feat=featuredCache;
shieldhome/src/com/chris/shieldhome/MainActivity.java:1248:        if (fs.equals(featuredBoundSig)) return;
shieldhome/src/com/chris/shieldhome/MainActivity.java:1249:        featuredBoundSig=fs;
shieldhome/src/com/chris/shieldhome/MainActivity.java:1282:            h.post(()->{ if (g!=heroLoadGen) return; heroOnScreen=o; transitionHero(o, bm); });
shieldhome/src/com/chris/shieldhome/MainActivity.java:1338:            featuredCache=tv.collectFeatured(tv.queryPreviewFeatured());
shieldhome/src/com/chris/shieldhome/MainActivity.java:1339:            wnPickCache=tv.collectWatchNext();
shieldhome/src/com/chris/shieldhome/MainActivity.java:1361:    boolean playNextOn(){ return wnPickCache!=null && !wnPickCache.isEmpty(); }
shieldhome/src/com/chris/shieldhome/MainActivity.java:1373:        if (tab==0){ if (wnPickCache!=null) for (Object[] o: wnPickCache) b.append(o[0]).append('|').append(o[1]).append('|').append(o[2]).append(','); }
shieldhome/src/com/chris/shieldhome/MainActivity.java:1385:            final View.OnFocusChangeListener base = v.getOnFocusChangeListener();
shieldhome/src/com/chris/shieldhome/MainActivity.java:1386:            v.setOnFocusChangeListener((x,f)->{ if (base!=null) base.onFocusChange(x,f); if (f) snapCardRow(x); });
shieldhome/src/com/chris/shieldhome/MainActivity.java:1398:        if (focus!=null){ focus.lastIdx[3]=0; focus.lastCardX=0; }
shieldhome/src/com/chris/shieldhome/MainActivity.java:1494:        List<Object[]> pick=wnPickCache;
shieldhome/src/com/chris/shieldhome/MainActivity.java:1503:    List<Object[]> plutoCaps = new ArrayList<>(); Map<String,String> plutoNow = new HashMap<>(); Map<String,String> plutoArt = new HashMap<>(); Map<String,String> plutoLogo = new HashMap<>(); final Map<String,String> plutoSlug = new java.util.concurrent.ConcurrentHashMap<>(); long plutoAt=0;
shieldhome/src/com/chris/shieldhome/MainActivity.java:1507:        if (System.currentTimeMillis()-plutoAt < 10*60000L && !plutoNow.isEmpty()){ applyLiveNow(); return; }
shieldhome/src/com/chris/shieldhome/MainActivity.java:1528:            h.post(()->{ plutoNow.clear(); plutoNow.putAll(m); plutoArt.clear(); plutoArt.putAll(art); plutoLogo.clear(); plutoLogo.putAll(logos); plutoAt=System.currentTimeMillis(); applyLiveNow(); });
shieldhome/src/com/chris/shieldhome/MainActivity.java:1607:        String art=liveArt(nm, id), logo=plutoLogo.get(nm);
shieldhome/src/com/chris/shieldhome/MainActivity.java:1613:        if (plutoNow.get(nm)!=null && cap!=null) cap.setText(plutoNow.get(nm));
shieldhome/src/com/chris/shieldhome/MainActivity.java:1631:            String t=plutoNow.get(nm), art=liveArt(nm,id), logo=plutoLogo.get(nm);
shieldhome/src/com/chris/shieldhome/TvData.java:132:        if (a.featuredCache != null) for (Object[] f : a.featuredCache) {

exec
/bin/bash -lc "nl -ba shieldhome/src/com/chris/shieldhome/MainActivity.java | sed -n '1,80p;360,470p;470,540p;780,855p;1320,1495p'" in /tmp/codexreview
 succeeded in 0ms:
     1	package com.chris.shieldhome;
     2	
     3	import android.app.Activity;
     4	import android.content.*;
     5	import android.content.pm.*;
     6	import android.database.Cursor;
     7	import android.graphics.*;
     8	import android.graphics.drawable.*;
     9	import android.media.tv.TvContract;
    10	import android.net.Uri;
    11	import android.os.*;
    12	import android.util.TypedValue;
    13	import android.view.*;
    14	import android.widget.*;
    15	import java.text.SimpleDateFormat;
    16	import java.util.*;
    17	
    18	public class MainActivity extends Activity {
    19	    // All geometry is in mockup pixels (1024x576 reference), scaled to the real screen.
    20	    float u; PackageManager pm; FrameLayout root; LinearLayout cardRow; HorizontalScrollView cardScroll; FrameLayout cardWrap; View clk;
    21	    SegBar tabBar; TextView tabPlay, tabLive; int tab = 0; Handler h = new Handler(Looper.getMainLooper());
    22	    static final int TILE = Color.argb(34, 255, 255, 255), FOCUS = Color.rgb(64, 156, 255), BG = Color.rgb(8, 8, 16);
    23	    static final int HERO_SCRIM_RGB=0x050507; // darker than BG 0x080810 so the wash doesn't read as wallpaper
    24	    static final float HERO_LEFT_FADE=0.24f;
    25	    static final int TILE_R = 11;
    26	    static final float APP_FOCUS=1.13f, APP_HOLD=1.16f, APP_W=142.5f, APP_H=78.5f, APP_HG=19.8f, APP_VG=21f, APP_GRID_Y=467.5f;
    27	    static final String[] PLAY_PKGS = {"com.google.android.youtube.tv","com.netflix.ninja","com.disney.disneyplus","com.hulu.livingroomplus","com.wbd.stream","com.amazon.amazonvideo.livingroom","com.plexapp.android"};
    28	    static final String[] LIVE_PKGS = {"tv.pluto.android","com.cnbc.client","com.haystack.android","com.plexapp.android"};
    29	
    30	    FrameLayout.LayoutParams lp(int w,int h,int g,int l,int t,int r,int b){ FrameLayout.LayoutParams x=new FrameLayout.LayoutParams(w,h,g); x.setMargins(l,t,r,b); return x; }
    31	    java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(4);
    32	    ImageLoader images; FocusController focus; TvData tv; NetData net;
    33	    java.util.concurrent.Future<?> dataFut; int dataGen, heroLoadGen;
    34	    List<String[]> appsCache, fullOrder=new ArrayList<>();
    35	    List<Object[]> featuredCache, wnPickCache;
    36	    Map<String,Long> plutoIdsCache=new HashMap<>();
    37	    String homeBindSig="", cardsBindSig="", featuredBoundSig="";
    38	    final Object heroLock=new Object(); Object[] heroOnScreen;
    39	    void loadInto(final ImageView iv, String uri0, final boolean fit){
    40	        if (images==null||uri0==null) return;
    41	        images.load(iv, uri0, fit, iv.getWidth()>0?iv.getWidth():p(184.4f), iv.getHeight()>0?iv.getHeight():p(112));
    42	    }
    43	    int p(float v){ return Math.round(v*u); }
    44	    void ts(TextView t, float v){ t.setTextSize(TypedValue.COMPLEX_UNIT_PX, v*u); }
    45	
    46	    @Override protected void onCreate(Bundle b){
    47	        super.onCreate(b);
    48	        pm = getPackageManager();
    49	        images = new ImageLoader(this, pool, h);
    50	        focus = new FocusController(this);
    51	        tv = new TvData(this);
    52	        net = new NetData(this);
    53	        prefs().edit().remove("heromode").remove("featured").apply(); // v9: card mode removed
    54	        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    55	        DisplayMetricsHolder();
    56	        kickData(false);
    57	        build();
    58	        IntentFilter pf = new IntentFilter(); pf.addAction(Intent.ACTION_PACKAGE_REMOVED); pf.addAction(Intent.ACTION_PACKAGE_ADDED); pf.addAction(Intent.ACTION_PACKAGE_CHANGED); pf.addDataScheme("package");
    59	        registerReceiver(pkgRx, pf);
    60	    }
    61	    final BroadcastReceiver pkgRx = new BroadcastReceiver(){ @Override public void onReceive(Context c, Intent i){
    62	        String pkg = i.getData()==null?null:i.getData().getSchemeSpecificPart(); boolean repl = i.getBooleanExtra(Intent.EXTRA_REPLACING,false);
    63	        android.util.Log.i("ShieldHome","pkg "+i.getAction()+" "+pkg+" repl="+repl);
    64	        final boolean removed = Intent.ACTION_PACKAGE_REMOVED.equals(i.getAction()) && !repl && pkg!=null;
    65	        if (removed){
    66	            Runnable home = ()->{ appsCache=null; kickData(true); };
    67	            if (!editMode && dragIdx<0) fadeRemove(homeGrid, pkg, home); else home.run();
    68	            return; }
    69	        h.postDelayed(()->{ appsCache=null; kickData(true); }, 150); } };
    70	    @Override protected void onDestroy(){
    71	        dataGen++; heroLoadGen++;
    72	        try { unregisterReceiver(pkgRx); } catch (Exception e){}
    73	        pool.shutdownNow();
    74	        super.onDestroy();
    75	    }
    76	    void DisplayMetricsHolder(){ android.util.DisplayMetrics m = new android.util.DisplayMetrics(); getWindowManager().getDefaultDisplay().getRealMetrics(m); u = m.widthPixels/1024f; }
    77	    @Override protected void onResume(){
    78	        super.onResume();
    79	        focus.prepare();
    80	        if (appsCache==null){ if (dataFut==null) kickData(false); awaitData(); }
   360	        cardScroll.setClipChildren(false); cardScroll.setClipToPadding(false);
   361	        cardScroll.setPadding(p(20),p(20),p(20),p(20));
   362	        cardRow = new LinearLayout(this); cardRow.setClipChildren(false); cardRow.setClipToPadding(false);
   363	        cardScroll.addView(cardRow, new ViewGroup.LayoutParams(-2,-1));
   364	        cardWrap.addView(cardScroll, new FrameLayout.LayoutParams(-1,-1));
   365	        place(cardWrap, 15, 320, 994, 160);
   366	        cardScroll.getViewTreeObserver().addOnScrollChangedListener(this::updateCardAlphas);
   367	
   368	        homeGrid = new GridLayout(this); homeGrid.setColumnCount(6); homeGrid.setClipChildren(false); homeGrid.setClipToPadding(false);
   369	        place(homeGrid, 35, APP_GRID_Y, 954, APP_H+APP_VG);
   370	        ensureDim();
   371	        buildHomeGrid();
   372	
   373	        setContentView(root);
   374	        h.post(()->{ if (tabPlay.getVisibility()==View.VISIBLE) tabPlay.requestFocus(); else tabLive.requestFocus(); });
   375	    }
   376	
   377	    // ---------- Pills with drawn icons ----------
   378	    class PillView extends View {
   379	        String label; int icon; boolean on; Drawable asst; Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);
   380	        PillView(Context c, String l, int ic, boolean o){ super(c); label=l; icon=ic; on=o; setFocusable(true); setClickable(true); }
   381	        void setOn(boolean o){ on=o; invalidate(); }
   382	        @Override protected void onFocusChanged(boolean f, int d, Rect r){ super.onFocusChanged(f,d,r); if (this==heroBtn) return; animate().scaleX(f?1.07f:1f).scaleY(f?1.07f:1f).setDuration(160).setInterpolator(new android.view.animation.DecelerateInterpolator()).start(); setElevation(f?p(4):0); invalidate(); }
   383	        @Override protected void onDraw(Canvas c){
   384	            float w=getWidth(), hh=getHeight(), r=this==heroBtn?p(14):hh/2;
   385	            boolean lit = on || (icon==2 && isFocused());
   386	            pt.setStyle(Paint.Style.FILL);
   387	            boolean fo = isFocused();
   388	            boolean bare = this==gear && !fo;
   389	            if (!bare){
   390	                if (icon==2) pt.setColor(fo?Color.WHITE:Color.rgb(225,224,228));
   391	                else if (on) pt.setColor(fo?Color.WHITE:Color.rgb(229,227,228));
   392	                else if (icon==5) pt.setColor(fo?Color.argb(155,170,170,191):Color.argb(95,162,162,183));
   393	                else pt.setColor(fo?Color.argb(185,102,100,119):Color.argb(140,60,58,75));
   394	                c.drawRoundRect(0,0,w,hh,r,r,pt);
   395	                if (icon==5 && !on){ pt.setShader(new LinearGradient(0,0,0,hh,Color.argb(70,255,255,255),Color.argb(0,255,255,255),Shader.TileMode.CLAMP)); c.drawRoundRect(0,0,w,hh,r,r,pt); pt.setShader(null); }
   396	                if (!on && icon!=2){ pt.setStyle(Paint.Style.STROKE); pt.setStrokeWidth(1); pt.setColor(Color.argb(40,255,255,255)); c.drawRoundRect(.5f,.5f,w-.5f,hh-.5f,r,r,pt); }
   397	            }
   398	            int fg = (icon==2||on) ? Color.rgb(22,22,30) : Color.rgb(232,232,240);
   399	            if (this==dimBar){
   400	                pt.setStyle(Paint.Style.FILL); pt.setTextSize(p(12)); pt.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
   401	                float labW=pt.measureText("90%"), pad=p(12), gap=p(8), th=p(6), tw=w-pad*2-gap-labW, ty=(hh-th)/2, rr=th/2, t=Math.max(0, Math.min(1, dimFill/0.9f));
   402	                pt.setColor(Color.argb(50, Color.red(fg), Color.green(fg), Color.blue(fg)));
   403	                c.drawRoundRect(pad, ty, pad+tw, ty+th, rr, rr, pt);
   404	                if (t>0){ pt.setColor(fg); c.drawRoundRect(pad, ty, pad+Math.max(t*tw, th), ty+th, rr, rr, pt); }
   405	                pt.setColor(fg); pt.setTextAlign(Paint.Align.LEFT);
   406	                Paint.FontMetrics fm=pt.getFontMetrics();
   407	                c.drawText(dimPct()+"%", pad+tw+gap, hh/2f-(fm.ascent+fm.descent)/2, pt);
   408	                return;
   409	            }
   410	            pt.setColor(fg); pt.setStrokeWidth(p(1.1f)); pt.setStyle(Paint.Style.STROKE); pt.setStrokeCap(Paint.Cap.ROUND);
   411	            float cx = icon==3 ? p(19) : p(icon==2?18:28) - (icon==2?p(4):0), cy = hh/2;
   412	            if (icon==3){ // magnifier + mic
   413	                c.drawCircle(cx-p(1),cy-p(1),p(4.2f),pt); c.drawLine(cx+p(2),cy+p(2),cx+p(5),cy+p(5),pt);
   414	                if (asst==null) asst = getContext().getDrawable(R.drawable.assistant);
   415	                int ax=(int)(cx+p(8)); asst.setBounds(ax, (int)(cy-p(7.5f)), ax+p(15), (int)(cy+p(7.5f))); asst.draw(c); }
   416	            else if (icon==5){ float gx=w/2f, sr=p(4.0f);
   417	                pt.setStrokeWidth(p(1.5f));
   418	                c.drawCircle(gx, cy, sr, pt);
   419	                pt.setStrokeWidth(p(1.3f));
   420	                for (int i=0;i<8;i++){ double a=Math.toRadians(i*45); float s=sr+p(1.5f), e=sr+p(3.3f); c.drawLine(gx+(float)Math.cos(a)*s, cy+(float)Math.sin(a)*s, gx+(float)Math.cos(a)*e, cy+(float)Math.sin(a)*e, pt); }
   421	                if (dimFill>0.01f){ c.save(); c.clipRect(gx+sr-2*sr*dimFill, cy-sr-1, gx+sr+1, cy+sr+1); pt.setStyle(Paint.Style.FILL); c.drawCircle(gx, cy, sr-p(0.3f), pt); pt.setStyle(Paint.Style.STROKE); c.restore(); } }
   422	            else if (icon==6){
   423	                if (asst==null) asst = getContext().getDrawable(R.drawable.assistant);
   424	                int ax=(int)(w/2f-p(7.5f)); asst.setBounds(ax, (int)(cy-p(7.5f)), ax+p(15), (int)(cy+p(7.5f))); asst.draw(c); }
   425	            else if (icon==7){
   426	                float kx=w/2f, l=kx-p(8), rr=kx+p(8), t=cy-p(5.2f), b=cy+p(5.2f);
   427	                pt.setStyle(Paint.Style.STROKE); pt.setStrokeWidth(p(1.1f)); pt.setStrokeJoin(Paint.Join.ROUND);
   428	                c.drawRoundRect(l, t, rr, b, p(2.2f), p(2.2f), pt);
   429	                pt.setStyle(Paint.Style.FILL);
   430	                float inset=p(2.4f), kl=l+inset, kr=rr-inset, kt=t+inset, gapx=(kr-kl)/4f, gapy=p(2.35f), kd=p(0.85f);
   431	                for (int row=0; row<2; row++) for (int col=0; col<5; col++) c.drawCircle(kl+col*gapx, kt+row*gapy, kd, pt);
   432	                float by=kt+2*gapy, sh=p(1.15f);
   433	                c.drawCircle(kl, by, kd, pt); c.drawCircle(kr, by, kd, pt);
   434	                c.drawRoundRect(kl+gapx, by-sh/2, kr-gapx, by+sh/2, p(0.6f), p(0.6f), pt);
   435	            }
   436	            else if (icon!=4) { pt.setStyle(Paint.Style.FILL); Path tr=new Path(); tr.moveTo(cx-p(3),cy-p(5)); tr.lineTo(cx+p(5),cy); tr.lineTo(cx-p(3),cy+p(5)); tr.close(); c.drawPath(tr,pt); }
   437	            pt.setStyle(Paint.Style.FILL); pt.setTextSize(p(this==heroBtn?14:icon==2?12.5f:12)); pt.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
   438	            Paint.FontMetrics fm = pt.getFontMetrics();
   439	            if (this==heroBtn && ("Play Now".equals(label) || "More Info".equals(label))){
   440	                float tw=pt.measureText(label), cap=-fm.ascent, th=cap, twt=cap*0.78f, gap=p(10), x0=(w-twt-gap-tw)/2;
   441	                if ("Play Now".equals(label)){
   442	                    pt.setPathEffect(new CornerPathEffect(Math.max(1, cap*0.12f)));
   443	                    Path tr=new Path(); tr.moveTo(x0, cy-th/2); tr.lineTo(x0+twt, cy); tr.lineTo(x0, cy+th/2); tr.close();
   444	                    c.drawPath(tr, pt); pt.setPathEffect(null);
   445	                } else {
   446	                    float icx=x0+twt/2;
   447	                    pt.setStyle(Paint.Style.STROKE); pt.setStrokeWidth(Math.max(1, th*0.12f));
   448	                    c.drawCircle(icx, cy, th/2, pt);
   449	                    pt.setStyle(Paint.Style.FILL); pt.setTextAlign(Paint.Align.CENTER);
   450	                    pt.setTextSize(th*0.72f);
   451	                    Paint.FontMetrics ifm=pt.getFontMetrics();
   452	                    c.drawText("i", icx, cy-(ifm.ascent+ifm.descent)/2, pt);
   453	                    pt.setTextSize(p(14));
   454	                }
   455	                pt.setTextAlign(Paint.Align.LEFT);
   456	                c.drawText(label, x0+twt+gap, cy-(fm.ascent+fm.descent)/2, pt);
   457	                return;
   458	            }
   459	            if (icon==4) pt.setTextAlign(Paint.Align.CENTER); else pt.setTextAlign(Paint.Align.LEFT);
   460	            if (label.length()>0) c.drawText(label, icon==4? w/2f : p(icon==2?24:icon==3?46:43), cy-(fm.ascent+fm.descent)/2, pt);
   461	        }
   462	    }
   463	
   464	    class SegBar extends LinearLayout {
   465	        TextView play, live; float hx, hw, hs=1f; android.animation.ValueAnimator ha, pa;
   466	        SegBar(){
   467	            super(MainActivity.this);
   468	            setOrientation(HORIZONTAL); setGravity(Gravity.CENTER_VERTICAL);
   469	            setPadding(0,0,0,0); setWillNotDraw(false); setClipChildren(false); setClipToPadding(false);
   470	            play=mk("Play Next"); live=mk("Live TV");
   470	            play=mk("Play Next"); live=mk("Live TV");
   471	            int sw=Math.max(Math.round(play.getPaint().measureText("Play Next")), Math.round(live.getPaint().measureText("Live TV")))+p(36);
   472	            play.setLayoutParams(new LinearLayout.LayoutParams(sw,-1));
   473	            live.setLayoutParams(new LinearLayout.LayoutParams(sw,-1));
   474	            addView(play); addView(live);
   475	            play.setOnFocusChangeListener((v,f)-> tabFocus(0,f));
   476	            live.setOnFocusChangeListener((v,f)-> tabFocus(1,f));
   477	            play.setId(View.generateViewId()); live.setId(View.generateViewId());
   478	            play.setNextFocusRightId(live.getId()); live.setNextFocusLeftId(play.getId());
   479	            play.setNextFocusDownId(View.NO_ID); live.setNextFocusDownId(View.NO_ID);
   480	            play.setNextFocusUpId(View.NO_ID); live.setNextFocusUpId(View.NO_ID);
   481	        }
   482	        TextView mk(String s){
   483	            TextView t=text(s,12,Color.rgb(232,232,240),"sans-serif");
   484	            t.setFocusable(true); t.setClickable(true); t.setGravity(Gravity.CENTER);
   485	            t.setPadding(0,0,0,0); t.setSingleLine(true);
   486	            t.setDefaultFocusHighlightEnabled(false);
   487	            return t;
   488	        }
   489	        void tabFocus(int t, boolean f){
   490	            if (f){ pick(t); lift(true); }
   491	            else post(()->{ if (!play.isFocused() && !live.isFocused()) lift(false); });
   492	        }
   493	        void pick(int t){
   494	            boolean ch=t!=tab;
   495	            if (ch){ tab=t; refreshCards(); resetCardRow(); }
   496	            slide(hw>0); color();
   497	        }
   498	        void color(){
   499	            int d=Color.rgb(22,22,30), l=Color.rgb(232,232,240);
   500	            play.setTextColor(tab==0?d:l); live.setTextColor(tab==1?d:l);
   501	        }
   502	        void lift(boolean on){
   503	            float to=on?1.18f:1f;
   504	            if (on && hs>1.02f) return;
   505	            if (Math.abs(hs-to)<0.005f) return;
   506	            if (pa!=null) pa.cancel();
   507	            pa=android.animation.ValueAnimator.ofFloat(hs, to); pa.setDuration(340);
   508	            pa.setInterpolator(new android.view.animation.OvershootInterpolator(1.1f));
   509	            pa.addUpdateListener(a->{ hs=(Float)a.getAnimatedValue(); invalidate(); });
   510	            pa.start();
   511	        }
   512	        void showPlay(boolean on){ play.setVisibility(on?VISIBLE:GONE); play.setFocusable(on); requestLayout(); }
   513	        void slide(boolean anim){
   514	            TextView s=(tab==0 && play.getVisibility()==VISIBLE)?play:live;
   515	            float nx=s.getLeft(), nw=s.getWidth();
   516	            if (nw<=0){ post(()->slide(false)); return; }
   517	            if (!anim){ hx=nx; hw=nw; invalidate(); return; }
   518	            if (ha!=null) ha.cancel();
   519	            float x0=hx, w0=hw;
   520	            ha=android.animation.ValueAnimator.ofFloat(0,1); ha.setDuration(250);
   521	            ha.setInterpolator(new android.view.animation.PathInterpolator(0.16f,1f,0.3f,1f));
   522	            ha.addUpdateListener(a->{ float u=(Float)a.getAnimatedValue(); hx=x0+(nx-x0)*u; hw=w0+(nw-w0)*u; invalidate(); });
   523	            ha.start();
   524	        }
   525	        @Override protected void onLayout(boolean ch, int l, int t, int r, int b){
   526	            super.onLayout(ch,l,t,r,b);
   527	            if (ha==null || !ha.isRunning()) slide(false);
   528	            color();
   529	        }
   530	        @Override protected void dispatchDraw(Canvas c){
   531	            float w=getWidth(), hh=getHeight(), rr=hh/2;
   532	            Paint pt=new Paint(Paint.ANTI_ALIAS_FLAG);
   533	            pt.setColor(Color.argb(140,60,58,75));
   534	            c.drawRoundRect(0,0,w,hh,rr,rr,pt);
   535	            pt.setStyle(Paint.Style.FILL);
   536	            if (hw>0){
   537	                float cx=hx+hw/2, cy=hh/2, sw=hw*hs, sh=hh*hs, sr=sh/2;
   538	                Bitmap sb=shadowBmp(Math.max(1,(int)sw), Math.max(1,(int)sh), sr);
   539	                if (sb!=null){ Paint sp=new Paint(Paint.ANTI_ALIAS_FLAG); sp.setAlpha(Math.max(0,Math.min(130,(int)(130*(hs-1f)/0.04f)))); int pad=p(48); c.drawBitmap(sb, cx-sw/2-pad, cy-sh/2-pad+p(19), sp); }
   540	                pt.setColor(Color.rgb(229,227,228));
   780	        }
   781	        if (edge) staggerDrawer(open);
   782	    }
   783	    void buildHomeGrid(){ buildHomeGrid(-1); }
   784	    void buildHomeGrid(int focusIdx){
   785	        if (editMode) return;
   786	        if (homeGrid==null) return;
   787	        List<String[]> all = homeApps();
   788	        fullOrder = all;
   789	        String sig=(editMode?"E":"N")+"|"+dragIdx+"|"+(drawerOpen?"D":"H")+"|";
   790	        for (String[] a: all) sig += a[0]+"/"+a[1]+",";
   791	        if (focusIdx<0 && sig.equals(homeBindSig) && homeGrid.getChildCount()==all.size()) return;
   792	        homeBindSig=sig;
   793	        homeGrid.removeAllViews();
   794	        for (int i=0;i<all.size();i++) homeGrid.addView(homeTile(all.get(i), i), gridLp(i));
   795	        ViewGroup.LayoutParams lp=homeGrid.getLayoutParams();
   796	        if (lp!=null){ lp.height=rowH()*Math.max(1,(all.size()+5)/6); homeGrid.setLayoutParams(lp); }
   797	        if (focusIdx>=0 && homeGrid.getChildCount()>0){
   798	            final int f=Math.min(focusIdx, homeGrid.getChildCount()-1);
   799	            homeGrid.getChildAt(f).requestFocus();
   800	            h.post(()->{ if (f<homeGrid.getChildCount()) homeGrid.getChildAt(f).requestFocus(); });
   801	        }
   802	    }
   803	    View homeTile(final String[] a, final int i){
   804	        final GlowFrame g = (GlowFrame)appTile(a[0], a[1], a[2]);
   805	        g.setOnLongClickListener(v->{ if (!editMode) appMenu(g, a, tileIx(g)); return true; });
   806	        g.setOnKeyListener((v,k,e)->{
   807	            int ix=tileIx(v);
   808	            boolean dpad = k==KeyEvent.KEYCODE_DPAD_LEFT||k==KeyEvent.KEYCODE_DPAD_RIGHT||k==KeyEvent.KEYCODE_DPAD_UP||k==KeyEvent.KEYCODE_DPAD_DOWN;
   809	            boolean ok = k==KeyEvent.KEYCODE_DPAD_CENTER||k==KeyEvent.KEYCODE_ENTER||k==KeyEvent.KEYCODE_NUMPAD_ENTER;
   810	            if (v==dragV){
   811	                if (e.getAction()!=KeyEvent.ACTION_DOWN) return dpad||ok;
   812	                if (ok){ if (e.getRepeatCount()==0) drop(ix); return true; }
   813	                if (k==KeyEvent.KEYCODE_BACK){ drop(ix); return true; }
   814	                int to = k==KeyEvent.KEYCODE_DPAD_LEFT? ix-1 : k==KeyEvent.KEYCODE_DPAD_RIGHT? ix+1 : k==KeyEvent.KEYCODE_DPAD_UP? ix-6 : k==KeyEvent.KEYCODE_DPAD_DOWN? ix+6 : -99;
   815	                if (to==-99) return false;
   816	                if (to>=0 && to<fullOrder.size()) moveDrag(ix, to);
   817	                else if (k==KeyEvent.KEYCODE_DPAD_UP && ix<6 && drawerOpen) setDrawer(false);
   818	                return true;
   819	            }
   820	            if (editMode) return false;
   821	            if (ok){
   822	                if (e.getAction()==KeyEvent.ACTION_DOWN && e.getRepeatCount()==0 && !e.isLongPress()){ pressHold(g); return false; }
   823	                if (e.getAction()==KeyEvent.ACTION_DOWN && (e.isLongPress() || e.getRepeatCount()>=3)){ appMenu(g, a, ix); return true; }
   824	                if (e.getAction()==KeyEvent.ACTION_UP){
   825	                    if (appMenu!=null) return true;
   826	                    if (pressHeld) pressUp(g);
   827	                    return false;
   828	                }
   829	            }
   830	            return false;
   831	        });
   832	        return g;
   833	    }
   834	    void markHomeSig(){
   835	        String sig=(editMode?"E":"N")+"|"+dragIdx+"|"+(drawerOpen?"D":"H")+"|";
   836	        if (fullOrder!=null) for (String[] a: fullOrder) sig += a[0]+"/"+a[1]+",";
   837	        homeBindSig=sig;
   838	    }
   839	    int tileIx(View v){
   840	        if (tiles!=null) for (int i=0;i<tiles.length;i++) if (tiles[i]==v) return i;
   841	        return homeGrid==null?-1:homeGrid.indexOfChild(v);
   842	    }
   843	    int slotX(int i){ return (i%6)*slotW; }
   844	    int slotY(int i){ return (i/6)*slotH; }
   845	    void layer(View v, boolean on){ View t=v instanceof GlowFrame? ((GlowFrame)v).inner:v; if (t!=null) t.setLayerType(on?View.LAYER_TYPE_HARDWARE:View.LAYER_TYPE_NONE, null); }
   846	    void retarget(int i){
   847	        View v=tiles[i];
   848	        tgtX[i]=slotX(i)-v.getLeft();
   849	        tgtY[i]=slotY(i)-v.getTop()+(v==dragV?-liftY:0);
   850	        if (v.getLayerType()!=View.LAYER_TYPE_HARDWARE) layer(v, true);
   851	    }
   852	    void kickGlide(){
   853	        if (!glideOn){ glideOn=true; android.view.Choreographer.getInstance().postFrameCallback(glideCb); }
   854	    }
   855	    void clearLift(View v, boolean settle){
  1320	            for (int r: refs){ float dd; if (Color.alpha(r)<40) dd=a/255f; else dd=(Math.abs(Color.red(q)-Color.red(r))+Math.abs(Color.green(q)-Color.green(r))+Math.abs(Color.blue(q)-Color.blue(r)))/(3*255f)*(a/255f); dist=Math.min(dist,dd); }
  1321	            int al=Math.round(Math.min(1f,Math.max(0f,(dist-0.10f)*3.2f))*255);
  1322	            px[i]=Color.argb(al,255,255,255);
  1323	            if (al>40){ if(x<x0)x0=x; if(x>x1)x1=x; if(y<y0)y0=y; if(y>y1)y1=y; } }
  1324	        Bitmap w=Bitmap.createBitmap(px,W,H,Bitmap.Config.ARGB_8888);
  1325	        if (x1>x0 && y1>y0) w=Bitmap.createBitmap(w,x0,y0,x1-x0+1,y1-y0+1);
  1326	        int pad=8; Bitmap out=Bitmap.createBitmap(w.getWidth()+pad*2,w.getHeight()+pad*2,Bitmap.Config.ARGB_8888); android.graphics.Canvas oc=new android.graphics.Canvas(out);
  1327	        android.graphics.Paint sh=new android.graphics.Paint(); sh.setMaskFilter(new android.graphics.BlurMaskFilter(5, android.graphics.BlurMaskFilter.Blur.NORMAL));
  1328	        int[] off=new int[2]; Bitmap a=w.extractAlpha(sh,off); android.graphics.Paint sp=new android.graphics.Paint(); sp.setColor(Color.argb(150,0,0,0)); oc.drawBitmap(a,pad+off[0],pad+off[1]+2,sp);
  1329	        oc.drawBitmap(w,pad,pad,null);
  1330	        glyphs.put(pkg,out); return out; }
  1331	
  1332	    void kickData(boolean postBind){
  1333	        final int g=++dataGen;
  1334	        dataFut=pool.submit(()->{
  1335	            Map<String,Long> nextUse=tv.queryUsage();
  1336	            List<String[]> apps=tv.queryApps();
  1337	            usage=nextUse;
  1338	            featuredCache=tv.collectFeatured(tv.queryPreviewFeatured());
  1339	            wnPickCache=tv.collectWatchNext();
  1340	            plutoIdsCache=tv.queryPlutoIds();
  1341	            appsCache=apps;
  1342	            StringBuilder sb=new StringBuilder(); List<String> keys=new ArrayList<>(usage.keySet()); Collections.sort(keys,(a,b)->Long.compare(use(b),use(a)));
  1343	            for (int i=0;i<Math.min(15,keys.size());i++) sb.append(keys.get(i)).append('=').append(use(keys.get(i))/60000).append("m ");
  1344	            android.util.Log.i("ShieldHome","usage "+sb);
  1345	            if (postBind) h.post(()->{ if (g==dataGen) bindFromCache(); });
  1346	            return null;
  1347	        });
  1348	    }
  1349	    void awaitData(){
  1350	        if (dataFut==null) return;
  1351	        try { dataFut.get(4, java.util.concurrent.TimeUnit.SECONDS); } catch (Exception e) { android.util.Log.w("ShieldHome","await "+e); }
  1352	    }
  1353	    void bindFromCache(){
  1354	        if (cardRow==null) return;
  1355	        buildHomeGrid();
  1356	        applyTabs();
  1357	        refreshCards();
  1358	        applyFeaturedCache();
  1359	        if (focus!=null) focus.afterData();
  1360	    }
  1361	    boolean playNextOn(){ return wnPickCache!=null && !wnPickCache.isEmpty(); }
  1362	    void applyTabs(){
  1363	        boolean pn=playNextOn();
  1364	        if (tabBar!=null) tabBar.showPlay(pn);
  1365	        if (!pn){
  1366	            if (tab==0) tab=1;
  1367	            if (tabPlay!=null && tabPlay.hasFocus() && tabLive!=null) tabLive.requestFocus();
  1368	        }
  1369	        if (tabBar!=null) tabBar.slide(false);
  1370	    }
  1371	    String cardSig(){
  1372	        StringBuilder b=new StringBuilder(tab==0?"P|":"L|");
  1373	        if (tab==0){ if (wnPickCache!=null) for (Object[] o: wnPickCache) b.append(o[0]).append('|').append(o[1]).append('|').append(o[2]).append(','); }
  1374	        else { if (plutoIdsCache!=null) b.append(plutoIdsCache); b.append('|').append(plutoSlug.size()); }
  1375	        return b.toString();
  1376	    }
  1377	    List<String[]> apps(){ return appsCache!=null?appsCache:tv.queryApps(); }
  1378	
  1379	    // ---------- Cards ----------
  1380	    void addCard(View v, int i){
  1381	        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(p(184.4f), p(112)); lp.bottomMargin=p(8); lp.leftMargin = i==0?0:p(8);
  1382	        if (v.getParent()==cardRow){ v.setLayoutParams(lp); return; }
  1383	        if (v.getParent()!=null) ((ViewGroup)v.getParent()).removeView(v);
  1384	        if (!(v.getLayoutParams() instanceof LinearLayout.LayoutParams)){
  1385	            final View.OnFocusChangeListener base = v.getOnFocusChangeListener();
  1386	            v.setOnFocusChangeListener((x,f)->{ if (base!=null) base.onFocusChange(x,f); if (f) snapCardRow(x); });
  1387	        }
  1388	        cardRow.addView(v, lp);
  1389	        if (v instanceof GlowFrame) ((GlowFrame)v).warm(p(184.4f), p(112));
  1390	    }
  1391	    void snapCardRow(View v){
  1392	        if (cardScroll==null) return;
  1393	        int idx=cardRow.indexOfChild(v); if (idx<0) return;
  1394	        int x=snapX(cardScroll.getScrollX(), idx, p(184.4f), p(8), p(954), cardRow.getChildCount());
  1395	        if (x!=cardScroll.getScrollX()) cardScroll.smoothScrollTo(x,0); else updateCardAlphas();
  1396	    }
  1397	    void resetCardRow(){
  1398	        if (focus!=null){ focus.lastIdx[3]=0; focus.lastCardX=0; }
  1399	        if (cardScroll!=null) cardScroll.scrollTo(0,0);
  1400	        if (cardRow!=null && cardRow.getChildCount()>0 && cardRow.indexOfChild(getCurrentFocus())>=0) cardRow.getChildAt(0).requestFocus();
  1401	        updateCardAlphas();
  1402	    }
  1403	    void updateCardAlphas(){
  1404	        if (cardRow==null) return;
  1405	        int sx=cardScroll==null?0:cardScroll.getScrollX(), stride=p(184.4f)+p(8);
  1406	        for (int i=0;i<cardRow.getChildCount();i++){
  1407	            View c=cardRow.getChildAt(i); float a=tileAlpha(i,sx,stride,5);
  1408	            if (c instanceof GlowFrame){
  1409	                GlowFrame g=(GlowFrame)c; g.fade=a; g.setAlpha(1f); g.inner.setAlpha(a); g.syncHalo(); g.invalidate();
  1410	                if (a>0.01f){
  1411	                    View iv=g.inner.getChildAt(0);
  1412	                    if (iv instanceof ImageView){ Object tg=iv.getTag();
  1413	                        if (tg instanceof String && (((String)tg).startsWith("pend:")||((String)tg).startsWith("pendf:"))){
  1414	                            boolean fit=((String)tg).startsWith("pendf:");
  1415	                            loadInto((ImageView)iv, ((String)tg).substring(fit?6:5), fit); } }
  1416	                }
  1417	            } else c.setAlpha(a);
  1418	        }
  1419	    }
  1420	    static float tileAlpha(int i, int sx, int stride, int nFit){
  1421	        if (stride<=0) return 1f;
  1422	        int b=sx/stride; float t=(sx-b*(float)stride)/stride;
  1423	        if (i<b) return 0f;
  1424	        if (i==b) return 1f-t;
  1425	        if (i<b+nFit) return 1f;
  1426	        if (i==b+nFit) return t;
  1427	        return 0f;
  1428	    }
  1429	    static int snapX(int sx, int idx, int cardW, int gap, int vis, int n){
  1430	        int stride=cardW+gap;
  1431	        if (stride<=0||n<=0) return 0;
  1432	        int nFit=Math.max(1,(vis+gap)/stride);
  1433	        int max=Math.max(0,n-nFit)*stride;
  1434	        int left=idx*stride, right=left+cardW;
  1435	        int target=sx;
  1436	        if (left<sx) target=left;
  1437	        else if (right>sx+vis) target=right-vis;
  1438	        target=Math.round(target/(float)stride)*stride;
  1439	        return Math.max(0, Math.min(target, max));
  1440	    }
  1441	
  1442	    View card(Drawable art, String artUri, boolean fitArt, String badgePkg, String caption, float progress, final Runnable click){
  1443	        FrameLayout f = new FrameLayout(this); f.setClipToOutline(true);
  1444	        GradientDrawable bg = new GradientDrawable(); bg.setCornerRadius(p(TILE_R)); bg.setColor(Color.argb(96,190,185,220)); bg.setStroke(Math.max(1,p(.6f)), Color.argb(60,255,255,255)); f.setBackground(bg);
  1445	        f.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
  1446	        ImageView iv = new ImageView(this); iv.setScaleType(ImageView.ScaleType.CENTER_CROP); if (art!=null) iv.setImageDrawable(art); if (fitArt){ iv.setScaleType(ImageView.ScaleType.FIT_CENTER); iv.setPadding(p(30),p(18),p(30),p(30)); } loadInto(iv, artUri, fitArt);
  1447	        f.addView(iv, new FrameLayout.LayoutParams(-1,-1));
  1448	        View shade = new View(this); shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{Color.TRANSPARENT, Color.argb(70,0,0,0), Color.argb(160,0,0,0), Color.argb(204,0,0,0)}));
  1449	        if (caption!=null || progress>=0) f.addView(shade, new FrameLayout.LayoutParams(-1, p(56), Gravity.BOTTOM));
  1450	        if (badgePkg != null){
  1451	            ImageView b = new ImageView(this); b.setScaleType(ImageView.ScaleType.CENTER_CROP); b.setClipToOutline(true);
  1452	            GradientDrawable bb = new GradientDrawable(); bb.setCornerRadius(p(4)); bb.setColor(Color.BLACK); b.setBackground(bb);
  1453	            try { Drawable d = pm.getApplicationBanner(badgePkg); if (d==null) d = pm.getApplicationIcon(badgePkg); b.setImageDrawable(d);} catch(Exception e){}
  1454	            f.addView(b, lp(p(36), p(20), Gravity.END|Gravity.TOP, 0, p(7), p(7), 0));
  1455	        }
  1456	        if (caption != null){
  1457	            TextView t = text(caption, 10.5f, Color.WHITE, "sans-serif-medium"); t.setSingleLine(); t.setEllipsize(android.text.TextUtils.TruncateAt.END);
  1458	            f.addView(t, lp(-1,-2,Gravity.BOTTOM, p(9), 0, p(9), p(progress>=0?14:9)));
  1459	        }
  1460	        if (progress >= 0){
  1461	            FrameLayout bar = new FrameLayout(this); bar.setBackgroundColor(Color.argb(90,255,255,255));
  1462	            View fill = new View(this); fill.setBackgroundColor(Color.rgb(229,9,20));
  1463	            bar.addView(fill, new FrameLayout.LayoutParams(Math.round(p(166)*progress), -1));
  1464	            f.addView(bar, lp(p(166), p(2.5f), Gravity.BOTTOM, p(9), 0, 0, p(8)));
  1465	        }
  1466	        return focusable(f, bg, TILE_R, click);
  1467	    }
  1468	
  1469	    void refreshCards(){
  1470	        if (cardRow==null) return;
  1471	        if (tab==0 && !playNextOn()) applyTabs();
  1472	        if (tab==1 && liveCache!=null && !liveCache.isEmpty()){
  1473	            showLiveCache(); cardsBindSig=cardSig(); h.post(this::updateCardAlphas); return;
  1474	        }
  1475	        String next=cardSig();
  1476	        if (next.equals(cardsBindSig) && cardRow.getChildCount()>0) return;
  1477	        cardsBindSig=next;
  1478	        cardRow.removeAllViews();
  1479	        if (cardScroll!=null) cardScroll.scrollTo(0,0);
  1480	        int n = tab==0 ? loadWatchNext() : loadLive();
  1481	        if (n>0){ h.post(this::updateCardAlphas); return; }
  1482	        if (tab==0){ applyTabs(); if (tab==1){ loadLive(); h.post(this::updateCardAlphas); } return; }
  1483	        int i=0;
  1484	        for (final String pk : (tab==0?PLAY_PKGS:LIVE_PKGS)){
  1485	            if (!installed(pk)) continue;
  1486	            Drawable art=null; try { art = pm.getApplicationBanner(pk); if (art==null){ Intent li=pm.getLeanbackLaunchIntentForPackage(pk); if(li!=null) art=pm.getActivityBanner(li);} if(art==null) art=pm.getApplicationIcon(pk);} catch(Exception e){}
  1487	            String cap = tab==1 && pk.equals("com.google.android.tv") ? "Free channels" : null;
  1488	            View v=card(art, null, false, null, cap, -1, ()->launchPkg(pk)); addCard(v, i++);
  1489	        }
  1490	        h.post(this::updateCardAlphas);
  1491	    }
  1492	
  1493	    int loadWatchNext(){
  1494	        List<Object[]> pick=wnPickCache;
  1495	        if (pick==null||pick.isEmpty()) return 0;

codex
## Efficiency

- [MainActivity.java:570](/tmp/codexreview/shieldhome/src/com/chris/shieldhome/MainActivity.java:570) — **low** — `HeroDots.onDraw()` creates a new `Paint` on every redraw. Keep one `Paint` as a view field and update its color as needed.
- [MainActivity.java:530](/tmp/codexreview/shieldhome/src/com/chris/shieldhome/MainActivity.java:530) — **low** — `SegBar.dispatchDraw()` allocates `Paint` objects during every draw. Cache the paints as fields; similarly reuse the shadow paint at [MainActivity.java:539](/tmp/codexreview/shieldhome/src/com/chris/shieldhome/MainActivity.java:539).
- [MainActivity.java:604](/tmp/codexreview/shieldhome/src/com/chris/shieldhome/MainActivity.java:604) — **med** — Each focus change creates a `DecelerateInterpolator` and `ValueAnimator`, without cancelling the previous glow animator. Keep the animator per `GlowFrame`, cancel or retarget it, and reuse a view-level interpolator.
- [MainActivity.java:382](/tmp/codexreview/shieldhome/src/com/chris/shieldhome/MainActivity.java:382) — **low** — `PillView.onFocusChanged()` constructs an interpolator on each focus change. Reuse one.
- [MainActivity.java:1697](/tmp/codexreview/shieldhome/shieldhome/src/com/chris/shieldhome/MainActivity.java:1697) — **med** — `useWhatsNew()` repeatedly constructs date formatters and parses the current date inside the item loop. Create the formatters and compute today’s date once per call; parse each item date once and reuse it for `avail`.
- [FocusController.java:156](/tmp/codexreview/shieldhome/src/com/chris/shieldhome/FocusController.java:156) — **low** — `secRoot()` builds a section list just to find an ancestor, then `indexOf()` rebuilds the same list in `remember()`/navigation. Resolve the section child directly and avoid repeated list creation on key events.
- [MainActivity.java:1380](/tmp/codexreview/shieldhome/src/com/chris/shieldhome/MainActivity.java:1380) — **med** — `addCard()` calls `setLayoutParams()` even when an existing card already has identical dimensions and margins, triggering layout work. Compare parameters first or only update when the card’s position/size changed.

## Structure

- [MainActivity.java:18](/tmp/codexreview/shieldhome/src/com/chris/shieldhome/MainActivity.java:18) — **high** — `MainActivity` is an 1,804-line god class combining view construction/drawing, focus and input, app-grid editing, card binding, Pluto/network data, hero rotation, search, and dimming. Extract cohesive controllers, starting with card/live-TV data and binding, then hero UI/rotation and search/dimmer behavior; keep the activity as lifecycle and coordination glue.
- [MainActivity.java:1503](/tmp/codexreview/shieldhome/shieldhome/src/com/chris/shieldhome/MainActivity.java:1503) — **med** — Pluto state is stored in parallel maps and `List<Object[]>` records, making relationships and index meanings fragile. Use typed channel/card model classes and move fetch/parse/cache logic into a Pluto data component.
- [TvData.java:55](/tmp/codexreview/shieldhome/shieldhome/src/com/chris/shieldhome/TvData.java:55) — **med** — TV rows are represented as positional `Object[]` values throughout the data pipeline. Replace them with named model types so callers don’t rely on numeric indexes and casts.
- [FocusController.java:156](/tmp/codexreview/shieldhome/shieldhome/src/com/chris/shieldhome/FocusController.java:156) — **low** — `secItems()` repeats visibility/focusability filtering for card and app sections. A shared helper would make section enumeration easier to maintain.

## Cleanup

- [MainActivity.java:385](/tmp/codexreview/shieldhome/src/com/chris/shieldhome/MainActivity.java:385) — **low** — `lit` is computed but never used; remove it.
- [MainActivity.java:600](/tmp/codexreview/shieldhome/shieldhome/src/com/chris/shieldhome/MainActivity.java:600) — **low** — The `base` parameter to both `focusable()` overloads is unused. Remove it and update call sites.
- [MainActivity.java:796](/tmp/codexreview/shieldhome/shieldhome/src/com/chris/shieldhome/MainActivity.java:796) — **low** — `homeGrid.setLayoutParams()` is called after every rebuild even if the row count and height are unchanged. Skip the update when the computed height matches.
- [MainActivity.java:1484](/tmp/codexreview/shieldhome/shieldhome/src/com/chris/shieldhome/MainActivity.java:1484) — **low** — After the preceding fallback logic, this loop only runs for the live tab, so the `tab==0 ? PLAY_PKGS : LIVE_PKGS` branch is redundant.
- [NetData.java:62](/tmp/codexreview/shieldhome/shieldhome/src/com/chris/shieldhome/NetData.java:62) — **low** — The date offsets and fetch/cache durations are embedded literals across the data code. Name the schedule offsets and timing values as constants to make their intent and tuning clear.
- [MainActivity.java:1241](/tmp/codexreview/shieldhome/shieldhome/src/com/chris/shieldhome/MainActivity.java:1241) — **low** — `16000` is repeated for hero rotation scheduling. Define a named rotation interval; likewise name repeated animation durations and card/grid sizing values where they represent shared design decisions.
tokens used
51,666
## Efficiency

- [MainActivity.java:570](/tmp/codexreview/shieldhome/src/com/chris/shieldhome/MainActivity.java:570) — **low** — `HeroDots.onDraw()` creates a new `Paint` on every redraw. Keep one `Paint` as a view field and update its color as needed.
- [MainActivity.java:530](/tmp/codexreview/shieldhome/src/com/chris/shieldhome/MainActivity.java:530) — **low** — `SegBar.dispatchDraw()` allocates `Paint` objects during every draw. Cache the paints as fields; similarly reuse the shadow paint at [MainActivity.java:539](/tmp/codexreview/shieldhome/src/com/chris/shieldhome/MainActivity.java:539).
- [MainActivity.java:604](/tmp/codexreview/shieldhome/src/com/chris/shieldhome/MainActivity.java:604) — **med** — Each focus change creates a `DecelerateInterpolator` and `ValueAnimator`, without cancelling the previous glow animator. Keep the animator per `GlowFrame`, cancel or retarget it, and reuse a view-level interpolator.
- [MainActivity.java:382](/tmp/codexreview/shieldhome/src/com/chris/shieldhome/MainActivity.java:382) — **low** — `PillView.onFocusChanged()` constructs an interpolator on each focus change. Reuse one.
- [MainActivity.java:1697](/tmp/codexreview/shieldhome/shieldhome/src/com/chris/shieldhome/MainActivity.java:1697) — **med** — `useWhatsNew()` repeatedly constructs date formatters and parses the current date inside the item loop. Create the formatters and compute today’s date once per call; parse each item date once and reuse it for `avail`.
- [FocusController.java:156](/tmp/codexreview/shieldhome/src/com/chris/shieldhome/FocusController.java:156) — **low** — `secRoot()` builds a section list just to find an ancestor, then `indexOf()` rebuilds the same list in `remember()`/navigation. Resolve the section child directly and avoid repeated list creation on key events.
- [MainActivity.java:1380](/tmp/codexreview/shieldhome/src/com/chris/shieldhome/MainActivity.java:1380) — **med** — `addCard()` calls `setLayoutParams()` even when an existing card already has identical dimensions and margins, triggering layout work. Compare parameters first or only update when the card’s position/size changed.

## Structure

- [MainActivity.java:18](/tmp/codexreview/shieldhome/src/com/chris/shieldhome/MainActivity.java:18) — **high** — `MainActivity` is an 1,804-line god class combining view construction/drawing, focus and input, app-grid editing, card binding, Pluto/network data, hero rotation, search, and dimming. Extract cohesive controllers, starting with card/live-TV data and binding, then hero UI/rotation and search/dimmer behavior; keep the activity as lifecycle and coordination glue.
- [MainActivity.java:1503](/tmp/codexreview/shieldhome/shieldhome/src/com/chris/shieldhome/MainActivity.java:1503) — **med** — Pluto state is stored in parallel maps and `List<Object[]>` records, making relationships and index meanings fragile. Use typed channel/card model classes and move fetch/parse/cache logic into a Pluto data component.
- [TvData.java:55](/tmp/codexreview/shieldhome/shieldhome/src/com/chris/shieldhome/TvData.java:55) — **med** — TV rows are represented as positional `Object[]` values throughout the data pipeline. Replace them with named model types so callers don’t rely on numeric indexes and casts.
- [FocusController.java:156](/tmp/codexreview/shieldhome/shieldhome/src/com/chris/shieldhome/FocusController.java:156) — **low** — `secItems()` repeats visibility/focusability filtering for card and app sections. A shared helper would make section enumeration easier to maintain.

## Cleanup

- [MainActivity.java:385](/tmp/codexreview/shieldhome/src/com/chris/shieldhome/MainActivity.java:385) — **low** — `lit` is computed but never used; remove it.
- [MainActivity.java:600](/tmp/codexreview/shieldhome/shieldhome/src/com/chris/shieldhome/MainActivity.java:600) — **low** — The `base` parameter to both `focusable()` overloads is unused. Remove it and update call sites.
- [MainActivity.java:796](/tmp/codexreview/shieldhome/shieldhome/src/com/chris/shieldhome/MainActivity.java:796) — **low** — `homeGrid.setLayoutParams()` is called after every rebuild even if the row count and height are unchanged. Skip the update when the computed height matches.
- [MainActivity.java:1484](/tmp/codexreview/shieldhome/shieldhome/src/com/chris/shieldhome/MainActivity.java:1484) — **low** — After the preceding fallback logic, this loop only runs for the live tab, so the `tab==0 ? PLAY_PKGS : LIVE_PKGS` branch is redundant.
- [NetData.java:62](/tmp/codexreview/shieldhome/shieldhome/src/com/chris/shieldhome/NetData.java:62) — **low** — The date offsets and fetch/cache durations are embedded literals across the data code. Name the schedule offsets and timing values as constants to make their intent and tuning clear.
- [MainActivity.java:1241](/tmp/codexreview/shieldhome/shieldhome/src/com/chris/shieldhome/MainActivity.java:1241) — **low** — `16000` is repeated for hero rotation scheduling. Define a named rotation interval; likewise name repeated animation durations and card/grid sizing values where they represent shared design decisions.
