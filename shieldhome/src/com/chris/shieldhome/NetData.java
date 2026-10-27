package com.chris.shieldhome;

import java.text.SimpleDateFormat;
import java.util.*;

/** TVmaze + weather HTTP. URL cache. */
class NetData {
    final MainActivity a;
    final Map<String, String> httpCache = new java.util.concurrent.ConcurrentHashMap<>();
    static final String[][] WEB_APPS = {{"Netflix","com.netflix.ninja"},{"Hulu","com.hulu.livingroomplus"},{"Disney+","com.disney.disneyplus"},{"Prime Video","com.amazon.amazonvideo.livingroom"},{"Amazon","com.amazon.amazonvideo.livingroom"},{"HBO Max","com.wbd.stream"},{"Max","com.wbd.stream"},{"Crunchyroll","com.crunchyroll.crunchyroid"},{"STARZ","com.bydeluxe.d3.android.program.starz"},{"Starz","com.bydeluxe.d3.android.program.starz"},{"Paramount+","com.cbs.ott"},{"Peacock","com.peacocktv.peacockandroid"},{"Apple TV+","com.apple.atve.androidtv.appletv"},{"YouTube","com.google.android.youtube.tv"}};

    NetData(MainActivity a) { this.a = a; }

    String webPkg(String net) {
        if (net == null) return null;
        for (String[] w : WEB_APPS)
            if (net.equalsIgnoreCase(w[0])) {
                String pk = w[1];
                if (pk.startsWith("com.amazon") && !a.installed(pk) && a.installed(pk + ".nvidia")) pk = pk + ".nvidia";
                return a.installed(pk) ? pk : null;
            }
        return null;
    }

    void loadWhatsNew() {
        String cached = a.prefs().getString("wn_json", null);
        long at = a.prefs().getLong("wn_at", 0);
        if (cached != null && System.currentTimeMillis() - at < 6 * 3600 * 1000L) { a.hero.useWhatsNew(cached); return; }
        final int g = a.dataGen;
        a.pool.execute(() -> {
            String js = null;
            try { js = fetchWhatsNew(); } catch (Exception e) { android.util.Log.w("ShieldHome", "whatsnew " + e); }
            final String fj = js != null ? js : cached;
            if (js != null) a.prefs().edit().putString("wn_json", js).putLong("wn_at", System.currentTimeMillis()).apply();
            a.h.post(() -> { if (g == a.dataGen) a.hero.useWhatsNew(fj); });
        });
    }

    String httpGet(String url) throws Exception {
        String hit = httpCache.get(url);
        if (hit != null) return hit;
        java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        c.setConnectTimeout(6000);
        c.setReadTimeout(8000);
        c.setRequestProperty("User-Agent", "ShieldHome/6");
        try (java.io.InputStream in = c.getInputStream()) {
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) bo.write(b, 0, n);
            String s = bo.toString("UTF-8");
            httpCache.put(url, s);
            return s;
        }
    }

    String fetchWhatsNew() throws Exception {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        Map<String, List<org.json.JSONObject>> byPkg = new LinkedHashMap<>();
        Set<Integer> seen = new HashSet<>();
        long now = System.currentTimeMillis();
        for (int dd : new int[]{0, 1, -1, 2, 3, -2}) {
            String day = df.format(new Date(now + dd * 86400000L));
            org.json.JSONArray arr1 = new org.json.JSONArray(httpGet("https://api.tvmaze.com/schedule/web?date=" + day + "&country=US"));
            org.json.JSONArray arr2 = new org.json.JSONArray(httpGet("https://api.tvmaze.com/schedule/web?date=" + day));
            for (org.json.JSONArray arr : new org.json.JSONArray[]{arr1, arr2})
                for (int i = 0; i < arr.length(); i++) {
                    org.json.JSONObject ep = arr.getJSONObject(i);
                    org.json.JSONObject sh = ep.optJSONObject("_embedded");
                    if (sh == null) continue;
                    sh = sh.optJSONObject("show");
                    if (sh == null) continue;
                    org.json.JSONObject wc = sh.optJSONObject("webChannel");
                    String pkg = webPkg(wc == null ? null : wc.optString("name"));
                    if (pkg == null || !seen.add(sh.optInt("id"))) continue;
                    String lang = sh.optString("language", "");
                    if (!lang.isEmpty() && !"English".equals(lang)) continue;
                    org.json.JSONObject o = new org.json.JSONObject();
                    o.put("pkg", pkg).put("net", wc.optString("name")).put("show", sh.optString("name")).put("id", sh.optInt("id")).put("season", ep.optInt("season")).put("num", ep.optInt("number")).put("date", ep.optString("airdate")).put("dd", dd)
                            .put("summary", android.text.Html.fromHtml(sh.optString("summary", ""), 0).toString().trim())
                            .put("weight", sh.optJSONObject("rating") != null && !sh.getJSONObject("rating").isNull("average") ? sh.getJSONObject("rating").optDouble("average", 0) : 0)
                            .put("img", sh.optJSONObject("image") != null ? sh.getJSONObject("image").optString("original") : "");
                    if (!byPkg.containsKey(pkg)) byPkg.put(pkg, new ArrayList<>());
                    byPkg.get(pkg).add(o);
                }
            int total = 0;
            for (List<org.json.JSONObject> l : byPkg.values()) total += l.size();
            if (total >= 40) break;
        }
        for (List<org.json.JSONObject> l : byPkg.values())
            Collections.sort(l, (x, y) -> Double.compare(y.optDouble("weight"), x.optDouble("weight")));
        List<org.json.JSONObject> pick = new ArrayList<>();
        for (int r = 0; r < 4 && pick.size() < 10; r++)
            for (List<org.json.JSONObject> l : byPkg.values())
                if (l.size() > r && pick.size() < 10) pick.add(l.get(r));
        org.json.JSONArray out = new org.json.JSONArray();
        for (org.json.JSONObject o : pick) {
            try {
                org.json.JSONArray im = new org.json.JSONArray(httpGet("https://api.tvmaze.com/shows/" + o.getInt("id") + "/images"));
                String best = null;
                int bw = 0;
                for (int i = 0; i < im.length(); i++) {
                    org.json.JSONObject x = im.getJSONObject(i);
                    if (!"background".equals(x.optString("type"))) continue;
                    org.json.JSONObject res = x.optJSONObject("resolutions");
                    org.json.JSONObject org1 = res == null ? null : res.optJSONObject("original");
                    if (org1 == null) continue;
                    int w = org1.optInt("width");
                    if (w > bw && w <= 3000) { bw = w; best = org1.optString("url"); }
                }
                if (best != null) o.put("img", best);
            } catch (Exception e) {}
            out.put(o);
        }
        android.util.Log.i("ShieldHome", "whatsnew items=" + out.length() + " apps=" + byPkg.keySet());
        return out.toString();
    }

    void fetchWeather() {
        final int g = a.dataGen;
        a.pool.execute(() -> {
            try {
                double lat, lon;
                String city;
                try {
                    org.json.JSONObject j = new org.json.JSONObject(httpGet("https://ipapi.co/json/"));
                    lat = j.getDouble("latitude");
                    lon = j.getDouble("longitude");
                    city = j.optString("city", "");
                } catch (Exception e) {
                    org.json.JSONObject j = new org.json.JSONObject(httpGet("https://ipwho.is/"));
                    lat = j.getDouble("latitude");
                    lon = j.getDouble("longitude");
                    city = j.optString("city", "");
                }
                org.json.JSONObject cw = new org.json.JSONObject(httpGet("https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon + "&current_weather=true&temperature_unit=fahrenheit")).getJSONObject("current_weather");
                a.prefs().edit().putInt("wx_t", (int) Math.round(cw.getDouble("temperature"))).putInt("wx_code", cw.getInt("weathercode")).putInt("wx_day", cw.optInt("is_day", 1)).putString("wx_city", city).putLong("wx_at", System.currentTimeMillis()).apply();
                android.util.Log.i("ShieldHome", "weather ok code=" + cw.getInt("weathercode"));
                a.h.post(() -> { if (g == a.dataGen) a.showWeather(); });
            } catch (Exception e) { android.util.Log.w("ShieldHome", "weather " + e); }
        });
    }
}
