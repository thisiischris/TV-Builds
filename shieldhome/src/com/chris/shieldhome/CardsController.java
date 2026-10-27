package com.chris.shieldhome;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.*;

class CardsController {
    final MainActivity a;
    static final String[] LIVE_PKGS = {"tv.pluto.android","com.cnbc.client","com.haystack.android","com.plexapp.android"};
    String cardsBindSig="";
    Map<String,Long> plutoIdsCache=new HashMap<>();
    CardsController(MainActivity a) { this.a = a; }

    String cardSig(){
        StringBuilder b=new StringBuilder(a.tab==0?"P|":"L|");
        if (a.tab==0){ if (a.wnPickCache!=null) for (Object[] o: a.wnPickCache) b.append(o[0]).append('|').append(o[1]).append('|').append(o[2]).append(','); }
        else { if (plutoIdsCache!=null) b.append(plutoIdsCache); b.append('|').append(plutoSlug.size()); }
        return b.toString();
    }
    // ---------- Cards ----------
    void addCard(View v, int i){
        int width=a.p(184.4f), height=a.p(112), bottom=a.p(8), left=i==0?0:a.p(8);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, height); lp.bottomMargin=bottom; lp.leftMargin=left;
        if (v.getParent()==a.cardRow){ ViewGroup.LayoutParams old=v.getLayoutParams(); if (!(old instanceof LinearLayout.LayoutParams) || old.width!=width || old.height!=height || ((LinearLayout.LayoutParams)old).bottomMargin!=bottom || ((LinearLayout.LayoutParams)old).leftMargin!=left) v.setLayoutParams(lp); return; }
        if (v.getParent()!=null) ((ViewGroup)v.getParent()).removeView(v);
        if (!(v.getLayoutParams() instanceof LinearLayout.LayoutParams)){
            final View.OnFocusChangeListener base = v.getOnFocusChangeListener();
            v.setOnFocusChangeListener((x,f)->{ if (base!=null) base.onFocusChange(x,f); if (f) snapCardRow(x); });
        }
        a.cardRow.addView(v, lp);
        if (v instanceof MainActivity.GlowFrame) ((MainActivity.GlowFrame)v).warm(a.p(184.4f), a.p(112));
    }
    void snapCardRow(View v){
        if (a.cardScroll==null) return;
        int idx=a.cardRow.indexOfChild(v); if (idx<0) return;
        int x=snapX(a.cardScroll.getScrollX(), idx, a.p(184.4f), a.p(8), a.p(954), a.cardRow.getChildCount());
        if (x!=a.cardScroll.getScrollX()) a.cardScroll.smoothScrollTo(x,0); else updateCardAlphas();
    }
    void resetCardRow(){
        if (a.focus!=null){ a.focus.lastIdx[3]=0; a.focus.lastCardX=0; }
        if (a.cardScroll!=null) a.cardScroll.scrollTo(0,0);
        if (a.cardRow!=null && a.cardRow.getChildCount()>0 && a.cardRow.indexOfChild(a.getCurrentFocus())>=0) a.cardRow.getChildAt(0).requestFocus();
        updateCardAlphas();
    }
    void updateCardAlphas(){
        if (a.cardRow==null) return;
        int sx=a.cardScroll==null?0:a.cardScroll.getScrollX(), stride=a.p(184.4f)+a.p(8);
        for (int i=0;i<a.cardRow.getChildCount();i++){
            View c=a.cardRow.getChildAt(i); float a=tileAlpha(i,sx,stride,5);
            if (c instanceof MainActivity.GlowFrame){
                MainActivity.GlowFrame g=(MainActivity.GlowFrame)c; g.fade=a; g.setAlpha(1f); g.inner.setAlpha(a); g.syncHalo(); g.invalidate();
                if (a>0.01f){
                    View iv=g.inner.getChildAt(0);
                    if (iv instanceof ImageView){ Object tg=iv.getTag();
                        if (tg instanceof String && (((String)tg).startsWith("pend:")||((String)tg).startsWith("pendf:"))){
                            boolean fit=((String)tg).startsWith("pendf:");
                            this.a.loadInto((ImageView)iv, ((String)tg).substring(fit?6:5), fit); } }
                }
            } else c.setAlpha(a);
        }
    }
    static float tileAlpha(int i, int sx, int stride, int nFit){
        if (stride<=0) return 1f;
        int b=sx/stride; float t=(sx-b*(float)stride)/stride;
        if (i<b) return 0f;
        if (i==b) return 1f-t;
        if (i<b+nFit) return 1f;
        if (i==b+nFit) return t;
        return 0f;
    }
    static int snapX(int sx, int idx, int cardW, int gap, int vis, int n){
        int stride=cardW+gap;
        if (stride<=0||n<=0) return 0;
        int nFit=Math.max(1,(vis+gap)/stride);
        int max=Math.max(0,n-nFit)*stride;
        int left=idx*stride, right=left+cardW;
        int target=sx;
        if (left<sx) target=left;
        else if (right>sx+vis) target=right-vis;
        target=Math.round(target/(float)stride)*stride;
        return Math.max(0, Math.min(target, max));
    }

    View card(Drawable art, String artUri, boolean fitArt, String badgePkg, String caption, float progress, final Runnable click){
        FrameLayout f = new FrameLayout(a); f.setClipToOutline(true);
        GradientDrawable bg = new GradientDrawable(); bg.setCornerRadius(a.p(a.TILE_R)); bg.setColor(Color.argb(96,190,185,220)); bg.setStroke(Math.max(1,a.p(.6f)), Color.argb(60,255,255,255)); f.setBackground(bg);
        f.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        ImageView iv = new ImageView(a); iv.setScaleType(ImageView.ScaleType.CENTER_CROP); if (art!=null) iv.setImageDrawable(art); if (fitArt){ iv.setScaleType(ImageView.ScaleType.FIT_CENTER); iv.setPadding(a.p(30),a.p(18),a.p(30),a.p(30)); } a.loadInto(iv, artUri, fitArt);
        f.addView(iv, new FrameLayout.LayoutParams(-1,-1));
        View shade = new View(a); shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{Color.TRANSPARENT, Color.argb(70,0,0,0), Color.argb(160,0,0,0), Color.argb(204,0,0,0)}));
        if (caption!=null || progress>=0) f.addView(shade, new FrameLayout.LayoutParams(-1, a.p(56), Gravity.BOTTOM));
        if (badgePkg != null){
            ImageView b = new ImageView(a); b.setScaleType(ImageView.ScaleType.CENTER_CROP); b.setClipToOutline(true);
            GradientDrawable bb = new GradientDrawable(); bb.setCornerRadius(a.p(4)); bb.setColor(Color.BLACK); b.setBackground(bb);
            try { Drawable d = a.pm.getApplicationBanner(badgePkg); if (d==null) d = a.pm.getApplicationIcon(badgePkg); b.setImageDrawable(d);} catch(Exception e){}
            f.addView(b, a.lp(a.p(36), a.p(20), Gravity.END|Gravity.TOP, 0, a.p(7), a.p(7), 0));
        }
        if (caption != null){
            TextView t = a.text(caption, 10.5f, Color.WHITE, "sans-serif-medium"); t.setSingleLine(); t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            f.addView(t, a.lp(-1,-2,Gravity.BOTTOM, a.p(9), 0, a.p(9), a.p(progress>=0?14:9)));
        }
        if (progress >= 0){
            FrameLayout bar = new FrameLayout(a); bar.setBackgroundColor(Color.argb(90,255,255,255));
            View fill = new View(a); fill.setBackgroundColor(Color.rgb(229,9,20));
            bar.addView(fill, new FrameLayout.LayoutParams(Math.round(a.p(166)*progress), -1));
            f.addView(bar, a.lp(a.p(166), a.p(2.5f), Gravity.BOTTOM, a.p(9), 0, 0, a.p(8)));
        }
        return a.focusable(f, a.TILE_R, click);
    }

    void refreshCards(){
        if (a.cardRow==null) return;
        if (a.tab==0 && !a.playNextOn()) a.applyTabs();
        if (a.tab==1 && liveCache!=null && !liveCache.isEmpty()){
            showLiveCache(); cardsBindSig=cardSig(); a.h.post(this::updateCardAlphas); return;
        }
        String next=cardSig();
        if (next.equals(cardsBindSig) && a.cardRow.getChildCount()>0) return;
        cardsBindSig=next;
        a.cardRow.removeAllViews();
        if (a.cardScroll!=null) a.cardScroll.scrollTo(0,0);
        int n = a.tab==0 ? loadWatchNext() : loadLive();
        if (n>0){ a.h.post(this::updateCardAlphas); return; }
        if (a.tab==0){ a.applyTabs(); if (a.tab==1){ loadLive(); a.h.post(this::updateCardAlphas); } return; }
        int i=0;
        for (final String pk : LIVE_PKGS){
            if (!a.installed(pk)) continue;
            Drawable art=null; try { art = a.pm.getApplicationBanner(pk); if (art==null){ Intent li=a.pm.getLeanbackLaunchIntentForPackage(pk); if(li!=null) art=a.pm.getActivityBanner(li);} if(art==null) art=a.pm.getApplicationIcon(pk);} catch(Exception e){}
            String cap = a.tab==1 && pk.equals("com.google.android.tv") ? "Free channels" : null;
            View v=card(art, null, false, null, cap, -1, ()->a.launchPkg(pk)); addCard(v, i++);
        }
        a.h.post(this::updateCardAlphas);
    }

    int loadWatchNext(){
        List<Object[]> pick=a.wnPickCache;
        if (pick==null||pick.isEmpty()) return 0;
        int n=0;
        for (Object[] o: pick){ final Intent fi=new Intent((Intent)o[3]).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); final String pk2=(String)o[0]; boolean isNew=o.length>5;
            View v = card(null,(String)o[2],false,pk2,(String)o[1], isNew?-1f:(Float)o[4], ()->{try{a.startActivity(fi);}catch(Exception e){ a.launchPkg(pk2);} });
            addCard(v, n++); }
        android.util.Log.i("ShieldHome","playnext shown="+n);
        return n;
    }
    List<Object[]> plutoCaps = new ArrayList<>(); Map<String,String> plutoNow = new HashMap<>(); Map<String,String> plutoArt = new HashMap<>(); Map<String,String> plutoLogo = new HashMap<>(); final Map<String,String> plutoSlug = new java.util.concurrent.ConcurrentHashMap<>(); long plutoAt=0;
    List<View> liveCache; final Set<String> liveSlugs = new LinkedHashSet<>();
    // Pluto doesn't publish programs to TvContract (0 readable rows), so read what's on now from Pluto's public guide.
    void fillPlutoNow(final List<Object[]> caps){
        if (System.currentTimeMillis()-plutoAt < 10*60000L && !plutoNow.isEmpty()){ applyLiveNow(); return; }
        a.pool.execute(()->{ try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US); f.setTimeZone(TimeZone.getTimeZone("UTC"));
            long now = System.currentTimeMillis();
            java.net.HttpURLConnection c=(java.net.HttpURLConnection)new java.net.URL("https://api.pluto.tv/v2/channels?start="+f.format(new Date(now))+"&stop="+f.format(new Date(now+60000))).openConnection();
            c.setConnectTimeout(6000); c.setReadTimeout(20000);
            java.io.InputStream in = c.getInputStream(); java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream(); byte[] buf=new byte[65536]; int r; while ((r=in.read(buf))>0) bo.write(buf,0,r); in.close();
            org.json.JSONArray channels = new org.json.JSONArray(bo.toString("UTF-8")); final Map<String,String> m = new HashMap<>(), art = new HashMap<>(), logos = new HashMap<>();
            Set<String> ban = new HashSet<>(); Map<String,Integer> shared = new HashMap<>();
            for (int i=0;i<channels.length();i++){ org.json.JSONObject ch=channels.getJSONObject(i);
                addBan(ban, ch.optJSONObject("featuredImage")); addBan(ban, ch.optJSONObject("logo")); addBan(ban, ch.optJSONObject("thumbnail")); addBan(ban, ch.optJSONObject("tile"));
                String logo=jsonPath(ch.optJSONObject("colorLogoPNG")); if (logo==null) logo=jsonPath(ch.optJSONObject("logo"));
                if (logo!=null) logos.put(ch.optString("name"), logo);
                org.json.JSONArray tl=ch.optJSONArray("timelines");
                if (tl!=null && tl.length()>0){ String first=plutoGuideArt(tl.getJSONObject(0), ban); if (first!=null){ String b=bareUrl(first); shared.put(b, (shared.containsKey(b)?shared.get(b):0)+1); } }
            }
            for (Map.Entry<String,Integer> e: shared.entrySet()) if (e.getValue()>=4) ban.add(e.getKey());
            for (int i=0;i<channels.length();i++){ org.json.JSONObject ch=channels.getJSONObject(i); org.json.JSONArray tl=ch.optJSONArray("timelines");
                if (tl!=null && tl.length()>0){ org.json.JSONObject t0=tl.getJSONObject(0); m.put(ch.optString("name"), t0.optString("title")); String img=plutoGuideArt(t0, ban); if (img!=null) art.put(ch.optString("name"), img); }
                String sl=ch.optString("slug",null); if (sl!=null) plutoSlug.put(ch.optString("name"), sl); }
            android.util.Log.i("ShieldHome","pluto now: "+m.size()+" channels art="+art.size()+" logos="+logos.size());
            a.h.post(()->{ plutoNow.clear(); plutoNow.putAll(m); plutoArt.clear(); plutoArt.putAll(art); plutoLogo.clear(); plutoLogo.putAll(logos); plutoAt=System.currentTimeMillis(); applyLiveNow(); });
        } catch (Exception e){ android.util.Log.w("ShieldHome","pluto guide "+e); } });
    }
    static String plutoGuideArt(org.json.JSONObject t0, Set<String> ban){
        org.json.JSONObject ep=t0.optJSONObject("episode"); if (ep==null) return null;
        org.json.JSONObject se=ep.optJSONObject("series");
        String[] cand={ jsonPath(ep.optJSONObject("poster16_9")), jsonPath(ep.optJSONObject("thumbnail")),
            se==null?null:jsonPath(se.optJSONObject("tile")), se==null?null:jsonPath(se.optJSONObject("featuredImage")),
            jsonPath(ep.optJSONObject("featuredImage")) };
        for (String u: cand) if (!genericArt(u, ban)) return u;
        return null;
    }
    static void addBan(Set<String> ban, org.json.JSONObject o){ String u=jsonPath(o); if (u!=null){ ban.add(u); ban.add(bareUrl(u)); } }
    static String bareUrl(String u){ if (u==null) return ""; int q=u.indexOf('?'); return q<0?u:u.substring(0,q); }
    static boolean genericArt(String u, Set<String> ban){
        if (u==null || u.isEmpty()) return true;
        String s=u.toLowerCase(Locale.US), b=bareUrl(s);
        if (s.contains("default") || s.contains("placeholder") || s.contains("pluto-default") || s.contains("/channels/")) return true;
        return ban!=null && (ban.contains(u) || ban.contains(b) || ban.contains(bareUrl(u)));
    }
    static String jsonPath(org.json.JSONObject o){ if (o==null) return null; String p=o.optString("path",""); return p.isEmpty()?null:p; }
    static final String[] LIVE_SLUGS = {"cnn-headlines","nbc-news-now","abc-news-live","cbs-news"};
    String slugify(String n){ return n.toLowerCase(Locale.US).replace("+","").replaceAll("[^a-z0-9]+","-").replaceAll("(^-|-$)",""); }
    Intent plutoIntent(String slug){ return new Intent(Intent.ACTION_VIEW, Uri.parse("https://pluto.tv/live-tv"+(slug==null?"":"/"+slug))).setPackage("tv.pluto.android").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); }
    void startSafe(Intent in, String fallbackPkg){ try { if (in.resolveActivity(a.pm)!=null){ a.startActivity(in); return; } } catch (Exception e){} try { a.launchPkg(fallbackPkg); } catch (Exception e){} }
    Long plutoId(Map<String,Long> ids, String nm, String slug){
        Long id = ids.get(nm); if (id!=null) return id;
        for (Map.Entry<String,Long> e: ids.entrySet()){ String k=slugify(e.getKey()); if (k.equals(slug)||k.equals(slugify(nm))) return e.getValue(); }
        return null;
    }
    int loadLive(){
        if (liveCache!=null && !liveCache.isEmpty()){ showLiveCache(); fillPlutoNow(plutoCaps); return liveCache.size(); }
        liveCache=new ArrayList<>(); liveSlugs.clear(); plutoCaps.clear();
        if (a.installed("com.google.android.tvlauncher")) putLive(freeplayLiveTile(), null);
        if (a.installed("tv.pluto.android")){
            LinkedHashMap<String,String> names=liveSlugNames();
            for (String slug: LIVE_SLUGS){
                if (liveCache.size()>=4) break;
                String nm=names.get(slug); if (nm==null) continue;
                putLive(plutoTile(slug, nm, true), slug);
            }
        }
        putLive(plutoGuideTile(), "pluto-guide");
        if (!plutoCaps.isEmpty()) fillPlutoNow(plutoCaps);
        return liveCache.size();
    }
    View plutoGuideTile(){
        Drawable ban=null, ic=null;
        try { ban=a.pm.getApplicationBanner("tv.pluto.android"); ic=a.pm.getApplicationIcon("tv.pluto.android"); } catch (Exception e){}
        return card(ban!=null?ban:ic, null, ban==null, null, "Pluto TV guide", -1, ()->startSafe(plutoIntent(null), "tv.pluto.android"));
    }
    LinkedHashMap<String,String> liveSlugNames(){
        LinkedHashMap<String,String> slugName=new LinkedHashMap<>();
        Map<String,Long> plutoIds=plutoIdsCache!=null?plutoIdsCache:new HashMap<>();
        for (String slug: LIVE_SLUGS){
            String nm=null; for (Map.Entry<String,String> en: plutoSlug.entrySet()) if (slug.equals(en.getValue())) nm=en.getKey();
            if (nm==null) nm=slug.equals("cnn-headlines")?"CNN Headlines":slug.equals("nbc-news-now")?"NBC News NOW":slug.equals("abc-news-live")?"ABC News Live":"CBS News 24/7";
            slugName.put(slug, nm);
        }
        for (Map.Entry<String,Long> e: plutoIds.entrySet()){ String slug=slugify(e.getKey()); if (!slugName.containsKey(slug)) slugName.put(slug, e.getKey()); }
        for (Map.Entry<String,String> e: plutoSlug.entrySet()) if (!slugName.containsKey(e.getValue())) slugName.put(e.getValue(), e.getKey());
        return slugName;
    }
    void putLive(View v, String slug){ if (slug!=null) liveSlugs.add(slug); liveCache.add(v); addCard(v, liveCache.size()-1); }
    void showLiveCache(){
        if (a.cardRow.getChildCount()==liveCache.size() && a.cardRow.getChildCount()>0 && a.cardRow.getChildAt(0)==liveCache.get(0)){ applyLiveNow(); return; }
        a.cardRow.removeAllViews();
        int i=0; for (View v: liveCache){ if (v.getParent()!=null) ((ViewGroup)v.getParent()).removeView(v); addCard(v, i++); }
        applyLiveNow();
    }
    View plutoTile(String slug, String nm, boolean loadArt){
        Map<String,Long> plutoIds=plutoIdsCache!=null?plutoIdsCache:new HashMap<>();
        final Intent in=plutoIntent(slug); Long id=plutoId(plutoIds, nm, slug);
        View v=card(null, null, false, null, nm, -1, ()->startSafe(in, "tv.pluto.android"));
        ImageView big=(ImageView)((MainActivity.GlowFrame)v).inner.getChildAt(0);
        TextView ov=chName(nm);
        ((MainActivity.GlowFrame)v).inner.addView(ov, a.lp(-2,-2,Gravity.TOP|Gravity.START,0,0,0,0));
        TextView cap=null;
        for (int ci=0; ci<((MainActivity.GlowFrame)v).inner.getChildCount(); ci++){ View cv=((MainActivity.GlowFrame)v).inner.getChildAt(ci); if (cv instanceof TextView && cv!=ov) cap=(TextView)cv; }
        String art=liveArt(nm, id), logo=plutoLogo.get(nm);
        if (art!=null && loadArt) setLiveArt(big, art, true, false);
        else if (logo!=null && loadArt) setLiveArt(big, logo, true, true);
        else if (art!=null) big.setTag("pend:"+art);
        else if (logo!=null) big.setTag("pendf:"+logo);
        ov.setVisibility(art!=null||logo!=null?View.VISIBLE:View.GONE);
        if (plutoNow.get(nm)!=null && cap!=null) cap.setText(plutoNow.get(nm));
        plutoCaps.add(new Object[]{cap, nm, big, ov, id});
        return v;
    }
    TextView chName(String nm){
        TextView ov=a.text(nm, 7, Color.WHITE, "sans-serif-medium");
        ov.setPadding(a.p(6),a.p(3),a.p(10),a.p(4));
        ov.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Color.argb(160,0,0,0), Color.argb(70,0,0,0), Color.TRANSPARENT}));
        return ov;
    }
    String liveArt(String nm, Long id){
        String art=plutoArt.get(nm); if (!genericArt(art,null)) return art;
        art=tifNowArt(id); return genericArt(art,null)?null:art;
    }
    void applyLiveNow(){
        for (Object[] o: plutoCaps){
            String nm=(String)o[1]; TextView cap=(TextView)o[0]; ImageView iv=o.length>2?(ImageView)o[2]:null; TextView ov=o.length>3?(TextView)o[3]:null;
            Long id=o.length>4?(Long)o[4]:null;
            String t=plutoNow.get(nm), art=liveArt(nm,id), logo=plutoLogo.get(nm);
            if (art!=null && !art.isEmpty()){
                if (ov!=null) ov.setVisibility(View.VISIBLE);
                if (cap!=null) cap.setText(t!=null?t:nm);
                setLiveArt(iv, art, false, false);
            } else if (logo!=null){
                if (ov!=null) ov.setVisibility(View.VISIBLE);
                if (cap!=null) cap.setText(t!=null?t:nm);
                setLiveArt(iv, logo, false, true);
            } else {
                if (ov!=null) ov.setVisibility(View.GONE);
                if (cap!=null) cap.setText(t!=null?nm+" \u00B7 "+t:nm);
            }
        }
        if (a.tab==1) updateCardAlphas();
    }
    void setLiveArt(ImageView iv, String uri, boolean now, boolean fit){
        if (iv==null || uri==null) return;
        Object old=iv.getTag(); if (uri.equals(old) || ("pend:"+uri).equals(old) || ("pendf:"+uri).equals(old)) return;
        iv.setPadding(fit?a.p(22):0, fit?a.p(16):0, fit?a.p(22):0, fit?a.p(16):0);
        iv.setScaleType(fit?ImageView.ScaleType.FIT_CENTER:ImageView.ScaleType.CENTER_CROP);
        if (now || (a.tab==1 && iv.getAlpha()>0)) a.loadInto(iv, uri, fit); else iv.setTag((fit?"pendf:":"pend:")+uri);
    }
    String tifNowArt(Long id){
        if (id==null) return null;
        long now=System.currentTimeMillis();
        try (Cursor c=a.getContentResolver().query(TvContract.buildProgramsUriForChannel(id, now, now+60000), null, null, null, null)){
            if (c!=null && c.moveToFirst()){
                int i=c.getColumnIndex("poster_art_uri"); String a=i>=0&&!c.isNull(i)?c.getString(i):null;
                if (a==null){ i=c.getColumnIndex("thumbnail_uri"); a=i>=0&&!c.isNull(i)?c.getString(i):null; }
                return a;
            }
        } catch (Exception e){}
        return null;
    }
    String freeplayNowArt(){
        try (Cursor c=a.getContentResolver().query(TvContract.Channels.CONTENT_URI, null, null, null, null)){
            while (c!=null && c.moveToNext()){
                int pi=c.getColumnIndex("package_name"); String pkg=pi>=0&&!c.isNull(pi)?c.getString(pi):null;
                if (pkg==null || !(pkg.contains("tvlauncher")||pkg.equals("com.google.android.tv"))) continue;
                int i=c.getColumnIndex("_id"); if (i<0) continue;
                String art=tifNowArt(c.getLong(i)); if (art!=null) return art;
            }
        } catch (Exception e){}
        return null;
    }
    void playFreeplay(){ try { Intent b=new Intent("com.google.android.tvlauncher.action.VIEW", Uri.parse("tv://freeplay")); b.setComponent(new android.content.ComponentName("com.google.android.tvlauncher","com.google.android.tvlauncher.TvContentIntentReceiver")); b.addFlags(Intent.FLAG_RECEIVER_FOREGROUND); a.sendBroadcast(b); android.util.Log.i("ShieldHome","freeplay broadcast sent"); } catch(Exception e){ android.util.Log.w("ShieldHome","freeplay "+e); } }
    View freeplayLiveTile(){
        String art=freeplayNowArt();
        Drawable ic=null; if (art==null) try { android.content.res.Resources r=a.pm.getResourcesForApplication("com.google.android.tvlauncher"); int id=r.getIdentifier("freeplay_icon","drawable","com.google.android.tvlauncher"); if (id!=0) ic=r.getDrawable(id,null); } catch(Exception e){}
        View v=card(ic, art, art==null, null, "Google TV Freeplay \u00B7 Live", -1, this::playFreeplay);
        TextView tg=a.text("LIVE", 7, Color.WHITE, "sans-serif-medium"); tg.setLetterSpacing(.08f); tg.setPadding(a.p(5),a.p(2),a.p(5),a.p(2)); GradientDrawable gb=new GradientDrawable(); gb.setCornerRadius(a.p(3)); gb.setColor(Color.rgb(200,30,40)); tg.setBackground(gb);
        ((MainActivity.GlowFrame)v).inner.addView(tg, a.lp(-2,-2,Gravity.TOP|Gravity.START,a.p(7),a.p(7),0,0));
        if (art!=null){ TextView ov=chName("Freeplay"); ((MainActivity.GlowFrame)v).inner.addView(ov, a.lp(-2,-2,Gravity.TOP|Gravity.START,0,0,0,0)); }
        return v;
    }
}
