package com.chris.shieldhome;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Paint;
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
    // Card row geometry (mockup px) and the focus caption shown under the focused card.
    static final float CARD_W=184.4f, CARD_H=112, CARD_GAP=8, ROW_PAD=20;
    // Card row frame (mockup px) as placed by HomeLayout.
    static final float WRAP_X=15, WRAP_Y=320, WRAP_W=994, WRAP_H=160;
    // Caption: the text block height comes from measured font metrics; the badge matches it. CAP_EDGE = right screen margin the title may run to; CAP_TITLE_LINES = title wrap limit.
    static final float CAP_GAP=14, CAP_TITLE=13, CAP_SUB=10.5f, CAP_LINE_GAP=0, CAP_TEXT_GAP=8, CAP_EDGE=35, CAP_GRID_CLEAR=6, ROW_LIFT=35;
    static final int CAP_TITLE_LINES=2;
    // Platform badge (the pre-v80 tile badge, 36x20 aspect) sized to the caption's two-line height.
    static final float BADGE_ASPECT=36f/20f, BADGE_R=4;
    static final float BAR_W=166, BAR_H=2.5f, BAR_X=9, BAR_Y=8;
    static final String SUB_RESUME="Resume watching", SUB_NEW="New";
    static final int FOCUS_MS=220, VISIBLE_CARDS=5;
    final Map<View,String[]> capMeta = new WeakHashMap<>();
    LinearLayout capLine; ImageView capIcon; TextView capText, capSub; int capBlockH; boolean rowLifted;
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
        int width=a.p(CARD_W), height=a.p(CARD_H), bottom=a.p(CARD_GAP), left=i==0?0:a.p(CARD_GAP);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, height); lp.bottomMargin=bottom; lp.leftMargin=left;
        if (v.getParent()==a.cardRow){ ViewGroup.LayoutParams old=v.getLayoutParams(); if (!(old instanceof LinearLayout.LayoutParams) || old.width!=width || old.height!=height || ((LinearLayout.LayoutParams)old).bottomMargin!=bottom || ((LinearLayout.LayoutParams)old).leftMargin!=left) v.setLayoutParams(lp); return; }
        if (v.getParent()!=null) ((ViewGroup)v.getParent()).removeView(v);
        if (!(v.getLayoutParams() instanceof LinearLayout.LayoutParams)){
            final View.OnFocusChangeListener base = v.getOnFocusChangeListener();
            v.setOnFocusChangeListener((x,f)->{ if (base!=null) base.onFocusChange(x,f); if (f) showCaption(x, snapCardRow(x)); else a.h.post(this::dropCaptionIfLeft); });
        }
        a.cardRow.addView(v, lp);
        if (v instanceof GlowFrame) ((GlowFrame)v).warm(a.p(CARD_W), a.p(CARD_H));
    }
    /** Anchored scroll: the focused card slides to the first slot; returns the target scrollX. */
    int snapCardRow(View v){
        if (a.cardScroll==null) return 0;
        int idx=a.cardRow.indexOfChild(v); if (idx<0) return a.cardScroll.getScrollX();
        int x=anchorX(idx, a.p(CARD_W)+a.p(CARD_GAP));
        if (x!=a.cardScroll.getScrollX()) a.cardScroll.smoothScrollTo(x,0); else updateCardAlphas();
        return x;
    }
    TextView capLabel(float size, int color, String font){
        TextView t=a.text("", size, color, font); t.setSingleLine(); t.setEllipsize(android.text.TextUtils.TruncateAt.END); t.setIncludeFontPadding(false); return t;
    }
    // ---------- Focus caption: app icon + title under the focused card; row and tabs lift to make room ----------
    void showCaption(View card, int scrollX){
        String[] m=capMeta.get(card); if (m==null || a.cardWrap==null) return;
        if (capLine==null){
            capLine=new LinearLayout(a); capLine.setOrientation(LinearLayout.HORIZONTAL); capLine.setGravity(Gravity.CENTER_VERTICAL); capLine.setAlpha(0f);
            capIcon=new ImageView(a); capIcon.setScaleType(ImageView.ScaleType.CENTER_CROP); capIcon.setClipToOutline(true);
            GradientDrawable bb=new GradientDrawable(); bb.setCornerRadius(a.p(BADGE_R)); bb.setColor(Color.BLACK); capIcon.setBackground(bb);
            capText=capLabel(CAP_TITLE, Color.WHITE, "sans-serif-medium");
            capSub=capLabel(CAP_SUB, Color.argb(170,255,255,255), "sans-serif");
            capText.setSingleLine(false); capText.setMaxLines(CAP_TITLE_LINES);
            capLine.addView(capIcon, new LinearLayout.LayoutParams(0, 0));
            LinearLayout col=new LinearLayout(a); col.setOrientation(LinearLayout.VERTICAL); col.setGravity(Gravity.CENTER_VERTICAL);
            col.addView(capText, new LinearLayout.LayoutParams(-1,-2));
            LinearLayout.LayoutParams sl=new LinearLayout.LayoutParams(-1,-2); sl.topMargin=a.p(CAP_LINE_GAP); col.addView(capSub, sl);
            LinearLayout.LayoutParams tl=new LinearLayout.LayoutParams(0,-1,1f); tl.leftMargin=a.p(CAP_TEXT_GAP); capLine.addView(col, tl);
            // Explicit width and height, set per focus in sizeCaption(): cardWrap is shorter than the caption, and a WRAP_CONTENT child would be squeezed.
            a.cardWrap.addView(capLine, a.lp(0, 0, Gravity.TOP|Gravity.START, 0, a.p(capTop()), 0, 0));
        }
        Drawable ic=null; try { if (m[0]!=null){ ic=a.pm.getApplicationBanner(m[0]); if (ic==null) ic=a.pm.getApplicationIcon(m[0]); } } catch (Exception e){}
        capIcon.setImageDrawable(ic); capIcon.setVisibility(ic==null?View.GONE:View.VISIBLE);
        capText.setText(m[1]!=null?m[1]:m[0]!=null?a.tv.appName(m[0]):"");
        capSub.setText(m[2]); capSub.setVisibility(m[2]==null?View.GONE:View.VISIBLE);
        float x=card.getLeft()-scrollX+a.p(ROW_PAD);
        sizeCaption(x);
        if (capLine.getAlpha()<0.05f) capLine.setTranslationX(x);
        capLine.animate().cancel();
        capLine.animate().alpha(1f).translationX(x).setDuration(FOCUS_MS).setInterpolator(a.focusInterpolator).start();
        liftRow(true);
    }
    void dropCaptionIfLeft(){
        View f=a.getCurrentFocus();
        if (f!=null && f.getParent()==a.cardRow) return;
        if (capLine!=null){ capLine.animate().cancel(); capLine.animate().alpha(0f).setDuration(FOCUS_MS).setInterpolator(a.focusInterpolator).start(); }
        if (a.drawerOpen) rowLifted=false; else liftRow(false); // drawer slide owns translationY
    }
    /** Moves the tab bar and card row up together (never the app grid). */
    void liftRow(boolean up){
        if (up==rowLifted && !up) return;
        rowLifted=up;
        float y=up?-liftPx():0;
        for (View v: new View[]{a.tabBar, a.cardWrap}) if (v!=null) v.animate().translationY(y).setDuration(FOCUS_MS).setInterpolator(a.focusInterpolator).start();
    }
    /** Sizes the caption to run from x to CAP_EDGE: the title gets the full width (wrapping to CAP_TITLE_LINES) and the badge matches the text block height. */
    void sizeCaption(float x){
        int w=Math.max(0, a.root.getWidth()-a.p(WRAP_X)-Math.round(x)-a.p(CAP_EDGE));
        int titleLines=1;
        for (int pass=0; pass<2; pass++){
            capBlockH=titleLines*lineH(capText)+(capSub.getVisibility()==View.VISIBLE?a.p(CAP_LINE_GAP)+lineH(capSub):0);
            int iconW=capIcon.getVisibility()==View.VISIBLE?Math.round(capBlockH*BADGE_ASPECT)+a.p(CAP_TEXT_GAP):0;
            int textW=Math.max(0, w-iconW);
            titleLines=capText.getPaint().measureText(capText.getText().toString())>textW?CAP_TITLE_LINES:1;
        }
        ViewGroup.LayoutParams il=capIcon.getLayoutParams(); il.width=Math.round(capBlockH*BADGE_ASPECT); il.height=capBlockH; capIcon.setLayoutParams(il);
        ViewGroup.LayoutParams ll=capLine.getLayoutParams(); ll.width=w; ll.height=capBlockH; capLine.setLayoutParams(ll);
        capLine.requestLayout();
    }
    /** Caption top inside cardWrap: CAP_GAP below the focused card's scaled bottom edge. */
    static float capTop(){ return ROW_PAD+CARD_H*(1+MainActivity.FOCUS_SCALE)/2+CAP_GAP; }
    /** Line height of a caption label without font padding (ascent to descent), so stacked lines touch and the badge spans exactly the text. */
    static int lineH(TextView t){ Paint.FontMetricsInt fm=t.getPaint().getFontMetricsInt(); return fm.descent-fm.ascent; }
    /** ROW_LIFT, or more if the measured caption would otherwise come within CAP_GRID_CLEAR of the app grid. */
    int liftPx(){
        int capBottom=a.p(WRAP_Y+capTop())+capBlockH, limit=a.p(a.APP_GRID_Y-CAP_GRID_CLEAR);
        return Math.max(a.p(ROW_LIFT), capBottom-limit);
    }
    void resetCardRow(){
        if (a.focus!=null){ a.focus.lastIdx[3]=0; a.focus.lastCardX=0; }
        if (a.cardScroll!=null) a.cardScroll.scrollTo(0,0);
        if (a.cardRow!=null && a.cardRow.getChildCount()>0 && a.cardRow.indexOfChild(a.getCurrentFocus())>=0) a.cardRow.getChildAt(0).requestFocus();
        updateCardAlphas();
    }
    void updateCardAlphas(){
        if (a.cardRow==null) return;
        int sx=a.cardScroll==null?0:a.cardScroll.getScrollX(), stride=a.p(CARD_W)+a.p(CARD_GAP);
        for (int i=0;i<a.cardRow.getChildCount();i++){
            View c=a.cardRow.getChildAt(i); float a=tileAlpha(i,sx,stride,VISIBLE_CARDS);
            if (c instanceof GlowFrame){
                GlowFrame g=(GlowFrame)c; g.fade=a; g.setAlpha(1f); g.inner.setAlpha(a); g.syncHalo(); g.invalidate();
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
    /** Focused card always at the first slot, including the last cards (trailing row padding makes room). */
    static int anchorX(int idx, int stride){ return Math.max(0, idx)*stride; }

    GradientDrawable pill(int color, int h){ GradientDrawable g=new GradientDrawable(); g.setColor(color); g.setCornerRadius(h/2f); return g; }
    /** Video card: art + optional progress bar. pkg and title feed the focus caption, not the tile. */
    View card(Drawable art, String artUri, boolean fitArt, String pkg, String title, String sub, float progress, final Runnable click){
        FrameLayout f = new FrameLayout(a); f.setClipToOutline(true);
        GradientDrawable bg = new GradientDrawable(); bg.setCornerRadius(a.p(a.TILE_R)); bg.setColor(Color.argb(96,190,185,220)); bg.setStroke(Math.max(1,a.p(.6f)), Color.argb(60,255,255,255)); f.setBackground(bg);
        f.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        ImageView iv = new ImageView(a); iv.setScaleType(ImageView.ScaleType.CENTER_CROP); if (art!=null) iv.setImageDrawable(art); if (fitArt){ iv.setScaleType(ImageView.ScaleType.FIT_CENTER); iv.setPadding(a.p(30),a.p(18),a.p(30),a.p(30)); } a.loadInto(iv, artUri, fitArt);
        f.addView(iv, new FrameLayout.LayoutParams(-1,-1));
        View shade = new View(a); shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{Color.TRANSPARENT, Color.argb(70,0,0,0), Color.argb(160,0,0,0), Color.argb(204,0,0,0)}));
        if (progress>=0) f.addView(shade, new FrameLayout.LayoutParams(-1, a.p(56), Gravity.BOTTOM));
        if (progress >= 0){
            int bw=a.p(BAR_W), bh=a.p(BAR_H);
            FrameLayout bar = new FrameLayout(a); bar.setBackground(pill(Color.argb(90,255,255,255), bh));
            View fill = new View(a); fill.setBackground(pill(Color.rgb(229,9,20), bh));
            bar.addView(fill, new FrameLayout.LayoutParams(Math.max(bh, Math.round(bw*progress)), -1)); // never narrower than its height, so both ends stay round
            f.addView(bar, a.lp(bw, bh, Gravity.BOTTOM, a.p(BAR_X), 0, 0, a.p(BAR_Y)));
        }
        View v=a.focusable(f, a.TILE_R, click);
        capMeta.put(v, new String[]{pkg, title, sub});
        return v;
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
        a.cardRow.setPadding(0,0,(VISIBLE_CARDS-1)*(a.p(CARD_W)+a.p(CARD_GAP)),0); // room to anchor the last card at the first slot
        if (a.cardScroll!=null) a.cardScroll.scrollTo(0,0);
        int n = a.tab==0 ? loadWatchNext() : loadLive();
        if (n>0){ a.h.post(this::updateCardAlphas); return; }
        if (a.tab==0){ a.applyTabs(); if (a.tab==1){ loadLive(); a.h.post(this::updateCardAlphas); } return; }
        int i=0;
        for (final String pk : LIVE_PKGS){
            if (!a.installed(pk)) continue;
            Drawable art=null; try { art = a.pm.getApplicationBanner(pk); if (art==null){ Intent li=a.pm.getLeanbackLaunchIntentForPackage(pk); if(li!=null) art=a.pm.getActivityBanner(li);} if(art==null) art=a.pm.getApplicationIcon(pk);} catch(Exception e){}
            View v=card(art, null, false, pk, null, null, -1, ()->a.launchPkg(pk)); addCard(v, i++);
        }
        a.h.post(this::updateCardAlphas);
    }

    int loadWatchNext(){
        List<Object[]> pick=a.wnPickCache;
        if (pick==null||pick.isEmpty()) return 0;
        int n=0;
        for (Object[] o: pick){ final Intent fi=new Intent((Intent)o[3]).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); final String pk2=(String)o[0]; boolean isNew=(Boolean)o[TvData.PICK_IS_NEW];
            View v = card(null,(String)o[2],false,pk2,(String)o[1], isNew?SUB_NEW:SUB_RESUME, isNew?-1f:(Float)o[4], ()->{try{a.startActivity(fi);}catch(Exception e){ a.launchPkg(pk2);} });
            addCard(v, n++); }
        int nNew=0; for (Object[] o: pick) if ((Boolean)o[TvData.PICK_IS_NEW]) nNew++;
        android.util.Log.i("ShieldHome","playnext shown="+n+" subNew="+nNew+" subResume="+(n-nNew));
        return n;
    }
    List<Object[]> plutoCaps = new ArrayList<>(); Map<String,String> plutoNow = new HashMap<>(); Map<String,String> plutoArt = new HashMap<>(); Map<String,String> plutoLogo = new HashMap<>(); final Map<String,String> plutoSlug = new java.util.concurrent.ConcurrentHashMap<>(); long plutoAt=0;
    List<View> liveCache; final Set<String> liveSlugs = new LinkedHashSet<>();
    // Pluto doesn't publish programs to TvContract (0 readable rows), so read what's on now from Pluto's public guide.
    /** Guide is reused for GUIDE_FRESH_MS; a failed fetch waits GUIDE_RETRY_MS; reads stop at GUIDE_MAX_BYTES (the full guide is ~5 MB). */
    static final long GUIDE_FRESH_MS=10*60000L, GUIDE_RETRY_MS=60000L; static final int GUIDE_MAX_BYTES=16<<20;
    boolean plutoBusy; long plutoTriedAt; // main thread only
    void fillPlutoNow(final List<Object[]> caps){
        long t=System.currentTimeMillis();
        if (t-plutoAt < GUIDE_FRESH_MS && !plutoNow.isEmpty()){ applyLiveNow(); return; }
        if (plutoBusy || t-plutoTriedAt < GUIDE_RETRY_MS) return;
        plutoBusy=true; plutoTriedAt=t;
        a.pool.execute(()->{ java.net.HttpURLConnection c=null; try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US); f.setTimeZone(TimeZone.getTimeZone("UTC"));
            long now = System.currentTimeMillis();
            c=(java.net.HttpURLConnection)new java.net.URL("https://api.pluto.tv/v2/channels?start="+f.format(new Date(now))+"&stop="+f.format(new Date(now+60000))).openConnection();
            c.setConnectTimeout(6000); c.setReadTimeout(20000);
            java.io.InputStream in = c.getInputStream(); java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream(); byte[] buf=new byte[65536]; int r; while ((r=in.read(buf))>0){ bo.write(buf,0,r); if (bo.size()>GUIDE_MAX_BYTES) throw new java.io.IOException("guide over "+GUIDE_MAX_BYTES+" bytes"); } in.close();
            org.json.JSONArray channels = new org.json.JSONArray(bo.toString("UTF-8")); final Map<String,String> m = new HashMap<>(), art = new HashMap<>(), logos = new HashMap<>();
            Set<String> ban = new HashSet<>(); Map<String,Integer> shared = new HashMap<>();
            for (int i=0;i<channels.length();i++){ org.json.JSONObject ch=channels.optJSONObject(i); if (ch==null) continue;
                addBan(ban, ch.optJSONObject("featuredImage")); addBan(ban, ch.optJSONObject("logo")); addBan(ban, ch.optJSONObject("thumbnail")); addBan(ban, ch.optJSONObject("tile"));
                String logo=jsonPath(ch.optJSONObject("colorLogoPNG")); if (logo==null) logo=jsonPath(ch.optJSONObject("logo"));
                if (logo!=null) logos.put(ch.optString("name"), logo);
                org.json.JSONArray tl=ch.optJSONArray("timelines");
                if (tl!=null && tl.length()>0){ String first=tl.optJSONObject(0)==null?null:plutoGuideArt(tl.optJSONObject(0), ban); if (first!=null){ String b=bareUrl(first); shared.put(b, (shared.containsKey(b)?shared.get(b):0)+1); } }
            }
            for (Map.Entry<String,Integer> e: shared.entrySet()) if (e.getValue()>=4) ban.add(e.getKey());
            final List<String[]> picks=pickChannels(channels);
            for (int i=0;i<channels.length();i++){ org.json.JSONObject ch=channels.optJSONObject(i); if (ch==null) continue; org.json.JSONArray tl=ch.optJSONArray("timelines");
                org.json.JSONObject t0=tl==null?null:tl.optJSONObject(0);
                if (t0!=null){ m.put(ch.optString("name"), t0.optString("title")); String img=plutoGuideArt(t0, ban); if (img!=null) art.put(ch.optString("name"), img); }
                String sl=ch.optString("slug",null); if (sl!=null) plutoSlug.put(ch.optString("name"), sl); }
            android.util.Log.i("ShieldHome","pluto now: "+m.size()+" channels art="+art.size()+" logos="+logos.size());
            a.h.post(()->{ plutoNow.clear(); plutoNow.putAll(m); plutoArt.clear(); plutoArt.putAll(art); plutoLogo.clear(); plutoLogo.putAll(logos); plutoAt=System.currentTimeMillis(); applyLiveNow(); setPicks(picks); });
        } catch (Exception e){ android.util.Log.w("ShieldHome","pluto guide "+e); }
        finally { if (c!=null) c.disconnect(); a.h.post(()->plutoBusy=false); } });
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
    /** Live TV row: the Pluto TV app tile, then up to MAX_LIVE_CHANNELS channel tiles that deep-link into Pluto. */
    static final int MAX_LIVE_CHANNELS=20;
    /** Pluto guide categories in row order; channels are picked round-robin, one per category per pass, for a balanced mix. */
    static final String[] LIVE_CATEGORIES={"News + Opinion","Sports","Movies","Comedy","Kids","Reality","Drama","True Crime","Classic TV","Home + Food",
        "Sci-Fi","Animals + Nature","History + Science","Entertainment","Competition Reality","Daytime + Game Shows","Westerns","Anime","Music Videos"};
    /** Shown until the first guide fetch picks the mix (slug, name). */
    static final String[][] LIVE_DEFAULTS={{"cnn-headlines","CNN Headlines"},{"nbc-news-now","NBC News NOW"},{"abc-news-live","ABC News Live"},{"cbs-news","CBS News 24/7"}};
    /** Pluto lists internal test feeds (e.g. "... VIDAA TEST") in real categories; skip names ending in "test" or "test N". */
    static final java.util.regex.Pattern TEST_MARK=java.util.regex.Pattern.compile("\\btest\\s*\\d*$", java.util.regex.Pattern.CASE_INSENSITIVE);
    /** Last picked mix, so the row comes up with the same channels before the guide answers: "slug\tname" lines. */
    static final String PREF_LIVE_PICKS="live_picks", PICK_ROW="\n", PICK_COL="\t";
    List<String[]> livePicks(){
        List<String[]> out=new ArrayList<>();
        for (String ln: a.prefs().getString(PREF_LIVE_PICKS, "").split(PICK_ROW)){ String[] f=ln.split(PICK_COL); if (f.length==2) out.add(f); }
        if (out.isEmpty()) for (String[] d: LIVE_DEFAULTS) out.add(d);
        return out;
    }
    static List<String[]> pickChannels(org.json.JSONArray channels){
        Map<String,List<org.json.JSONObject>> byCat=new HashMap<>();
        for (int i=0;i<channels.length();i++){ org.json.JSONObject ch=channels.optJSONObject(i); if (ch==null || ch.optString("slug").isEmpty() || TEST_MARK.matcher(ch.optString("name")).find()) continue;
            String cat=ch.optString("category"); if (!byCat.containsKey(cat)) byCat.put(cat, new ArrayList<>()); byCat.get(cat).add(ch); }
        for (List<org.json.JSONObject> l: byCat.values()) java.util.Collections.sort(l, (x,y)->Integer.compare(x.optInt("number",Integer.MAX_VALUE), y.optInt("number",Integer.MAX_VALUE)));
        List<String[]> out=new ArrayList<>();
        for (int pass=0; out.size()<MAX_LIVE_CHANNELS; pass++){ boolean any=false;
            for (String cat: LIVE_CATEGORIES){ List<org.json.JSONObject> l=byCat.get(cat); if (l==null || l.size()<=pass) continue; any=true;
                org.json.JSONObject ch=l.get(pass); out.add(new String[]{ch.optString("slug"), ch.optString("name")}); if (out.size()>=MAX_LIVE_CHANNELS) break; }
            if (!any) break; }
        return out;
    }
    /** New mix from the guide: remember it and rebuild the row now unless the user is in it (then it shows next time). */
    void setPicks(List<String[]> picks){
        if (picks.isEmpty()) return;
        StringBuilder b=new StringBuilder(); for (String[] p: picks){ if (b.length()>0) b.append(PICK_ROW); b.append(p[0]).append(PICK_COL).append(p[1]); }
        if (b.toString().equals(a.prefs().getString(PREF_LIVE_PICKS, ""))) return;
        a.prefs().edit().putString(PREF_LIVE_PICKS, b.toString()).apply();
        android.util.Log.i("ShieldHome","live picks: "+picks.size());
        liveCache=null; cardsBindSig="";
        if (a.tab==1 && a.cardRow!=null && !a.cardRow.hasFocus()) refreshCards();
    }
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
        if (a.installed("tv.pluto.android")){ // not installed: 0 tiles, so refreshCards falls back to the LIVE_PKGS app tiles
            putLive(plutoAppTile(), "pluto-app");
            for (String[] p: livePicks()){ if (liveCache.size()>MAX_LIVE_CHANNELS) break; putLive(plutoTile(p[0], p[1], true), p[0]); }
            fillPlutoNow(plutoCaps);
        }
        return liveCache.size();
    }
    View plutoAppTile(){
        Drawable ban=null, ic=null;
        try { ban=a.pm.getApplicationBanner("tv.pluto.android"); ic=a.pm.getApplicationIcon("tv.pluto.android"); } catch (Exception e){}
        return card(ban!=null?ban:ic, null, ban==null, "tv.pluto.android", "Pluto TV", null, -1, ()->startSafe(plutoIntent(null), "tv.pluto.android"));
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
        View v=card(null, null, false, "tv.pluto.android", nm, null, -1, ()->startSafe(in, "tv.pluto.android"));
        ImageView big=(ImageView)((GlowFrame)v).inner.getChildAt(0);
        TextView ov=chName(nm);
        ((GlowFrame)v).inner.addView(ov, a.lp(-2,-2,Gravity.TOP|Gravity.START,0,0,0,0));
        TextView cap=null;
        for (int ci=0; ci<((GlowFrame)v).inner.getChildCount(); ci++){ View cv=((GlowFrame)v).inner.getChildAt(ci); if (cv instanceof TextView && cv!=ov) cap=(TextView)cv; }
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
}
