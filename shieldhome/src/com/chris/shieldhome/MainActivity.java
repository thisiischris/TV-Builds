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
    static final int TILE_R = 11;
    static final float APP_FOCUS=1.13f, APP_HOLD=1.16f, APP_W=142.5f, APP_H=78.5f, APP_HG=19.8f, APP_VG=21f;
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
    void setPill(PillView v, String s){ if (v==null) return; v.label=s; ViewGroup.LayoutParams lp=v.getLayoutParams(); if (lp!=null){ lp.width=pillTextW(v,s); if (v==heroBtn) lp.height=p(34); v.setLayoutParams(lp); } v.invalidate(); if (v==heroBtn && heroDots!=null) heroDots.invalidate(); }

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

        // Quick settings gear + inline options
        gear = new PillView(this, "", 5, false); gear.setId(View.generateViewId());
        gear.setOnClickListener(v->openDimSlide());
        gear.setOnFocusChangeListener((v,f)->{ if (!f && dimSlide) closeDimSlide(); });
        place(gear, 35, 17, 28, 28);
        PillView search = searchV = new PillView(this, "Search", 3, false);
        search.setOnFocusChangeListener((v,f)->{ if (f && !dimSlide) expandSearch(); });
        place(search, 71, 17, 104, 28);
        searchSplit = new LinearLayout(this); searchSplit.setOrientation(LinearLayout.HORIZONTAL); searchSplit.setGravity(Gravity.CENTER_VERTICAL);
        searchSplit.setVisibility(View.GONE);
        speakV = new PillView(this, "", 6, false); typeV = new PillView(this, "", 7, false);
        speakV.setId(View.generateViewId()); typeV.setId(View.generateViewId());
        speakV.setNextFocusRightId(typeV.getId()); typeV.setNextFocusLeftId(speakV.getId());
        speakV.setNextFocusDownId(View.NO_ID); typeV.setNextFocusDownId(View.NO_ID);
        speakV.setNextFocusUpId(View.NO_ID); typeV.setNextFocusUpId(View.NO_ID);
        speakV.setOnClickListener(v->openAssistant()); typeV.setOnClickListener(v->openSearchField());
        speakV.setOnFocusChangeListener((v,f)->{ if (!f) h.post(this::maybeCollapseSearch); });
        typeV.setOnFocusChangeListener((v,f)->{ if (!f) h.post(this::maybeCollapseSearch); });
        searchSplit.addView(speakV, new LinearLayout.LayoutParams(p(49), p(28)));
        searchSplit.addView(new View(this), new LinearLayout.LayoutParams(p(6), p(28)));
        searchSplit.addView(typeV, new LinearLayout.LayoutParams(p(49), p(28)));
        place(searchSplit, 71, 17, -2, 28);
        searchField = new EditText(this);
        searchField.setSingleLine(true);
        searchField.setHint("Search");
        searchField.setTextColor(Color.rgb(232,232,240));
        searchField.setHintTextColor(Color.argb(140,232,232,240));
        searchField.setHighlightColor(Color.argb(80,64,156,255));
        ts(searchField, 12);
        searchField.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        searchField.setIncludeFontPadding(false);
        searchField.setGravity(Gravity.CENTER_VERTICAL);
        searchField.setPadding(p(14), 0, p(14), 0);
        searchField.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        searchField.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        searchField.setMinHeight(0); searchField.setMinimumHeight(0);
        searchField.setFocusableInTouchMode(true);
        GradientDrawable fieldBg=new GradientDrawable();
        fieldBg.setCornerRadius(p(14));
        fieldBg.setColor(Color.argb(185,102,100,119));
        fieldBg.setStroke(1, Color.argb(40,255,255,255));
        searchField.setBackground(fieldBg);
        searchField.setVisibility(View.GONE);
        searchField.setOnEditorActionListener((v,id,ev)->{
            if (id!=android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH && id!=android.view.inputmethod.EditorInfo.IME_ACTION_DONE) return false;
            String q=v.getText().toString().trim();
            if (!q.isEmpty()) launchSearch(q);
            return true;
        });
        searchField.setOnFocusChangeListener((v,f)->{ if (!f) closeSearchField(); });
        place(searchField, 71, 17, 200, 28);
        dimBar = new PillView(this, "", 5, false);
        dimBar.setFocusable(false); dimBar.setClickable(false); dimBar.setVisibility(View.GONE);
        dimBar.setPivotX(p(100)); dimBar.setPivotY(p(14));
        place(dimBar, 71, 17, 200, 28);

        FrameLayout hero = new FrameLayout(this); hero.setClipChildren(false);
        FrameLayout artHost = new FrameLayout(this); artHost.setClipChildren(true);
        ImageView art = new ImageView(this); art.setImageResource(R.drawable.hero); art.setScaleType(ImageView.ScaleType.FIT_XY);
        heroArt2 = new ImageView(this); heroArt2.setScaleType(ImageView.ScaleType.FIT_XY); heroArt2.setAlpha(0f);
        artHost.addView(art, new FrameLayout.LayoutParams(-1,-1));
        artHost.addView(heroArt2, new FrameLayout.LayoutParams(-1,-1));
        hero.addView(artHost, lp(-1, p(400), Gravity.TOP, 0,0,0,0));
        LinearLayout meta = new LinearLayout(this); meta.setOrientation(LinearLayout.VERTICAL);
        heroArt = art;
        heroBadge = new ImageView(this); heroBadge.setScaleType(ImageView.ScaleType.FIT_START); heroBadge.setAlpha(0.7f);
        heroLabel = text("", 11, Color.rgb(190,188,205), "sans-serif-medium");
        heroTitle = text("Your Library", 23, Color.rgb(220,220,230), "sans-serif-medium");
        heroTitle.setSingleLine(false); heroTitle.setMaxLines(2); heroTitle.setEllipsize(android.text.TextUtils.TruncateAt.END); heroTitle.setHorizontallyScrolling(false);
        heroTitle.setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NONE); heroTitle.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_SIMPLE);
        heroSub = text("Movies and shows from your Plex Media Server", 14, Color.rgb(190,188,205), "sans-serif");
        heroSub.setLineSpacing(p(3),1f); heroSub.setSingleLine(false); heroSub.setMaxLines(3); heroSub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        heroSub.setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NONE); heroSub.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_SIMPLE);
        heroLabel.setSingleLine(false); heroLabel.setMaxLines(2); heroLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);
        heroLabel.setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NONE); heroLabel.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_SIMPLE);
        heroLabel.setVisibility(View.GONE);
        heroFocus = new View(this); heroFocus.setBackgroundColor(Color.WHITE); heroFocus.setAlpha(1f);
        meta.addView(heroBadge, new LinearLayout.LayoutParams(p(80), p(22)));
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(-2,-2); ml.topMargin=p(6); meta.addView(heroLabel, ml);
        LinearLayout.LayoutParams mt = new LinearLayout.LayoutParams(-1,-2); mt.topMargin=p(6); meta.addView(heroTitle, mt);
        LinearLayout.LayoutParams mf = new LinearLayout.LayoutParams(p(40), Math.max(2,p(1.4f))); mf.topMargin=p(6); meta.addView(heroFocus, mf);
        LinearLayout.LayoutParams ms = new LinearLayout.LayoutParams(-1,-2); ms.topMargin=p(8); meta.addView(heroSub, ms);
        heroMeta = meta;
        hero.addView(meta, lp(p(360),-2,Gravity.TOP|Gravity.START,p(35),p(72),0,0));
        heroBtn = new PillView(this, "Play Now", 4, true);
        heroBtn.setAlpha(0f); heroBtn.setTranslationY(p(8));
        heroBtn.setOnClickListener(v->heroAction());
        heroDots = new HeroDots();
        LinearLayout ctaCol = new LinearLayout(this);
        ctaCol.setOrientation(LinearLayout.VERTICAL); ctaCol.setGravity(Gravity.CENTER_HORIZONTAL);
        ctaCol.setClipChildren(false); ctaCol.setClipToPadding(false);
        ctaCol.addView(heroBtn, new LinearLayout.LayoutParams(p(138), p(34)));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-2, p(28)); dlp.topMargin=p(9);
        ctaCol.addView(heroDots, dlp);
        hero.addView(ctaCol, lp(-2, -2, Gravity.TOP|Gravity.START, p(35), p(237), 0, 0));
        art.post(()->{ int w=art.getWidth(), h=art.getHeight(); if (w>0){ Drawable d=art.getDrawable(); if (d!=null){ int iw=Math.max(1,d.getIntrinsicWidth()), ih=Math.max(1,d.getIntrinsicHeight()); Bitmap src=Bitmap.createBitmap(iw,ih,Bitmap.Config.ARGB_8888); Canvas cv=new Canvas(src); d.setBounds(0,0,iw,ih); d.draw(cv); art.setImageBitmap(maskHero("hero", src, w, h)); } } });
        heroG = hero; hero.setFocusable(false); hero.setClickable(false);
        heroBtn.setOnFocusChangeListener((x,f)-> showHeroAction(f));
        root.addView(hero, 0, lp(-1, p(400), Gravity.TOP, 0,0,0,0));

        tabBar = new SegBar(); tabPlay=tabBar.play; tabLive=tabBar.live;
        place(tabBar, 35, 308, -2, 31);

        // Cards row: 5-tile viewport; clip off so scale+glow can draw; 6th hidden by alpha
        cardWrap = new FrameLayout(this);
        cardWrap.setClipChildren(false); cardWrap.setClipToPadding(false);
        cardScroll = new HorizontalScrollView(this){
            @Override protected int computeScrollDeltaToGetChildRectOnScreen(Rect r){ return 0; }
        };
        cardScroll.setHorizontalScrollBarEnabled(false);
        cardScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        cardScroll.setClipChildren(false); cardScroll.setClipToPadding(false);
        cardScroll.setPadding(p(20),p(20),p(20),p(20));
        cardRow = new LinearLayout(this); cardRow.setClipChildren(false); cardRow.setClipToPadding(false);
        cardScroll.addView(cardRow, new ViewGroup.LayoutParams(-2,-1));
        cardWrap.addView(cardScroll, new FrameLayout.LayoutParams(-1,-1));
        place(cardWrap, 15, 330, 994, 160);
        cardScroll.getViewTreeObserver().addOnScrollChangedListener(this::updateCardAlphas);

        homeGrid = new GridLayout(this); homeGrid.setColumnCount(6); homeGrid.setClipChildren(false); homeGrid.setClipToPadding(false);
        place(homeGrid, 35, 483, 954, APP_H+APP_VG);
        ensureDim();
        buildHomeGrid();

        setContentView(root);
        h.post(()->{ if (tabPlay.getVisibility()==View.VISIBLE) tabPlay.requestFocus(); else tabLive.requestFocus(); });
    }

    // ---------- Pills with drawn icons ----------
    class PillView extends View {
        String label; int icon; boolean on; Drawable asst; Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);
        PillView(Context c, String l, int ic, boolean o){ super(c); label=l; icon=ic; on=o; setFocusable(true); setClickable(true); }
        void setOn(boolean o){ on=o; invalidate(); }
        @Override protected void onFocusChanged(boolean f, int d, Rect r){ super.onFocusChanged(f,d,r); animate().scaleX(f?1.07f:1f).scaleY(f?1.07f:1f).setDuration(160).setInterpolator(new android.view.animation.DecelerateInterpolator()).start(); setElevation(f?p(4):0); invalidate(); }
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
            if (!anim || getAlpha()<0.05f || n<=1){ snap(i); return; }
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
            pt.setStyle(Paint.Style.STROKE); pt.setStrokeWidth(1); pt.setColor(Color.argb(40,255,255,255));
            c.drawRoundRect(.5f,.5f,getWidth()-.5f,hh-.5f,rr,rr,pt);
            pt.setStyle(Paint.Style.FILL);
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
        View gv = focusable(f, bg, TILE_R, APP_FOCUS, ()->{ try{ startActivity(li);}catch(Exception e){} });
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
        for (int row=1; row<rows; row++){
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
        float y = open ? -(p(466)+Math.max(0, r-4)*rowH()) : 0;
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
        if (lp!=null){ lp.height=rowH()*Math.max(1,(all.size()+5)/6); homeGrid.setLayoutParams(lp); }
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
        float artW=Math.min(w,p(758)), artH=Math.min(h,p(360)), artL=Math.max(0,w-artW), artB=artH, leftEnd=artL+artW*0.24f, botEnd=artB-artH*0.40f, botRange=h-botEnd;
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
        desc=desc.replaceAll("\\s+"," ").trim();
        if (heroLabel!=null){ heroLabel.setText(meta); heroLabel.setVisibility(meta.isEmpty()?View.GONE:View.VISIBLE); }
        heroTitle.setText((String)o[1]); heroSub.setText(desc);
        bindHeroAction(o);
    }
    boolean heroPlayable(Object[] o){
        if (o==null || o.length<5 || !(o[4] instanceof Intent)) return false;
        try { if (((Intent)o[4]).resolveActivity(pm)==null) return false; } catch(Exception e){ return false; }
        long avail=0; if (o.length>6 && o[6] instanceof Number) avail=((Number)o[6]).longValue();
        return avail<=System.currentTimeMillis();
    }
    void bindHeroAction(Object[] o){ setPill(heroBtn, heroPlayable(o)?"Play Now":"More Info"); }
    void showHeroAction(boolean on){
        if (heroBtn==null) return;
        heroBtn.animate().cancel();
        android.view.animation.Interpolator ease=new android.view.animation.PathInterpolator(0.16f,1f,0.3f,1f);
        if (on){ heroBtn.setFocusable(true); heroBtn.animate().alpha(1f).translationY(0).setDuration(160).setInterpolator(ease).start(); }
        else heroBtn.animate().alpha(0f).translationY(p(8)).setDuration(120).setInterpolator(new android.view.animation.AccelerateInterpolator()).start();
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
    final Runnable heroRotate = new Runnable(){ public void run(){ synchronized(heroLock){ if (heroItems.size()>1) heroIdx=(heroIdx+1)%heroItems.size(); else { h.postDelayed(this, 16000); return; } } showHero(); h.postDelayed(this, 16000); } };
    ImageView heroArt2; boolean heroFront=true;
    void loadHero(){ applyFeaturedCache(); }
    void applyFeaturedCache(){
        List<Object[]> feat=featuredCache;
        if (feat==null) return;
        String fs=heroSig(feat);
        if (fs.equals(featuredBoundSig)) return;
        featuredBoundSig=fs;
        synchronized(heroLock){ heroItems.clear(); heroItems.addAll(feat); heroIdx=0; }
        h.removeCallbacks(heroRotate);
        if (!heroItems.isEmpty()){ showHero(); h.postDelayed(heroRotate, 16000); }
        net.loadWhatsNew();
    }
    static String heroSig(List<Object[]> l){
        StringBuilder b=new StringBuilder();
        for (Object[] o: l) b.append(o[0]).append('|').append(o[1]).append('|').append(o[3]).append(',');
        return b.toString();
    }
    void heroStep(boolean next){
        synchronized(heroLock){
            int n=heroItems.size();
            if (n<=1) return;
            heroIdx = (heroIdx + (next?1:-1) + n) % n;
        }
        h.removeCallbacks(heroRotate);
        showHero();
        h.postDelayed(heroRotate, 16000);
    }
    void showHero(){
        final Object[] o;
        synchronized(heroLock){ if (heroItems.isEmpty()) return; o=heroItems.get(Math.min(heroIdx, heroItems.size()-1)); }
        bindHeroAction(o);
        final String uri = (String)o[3];
        final int tw=heroG!=null&&heroG.getWidth()>0?heroG.getWidth():p(1024), th=heroG!=null&&heroG.getHeight()>0?heroG.getHeight():p(400), g=++heroLoadGen;
        pool.execute(()->{
            Bitmap raw = uri==null||images==null?null:images.decode(uri, tw, th);
            Bitmap bm = raw==null?null:maskHero(uri, raw, tw, th);
            h.post(()->{ if (g!=heroLoadGen) return; heroOnScreen=o; transitionHero(o, bm); });
        });
    }
    void transitionHero(final Object[] o, Bitmap bm){
        android.view.animation.Interpolator ease = new android.view.animation.PathInterpolator(0.4f,0f,0.2f,1f);
        final ImageView in = heroFront ? heroArt2 : heroArt, out = heroFront ? heroArt : heroArt2;
        if (bm != null){
            in.setScaleType(ImageView.ScaleType.FIT_XY);
            in.setImageBitmap(bm); in.setScaleX(1f); in.setScaleY(1f); in.setAlpha(0f); in.bringToFront();
            in.animate().alpha(1f).setDuration(1600).setInterpolator(ease).start();
            out.animate().alpha(0f).setDuration(1600).setInterpolator(ease).start();
            heroFront = !heroFront;
        }
        final View[] texts = {heroTitle, heroSub, heroBadge, heroFocus, heroLabel};
        for (View t: texts) t.animate().alpha(0f).translationX(-p(10)).setDuration(450).setInterpolator(new android.view.animation.AccelerateInterpolator()).start();
        h.postDelayed(()->{
            bindHeroCopy(o);
            try { Drawable bd = pm.getApplicationBanner((String)o[0]); if (bd==null) bd = pm.getApplicationIcon((String)o[0]); heroBadge.setImageBitmap(glyph((String)o[0], bd));} catch(Exception e){}
            fitHeroMeta();
            if (heroDots!=null && heroDots.getVisibility()==View.VISIBLE){ int n; synchronized(heroLock){ n=heroItems.size(); } heroDots.bind(n, heroIdx, true); }
            int d=200; for (View t: texts){ t.setTranslationX(p(16)); t.animate().alpha(t==heroBadge?0.7f:1f).translationX(0).setStartDelay(d).setDuration(900).setInterpolator(new android.view.animation.DecelerateInterpolator(2f)).start(); d+=90; }
        }, 480);
    }

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
            plutoIdsCache=tv.queryPlutoIds();
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
    String cardSig(){
        StringBuilder b=new StringBuilder(tab==0?"P|":"L|");
        if (tab==0){ if (wnPickCache!=null) for (Object[] o: wnPickCache) b.append(o[0]).append('|').append(o[1]).append('|').append(o[2]).append(','); }
        else { if (plutoIdsCache!=null) b.append(plutoIdsCache); b.append('|').append(plutoSlug.size()); }
        return b.toString();
    }
    List<String[]> apps(){ return appsCache!=null?appsCache:tv.queryApps(); }

    // ---------- Cards ----------
    void addCard(View v, int i){
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(p(184.4f), p(112)); lp.bottomMargin=p(8); lp.leftMargin = i==0?0:p(8);
        if (v.getParent()==cardRow){ v.setLayoutParams(lp); return; }
        if (v.getParent()!=null) ((ViewGroup)v.getParent()).removeView(v);
        if (!(v.getLayoutParams() instanceof LinearLayout.LayoutParams)){
            final View.OnFocusChangeListener base = v.getOnFocusChangeListener();
            v.setOnFocusChangeListener((x,f)->{ if (base!=null) base.onFocusChange(x,f); if (f) snapCardRow(x); });
        }
        cardRow.addView(v, lp);
        if (v instanceof GlowFrame) ((GlowFrame)v).warm(p(184.4f), p(112));
    }
    void snapCardRow(View v){
        if (cardScroll==null) return;
        int idx=cardRow.indexOfChild(v); if (idx<0) return;
        int x=snapX(cardScroll.getScrollX(), idx, p(184.4f), p(8), p(954), cardRow.getChildCount());
        if (x!=cardScroll.getScrollX()) cardScroll.smoothScrollTo(x,0); else updateCardAlphas();
    }
    void resetCardRow(){
        if (focus!=null){ focus.lastIdx[3]=0; focus.lastCardX=0; }
        if (cardScroll!=null) cardScroll.scrollTo(0,0);
        if (cardRow!=null && cardRow.getChildCount()>0 && cardRow.indexOfChild(getCurrentFocus())>=0) cardRow.getChildAt(0).requestFocus();
        updateCardAlphas();
    }
    void updateCardAlphas(){
        if (cardRow==null) return;
        int sx=cardScroll==null?0:cardScroll.getScrollX(), stride=p(184.4f)+p(8);
        for (int i=0;i<cardRow.getChildCount();i++){
            View c=cardRow.getChildAt(i); float a=tileAlpha(i,sx,stride,5);
            if (c instanceof GlowFrame){
                GlowFrame g=(GlowFrame)c; g.fade=a; g.setAlpha(1f); g.inner.setAlpha(a); g.syncHalo(); g.invalidate();
                if (a>0.01f){
                    View iv=g.inner.getChildAt(0);
                    if (iv instanceof ImageView){ Object tg=iv.getTag();
                        if (tg instanceof String && (((String)tg).startsWith("pend:")||((String)tg).startsWith("pendf:"))){
                            boolean fit=((String)tg).startsWith("pendf:");
                            loadInto((ImageView)iv, ((String)tg).substring(fit?6:5), fit); } }
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
        FrameLayout f = new FrameLayout(this); f.setClipToOutline(true);
        GradientDrawable bg = new GradientDrawable(); bg.setCornerRadius(p(TILE_R)); bg.setColor(Color.argb(96,190,185,220)); bg.setStroke(Math.max(1,p(.6f)), Color.argb(60,255,255,255)); f.setBackground(bg);
        f.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        ImageView iv = new ImageView(this); iv.setScaleType(ImageView.ScaleType.CENTER_CROP); if (art!=null) iv.setImageDrawable(art); if (fitArt){ iv.setScaleType(ImageView.ScaleType.FIT_CENTER); iv.setPadding(p(30),p(18),p(30),p(30)); } loadInto(iv, artUri, fitArt);
        f.addView(iv, new FrameLayout.LayoutParams(-1,-1));
        View shade = new View(this); shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{Color.TRANSPARENT, Color.argb(70,0,0,0), Color.argb(160,0,0,0), Color.argb(204,0,0,0)}));
        if (caption!=null || progress>=0) f.addView(shade, new FrameLayout.LayoutParams(-1, p(56), Gravity.BOTTOM));
        if (badgePkg != null){
            ImageView b = new ImageView(this); b.setScaleType(ImageView.ScaleType.CENTER_CROP); b.setClipToOutline(true);
            GradientDrawable bb = new GradientDrawable(); bb.setCornerRadius(p(4)); bb.setColor(Color.BLACK); b.setBackground(bb);
            try { Drawable d = pm.getApplicationBanner(badgePkg); if (d==null) d = pm.getApplicationIcon(badgePkg); b.setImageDrawable(d);} catch(Exception e){}
            f.addView(b, lp(p(36), p(20), Gravity.END|Gravity.TOP, 0, p(7), p(7), 0));
        }
        if (caption != null){
            TextView t = text(caption, 10.5f, Color.WHITE, "sans-serif-medium"); t.setSingleLine(); t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            f.addView(t, lp(-1,-2,Gravity.BOTTOM, p(9), 0, p(9), p(progress>=0?14:9)));
        }
        if (progress >= 0){
            FrameLayout bar = new FrameLayout(this); bar.setBackgroundColor(Color.argb(90,255,255,255));
            View fill = new View(this); fill.setBackgroundColor(Color.rgb(229,9,20));
            bar.addView(fill, new FrameLayout.LayoutParams(Math.round(p(166)*progress), -1));
            f.addView(bar, lp(p(166), p(2.5f), Gravity.BOTTOM, p(9), 0, 0, p(8)));
        }
        return focusable(f, bg, TILE_R, click);
    }

    void refreshCards(){
        if (cardRow==null) return;
        if (tab==0 && !playNextOn()) applyTabs();
        if (tab==1 && liveCache!=null && !liveCache.isEmpty()){
            showLiveCache(); cardsBindSig=cardSig(); h.post(this::updateCardAlphas); return;
        }
        String next=cardSig();
        if (next.equals(cardsBindSig) && cardRow.getChildCount()>0) return;
        cardsBindSig=next;
        cardRow.removeAllViews();
        if (cardScroll!=null) cardScroll.scrollTo(0,0);
        int n = tab==0 ? loadWatchNext() : loadLive();
        if (n>0){ h.post(this::updateCardAlphas); return; }
        if (tab==0){ applyTabs(); if (tab==1){ loadLive(); h.post(this::updateCardAlphas); } return; }
        int i=0;
        for (final String pk : (tab==0?PLAY_PKGS:LIVE_PKGS)){
            if (!installed(pk)) continue;
            Drawable art=null; try { art = pm.getApplicationBanner(pk); if (art==null){ Intent li=pm.getLeanbackLaunchIntentForPackage(pk); if(li!=null) art=pm.getActivityBanner(li);} if(art==null) art=pm.getApplicationIcon(pk);} catch(Exception e){}
            String cap = tab==1 && pk.equals("com.google.android.tv") ? "Free channels" : null;
            View v=card(art, null, false, null, cap, -1, ()->launchPkg(pk)); addCard(v, i++);
        }
        h.post(this::updateCardAlphas);
    }

    int loadWatchNext(){
        List<Object[]> pick=wnPickCache;
        if (pick==null||pick.isEmpty()) return 0;
        int n=0;
        for (Object[] o: pick){ final Intent fi=new Intent((Intent)o[3]).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); final String pk2=(String)o[0]; boolean isNew=o.length>5;
            View v = card(null,(String)o[2],false,pk2,(String)o[1], isNew?-1f:(Float)o[4], ()->{try{startActivity(fi);}catch(Exception e){ launchPkg(pk2);} });
            addCard(v, n++); }
        android.util.Log.i("ShieldHome","playnext shown="+n);
        return n;
    }
    List<Object[]> plutoCaps = new ArrayList<>(); Map<String,String> plutoNow = new HashMap<>(); Map<String,String> plutoArt = new HashMap<>(); Map<String,String> plutoLogo = new HashMap<>(); final Map<String,String> plutoSlug = new java.util.concurrent.ConcurrentHashMap<>(); long plutoAt=0;
    List<View> liveCache; final Set<String> liveSlugs = new LinkedHashSet<>();
    // Pluto doesn't publish programs to TvContract (0 readable rows), so read what's on now from Pluto's public guide.
    void fillPlutoNow(final List<Object[]> caps){
        if (System.currentTimeMillis()-plutoAt < 10*60000L && !plutoNow.isEmpty()){ applyLiveNow(); return; }
        pool.execute(()->{ try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US); f.setTimeZone(TimeZone.getTimeZone("UTC"));
            long now = System.currentTimeMillis();
            java.net.HttpURLConnection c=(java.net.HttpURLConnection)new java.net.URL("https://api.pluto.tv/v2/channels?start="+f.format(new Date(now))+"&stop="+f.format(new Date(now+60000))).openConnection();
            c.setConnectTimeout(6000); c.setReadTimeout(20000);
            java.io.InputStream in = c.getInputStream(); java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream(); byte[] buf=new byte[65536]; int r; while ((r=in.read(buf))>0) bo.write(buf,0,r); in.close();
            org.json.JSONArray a = new org.json.JSONArray(bo.toString("UTF-8")); final Map<String,String> m = new HashMap<>(), art = new HashMap<>(), logos = new HashMap<>();
            Set<String> ban = new HashSet<>(); Map<String,Integer> shared = new HashMap<>();
            for (int i=0;i<a.length();i++){ org.json.JSONObject ch=a.getJSONObject(i);
                addBan(ban, ch.optJSONObject("featuredImage")); addBan(ban, ch.optJSONObject("logo")); addBan(ban, ch.optJSONObject("thumbnail")); addBan(ban, ch.optJSONObject("tile"));
                String logo=jsonPath(ch.optJSONObject("colorLogoPNG")); if (logo==null) logo=jsonPath(ch.optJSONObject("logo"));
                if (logo!=null) logos.put(ch.optString("name"), logo);
                org.json.JSONArray tl=ch.optJSONArray("timelines");
                if (tl!=null && tl.length()>0){ String first=plutoGuideArt(tl.getJSONObject(0), ban); if (first!=null){ String b=bareUrl(first); shared.put(b, (shared.containsKey(b)?shared.get(b):0)+1); } }
            }
            for (Map.Entry<String,Integer> e: shared.entrySet()) if (e.getValue()>=4) ban.add(e.getKey());
            for (int i=0;i<a.length();i++){ org.json.JSONObject ch=a.getJSONObject(i); org.json.JSONArray tl=ch.optJSONArray("timelines");
                if (tl!=null && tl.length()>0){ org.json.JSONObject t0=tl.getJSONObject(0); m.put(ch.optString("name"), t0.optString("title")); String img=plutoGuideArt(t0, ban); if (img!=null) art.put(ch.optString("name"), img); }
                String sl=ch.optString("slug",null); if (sl!=null) plutoSlug.put(ch.optString("name"), sl); }
            android.util.Log.i("ShieldHome","pluto now: "+m.size()+" channels art="+art.size()+" logos="+logos.size());
            h.post(()->{ plutoNow.clear(); plutoNow.putAll(m); plutoArt.clear(); plutoArt.putAll(art); plutoLogo.clear(); plutoLogo.putAll(logos); plutoAt=System.currentTimeMillis(); applyLiveNow(); });
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
    void startSafe(Intent in, String fallbackPkg){ try { if (in.resolveActivity(pm)!=null){ startActivity(in); return; } } catch (Exception e){} try { launchPkg(fallbackPkg); } catch (Exception e){} }
    Long plutoId(Map<String,Long> ids, String nm, String slug){
        Long id = ids.get(nm); if (id!=null) return id;
        for (Map.Entry<String,Long> e: ids.entrySet()){ String k=slugify(e.getKey()); if (k.equals(slug)||k.equals(slugify(nm))) return e.getValue(); }
        return null;
    }
    int loadLive(){
        if (liveCache!=null && !liveCache.isEmpty()){ showLiveCache(); fillPlutoNow(plutoCaps); return liveCache.size(); }
        liveCache=new ArrayList<>(); liveSlugs.clear(); plutoCaps.clear();
        if (installed("com.google.android.tvlauncher")) putLive(freeplayLiveTile(), null);
        if (installed("tv.pluto.android")){
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
        try { ban=pm.getApplicationBanner("tv.pluto.android"); ic=pm.getApplicationIcon("tv.pluto.android"); } catch (Exception e){}
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
        if (cardRow.getChildCount()==liveCache.size() && cardRow.getChildCount()>0 && cardRow.getChildAt(0)==liveCache.get(0)){ applyLiveNow(); return; }
        cardRow.removeAllViews();
        int i=0; for (View v: liveCache){ if (v.getParent()!=null) ((ViewGroup)v.getParent()).removeView(v); addCard(v, i++); }
        applyLiveNow();
    }
    View plutoTile(String slug, String nm, boolean loadArt){
        Map<String,Long> plutoIds=plutoIdsCache!=null?plutoIdsCache:new HashMap<>();
        final Intent in=plutoIntent(slug); Long id=plutoId(plutoIds, nm, slug);
        View v=card(null, null, false, null, nm, -1, ()->startSafe(in, "tv.pluto.android"));
        ImageView big=(ImageView)((GlowFrame)v).inner.getChildAt(0);
        TextView ov=chName(nm);
        ((GlowFrame)v).inner.addView(ov, lp(-2,-2,Gravity.TOP|Gravity.START,0,0,0,0));
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
        TextView ov=text(nm, 7, Color.WHITE, "sans-serif-medium");
        ov.setPadding(p(6),p(3),p(10),p(4));
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
        if (tab==1) updateCardAlphas();
    }
    void setLiveArt(ImageView iv, String uri, boolean now, boolean fit){
        if (iv==null || uri==null) return;
        Object old=iv.getTag(); if (uri.equals(old) || ("pend:"+uri).equals(old) || ("pendf:"+uri).equals(old)) return;
        iv.setPadding(fit?p(22):0, fit?p(16):0, fit?p(22):0, fit?p(16):0);
        iv.setScaleType(fit?ImageView.ScaleType.FIT_CENTER:ImageView.ScaleType.CENTER_CROP);
        if (now || (tab==1 && iv.getAlpha()>0)) loadInto(iv, uri, fit); else iv.setTag((fit?"pendf:":"pend:")+uri);
    }
    String tifNowArt(Long id){
        if (id==null) return null;
        long now=System.currentTimeMillis();
        try (Cursor c=getContentResolver().query(TvContract.buildProgramsUriForChannel(id, now, now+60000), null, null, null, null)){
            if (c!=null && c.moveToFirst()){
                int i=c.getColumnIndex("poster_art_uri"); String a=i>=0&&!c.isNull(i)?c.getString(i):null;
                if (a==null){ i=c.getColumnIndex("thumbnail_uri"); a=i>=0&&!c.isNull(i)?c.getString(i):null; }
                return a;
            }
        } catch (Exception e){}
        return null;
    }
    String freeplayNowArt(){
        try (Cursor c=getContentResolver().query(TvContract.Channels.CONTENT_URI, null, null, null, null)){
            while (c!=null && c.moveToNext()){
                int pi=c.getColumnIndex("package_name"); String pkg=pi>=0&&!c.isNull(pi)?c.getString(pi):null;
                if (pkg==null || !(pkg.contains("tvlauncher")||pkg.equals("com.google.android.tv"))) continue;
                int i=c.getColumnIndex("_id"); if (i<0) continue;
                String art=tifNowArt(c.getLong(i)); if (art!=null) return art;
            }
        } catch (Exception e){}
        return null;
    }
    void playFreeplay(){ try { Intent b=new Intent("com.google.android.tvlauncher.action.VIEW", Uri.parse("tv://freeplay")); b.setComponent(new android.content.ComponentName("com.google.android.tvlauncher","com.google.android.tvlauncher.TvContentIntentReceiver")); b.addFlags(Intent.FLAG_RECEIVER_FOREGROUND); sendBroadcast(b); android.util.Log.i("ShieldHome","freeplay broadcast sent"); } catch(Exception e){ android.util.Log.w("ShieldHome","freeplay "+e); } }
    View freeplayLiveTile(){
        String art=freeplayNowArt();
        Drawable ic=null; if (art==null) try { android.content.res.Resources r=pm.getResourcesForApplication("com.google.android.tvlauncher"); int id=r.getIdentifier("freeplay_icon","drawable","com.google.android.tvlauncher"); if (id!=0) ic=r.getDrawable(id,null); } catch(Exception e){}
        View v=card(ic, art, art==null, null, "Google TV Freeplay \u00B7 Live", -1, this::playFreeplay);
        TextView tg=text("LIVE", 7, Color.WHITE, "sans-serif-medium"); tg.setLetterSpacing(.08f); tg.setPadding(p(5),p(2),p(5),p(2)); GradientDrawable gb=new GradientDrawable(); gb.setCornerRadius(p(3)); gb.setColor(Color.rgb(200,30,40)); tg.setBackground(gb);
        ((GlowFrame)v).inner.addView(tg, lp(-2,-2,Gravity.TOP|Gravity.START,p(7),p(7),0,0));
        if (art!=null){ TextView ov=chName("Freeplay"); ((GlowFrame)v).inner.addView(ov, lp(-2,-2,Gravity.TOP|Gravity.START,0,0,0,0)); }
        return v;
    }
    boolean installed(String p){ try { pm.getApplicationInfo(p,0); return true; } catch(Exception e){ return false; } }
    void launchPkg(String pkg){ Intent i = pm.getLeanbackLaunchIntentForPackage(pkg); if (i==null) i = pm.getLaunchIntentForPackage(pkg); if (i!=null) startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }

    // ---------- v6: quick settings, featured toggle, dimmer, weather ----------
    PillView searchV, speakV, typeV; LinearLayout searchSplit; EditText searchField; TextView heroLabel;
    void useWhatsNew(String js){
        List<Object[]> nl = new ArrayList<>();
        try { org.json.JSONArray a = new org.json.JSONArray(js==null?"[]":js);
            for (int i=0;i<a.length();i++){ org.json.JSONObject o=a.getJSONObject(i); String pkg=o.getString("pkg"); if (!installed(pkg) || o.optString("img").isEmpty()) continue;
                int dd=o.optInt("dd"); String when;
                try { Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(o.getString("date")); long diff = Math.round((d.getTime() - new SimpleDateFormat("yyyy-MM-dd",Locale.US).parse(new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date())).getTime())/86400000.0);
                    when = diff==0?"New today":diff==1?"Coming tomorrow":diff==-1?"Added yesterday":diff<0?"Added "+new SimpleDateFormat("EEE",Locale.US).format(d):"Coming "+new SimpleDateFormat("EEEE",Locale.US).format(d); } catch (Exception e){ when="New"; }
                String ep = o.optInt("season")>0 ? "  \u00B7  S"+o.optInt("season")+(o.optInt("num")>0?" E"+o.optInt("num"):"") : "";
                String sum = o.optString("summary"); int dot = sum.indexOf(". "); if (dot>40) sum = sum.substring(0, dot+1);
                Intent li = pm.getLeanbackLaunchIntentForPackage(pkg); if (li==null) li = pm.getLaunchIntentForPackage(pkg);
                long avail=0; try { Date ad=new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(o.getString("date")); if (ad!=null && ad.getTime()>System.currentTimeMillis()) avail=ad.getTime(); } catch (Exception e){}
                nl.add(new Object[]{pkg, o.getString("show"), when+" on "+o.optString("net")+ep+"\n"+sum, o.getString("img"), li, "NEW", avail});
            }
        } catch (Exception e){ android.util.Log.w("ShieldHome","whatsnew parse "+e); }
        // v9: one hero, Featured + What's New randomly mixed in the same rotation
        List<Object[]> mix = new ArrayList<>(); for (Object[] o: heroItems) if (o.length>5 && "FEATURED".equals(o[5])) mix.add(o);
        int nf = mix.size(); Collections.shuffle(nl); for (int i=0;i<Math.min(8,nl.size());i++) mix.add(nl.get(i));
        android.util.Log.i("ShieldHome","hero mix featured="+nf+" new="+(mix.size()-nf));
        if (mix.size()!=nf){
            Collections.shuffle(mix);
            synchronized(heroLock){ h.removeCallbacks(heroRotate); heroItems.clear(); heroItems.addAll(mix); heroIdx=0; }
            showHero(); h.postDelayed(heroRotate, 16000);
        }
    }

    PillView gear, dimBar, heroBtn, wxPill, timePill; boolean dimSlide=false, dimSlideIgnore=false; View heroG, heroFocus, inAppDim; HeroDots heroDots;
    float dimFill; android.animation.ValueAnimator dimFillAnim;
    int dimPct(){ return Math.max(0, Math.min(90, prefs().getInt("dim_pct",0))); }
    void setDimPct(int pct){ prefs().edit().putInt("dim_pct", Math.max(0, Math.min(90, pct))).apply(); applyDim(); }
    void syncDimFill(boolean anim){
        float to=dimPct()/100f;
        if (!anim){ dimFill=to; if (gear!=null) gear.invalidate(); if (dimBar!=null) dimBar.invalidate(); return; }
        if (dimFillAnim!=null) dimFillAnim.cancel();
        dimFillAnim=android.animation.ValueAnimator.ofFloat(dimFill, to); dimFillAnim.setDuration(200);
        dimFillAnim.addUpdateListener(a->{ dimFill=(Float)a.getAnimatedValue(); if (gear!=null) gear.invalidate(); if (dimBar!=null) dimBar.invalidate(); });
        dimFillAnim.start();
    }
    void dimSlideBy(int dir){ setDimPct(dimPct()+dir*5); }
    void hideSearch(boolean hide){
        if (searchV==null) return;
        android.view.animation.Interpolator ease=new android.view.animation.DecelerateInterpolator();
        if (hide){
            if (searchTyping){ searchTyping=false; hideKb(); if (searchField!=null){ searchField.setVisibility(View.GONE); searchField.setText(""); } }
            searchOpen=false;
            if (speakV!=null){ speakV.animate().cancel(); typeV.animate().cancel(); speakV.setFocusable(false); typeV.setFocusable(false); }
            if (searchSplit!=null) searchSplit.setVisibility(View.GONE);
            searchV.setFocusable(false);
            searchV.animate().alpha(0f).setDuration(180).setInterpolator(ease).withEndAction(()->{ if (dimSlide) searchV.setVisibility(View.INVISIBLE); }).start();
        } else {
            searchV.setVisibility(View.VISIBLE); searchV.setFocusable(!drawerOpen);
            searchV.animate().alpha(1f).setDuration(180).setInterpolator(ease).start();
        }
    }
    void popDimBar(boolean on){
        if (dimBar==null) return;
        android.view.animation.Interpolator ease=new android.view.animation.DecelerateInterpolator();
        dimBar.animate().cancel();
        if (on){
            dimBar.setVisibility(View.VISIBLE);
            dimBar.setAlpha(0f); dimBar.setScaleX(0.88f); dimBar.setScaleY(0.88f);
            dimBar.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180).setInterpolator(ease).start();
        } else {
            dimBar.animate().alpha(0f).scaleX(0.88f).scaleY(0.88f).setDuration(140).setInterpolator(ease).withEndAction(()->{ if (!dimSlide) dimBar.setVisibility(View.GONE); }).start();
        }
    }
    void openDimSlide(){
        if (dimSlide || gear==null) return;
        dimSlide=true; dimSlideIgnore=true;
        gear.setNextFocusLeftId(gear.getId()); gear.setNextFocusRightId(gear.getId());
        hideSearch(true);
        popDimBar(true);
        gear.requestFocus();
    }
    void closeDimSlide(){
        if (!dimSlide) return;
        dimSlide=false; dimSlideIgnore=false;
        gear.setNextFocusLeftId(View.NO_ID); gear.setNextFocusRightId(View.NO_ID);
        popDimBar(false);
        hideSearch(false);
    }
    void applyDim(){
        int pct = dimPct(); syncDimFill(true);
        boolean overlay = android.provider.Settings.canDrawOverlays(this);
        Intent si = new Intent(this, DimService.class).putExtra("pct", pct);
        try { if (overlay){ if (pct>0) startForegroundService(si); else stopService(si); } } catch (Exception e){ android.util.Log.w("ShieldHome","dimsvc "+e); overlay=false; }
        if (inAppDim==null){ inAppDim = new View(this); inAppDim.setBackgroundColor(Color.BLACK); inAppDim.setClickable(false); inAppDim.setFocusable(false); root.addView(inAppDim, new FrameLayout.LayoutParams(-1,-1)); inAppDim.setTranslationZ(p(60)); }
        inAppDim.setAlpha(overlay?0f:pct/100f); inAppDim.setVisibility(!overlay && pct>0 ? View.VISIBLE : View.GONE);
        android.util.Log.i("ShieldHome","dim pct="+pct+" overlay="+overlay);
    }
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
