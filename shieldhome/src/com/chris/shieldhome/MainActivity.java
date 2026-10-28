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
    static final long HERO_ROTATE_MS = 16000;
    final android.view.animation.DecelerateInterpolator focusInterpolator = new android.view.animation.DecelerateInterpolator();
    static final float APP_FOCUS=1.20f, APP_HOLD=1.23f, APP_W=142.5f, APP_H=78.5f, APP_HG=19.8f, APP_VG=21f, APP_GRID_Y=467.5f;
    static final String[] PLAY_PKGS = {"com.google.android.youtube.tv","com.netflix.ninja","com.disney.disneyplus","com.hulu.livingroomplus","com.wbd.stream","com.amazon.amazonvideo.livingroom","com.plexapp.android"};

    FrameLayout.LayoutParams lp(int w,int h,int g,int l,int t,int r,int b){ FrameLayout.LayoutParams x=new FrameLayout.LayoutParams(w,h,g); x.setMargins(l,t,r,b); return x; }
    java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(4);
    ImageLoader images; FocusController focus; TvData tv; NetData net; CardsController cards; HeroController hero; SearchDimController searchDim; AppGridController grid; HomeLayout layout;
    java.util.concurrent.Future<?> dataFut; int dataGen, heroLoadGen;
    List<String[]> appsCache, fullOrder=new ArrayList<>();
    List<Object[]> featuredCache, wnPickCache;
    
    String homeBindSig="";
    final Object heroLock=new Object(); Object[] heroOnScreen;
    void loadInto(final ImageView iv, String uri0, final boolean fit){
        if (images==null||uri0==null) return;
        images.load(iv, uri0, fit, iv.getWidth()>0?iv.getWidth():p(CardsController.CARD_W), iv.getHeight()>0?iv.getHeight():p(CardsController.CARD_H));
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
        cards = new CardsController(this);
        hero = new HeroController(this);
        searchDim = new SearchDimController(this);
        grid = new AppGridController(this);
        layout = new HomeLayout(this);
        prefs().edit().remove("heromode").remove("featured").apply(); // v9: card mode removed
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        DisplayMetricsHolder();
        kickData(false);
        layout.build();
        IntentFilter pf = new IntentFilter(); pf.addAction(Intent.ACTION_PACKAGE_REMOVED); pf.addAction(Intent.ACTION_PACKAGE_ADDED); pf.addAction(Intent.ACTION_PACKAGE_CHANGED); pf.addDataScheme("package");
        registerReceiver(pkgRx, pf);
    }
    final BroadcastReceiver pkgRx = new BroadcastReceiver(){ @Override public void onReceive(Context c, Intent i){
        String pkg = i.getData()==null?null:i.getData().getSchemeSpecificPart(); boolean repl = i.getBooleanExtra(Intent.EXTRA_REPLACING,false);
        android.util.Log.i("ShieldHome","pkg "+i.getAction()+" "+pkg+" repl="+repl);
        final boolean removed = Intent.ACTION_PACKAGE_REMOVED.equals(i.getAction()) && !repl && pkg!=null;
        if (removed){
            Runnable home = ()->{ appsCache=null; kickData(true); };
            if (!editMode && dragIdx<0) grid.fadeRemove(homeGrid, pkg, home); else home.run();
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
        if (!editMode) grid.resetAllLifts(false, null);
        kickData(true);
        tick(); showWeather(); weatherTick(); applyDim();
    }
    @Override protected void onPause(){ if (editMode) grid.exitEdit(); super.onPause(); focus.save(); h.removeCallbacks(this::tick); h.removeCallbacks(hero.heroRotate); h.removeCallbacks(wxRun); }
    boolean heroShown(){ return heroG!=null && heroG.getVisibility()==View.VISIBLE; }
    @Override protected void onNewIntent(Intent i){ super.onNewIntent(i); focus.restoreNow(); }
    boolean editLp=false;
    @Override public void onWindowFocusChanged(boolean has){ super.onWindowFocusChanged(has); if (!has && dimSlide) closeDimSlide(); }
    @Override public boolean dispatchKeyEvent(KeyEvent e){
        if (dimSlide) return grid.dimSlideKey(e);
        if (appMenu!=null) return grid.menuKey(e);
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
    boolean superDispatchKey(KeyEvent e){ return super.dispatchKeyEvent(e); }
    @Override public void onBackPressed(){ focus.back(); }
    @Override public boolean onKeyDown(int k, KeyEvent e){ if (focus.keyDown(k,e)) return true; return super.onKeyDown(k,e); }
    @Override public boolean onKeyLongPress(int k, KeyEvent e){ if (focus.keyLong(k,e)) return true; return super.onKeyLongPress(k,e); }
    @Override public boolean onKeyUp(int k, KeyEvent e){ if (focus.keyUp(k,e)) return true; return super.onKeyUp(k,e); }
    void openAssistant(){ searchDim.openAssistant(); }
    boolean searchOpen, searchTyping;
    void expandSearch(){ searchDim.expandSearch(); }
    void maybeCollapseSearch(){ searchDim.maybeCollapseSearch(); }
    void showKb(){ searchDim.showKb(); }
    void hideKb(){ searchDim.hideKb(); }
    void launchSearch(String q){ searchDim.launchSearch(q); }
    void openSearchField(){ searchDim.openSearchField(); }
    void closeSearchField(){ searchDim.closeSearchField(); }
    void collapseSearch(){ searchDim.collapseSearch(); }

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

    TextView text(String s, float size, int color, String font){ TextView t=new TextView(this); t.setText(s); ts(t,size); t.setTextColor(color); t.setTypeface(Typeface.create(font, Typeface.NORMAL)); t.setIncludeFontPadding(false); return t; }


    // ---------- Pills with drawn icons ----------






    // ---------- Focus helper: soft blue glow drawn outside the tile + scale ----------

    View focusable(final FrameLayout v, float radius, final Runnable click){ return focusable(v, radius, 1.13f, click); }
    View focusable(final FrameLayout v, float radius, final float sc, final Runnable click){
        final GlowFrame g = new GlowFrame(this, this, v, p(radius));
        g.setFocusable(true); g.setClickable(true);
        g.setOnFocusChangeListener((x,f)->{
            if (x.getParent()==null) return;
            if (editMode){ if (x==dragV) x.setTranslationZ(p(18)); return; }
            if (x==menuOwner && !f) return;
            if (x==menuPop){ if (f) menuPop=null; return; }
            x.animate().scaleX(f?sc:1f).scaleY(f?sc:1f).translationX(0).translationY(0).rotation(0).setDuration(220).setInterpolator(focusInterpolator).start();
            x.setTranslationZ(f?p(12):0);
            if (g.glowAnimator!=null) g.glowAnimator.cancel();
            g.glowAnimator = android.animation.ValueAnimator.ofFloat(g.glow, f?1f:0f); g.glowAnimator.setDuration(220);
            g.glowAnimator.addUpdateListener(a->{ g.glow=(Float)a.getAnimatedValue(); g.syncHalo(); }); g.glowAnimator.start();
        });
        g.setOnClickListener(x->click.run());
        return g;
    }
    final Map<String,Bitmap> glowBmps = new HashMap<>();
    Bitmap shadowBmp(int w, int h, float r){
        String k="sh:"+w+"x"+h+":"+(int)r;
        Bitmap hit=glowBmps.get(k); if (hit!=null && !hit.isRecycled()) return hit;
        int pad=p(48);
        Bitmap bm=Bitmap.createBitmap(Math.max(1,w+pad*2), Math.max(1,h+pad*2), Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(bm);
        Paint bl=new Paint(Paint.ANTI_ALIAS_FLAG);
        bl.setColor(Color.BLACK); bl.setMaskFilter(new BlurMaskFilter(p(32), BlurMaskFilter.Blur.NORMAL));
        c.drawRoundRect(pad, pad, pad+w, pad+h, r, r, bl);
        glowBmps.put(k, bm); return bm;
    }
    View appTile(final String pkg, String cls, String label){
        FrameLayout f = new FrameLayout(this); f.setClipToOutline(true);
        GradientDrawable bg = new GradientDrawable(); bg.setCornerRadius(p(TILE_R)); bg.setColor(TILE); bg.setStroke(1, Color.argb(30,255,255,255)); f.setBackground(bg);
        ImageView iv = new ImageView(this);
        Drawable banner = null; ComponentName cn = new ComponentName(pkg, cls);
        try { banner = pm.getActivityBanner(cn); if (banner==null) banner = pm.getApplicationBanner(pkg); } catch (Exception e) {}
        boolean settings = pkg.equals("com.android.tv.settings");
        if (banner != null && !settings){
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP); iv.setImageDrawable(banner);
            bg.setColor(brandColor(banner, true)); bg.setStroke(0, 0);
            f.addView(iv, new FrameLayout.LayoutParams(-1,-1));
            View sheen = new View(this); sheen.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{Color.argb(28,255,255,255), Color.TRANSPARENT, Color.argb(40,0,0,0)}));
            f.addView(sheen, new FrameLayout.LayoutParams(-1,-1));
        } else {
            Drawable icon = null; try { icon = pm.getActivityIcon(cn);} catch(Exception e){}
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER); iv.setImageDrawable(icon);
            bg.setColor(settings ? TILE : brandColor(icon, false));
            f.addView(iv, lp(p(30), p(30), Gravity.CENTER_HORIZONTAL|Gravity.TOP, 0, p(9), 0, 0));
            TextView t = text(label, 11, Color.rgb(230,229,236), "sans-serif"); t.setSingleLine(); t.setEllipsize(android.text.TextUtils.TruncateAt.END); t.setGravity(Gravity.CENTER);
            f.addView(t, lp(-1, -2, Gravity.BOTTOM, p(6), 0, p(6), p(8)));
        }
        final Intent li = new Intent(Intent.ACTION_MAIN).setComponent(cn).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        View gv = focusable(f, TILE_R, APP_FOCUS, ()->{ try{ startActivity(li);}catch(Exception e){} });
        gv.setTag(pkg); return gv;
    }
    // Banner: use its edge colour so the banner blends into the tile. Icon: dominant (most saturated common) colour, darkened.
    int brandColor(Drawable d, boolean edge){
        if (d==null) return TILE;
        try {
            Bitmap bm = Bitmap.createBitmap(64, 36, Bitmap.Config.ARGB_8888); Canvas c = new Canvas(bm); d.setBounds(0,0,64,36); d.draw(c);
            if (edge){ long r=0,g=0,b=0; int n=0;
                for (int x=0;x<64;x++) for (int y: new int[]{0,1,34,35}){ int px=bm.getPixel(x,y); if (Color.alpha(px)<128) continue; r+=Color.red(px); g+=Color.green(px); b+=Color.blue(px); n++; }
                for (int y=0;y<36;y++) for (int x: new int[]{0,1,62,63}){ int px=bm.getPixel(x,y); if (Color.alpha(px)<128) continue; r+=Color.red(px); g+=Color.green(px); b+=Color.blue(px); n++; }
                if (n==0) return TILE; return Color.rgb((int)(r/n),(int)(g/n),(int)(b/n));
            }
            Map<Integer,Integer> hist = new HashMap<>(); int best=0, bestN=0;
            for (int x=0;x<64;x++) for (int y=0;y<36;y++){ int px=bm.getPixel(x,y); if (Color.alpha(px)<200) continue; float[] hsv=new float[3]; Color.colorToHSV(px,hsv); if (hsv[1]<0.25f||hsv[2]<0.2f) continue;
                int k = ((int)(hsv[0]/20)); int v=(hist.containsKey(k)?hist.get(k):0)+1; hist.put(k,v); if (v>bestN){bestN=v; best=px;} }
            if (bestN==0) return TILE; float[] hsv=new float[3]; Color.colorToHSV(best,hsv); hsv[2]=Math.min(hsv[2],0.45f); hsv[1]=Math.min(hsv[1],0.75f); return Color.HSVToColor(hsv);
        } catch (Exception e) { return TILE; }
    }

    // ---------- Usage ----------
    Map<String,Long> usage = new HashMap<>();

    GridLayout homeGrid; boolean drawerOpen, editMode; int dragIdx=-1; View dragV; View[] tiles;
    final android.view.animation.Interpolator dragIp=new android.view.animation.PathInterpolator(0.16f,1f,0.3f,1f);
    final android.view.animation.Interpolator drawerIp=new android.view.animation.PathInterpolator(0.4f,0f,0.2f,1f);
    float[] tgtX, tgtY; boolean glideOn; int slotW, slotH, liftY;
    LinearLayout appMenu; View menuOwner, menuPop, dimV; boolean pressHeld;
    android.content.SharedPreferences prefs(){ return getSharedPreferences("home", MODE_PRIVATE); }
    Bitmap vigMask; final Map<String,Bitmap> heroMasked=new HashMap<>();
    // ---------- Featured hero ----------
    ImageView heroArt, heroBadge; TextView heroTitle, heroSub; LinearLayout heroMeta; List<Object[]> heroItems = new ArrayList<>(); int heroIdx=0;
    static String firstSentence(String s){
        if (s==null || s.isEmpty()) return s==null?"":s;
        int n=s.length();
        for (int i=0;i<n;i++){
            char c=s.charAt(i);
            if (c!='.' && c!='?' && c!='!') continue;
            if (i<n-1 && s.charAt(i+1)!=' ') continue;
            if (c=='?' || c=='!') return s.substring(0,i+1);
            int b=i; while (b>0 && Character.isLetter(s.charAt(b-1))) b--;
            String w=s.substring(b,i), lw=w.toLowerCase(Locale.US);
            if (w.length()>3) return s.substring(0,i+1);
            boolean cap=w.length()==1 && Character.isUpperCase(w.charAt(0));
            boolean dotted=w.length()==1 && b>0 && s.charAt(b-1)=='.';
            boolean title=lw.equals("dr")||lw.equals("mr")||lw.equals("mrs")||lw.equals("ms")||lw.equals("jr")||lw.equals("sr")||lw.equals("st")||lw.equals("vs");
            if (!cap && !title && !dotted) return s.substring(0,i+1);
        }
        return s;
    }
    ImageView heroArt2; Map<String,Bitmap> glyphs=new HashMap<>();
    long heroGate;


    void kickData(boolean postBind){
        final int g=++dataGen;
        dataFut=pool.submit(()->{
            Map<String,Long> nextUse=tv.queryUsage();
            List<String[]> apps=tv.queryApps();
            usage=nextUse;
            featuredCache=tv.collectFeatured(tv.queryPreviewFeatured());
            wnPickCache=tv.collectWatchNext();
            cards.plutoIdsCache=tv.queryPlutoIds();
            appsCache=apps;
            StringBuilder sb=new StringBuilder(); List<String> keys=new ArrayList<>(usage.keySet()); Collections.sort(keys,(a,b)->Long.compare(grid.use(b),grid.use(a)));
            for (int i=0;i<Math.min(15,keys.size());i++) sb.append(keys.get(i)).append('=').append(grid.use(keys.get(i))/60000).append("m ");
            android.util.Log.i("ShieldHome","usage "+sb);
            if (postBind) h.post(()->{ if (g==dataGen) bindFromCache(); });
            return null;
        });
    }
    void awaitData(){
        if (dataFut==null) return;
        try { dataFut.get(4, java.util.concurrent.TimeUnit.SECONDS); } catch (Exception e) { android.util.Log.w("ShieldHome","await "+e); }
    }
    void bindFromCache(){
        if (cardRow==null) return;
        if (focus!=null) focus.remember(); // identity of whatever is focused now, before views are rebuilt
        grid.buildHomeGrid();
        applyTabs();
        refreshCards();
        hero.applyFeaturedCache();
        if (focus!=null) focus.afterBind();
    }
    boolean playNextOn(){ return wnPickCache!=null && !wnPickCache.isEmpty(); }
    void applyTabs(){
        boolean pn=playNextOn();
        if (tabBar!=null) tabBar.showPlay(pn);
        if (!pn){
            if (tab==0) tab=1;
            if (tabPlay!=null && tabPlay.hasFocus() && tabLive!=null) tabLive.requestFocus();
        }
        if (tabBar!=null) tabBar.slide(false);
    }
    void resetCardRow(){ cards.resetCardRow(); }
    void updateCardAlphas(){ cards.updateCardAlphas(); }
    void refreshCards(){ cards.refreshCards(); }
    List<String[]> apps(){ return appsCache!=null?appsCache:tv.queryApps(); }

    boolean installed(String p){ try { pm.getApplicationInfo(p,0); return true; } catch(Exception e){ return false; } }
    void launchPkg(String pkg){ Intent i = pm.getLeanbackLaunchIntentForPackage(pkg); if (i==null) i = pm.getLaunchIntentForPackage(pkg); if (i!=null) startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }

    // ---------- v6: quick settings, featured toggle, dimmer, weather ----------
    PillView searchV, speakV, typeV; LinearLayout searchSplit; EditText searchField; TextView heroLabel;
    PillView gear, dimBar, heroBtn, wxPill, timePill; boolean dimSlide=false, dimSlideIgnore=false, heroCtaOn; View heroG, heroFocus, inAppDim; HeroDots heroDots;
    float dimFill; android.animation.ValueAnimator dimFillAnim;
    int dimPct(){ return searchDim.dimPct(); }
    void setDimPct(int pct){ searchDim.setDimPct(pct); }
    void syncDimFill(boolean anim){ searchDim.syncDimFill(anim); }
    void dimSlideBy(int dir){ searchDim.dimSlideBy(dir); }
    void hideSearch(boolean hide){ searchDim.hideSearch(hide); }
    void popDimBar(boolean on){ searchDim.popDimBar(on); }
    void openDimSlide(){ searchDim.openDimSlide(); }
    void closeDimSlide(){ searchDim.closeDimSlide(); }
    void applyDim(){ searchDim.applyDim(); }
    final Runnable wxRun = this::weatherTick;
    void weatherTick(){
        h.removeCallbacks(wxRun);
        long age = System.currentTimeMillis()-prefs().getLong("wx_at",0);
        if (age > 30*60*1000L) net.fetchWeather();
        h.postDelayed(wxRun, Math.max(60000L, 30*60*1000L-Math.min(age,30*60*1000L)+5000));
    }
    void showWeather(){
        if (!prefs().contains("wx_at")) return;
        int code=prefs().getInt("wx_code",0); boolean day=prefs().getInt("wx_day",1)==1;
        String ic;
        if (code==0) ic=day?"\u2600\uFE0F":"\uD83C\uDF19";
        else if (code<=2) ic=day?"\uD83C\uDF24\uFE0F":"\u2601\uFE0F";
        else if (code==3) ic="\u2601\uFE0F";
        else if (code==45||code==48) ic="\uD83C\uDF2B\uFE0F";
        else if (code>=51&&code<=57) ic="\uD83C\uDF26\uFE0F";
        else if (code>=61&&code<=67) ic="\uD83C\uDF27\uFE0F";
        else if (code>=71&&code<=77) ic="\u2744\uFE0F";
        else if (code>=80&&code<=82) ic="\uD83C\uDF26\uFE0F";
        else if (code==85||code==86) ic="\uD83C\uDF28\uFE0F";
        else ic="\u26C8\uFE0F";
        setPill(wxPill, ic+"  "+prefs().getInt("wx_t",0)+"\u00B0F");
    }
}
