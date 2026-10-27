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
    static final float APP_FOCUS=1.13f, APP_HOLD=1.16f, APP_W=142.5f, APP_H=78.5f, APP_HG=19.8f, APP_VG=21f, APP_GRID_Y=467.5f;
    static final String[] PLAY_PKGS = {"com.google.android.youtube.tv","com.netflix.ninja","com.disney.disneyplus","com.hulu.livingroomplus","com.wbd.stream","com.amazon.amazonvideo.livingroom","com.plexapp.android"};

    FrameLayout.LayoutParams lp(int w,int h,int g,int l,int t,int r,int b){ FrameLayout.LayoutParams x=new FrameLayout.LayoutParams(w,h,g); x.setMargins(l,t,r,b); return x; }
    java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(4);
    ImageLoader images; FocusController focus; TvData tv; NetData net; CardsController cards; HeroController hero; SearchDimController searchDim; HomeLayout layout;
    java.util.concurrent.Future<?> dataFut; int dataGen, heroLoadGen;
    List<String[]> appsCache, fullOrder=new ArrayList<>();
    List<Object[]> featuredCache, wnPickCache;
    
    String homeBindSig="";
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
        cards = new CardsController(this);
        hero = new HeroController(this);
        searchDim = new SearchDimController(this);
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
    @Override protected void onPause(){ if (editMode) exitEdit(); super.onPause(); focus.save(); h.removeCallbacks(this::tick); h.removeCallbacks(hero.heroRotate); h.removeCallbacks(wxRun); }
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
    long use(String p){ Long v = p==null?null:usage.get(p); long base = v==null?0:v; if ("com.google.android.youtube.tv".equals(p)) base = base*3+1; return base; }

    GridLayout homeGrid; boolean drawerOpen, editMode; int dragIdx=-1; View dragV; View[] tiles;
    final android.view.animation.Interpolator dragIp=new android.view.animation.PathInterpolator(0.16f,1f,0.3f,1f);
    final android.view.animation.Interpolator drawerIp=new android.view.animation.PathInterpolator(0.4f,0f,0.2f,1f);
    float[] tgtX, tgtY; boolean glideOn; int slotW, slotH, liftY;
    final android.view.Choreographer.FrameCallback glideCb=new android.view.Choreographer.FrameCallback(){
        public void doFrame(long t){
            if (tiles==null){ glideOn=false; return; }
            boolean more=false;
            for (int i=0;i<tiles.length;i++){
                View v=tiles[i];
                float x=v.getTranslationX(), y=v.getTranslationY();
                float dx=tgtX[i]-x, dy=tgtY[i]-y;
                if (dx*dx+dy*dy<0.25f){ if (dx!=0||dy!=0){ v.setTranslationX(tgtX[i]); v.setTranslationY(tgtY[i]); } }
                else { v.setTranslationX(x+dx*0.28f); v.setTranslationY(y+dy*0.28f); more=true; }
            }
            if (more) android.view.Choreographer.getInstance().postFrameCallback(this);
            else glideOn=false;
        }
    };
    LinearLayout appMenu; View menuOwner, menuPop, dimV; boolean pressHeld;
    android.content.SharedPreferences prefs(){ return getSharedPreferences("home", MODE_PRIVATE); }
    List<String[]> homeApps(){
        List<String[]> all = new ArrayList<>(apps());
        String saved = prefs().getString("order", null);
        if (saved == null){
            final List<String> pref = Arrays.asList(PLAY_PKGS);
            Collections.sort(all,(a,b)->{
                if (a[0].equals("com.android.tv.settings")) return 1;
                if (b[0].equals("com.android.tv.settings")) return -1;
                long ua=use(a[0]), ub=use(b[0]); if (ua<300000L) ua=0; if (ub<300000L) ub=0;
                int c=Long.compare(ub,ua); if (c!=0) return c;
                int ia=pref.indexOf(a[0]), ib=pref.indexOf(b[0]); if(ia<0)ia=99; if(ib<0)ib=99;
                return ia!=ib?ia-ib:a[2].compareToIgnoreCase(b[2]);
            });
            return all;
        }
        List<String[]> out=new ArrayList<>(); Set<String> seen=new HashSet<>();
        for (String key: saved.split(",")){
            if (key.isEmpty() || key.equals("_")) continue;
            for (String[] a: all) if ((a[0]+"/"+a[1]).equals(key) && seen.add(key)) out.add(a);
        }
        for (String[] a: all) if (seen.add(a[0]+"/"+a[1])) out.add(a);
        return out;
    }
    void saveHome(List<String[]> l){ StringBuilder sb=new StringBuilder(); for (String[] a: l){ if (a==null) continue; if (sb.length()>0) sb.append(','); sb.append(a[0]).append('/').append(a[1]); } prefs().edit().putString("order", sb.toString()).commit(); }
    GridLayout.LayoutParams gridLp(int i){ GridLayout.LayoutParams gl = new GridLayout.LayoutParams(); gl.width=p(APP_W); gl.height=p(APP_H); gl.rightMargin = (i%6==5)?0:p(APP_HG); gl.bottomMargin = p(APP_VG); return gl; }
    int rowH(){ return p(APP_H+APP_VG); }
    View[] slideViews(){ return new View[]{clk, gear, searchV, searchSplit, searchField, dimBar, heroG, tabBar, cardWrap, homeGrid}; }
    void setDrawer(boolean on){ boolean edge=drawerOpen!=on; drawerOpen=on; slideHome(edge); }
    void fadeChrome(boolean hide){
        float a=hide?0f:1f;
        for (View v: new View[]{clk, gear, searchV, searchSplit, searchField, dimBar}){
            if (v==null) continue;
            if (v==dimBar && !dimSlide) continue;
            if (v==searchSplit && !searchOpen) continue;
            if (v==searchField && !searchTyping) continue;
            v.setVisibility(View.VISIBLE);
            v.animate().alpha(a).setDuration(600).setInterpolator(drawerIp).withEndAction(()->{ if (drawerOpen) v.setVisibility(View.INVISIBLE); }).start();
        }
        if (gear!=null) gear.setFocusable(!hide);
        if (searchV!=null) searchV.setFocusable(!hide && !dimSlide && !searchOpen);
        if (speakV!=null){ speakV.setFocusable(!hide && searchOpen && !dimSlide); typeV.setFocusable(!hide && searchOpen && !dimSlide); }
    }
    void boxLayer(View v, boolean on){ if (v!=null) v.setLayerType(on?View.LAYER_TYPE_HARDWARE:View.LAYER_TYPE_NONE, null); }
    void staggerDrawer(boolean open){
        if (homeGrid==null) return;
        int n=homeGrid.getChildCount(), rows=Math.max(1,(n+5)/6);
        for (int row=2; row<rows; row++){ // row 1 is the peek; leave it visible when closed
            int delay=open?(row-1)*40:(rows-1-row)*40;
            for (int c=0;c<6;c++){
                int i=row*6+c; if (i>=n) break;
                View t=homeGrid.getChildAt(i); t.animate().cancel();
                if (open){ t.setAlpha(0f); t.setTranslationY(p(12)); }
                t.animate().alpha(open?1f:0f).translationY(open?0:p(12)).setStartDelay(delay).setDuration(600).setInterpolator(drawerIp).start();
            }
        }
    }
    void slideHome(){ slideHome(false); }
    void slideHome(boolean edge){
        if (focus!=null && !editMode) focus.remember();
        int r=0; View f=getCurrentFocus();
        if (f!=null && homeGrid!=null && f.getParent()==homeGrid) r=Math.max(0, (editMode?tileIx(f):homeGrid.indexOfChild(f))/6);
        final boolean open=drawerOpen;
        float y = open ? -(p(APP_GRID_Y-17)+Math.max(0, r-4)*rowH()) : 0;
        fadeChrome(open);
        if (open){
            for (View v: new View[]{tabBar, cardWrap, homeGrid}) if (v!=null) v.bringToFront();
        } else {
            if (clk!=null) clk.bringToFront();
            if (gear!=null) gear.bringToFront();
            if (searchV!=null) searchV.bringToFront();
            if (searchSplit!=null && searchOpen) searchSplit.bringToFront();
            if (searchField!=null && searchTyping) searchField.bringToFront();
            if (dimBar!=null && dimSlide) dimBar.bringToFront();
        }
        if (editMode) stackEditDim();
        for (View v: slideViews()){
            if (v==null) continue;
            boolean up=v==heroG||v==tabBar||v==cardWrap;
            if (v==heroG) boxLayer(v, true);
            android.view.ViewPropertyAnimator an=v.animate().translationY(y).setDuration(600).setInterpolator(drawerIp);
            if (up) an.alpha(open?0f:1f).scaleX(open?0.98f:1f).scaleY(open?0.98f:1f);
            if (v==homeGrid) an.withEndAction(()->{ if (!editMode && getCurrentFocus()==null && focus!=null) focus.restore(); });
            else if (v==heroG) an.withEndAction(()->boxLayer(v, false));
            an.start();
        }
        if (edge) staggerDrawer(open);
    }
    void buildHomeGrid(){ buildHomeGrid(-1); }
    void buildHomeGrid(int focusIdx){
        if (editMode) return;
        if (homeGrid==null) return;
        List<String[]> all = homeApps();
        fullOrder = all;
        String sig=(editMode?"E":"N")+"|"+dragIdx+"|"+(drawerOpen?"D":"H")+"|";
        for (String[] a: all) sig += a[0]+"/"+a[1]+",";
        if (focusIdx<0 && sig.equals(homeBindSig) && homeGrid.getChildCount()==all.size()) return;
        homeBindSig=sig;
        homeGrid.removeAllViews();
        for (int i=0;i<all.size();i++) homeGrid.addView(homeTile(all.get(i), i), gridLp(i));
        ViewGroup.LayoutParams lp=homeGrid.getLayoutParams();
        if (lp!=null){ int height=rowH()*Math.max(1,(all.size()+5)/6); if (lp.height!=height){ lp.height=height; homeGrid.setLayoutParams(lp); } }
        if (focusIdx>=0 && homeGrid.getChildCount()>0){
            final int f=Math.min(focusIdx, homeGrid.getChildCount()-1);
            homeGrid.getChildAt(f).requestFocus();
            h.post(()->{ if (f<homeGrid.getChildCount()) homeGrid.getChildAt(f).requestFocus(); });
        }
    }
    View homeTile(final String[] a, final int i){
        final GlowFrame g = (GlowFrame)appTile(a[0], a[1], a[2]);
        g.setOnLongClickListener(v->{ if (!editMode) appMenu(g, a, tileIx(g)); return true; });
        g.setOnKeyListener((v,k,e)->{
            int ix=tileIx(v);
            boolean dpad = k==KeyEvent.KEYCODE_DPAD_LEFT||k==KeyEvent.KEYCODE_DPAD_RIGHT||k==KeyEvent.KEYCODE_DPAD_UP||k==KeyEvent.KEYCODE_DPAD_DOWN;
            boolean ok = k==KeyEvent.KEYCODE_DPAD_CENTER||k==KeyEvent.KEYCODE_ENTER||k==KeyEvent.KEYCODE_NUMPAD_ENTER;
            if (v==dragV){
                if (e.getAction()!=KeyEvent.ACTION_DOWN) return dpad||ok;
                if (ok){ if (e.getRepeatCount()==0) drop(ix); return true; }
                if (k==KeyEvent.KEYCODE_BACK){ drop(ix); return true; }
                int to = k==KeyEvent.KEYCODE_DPAD_LEFT? ix-1 : k==KeyEvent.KEYCODE_DPAD_RIGHT? ix+1 : k==KeyEvent.KEYCODE_DPAD_UP? ix-6 : k==KeyEvent.KEYCODE_DPAD_DOWN? ix+6 : -99;
                if (to==-99) return false;
                if (to>=0 && to<fullOrder.size()) moveDrag(ix, to);
                else if (k==KeyEvent.KEYCODE_DPAD_UP && ix<6 && drawerOpen) setDrawer(false);
                return true;
            }
            if (editMode) return false;
            if (ok){
                if (e.getAction()==KeyEvent.ACTION_DOWN && e.getRepeatCount()==0 && !e.isLongPress()){ pressHold(g); return false; }
                if (e.getAction()==KeyEvent.ACTION_DOWN && (e.isLongPress() || e.getRepeatCount()>=3)){ appMenu(g, a, ix); return true; }
                if (e.getAction()==KeyEvent.ACTION_UP){
                    if (appMenu!=null) return true;
                    if (pressHeld) pressUp(g);
                    return false;
                }
            }
            return false;
        });
        return g;
    }
    void markHomeSig(){
        String sig=(editMode?"E":"N")+"|"+dragIdx+"|"+(drawerOpen?"D":"H")+"|";
        if (fullOrder!=null) for (String[] a: fullOrder) sig += a[0]+"/"+a[1]+",";
        homeBindSig=sig;
    }
    int tileIx(View v){
        if (tiles!=null) for (int i=0;i<tiles.length;i++) if (tiles[i]==v) return i;
        return homeGrid==null?-1:homeGrid.indexOfChild(v);
    }
    int slotX(int i){ return (i%6)*slotW; }
    int slotY(int i){ return (i/6)*slotH; }
    void layer(View v, boolean on){ View t=v instanceof GlowFrame? ((GlowFrame)v).inner:v; if (t!=null) t.setLayerType(on?View.LAYER_TYPE_HARDWARE:View.LAYER_TYPE_NONE, null); }
    void retarget(int i){
        View v=tiles[i];
        tgtX[i]=slotX(i)-v.getLeft();
        tgtY[i]=slotY(i)-v.getTop()+(v==dragV?-liftY:0);
        if (v.getLayerType()!=View.LAYER_TYPE_HARDWARE) layer(v, true);
    }
    void kickGlide(){
        if (!glideOn){ glideOn=true; android.view.Choreographer.getInstance().postFrameCallback(glideCb); }
    }
    void clearLift(View v, boolean settle){
        if (v==null) return;
        v.animate().cancel();
        boolean foc=(v.isFocused()||v==menuOwner) && !editMode;
        float sc=foc?APP_FOCUS:1f;
        v.setRotation(0);
        if (settle) v.animate().scaleX(sc).scaleY(sc).translationX(0).translationY(0).rotation(0).setDuration(180).setInterpolator(dragIp).start();
        else { v.setScaleX(sc); v.setScaleY(sc); v.setTranslationX(0); v.setTranslationY(0); v.setRotation(0); }
        v.setTranslationZ(foc?p(6):0);
        if (v instanceof GlowFrame){ GlowFrame g=(GlowFrame)v; g.glow=foc?1f:0f; g.syncHalo(); g.invalidate(); }
    }
    void resetAllLifts(boolean settle, View keep){
        if (homeGrid==null) return;
        for (int j=0;j<homeGrid.getChildCount();j++){
            View c=homeGrid.getChildAt(j);
            c.setAlpha(1f);
            clearLift(c, settle && c==keep);
        }
    }
    void commitTiles(){
        glideOn=false;
        if (tiles==null || homeGrid==null) return;
        for (int i=0;i<tiles.length;i++){
            View v=tiles[i]; v.animate().cancel();
            v.setTranslationX(0); v.setTranslationY(0); v.setRotation(0); layer(v, false);
        }
        homeGrid.removeAllViews();
        for (int i=0;i<tiles.length;i++) homeGrid.addView(tiles[i], gridLp(i));
        tiles=null; tgtX=null; tgtY=null;
    }
    void enterEdit(int i){ if (editMode) return; editMode=true; closeMenu(false); startDrag(i); setEditDim(true); }
    void startDrag(int i){
        if (i<0 || i>=fullOrder.size() || homeGrid==null) return;
        int n=homeGrid.getChildCount();
        tiles=new View[n]; tgtX=new float[n]; tgtY=new float[n];
        slotW=p(APP_W)+p(APP_HG); slotH=rowH(); liftY=p(6);
        for (int j=0;j<n;j++){ tiles[j]=homeGrid.getChildAt(j); tgtX[j]=0; tgtY[j]=0; }
        dragIdx=i; dragV=tiles[i];
        if (i>=6 && !drawerOpen) setDrawer(true);
        layer(dragV, true);
        for (int j=0;j<n;j++){
            View c=tiles[j];
            if (c==dragV){
                if (c instanceof GlowFrame){ GlowFrame g=(GlowFrame)c; g.glow=1f; g.syncHalo(); g.setTranslationZ(p(18)); }
                c.animate().cancel();
                float from=c.getScaleX();
                c.setRotation(0); c.setTranslationY(-liftY); tgtY[j]=-liftY;
                c.setScaleX(from); c.setScaleY(from);
                c.animate().scaleX(APP_HOLD).scaleY(APP_HOLD).setDuration(160).setInterpolator(dragIp).start();
            } else c.setAlpha(0.5f);
        }
        if (!dragV.isFocused()) dragV.requestFocus();
    }
    void moveDrag(int from, int to){
        if (tiles==null || from==to || to<0 || to>=tiles.length) return;
        Collections.swap(fullOrder, from, to);
        View tmp=tiles[from]; tiles[from]=tiles[to]; tiles[to]=tmp;
        dragIdx=to; dragV=tmp;
        if (to>=6 && !drawerOpen) setDrawer(true);
        else if (to<6 && from>=6 && drawerOpen) setDrawer(false);
        retarget(from); retarget(to); kickGlide();
    }
    void drop(int i){
        View keep=tiles!=null && i>=0 && i<tiles.length? tiles[i] : (homeGrid!=null && i>=0 && i<homeGrid.getChildCount()? homeGrid.getChildAt(i):null);
        dragV=null; dragIdx=-1; editMode=false; saveHome(fullOrder); markHomeSig();
        commitTiles(); setEditDim(false);
        if (drawerOpen){ if (gear!=null) gear.setFocusable(false); if (searchV!=null) searchV.setFocusable(false); if (speakV!=null){ speakV.setFocusable(false); typeV.setFocusable(false); } }
        resetAllLifts(true, keep);
        if (keep!=null){
            if (focus!=null){ focus.savedSec=4+i/6; focus.savedIdx=i%6; }
            keep.requestFocus();
        }
    }
    void exitEdit(){ drop(Math.max(0, dragIdx)); }
    void ensureDim(){
        if (dimV!=null || root==null) return;
        dimV=new View(this); dimV.setBackgroundColor(Color.argb(150,4,4,10)); dimV.setAlpha(0f);
        dimV.setVisibility(View.INVISIBLE); dimV.setClickable(false); dimV.setFocusable(false);
        dimV.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        int at=homeGrid==null?-1:root.indexOfChild(homeGrid);
        root.addView(dimV, at<0?0:at, new FrameLayout.LayoutParams(-1,-1));
    }
    void stackEditDim(){
        if (dimV!=null) dimV.bringToFront();
        if (homeGrid!=null) homeGrid.bringToFront();
    }
    void setEditDim(boolean on){
        ensureDim(); if (dimV==null) return;
        dimV.animate().cancel();
        if (on){ stackEditDim(); dimV.setVisibility(View.VISIBLE); dimV.animate().alpha(1f).setDuration(160).setInterpolator(dragIp).start(); }
        else dimV.animate().alpha(0f).setDuration(140).setInterpolator(dragIp).withEndAction(()->dimV.setVisibility(View.INVISIBLE)).start();
    }
    void fadeRemove(final GridLayout g, final String pkg, final Runnable after){
        View tv=null; for (int i=0;i<g.getChildCount();i++) if (pkg.equals(g.getChildAt(i).getTag())) { tv=g.getChildAt(i); break; }
        if (tv==null || !tv.isFocusable()){ if (after!=null) after.run(); return; }
        final View t=tv;
        t.setFocusable(false);
        t.animate().alpha(0f).scaleX(0.85f).scaleY(0.85f).setDuration(250).setInterpolator(new android.view.animation.AccelerateInterpolator()).withEndAction(()->{ if (after!=null) after.run(); }).start();
    }
    boolean systemApp(String pkg){
        try { int f=pm.getApplicationInfo(pkg,0).flags; return (f&(ApplicationInfo.FLAG_SYSTEM|ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))!=0; }
        catch (Exception e){ return true; }
    }
    static final int[] MENU_ICONS={R.drawable.ic_menu_edit,R.drawable.ic_menu_stop,R.drawable.ic_menu_info,R.drawable.ic_menu_delete};
    void pressHold(View v){
        pressHeld=true;
        v.animate().cancel();
        v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(ViewConfiguration.getLongPressTimeout()).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
    }
    void pressUp(View v){
        pressHeld=false;
        v.animate().cancel();
        v.animate().scaleX(APP_FOCUS).scaleY(APP_FOCUS).setDuration(180).setInterpolator(dragIp).start();
    }
    void pressPop(View v){
        pressHeld=false;
        if (v==null) return;
        menuPop=v;
        tileSpring(v, APP_HOLD, 1f);
    }
    void pressSettle(View v){
        if (v==null) return;
        menuPop=v;
        tileSpring(v, APP_FOCUS, 1f);
    }
    void tileSpring(View v, float sc, float glowTo){
        android.view.animation.Interpolator ip=new android.view.animation.OvershootInterpolator(1.3f);
        v.animate().cancel();
        v.setTranslationZ(sc>1f?p(8):0);
        v.animate().scaleX(sc).scaleY(sc).setDuration(420).setInterpolator(ip).start();
        if (v instanceof GlowFrame){
            GlowFrame g=(GlowFrame)v;
            android.animation.ValueAnimator va=android.animation.ValueAnimator.ofFloat(g.glow, glowTo);
            va.setDuration(420); va.setInterpolator(ip);
            va.addUpdateListener(a->{ g.glow=Math.max(0f, Math.min(1f,(Float)a.getAnimatedValue())); g.syncHalo(); });
            va.start();
        }
    }
    void appMenu(View anchor, String[] a, int idx){
        if (editMode || appMenu!=null) return;
        if (focus!=null) focus.remember();
        menuOwner=anchor;
        pressPop(anchor);
        LinearLayout p=new LinearLayout(this); p.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg=new GradientDrawable(); bg.setColor(Color.argb(235,28,28,32)); bg.setCornerRadius(p(14)); bg.setStroke(Math.max(1,p(1)), Color.argb(51,255,255,255));
        p.setBackground(bg); p.setPadding(p(8),p(8),p(8),p(8)); p.setElevation(p(18));
        String[] labs={"Edit Home Screen","Force Stop","App Info","Uninstall"};
        boolean sys=systemApp(a[0]);
        View first=null;
        java.util.ArrayList<View> rows=new java.util.ArrayList<>();
        for (int n=0;n<labs.length;n++){
            final int id=n;
            boolean off=id==3 && sys;
            LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(p(10),0,p(10),0);
            row.setFocusable(!off); row.setClickable(!off);
            final ImageView li=new ImageView(this); li.setImageResource(MENU_ICONS[n]); li.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int idle=off?Color.argb(80,255,255,255):(id==3?Color.rgb(224,112,112):Color.WHITE);
            li.setColorFilter(idle, PorterDuff.Mode.SRC_IN);
            row.addView(li, new LinearLayout.LayoutParams(p(18),p(18)));
            final TextView t=text(labs[n], 12, idle, "sans-serif");
            LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-2,-2); tp.leftMargin=p(10);
            row.addView(t, tp);
            row.setOnFocusChangeListener((v,f)->{
                GradientDrawable pill=new GradientDrawable(); pill.setCornerRadius(p(8));
                pill.setColor(f?Color.argb(240,255,255,255):Color.TRANSPARENT);
                row.setBackground(pill);
                int fg=off?Color.argb(80,255,255,255):(f?Color.rgb(22,22,30):(id==3?Color.rgb(224,112,112):Color.WHITE));
                t.setTextColor(fg); li.setColorFilter(fg, PorterDuff.Mode.SRC_IN);
            });
            if (!off) row.setOnClickListener(v->menuAct(id, a, idx));
            row.setOnKeyListener((v,k,e)->{ if (k==KeyEvent.KEYCODE_BACK && e.getAction()==KeyEvent.ACTION_DOWN){ closeMenu(); return true; } return false; });
            p.addView(row, new LinearLayout.LayoutParams(p(200), p(36)));
            if (!off){ row.setId(View.generateViewId()); rows.add(row); if (first==null) first=row; }
        }
        for (int i=0;i<rows.size();i++){
            View r=rows.get(i);
            r.setNextFocusLeftId(r.getId()); r.setNextFocusRightId(r.getId());
            r.setNextFocusUpId(i==0?r.getId():rows.get(i-1).getId());
            r.setNextFocusDownId(i==rows.size()-1?r.getId():rows.get(i+1).getId());
        }
        p.measure(0,0);
        int mw=p.getMeasuredWidth(), mh=p.getMeasuredHeight();
        int[] loc=new int[2]; anchor.getLocationInWindow(loc);
        int sw=root.getWidth()>0?root.getWidth():p(1024), sh=root.getHeight()>0?root.getHeight():p(576);
        int x=loc[0]+anchor.getWidth()+p(10), y=loc[1];
        if (x+mw>sw-p(8)) x=loc[0]-mw-p(10);
        if (x<p(8)) x=p(8);
        if (y+mh>sh-p(8)) y=sh-mh-p(8);
        if (y<p(8)) y=p(8);
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(mw, mh);
        lp.leftMargin=x; lp.topMargin=y;
        p.setPivotX(x>=loc[0]?0:mw);
        p.setPivotY(Math.max(0, Math.min(mh, loc[1]+anchor.getHeight()/2f-y)));
        p.setAlpha(0f); p.setScaleX(0.3f); p.setScaleY(0.3f);
        appMenu=p; root.addView(p, lp);
        lockBehind(true);
        p.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(new android.view.animation.OvershootInterpolator(0.9f)).start();
        final View f0=first;
        h.post(()->{ if (appMenu!=null && f0!=null) f0.requestFocus(); });
    }
    boolean dimSlideKey(KeyEvent e){
        int k=e.getKeyCode();
        boolean ok=k==KeyEvent.KEYCODE_DPAD_CENTER||k==KeyEvent.KEYCODE_ENTER||k==KeyEvent.KEYCODE_NUMPAD_ENTER;
        if (ok){
            if (e.getAction()==KeyEvent.ACTION_UP){ dimSlideIgnore=false; return true; }
            if (e.getAction()!=KeyEvent.ACTION_DOWN) return true;
            if (dimSlideIgnore && e.getRepeatCount()>0) return true;
            closeDimSlide(); return true;
        }
        if (e.getAction()!=KeyEvent.ACTION_DOWN) return true;
        if (k==KeyEvent.KEYCODE_BACK){ closeDimSlide(); return true; }
        if (k==KeyEvent.KEYCODE_DPAD_LEFT){ dimSlideBy(-1); return true; }
        if (k==KeyEvent.KEYCODE_DPAD_RIGHT){ dimSlideBy(1); return true; }
        if (k==KeyEvent.KEYCODE_DPAD_UP||k==KeyEvent.KEYCODE_DPAD_DOWN){ closeDimSlide(); return dispatchKeyEvent(e); }
        return true;
    }
    boolean menuKey(KeyEvent e){
        int k=e.getKeyCode();
        if (k==KeyEvent.KEYCODE_BACK){ if (e.getAction()==KeyEvent.ACTION_DOWN) closeMenu(); return true; }
        if (k==KeyEvent.KEYCODE_DPAD_LEFT||k==KeyEvent.KEYCODE_DPAD_RIGHT) return true;
        if (k==KeyEvent.KEYCODE_DPAD_UP||k==KeyEvent.KEYCODE_DPAD_DOWN){
            if (e.getAction()==KeyEvent.ACTION_DOWN) menuMove(k==KeyEvent.KEYCODE_DPAD_DOWN);
            return true;
        }
        return super.dispatchKeyEvent(e);
    }
    void menuMove(boolean down){
        if (appMenu==null) return;
        java.util.ArrayList<View> items=new java.util.ArrayList<>();
        for (int i=0;i<appMenu.getChildCount();i++){ View c=appMenu.getChildAt(i); if (c.isFocusable()) items.add(c); }
        if (items.isEmpty()) return;
        View f=getCurrentFocus(); int ix=items.indexOf(f);
        if (ix<0){ items.get(0).requestFocus(); return; }
        int n=ix+(down?1:-1);
        if (n>=0 && n<items.size()) items.get(n).requestFocus();
    }
    void lockBehind(boolean on){
        if (root==null) return;
        for (int i=0;i<root.getChildCount();i++){
            View c=root.getChildAt(i);
            if (c==appMenu) continue;
            if (c instanceof ViewGroup){
                ((ViewGroup)c).setDescendantFocusability(on?ViewGroup.FOCUS_BLOCK_DESCENDANTS:ViewGroup.FOCUS_AFTER_DESCENDANTS);
                if (c==heroG){ c.setFocusable(false); if (heroBtn!=null) heroBtn.setFocusable(!on); }
            }
            else if (c instanceof PillView){
                if (on) c.setFocusable(false);
                else if (c==gear) c.setFocusable(!drawerOpen);
                else if (c==searchV) c.setFocusable(!drawerOpen && !dimSlide && !searchOpen);
                else if (c==speakV||c==typeV) c.setFocusable(!drawerOpen && searchOpen && !dimSlide);
                else if (c==dimBar) c.setFocusable(false);
                else c.setFocusable(true);
            }
        }
    }
    void menuAct(int id, String[] a, int idx){
        if (id==0){ enterEdit(idx); return; }
        closeMenu();
        if (id==1 || id==2){ try { startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:"+a[0])).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception e){} return; }
        if (id==3 && !systemApp(a[0])){ try { startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:"+a[0])).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception e){} }
    }
    void closeMenu(){ closeMenu(true); }
    void closeMenu(boolean back){
        if (dimSlide) closeDimSlide();
        if (appMenu==null) return;
        lockBehind(false);
        View owner=menuOwner; menuOwner=null;
        final View m=appMenu; appMenu=null;
        m.animate().cancel();
        m.animate().alpha(0f).scaleX(0.3f).scaleY(0.3f).setDuration(140).setInterpolator(new android.view.animation.AccelerateInterpolator()).withEndAction(()->{ if (m.getParent()!=null) root.removeView(m); }).start();
        if (owner!=null) pressSettle(owner);
        if (back && focus!=null) focus.restore();
    }
    void appInfo(String pkg){ try { startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:"+pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception e){} }
    Bitmap vigMask; final Map<String,Bitmap> heroMasked=new HashMap<>();
    Bitmap alphaMask(int w, int h){
        if (vigMask!=null && vigMask.getWidth()==w && vigMask.getHeight()==h) return vigMask;
        Bitmap bm=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(bm);
        c.drawColor(Color.WHITE);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.DITHER_FLAG); p.setDither(true);
        p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        float artW=Math.min(w,p(758)), artH=Math.min(h,p(360)), artL=Math.max(0,w-artW), artB=artH, leftEnd=artL+artW*HERO_LEFT_FADE, botEnd=artB-artH*0.40f, botRange=h-botEnd;
        p.setShader(new LinearGradient(0,0,leftEnd,0, new int[]{Color.TRANSPARENT,Color.TRANSPARENT,Color.WHITE}, new float[]{0, leftEnd<=0?0:artL/leftEnd, 1}, Shader.TileMode.CLAMP));
        c.drawRect(0,0,w,h,p);
        p.setShader(new LinearGradient(0,h,0,botEnd, new int[]{Color.TRANSPARENT,Color.TRANSPARENT,Color.WHITE}, new float[]{0, botRange<=0?0:(h-artB)/botRange, 1}, Shader.TileMode.CLAMP));
        c.drawRect(0,0,w,h,p);
        p.setShader(new LinearGradient(0,0,0,h*0.064f, Color.argb(240,255,255,255), Color.WHITE, Shader.TileMode.CLAMP));
        c.drawRect(0,0,w,h,p);
        float rad=artH*0.37f;
        p.setShader(new RadialGradient(artL, artB, rad, new int[]{Color.TRANSPARENT, Color.argb(50,255,255,255), Color.WHITE}, new float[]{0, 0.5f, 1}, Shader.TileMode.CLAMP));
        c.drawRect(artL, artB-rad, Math.min(w, artL+rad), artB, p);
        vigMask=bm; return bm;
    }
    Bitmap maskHero(String key, Bitmap src, int tw, int th){
        if (tw<=0||th<=0||src==null) return src;
        Bitmap hit=heroMasked.get(key);
        if (hit!=null && !hit.isRecycled() && hit.getWidth()==tw && hit.getHeight()==th) return hit;
        Bitmap out=Bitmap.createBitmap(tw,th,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(out);
        float artW=Math.min(tw,p(758)), artH=Math.min(th,p(360)), artL=tw-artW;
        float s=Math.max(artW/src.getWidth(), artH/src.getHeight());
        float dw=src.getWidth()*s, dh=src.getHeight()*s, cx=artL+artW/2f, cy=artH/2f;
        c.drawBitmap(src, null, new RectF(cx-dw/2,cy-dh/2,cx+dw/2,cy+dh/2), null);
        Paint xp=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.DITHER_FLAG); xp.setDither(true);
        xp.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        c.drawBitmap(alphaMask(tw,th),0,0,xp);
        float leftEnd=artL+artW*HERO_LEFT_FADE;
        xp.setXfermode(null);
        xp.setShader(new LinearGradient(0,0,leftEnd,0, new int[]{0xE6000000|HERO_SCRIM_RGB,0xC2000000|HERO_SCRIM_RGB,HERO_SCRIM_RGB}, new float[]{0, leftEnd<=0?0:artL/leftEnd, 1}, Shader.TileMode.CLAMP));
        c.drawRect(0,0,leftEnd,th,xp);
        heroMasked.put(key, out); return out;
    }
    // ---------- Featured hero ----------
    ImageView heroArt, heroBadge; TextView heroTitle, heroSub; LinearLayout heroMeta; List<Object[]> heroItems = new ArrayList<>(); int heroIdx=0;
    void fitHeroMeta(){
        if (heroTitle==null) return;
        ts(heroTitle, 23); ts(heroSub, 14);
        if (heroTitle.getPaint().measureText(heroTitle.getText().toString())>p(360)) ts(heroTitle, 17);
    }
    void bindHeroCopy(Object[] o){
        String raw=o[2]==null?"":(String)o[2];
        String tag=o.length>5?(String)o[5]:"";
        String meta="", desc=raw;
        int nl=raw.indexOf('\n');
        if ("NEW".equals(tag) && nl>=0){ meta=raw.substring(0,nl).trim(); desc=raw.substring(nl+1); }
        desc=firstSentence(desc.replaceAll("\\s+"," ").trim());
        if (heroLabel!=null){ heroLabel.setText(meta); heroLabel.setVisibility(meta.isEmpty()?View.GONE:View.VISIBLE); }
        heroTitle.setText((String)o[1]); heroSub.setText(desc);
        bindHeroAction(o);
    }
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
    boolean heroPlayable(Object[] o){
        if (o==null || o.length<5 || !(o[4] instanceof Intent)) return false;
        try { if (((Intent)o[4]).resolveActivity(pm)==null) return false; } catch(Exception e){ return false; }
        long avail=0; if (o.length>6 && o[6] instanceof Number) avail=((Number)o[6]).longValue();
        return avail<=System.currentTimeMillis();
    }
    void bindHeroAction(Object[] o){ setPill(heroBtn, heroPlayable(o)?"Play Now":"More Info"); }
    void showHeroAction(boolean on){
        if (heroBtn==null || on==heroCtaOn) return;
        heroCtaOn=on;
        android.view.animation.Interpolator ease=new android.view.animation.PathInterpolator(0.16f,1f,0.3f,1f);
        heroBtn.animate().cancel();
        if (on) heroBtn.setFocusable(true);
        heroBtn.animate().alpha(on?1f:0f).translationY(on?0:p(8)).scaleX(on?1.07f:1f).scaleY(on?1.07f:1f)
            .setStartDelay(0).setDuration(on?160:120).setInterpolator(ease).withLayer().start();
        if (heroDots!=null){
            int n; synchronized(heroLock){ n=heroItems.size(); }
            heroDots.animate().cancel();
            if (on && n>1){
                heroDots.bind(n, heroIdx, false);
                heroDots.setVisibility(View.VISIBLE);
                heroDots.animate().alpha(1f).setDuration(160).setInterpolator(ease).start();
            } else {
                heroDots.animate().alpha(0f).setDuration(120).setInterpolator(new android.view.animation.AccelerateInterpolator()).withEndAction(()->{ if (heroDots!=null && (heroBtn==null||!heroBtn.isFocused())) heroDots.setVisibility(View.GONE); }).start();
            }
        }
    }
    void heroAction(){
        Object[] o=heroOnScreen;
        if (o==null) synchronized(heroLock){ o=heroItems.isEmpty()?null:heroItems.get(heroIdx); }
        if (o==null){ launchPkg(installed("com.plexapp.android")?"com.plexapp.android":PLAY_PKGS[0]); return; }
        if (heroPlayable(o)){
            try { startActivity(((Intent)o[4]).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return; } catch(Exception e){}
        }
        try { if (o[4] instanceof Intent) startActivity(((Intent)o[4]).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); else launchPkg((String)o[0]); }
        catch(Exception e){ launchPkg((String)o[0]); }
    }
    ImageView heroArt2;
    void applyFeaturedCache(){ hero.applyFeaturedCache(); }
    long heroGate;
    void heroStep(boolean next){
        if (android.os.SystemClock.uptimeMillis()<heroGate) return;
        synchronized(heroLock){
            int n=heroItems.size();
            if (n<=1) return;
            heroIdx = (heroIdx + (next?1:-1) + n) % n;
        }
        h.removeCallbacks(hero.heroRotate);
        showHero();
        h.postDelayed(hero.heroRotate, HERO_ROTATE_MS);
    }
    void showHero(){ hero.showHero(); }

    Map<String,Bitmap> glyphs=new HashMap<>();
    // v12: white monochrome mark extracted from the app banner (pixels that differ from the banner background become white, alpha by contrast), cropped, with a soft shadow
    Bitmap glyph(String pkg, Drawable d){
        Bitmap c=glyphs.get(pkg); if (c!=null) return c;
        int W=320, H=Math.max(1,Math.round(320f*d.getIntrinsicHeight()/Math.max(1,d.getIntrinsicWidth()))); if (d.getIntrinsicWidth()<=0){ H=180; }
        Bitmap src=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888); android.graphics.Canvas cv=new android.graphics.Canvas(src); d.setBounds(0,0,W,H); d.draw(cv);
        int[] px=new int[W*H]; src.getPixels(px,0,W,0,0,W,H);
        int[] o=px.clone(); int x0=W,y0=H,x1=-1,y1=-1;
        for (int i=0;i<px.length;i++){ int x=i%W, y=i/W, q=o[i]; int a=Color.alpha(q);
            int[] refs={o[y*W+1],o[y*W+W-2],o[W+x],o[(H-2)*W+x]}; float dist=1f;
            for (int r: refs){ float dd; if (Color.alpha(r)<40) dd=a/255f; else dd=(Math.abs(Color.red(q)-Color.red(r))+Math.abs(Color.green(q)-Color.green(r))+Math.abs(Color.blue(q)-Color.blue(r)))/(3*255f)*(a/255f); dist=Math.min(dist,dd); }
            int al=Math.round(Math.min(1f,Math.max(0f,(dist-0.10f)*3.2f))*255);
            px[i]=Color.argb(al,255,255,255);
            if (al>40){ if(x<x0)x0=x; if(x>x1)x1=x; if(y<y0)y0=y; if(y>y1)y1=y; } }
        Bitmap w=Bitmap.createBitmap(px,W,H,Bitmap.Config.ARGB_8888);
        if (x1>x0 && y1>y0) w=Bitmap.createBitmap(w,x0,y0,x1-x0+1,y1-y0+1);
        int pad=8; Bitmap out=Bitmap.createBitmap(w.getWidth()+pad*2,w.getHeight()+pad*2,Bitmap.Config.ARGB_8888); android.graphics.Canvas oc=new android.graphics.Canvas(out);
        android.graphics.Paint sh=new android.graphics.Paint(); sh.setMaskFilter(new android.graphics.BlurMaskFilter(5, android.graphics.BlurMaskFilter.Blur.NORMAL));
        int[] off=new int[2]; Bitmap a=w.extractAlpha(sh,off); android.graphics.Paint sp=new android.graphics.Paint(); sp.setColor(Color.argb(150,0,0,0)); oc.drawBitmap(a,pad+off[0],pad+off[1]+2,sp);
        oc.drawBitmap(w,pad,pad,null);
        glyphs.put(pkg,out); return out; }

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
            StringBuilder sb=new StringBuilder(); List<String> keys=new ArrayList<>(usage.keySet()); Collections.sort(keys,(a,b)->Long.compare(use(b),use(a)));
            for (int i=0;i<Math.min(15,keys.size());i++) sb.append(keys.get(i)).append('=').append(use(keys.get(i))/60000).append("m ");
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
        buildHomeGrid();
        applyTabs();
        refreshCards();
        applyFeaturedCache();
        if (focus!=null) focus.afterData();
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
    void useWhatsNew(String js){ hero.useWhatsNew(js); }

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
