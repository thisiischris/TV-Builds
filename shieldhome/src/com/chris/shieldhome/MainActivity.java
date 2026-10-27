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
    float u; PackageManager pm; FrameLayout root; LinearLayout cardRow; HorizontalScrollView cardScroll; FrameLayout cardWrap; TextView clock, date; View clk;
    PillView tabPlay, tabLive; int tab = 0; Handler h = new Handler(Looper.getMainLooper());
    static final int TILE = Color.argb(34, 255, 255, 255), FOCUS = Color.rgb(64, 156, 255);
    static final int TILE_R = 11;
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
    @Override public boolean dispatchKeyEvent(KeyEvent e){
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
        Intent[] tries = { new Intent(Intent.ACTION_ASSIST).setPackage("com.google.android.katniss"), new Intent(Intent.ACTION_ASSIST), new Intent("android.speech.action.VOICE_SEARCH_HANDS_FREE"), new Intent("android.search.action.GLOBAL_SEARCH").setPackage("com.google.android.katniss"), new Intent(Intent.ACTION_SEARCH_LONG_PRESS), new Intent(Intent.ACTION_WEB_SEARCH) };
        for (Intent i: tries){ try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return; } catch (Exception e) {} }
    }

    void setFit(TextView t, String txt){ t.setText(txt); t.setSingleLine(true); t.setEllipsize(null); t.setHorizontallyScrolling(false); }
    void tick(){ Date d = new Date(); setFit(clock, new SimpleDateFormat("h:mm a", Locale.US).format(d)); setFit(date, new SimpleDateFormat("EEE, MMM d", Locale.US).format(d)); h.postDelayed(this::tick, 15000); }

    void place(View v, float x, float y, float w, float hh){ FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w<0?-2:p(w), hh<0?-2:p(hh)); lp.leftMargin=p(x); lp.topMargin=p(y); root.addView(v, lp); }
    TextView text(String s, float size, int color, String font){ TextView t=new TextView(this); t.setText(s); ts(t,size); t.setTextColor(color); t.setTypeface(Typeface.create(font, Typeface.NORMAL)); t.setIncludeFontPadding(false); return t; }

    void build(){
        root = new FrameLayout(this);
        root.setBackgroundResource(R.drawable.bg);
        root.setClipChildren(false);

        // Top bar
        clock = text("", 17, Color.WHITE, "sans-serif-medium"); clock.setGravity(Gravity.END);
        date = text("", 11.5f, Color.rgb(236,236,245), "sans-serif-medium"); date.setGravity(Gravity.END);
        wxTemp = text("", 17, Color.WHITE, "sans-serif-medium"); wxTemp.setGravity(Gravity.END);
        wxDesc = text("", 11.5f, Color.rgb(236,236,245), "sans-serif-medium"); wxDesc.setGravity(Gravity.END);
        for (TextView tv: new TextView[]{clock,date,wxTemp,wxDesc}){ tv.setShadowLayer(p(3), 0, p(1), Color.argb(170,0,0,0)); tv.setMaxLines(1); tv.setGravity(Gravity.NO_GRAVITY); }
        // v9: [weather, right-aligned] | [time / date, left-aligned]; rows share baselines
        LinearLayout tr = new LinearLayout(this); tr.setBaselineAligned(true);
        wxTemp.setGravity(Gravity.END); wxDesc.setGravity(Gravity.END); clock.setGravity(Gravity.START); date.setGravity(Gravity.START);
        for (TextView tv: new TextView[]{clock,date,wxTemp,wxDesc}){ tv.setSingleLine(true); tv.setEllipsize(null); tv.setIncludeFontPadding(false); }
        LinearLayout wc = new LinearLayout(this); wc.setOrientation(LinearLayout.VERTICAL); wc.setGravity(Gravity.END);
        wc.addView(wxTemp, new LinearLayout.LayoutParams(-2,-2)); LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2,-2); sl.topMargin=p(6); sl.gravity=Gravity.END; wc.addView(wxDesc, sl);
        LinearLayout cc = new LinearLayout(this); cc.setOrientation(LinearLayout.VERTICAL); cc.setGravity(Gravity.START);
        cc.addView(clock, new LinearLayout.LayoutParams(-2,-2)); LinearLayout.LayoutParams sl2 = new LinearLayout.LayoutParams(-2,-2); sl2.topMargin=p(6); cc.addView(date, sl2);
        wc.setBaselineAlignedChildIndex(0); cc.setBaselineAlignedChildIndex(0);
        wxSep = new View(this); wxSep.setBackgroundColor(Color.argb(110,255,255,255)); wxSep.setVisibility(View.GONE);
        tr.addView(wc); LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(p(1), p(30)); sp.leftMargin=p(14); sp.rightMargin=p(14); sp.gravity=Gravity.CENTER_VERTICAL; tr.addView(wxSep, sp); tr.addView(cc);
        clk = tr;
        root.addView(clk, lp(-2,-2,Gravity.TOP|Gravity.END,0,p(15),p(46),0));

        // Quick settings gear + inline options
        gear = new PillView(this, "", 5, false); gear.setOnClickListener(v->toggleQs()); place(gear, 35, 17, 28, 28);
        PillView search = searchV = new PillView(this, "Search", 3, false);
        search.setOnClickListener(v->openAssistant()); place(search, 71, 17, 104, 28);
        qsPanel = new LinearLayout(this); qsPanel.setClipChildren(false); qsPanel.setVisibility(View.GONE);
        qsDim = new PillView(this, "", 4, false); qsDim.setOnClickListener(v->{ int i=(dimIdx()+1)%DIMS.length; prefs().edit().putInt("dim", i).apply(); applyDim(); qsLabels(); });
        LinearLayout.LayoutParams q2 = new LinearLayout.LayoutParams(p(104), p(28)); qsPanel.addView(qsDim, q2);
        place(qsPanel, 73, 17, 290, 28); qsLabels();

        // Hero: full-bleed top half, under the top bar (inserted at index 0)
        FrameLayout hero = new FrameLayout(this);
        FrameLayout artHost = new FrameLayout(this); artHost.setClipChildren(true);
        ImageView art = new ImageView(this); art.setImageResource(R.drawable.hero); art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        heroArt2 = new ImageView(this); heroArt2.setScaleType(ImageView.ScaleType.CENTER_CROP); heroArt2.setAlpha(0f);
        artHost.addView(art, new FrameLayout.LayoutParams(-1,-1));
        artHost.addView(heroArt2, new FrameLayout.LayoutParams(-1,-1));
        View botFade = new View(this);
        botFade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{Color.TRANSPARENT, Color.argb(140,8,8,16), Color.rgb(8,8,16)}));
        artHost.addView(botFade, lp(-1, p(96), Gravity.BOTTOM, 0,0,0,0));
        hero.addView(artHost, new FrameLayout.LayoutParams(-1,-1));
        View topScrim = new View(this);
        topScrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{Color.argb(170,0,0,0), Color.argb(70,0,0,0), Color.TRANSPARENT}));
        hero.addView(topScrim, lp(-1, p(80), Gravity.TOP, 0,0,0,0));
        View textScrim = new View(this);
        textScrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.BL_TR, new int[]{Color.argb(200,0,0,0), Color.argb(90,0,0,0), Color.TRANSPARENT}));
        hero.addView(textScrim, lp(p(640), p(190), Gravity.BOTTOM|Gravity.START, 0,0,0,0));
        LinearLayout meta = new LinearLayout(this); meta.setOrientation(LinearLayout.VERTICAL);
        heroLabel = text("FEATURED", 8, Color.rgb(190,188,205), "sans-serif-medium");
        heroArt = art;
        heroTitle = text("Your Library", 31, Color.rgb(220,220,230), "sans-serif-medium"); heroTitle.setSingleLine(); heroTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        heroSub = text("Movies and shows from your\nPlex Media Server", 12, Color.rgb(190,188,205), "sans-serif"); heroSub.setLineSpacing(p(3),1f); heroSub.setMaxLines(2); heroSub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        heroBadge = new ImageView(this); heroBadge.setScaleType(ImageView.ScaleType.FIT_START); heroBadge.setAlpha(0.7f);
        heroFocus = new View(this); heroFocus.setBackgroundColor(Color.WHITE); heroFocus.setAlpha(0f);
        for (TextView tv: new TextView[]{heroLabel, heroTitle, heroSub}) tv.setShadowLayer(p(6), 0, p(2), Color.argb(180,0,0,0));
        meta.addView(heroLabel, new LinearLayout.LayoutParams(-2,-2));
        LinearLayout.LayoutParams mt = new LinearLayout.LayoutParams(p(440),-2); mt.topMargin=p(6); meta.addView(heroTitle, mt);
        LinearLayout.LayoutParams mf = new LinearLayout.LayoutParams(p(40), Math.max(2,p(1.4f))); mf.topMargin=p(6); meta.addView(heroFocus, mf);
        LinearLayout.LayoutParams ms = new LinearLayout.LayoutParams(p(400),-2); ms.topMargin=p(8); meta.addView(heroSub, ms);
        LinearLayout.LayoutParams mb = new LinearLayout.LayoutParams(p(70),p(18)); mb.topMargin=p(10); meta.addView(heroBadge, mb);
        hero.addView(meta, lp(p(500),-2,Gravity.BOTTOM|Gravity.START,p(35),0,0,p(18)));
        heroG = hero; hero.setFocusable(true); hero.setClickable(true);
        hero.setOnFocusChangeListener((x,f)->{
            heroTitle.setTextColor(f?Color.WHITE:Color.rgb(220,220,230));
            heroTitle.setShadowLayer(f?p(10):p(6), 0, p(2), Color.argb(f?210:180,0,0,0));
            heroFocus.animate().alpha(f?1f:0f).setDuration(180).start();
        });
        hero.setOnClickListener(x->{ Object[] o=heroOnScreen; if (o==null) synchronized(heroLock){ o=heroItems.isEmpty()?null:heroItems.get(heroIdx); } if (o==null) launchPkg(installed("com.plexapp.android")?"com.plexapp.android":PLAY_PKGS[0]); else { try { startActivity(((Intent)o[4]).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch(Exception e){ launchPkg((String)o[0]); } } });
        root.addView(hero, 0, lp(-1, p(288), Gravity.TOP, 0,0,0,0));

        // Tabs
        tabPlay = new PillView(this, "Play Next", 0, true); tabLive = new PillView(this, "Live TV", 1, false);
        place(tabPlay, 35, 308, 129, 28); place(tabLive, 175, 308, 110, 28);
        View.OnFocusChangeListener tl = (v,f)->{ if(f){ int t = v==tabPlay?0:1; if (t!=tab){ tab=t; tabPlay.setOn(tab==0); tabLive.setOn(tab==1); refreshCards(); resetCardRow(); } } };
        tabPlay.setOnFocusChangeListener(tl); tabLive.setOnFocusChangeListener(tl);

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

        homeGrid = new GridLayout(this); homeGrid.setColumnCount(6); homeGrid.setClipChildren(false);
        place(homeGrid, 35, 474, 954, 93);
        buildHomeGrid();

        setContentView(root);
        tabPlay.setNextFocusDownId(View.NO_ID); tabLive.setNextFocusDownId(View.NO_ID);
        h.post(()->{ if (tabPlay.getVisibility()==View.VISIBLE) tabPlay.requestFocus(); else tabLive.requestFocus(); });
    }

    // ---------- Pills with drawn icons ----------
    class PillView extends View {
        String label; int icon; boolean on; Drawable asst; Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);
        PillView(Context c, String l, int ic, boolean o){ super(c); label=l; icon=ic; on=o; setFocusable(true); setClickable(true); }
        void setOn(boolean o){ on=o; invalidate(); }
        @Override protected void onFocusChanged(boolean f, int d, Rect r){ super.onFocusChanged(f,d,r); animate().scaleX(f?1.07f:1f).scaleY(f?1.07f:1f).setDuration(160).setInterpolator(new android.view.animation.DecelerateInterpolator()).start(); setElevation(f?p(4):0); invalidate(); }
        @Override protected void onDraw(Canvas c){
            float w=getWidth(), hh=getHeight(), r=hh/2;
            boolean lit = on || (icon==2 && isFocused());
            pt.setStyle(Paint.Style.FILL);
            boolean fo = isFocused();
            if (icon==2) pt.setColor(fo?Color.WHITE:Color.rgb(225,224,228));
            else if (on) pt.setColor(fo?Color.WHITE:Color.rgb(229,227,228));
            else if (icon==5) pt.setColor(fo?Color.argb(120,200,200,225):Color.argb(70,190,190,215));
            else pt.setColor(fo?Color.argb(150,120,118,140):Color.argb(110,70,68,88));
            c.drawRoundRect(0,0,w,hh,r,r,pt);
            if (icon==5 && !on){ pt.setShader(new LinearGradient(0,0,0,hh,Color.argb(70,255,255,255),Color.argb(0,255,255,255),Shader.TileMode.CLAMP)); c.drawRoundRect(0,0,w,hh,r,r,pt); pt.setShader(null); }
            if (!on && icon!=2){ pt.setStyle(Paint.Style.STROKE); pt.setStrokeWidth(1); pt.setColor(Color.argb(40,255,255,255)); c.drawRoundRect(.5f,.5f,w-.5f,hh-.5f,r,r,pt); }
            int fg = (icon==2||on) ? Color.rgb(22,22,30) : Color.rgb(232,232,240);
            pt.setColor(fg); pt.setStrokeWidth(p(1.1f)); pt.setStyle(Paint.Style.STROKE); pt.setStrokeCap(Paint.Cap.ROUND);
            float cx = icon==3 ? p(19) : p(icon==2?18:28) - (icon==2?p(4):0), cy = hh/2;
            if (icon==0){ c.drawCircle(cx,cy,p(6.5f),pt); Path tr=new Path(); tr.moveTo(cx-p(2),cy-p(3.2f)); tr.lineTo(cx+p(3.3f),cy); tr.lineTo(cx-p(2),cy+p(3.2f)); tr.close(); c.drawPath(tr,pt); }
            else if (icon==1){ pt.setStyle(Paint.Style.FILL); c.drawCircle(cx,cy,p(1.6f),pt); pt.setStyle(Paint.Style.STROKE);
                for (float rr: new float[]{4.5f,7.5f}){ RectF o=new RectF(cx-p(rr),cy-p(rr),cx+p(rr),cy+p(rr)); c.drawArc(o,-40,80,false,pt); c.drawArc(o,140,80,false,pt);} }
            else if (icon==3){ // magnifier + mic
                c.drawCircle(cx-p(1),cy-p(1),p(4.2f),pt); c.drawLine(cx+p(2),cy+p(2),cx+p(5),cy+p(5),pt);
                if (asst==null) asst = getContext().getDrawable(R.drawable.assistant);
                int ax=(int)(cx+p(8)); asst.setBounds(ax, (int)(cy-p(7.5f)), ax+p(15), (int)(cy+p(7.5f))); asst.draw(c); }
            else if (icon==5){ float gx=w/2f; Path gp2=new Path();
                for (int i=0;i<=240;i++){ double a=2*Math.PI*i/240; double cs=Math.cos(8*a); double t=Math.max(-1,Math.min(1,cs*2.4)); float rr=p(5.9f)+p(1.55f)*(float)t; float x=gx+(float)Math.cos(a)*rr, y=cy+(float)Math.sin(a)*rr; if(i==0) gp2.moveTo(x,y); else gp2.lineTo(x,y); }
                gp2.close(); gp2.addCircle(gx,cy,p(2.3f),Path.Direction.CW); gp2.setFillType(Path.FillType.EVEN_ODD);
                pt.setStyle(Paint.Style.FILL); c.drawPath(gp2,pt); }
            else if (icon!=4) { pt.setStyle(Paint.Style.FILL); Path tr=new Path(); tr.moveTo(cx-p(3),cy-p(5)); tr.lineTo(cx+p(5),cy); tr.lineTo(cx-p(3),cy+p(5)); tr.close(); c.drawPath(tr,pt); }
            pt.setStyle(Paint.Style.FILL); pt.setTextSize(p(icon==2?12.5f:12)); pt.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            Paint.FontMetrics fm = pt.getFontMetrics();
            if (icon==4) pt.setTextAlign(Paint.Align.CENTER); else pt.setTextAlign(Paint.Align.LEFT);
            if (label.length()>0) c.drawText(label, icon==4? w/2f : p(icon==2?24:icon==3?46:43), cy-(fm.ascent+fm.descent)/2, pt);
        }
    }

    // ---------- Focus helper: soft blue glow drawn outside the tile + scale ----------
    class GlowFrame extends FrameLayout {
        FrameLayout inner; View badgeV; float radius; Paint gp = new Paint(Paint.ANTI_ALIAS_FLAG), rp = new Paint(Paint.ANTI_ALIAS_FLAG), bp = new Paint(); float glow=0, fade=1; Bitmap glowBm; int glowPad;
        GlowFrame(Context c, FrameLayout in, float r){ super(c); inner=in; radius=r; setClipChildren(false); setClipToPadding(false); setWillNotDraw(false);
            addView(in, new FrameLayout.LayoutParams(-1,-1));
            in.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            rp.setStyle(Paint.Style.STROKE); rp.setStrokeWidth(p(1.6f));
            glowPad=p(18);
            tint(FOCUS); }
        void tint(int c){
            gp.setColor(Color.argb(155, Color.red(c), Color.green(c), Color.blue(c)));
            rp.setColor(Color.rgb(Math.min(255,Color.red(c)+40), Math.min(255,Color.green(c)+40), Math.min(255,Color.blue(c)+40)));
            glowBm=null; }
        void warm(int w, int h){ if (w<=0||h<=0) return; glowBm=glowBmp(w,h,gp.getColor(),radius); }
        @Override protected void onSizeChanged(int w, int h, int ow, int oh){ super.onSizeChanged(w,h,ow,oh); if (w>0&&h>0) warm(w,h); }
        @Override public void draw(Canvas c){
            float g=glow*fade;
            if (g>0){ if (glowBm==null) warm(getWidth(),getHeight()); if (glowBm!=null){ bp.setAlpha((int)(150*g)); c.drawBitmap(glowBm,-glowPad,-glowPad,bp); } }
            super.draw(c);
            if (g>0){ rp.setAlpha((int)(255*g)); c.drawRoundRect(0,0,getWidth(),getHeight(),radius,radius,rp); }
            if (badgeV!=null && badgeV.getAlpha()>0.01f){ c.saveLayerAlpha(badgeV.getLeft(), badgeV.getTop(), badgeV.getRight(), badgeV.getBottom(), Math.round(255*badgeV.getAlpha())); c.translate(badgeV.getLeft(), badgeV.getTop()); badgeV.draw(c); c.restore(); }
        }
    }
    View focusable(final FrameLayout v, final GradientDrawable base, float radius, final Runnable click){ return focusable(v, base, radius, 1.07f, click); }
    View focusable(final FrameLayout v, final GradientDrawable base, float radius, final float sc, final Runnable click){
        final GlowFrame g = new GlowFrame(this, v, p(radius));
        g.setFocusable(true); g.setClickable(true);
        g.setOnFocusChangeListener((x,f)->{
            boolean lift=x==dragV;
            if (x.getParent()==null) return;
            x.animate().scaleX(lift?1.16f:(f?sc:1f)).scaleY(lift?1.16f:(f?sc:1f)).translationX(0).translationY(lift?-p(6):0).rotation(lift?x.getRotation():0).setDuration(sc<1.05f?260:180).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
            x.setTranslationZ(lift?p(18):(f?p(6):0));
            android.animation.ValueAnimator va = android.animation.ValueAnimator.ofFloat(g.glow, (lift||f)?1f:0f); va.setDuration(180);
            va.addUpdateListener(a->{ g.glow=(Float)a.getAnimatedValue(); g.invalidate(); }); va.start();
        });
        g.setOnClickListener(x->click.run());
        return g;
    }
    final Map<String,Integer> glowCache = new HashMap<>();
    final Map<String,Bitmap> glowBmps = new HashMap<>();
    Bitmap glowBmp(int w, int h, int color, float r){
        String k=w+"x"+h+":"+color+":"+(int)r;
        Bitmap hit=glowBmps.get(k); if (hit!=null && !hit.isRecycled()) return hit;
        int pad=p(18);
        Bitmap bm=Bitmap.createBitmap(Math.max(1,w+pad*2), Math.max(1,h+pad*2), Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(bm);
        Paint bl=new Paint(Paint.ANTI_ALIAS_FLAG);
        bl.setColor(color); bl.setMaskFilter(new BlurMaskFilter(p(9), BlurMaskFilter.Blur.NORMAL));
        c.drawRoundRect(pad, pad, pad+w, pad+h, r, r, bl);
        glowBmps.put(k, bm); return bm;
    }
    int glowColor(String pkg){
        if (pkg==null) return FOCUS;
        if (pkg.contains("netflix")||pkg.contains("youtube")) return Color.rgb(210,48,48);
        if (pkg.contains("spotify")) return Color.rgb(30,215,96);
        if (pkg.contains("amazon")) return Color.rgb(48,148,210);
        if (pkg.contains("disney")) return Color.rgb(64,112,214);
        if (pkg.contains("hulu")) return Color.rgb(40,186,118);
        if (pkg.contains("wbd.stream")) return Color.rgb(138,104,208);
        if (pkg.contains("crunchyroll")) return Color.rgb(224,128,48);
        if (pkg.contains("plex")) return Color.rgb(214,168,48);
        if (pkg.contains("pluto")) return Color.rgb(32,186,186);
        if (pkg.contains("haystack")) return Color.rgb(88,168,80);
        if (pkg.contains("tvlauncher")) return Color.rgb(72,132,220);
        Integer hit=glowCache.get(pkg); if (hit!=null) return hit;
        int c=sampleGlow(pkg); glowCache.put(pkg,c); return c;
    }
    int sampleGlow(String pkg){
        Drawable d=null;
        try { d=pm.getApplicationBanner(pkg); if (d==null) d=pm.getApplicationIcon(pkg); } catch (Exception e){}
        int c=vibrantColor(d); return c!=0?c:FOCUS;
    }
    int vibrantColor(Drawable d){
        if (d==null) return 0;
        try {
            Bitmap bm=Bitmap.createBitmap(64,36,Bitmap.Config.ARGB_8888); Canvas c=new Canvas(bm); d.setBounds(0,0,64,36); d.draw(c);
            Map<Integer,Integer> hist=new HashMap<>(); int best=0,bestN=0;
            for (int x=0;x<64;x++) for (int y=0;y<36;y++){
                int px=bm.getPixel(x,y); if (Color.alpha(px)<200) continue;
                float[] hsv=new float[3]; Color.colorToHSV(px,hsv);
                if (hsv[1]<0.22f || hsv[2]<0.15f || (hsv[2]>0.92f && hsv[1]<0.25f)) continue;
                int k=(int)(hsv[0]/20); int n=(hist.containsKey(k)?hist.get(k):0)+1; hist.put(k,n);
                if (n>bestN){ bestN=n; best=px; }
            }
            if (bestN==0) return 0;
            float[] hsv=new float[3]; Color.colorToHSV(best,hsv);
            hsv[1]=Math.max(0.5f,Math.min(1f,hsv[1])); hsv[2]=Math.max(0.5f,Math.min(0.85f,hsv[2]));
            return Color.HSVToColor(hsv);
        } catch (Exception e){ return 0; }
    }
    void tintCard(View v, String pkg){ if (v instanceof GlowFrame) ((GlowFrame)v).tint(glowColor(pkg)); }

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
        View gv = focusable(f, bg, TILE_R, ()->{ try{ startActivity(li);}catch(Exception e){} }); gv.setTag(pkg); tintCard(gv, pkg); return gv;
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

    GridLayout homeGrid; boolean drawerOpen, editMode; int dragIdx=-1; View dragV; android.animation.ObjectAnimator wig;
    LinearLayout appMenu; List<View> editBlocked = new ArrayList<>(); View dimV;
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
    void saveHome(List<String[]> l){ StringBuilder sb=new StringBuilder(); for (String[] a: l){ if (a==null) continue; if (sb.length()>0) sb.append(','); sb.append(a[0]).append('/').append(a[1]); } prefs().edit().putString("order", sb.toString()).apply(); }
    GridLayout.LayoutParams gridLp(int i){ GridLayout.LayoutParams gl = new GridLayout.LayoutParams(); gl.width=p(147); gl.height=p(81); gl.rightMargin = (i%6==5)?0:p(14.4f); gl.bottomMargin = p(12); return gl; }
    int rowH(){ return p(93); }
    View[] slideViews(){ return new View[]{clk, gear, searchV, qsPanel, heroG, tabPlay, tabLive, cardWrap, homeGrid}; }
    void setDrawer(boolean on){ drawerOpen=on; slideHome(); }
    void fadeChrome(boolean hide){
        float a=hide?0f:1f;
        android.view.animation.Interpolator ip=new android.view.animation.DecelerateInterpolator();
        for (View v: new View[]{clk, gear, searchV}){
            if (v==null) continue;
            v.setVisibility(View.VISIBLE);
            v.animate().alpha(a).setDuration(250).setInterpolator(ip).withEndAction(()->{ if (drawerOpen) v.setVisibility(View.INVISIBLE); }).start();
        }
        if (gear!=null) gear.setFocusable(!hide);
        if (searchV!=null) searchV.setFocusable(!hide && !qsOpen);
        if (qsPanel!=null){
            if (hide) qsPanel.animate().alpha(0f).setDuration(250).withEndAction(()->{ if (drawerOpen) qsPanel.setVisibility(View.GONE); }).start();
            else if (qsOpen){ qsPanel.setVisibility(View.VISIBLE); qsPanel.animate().alpha(1f).setDuration(250).start(); }
        }
    }
    void slideHome(){
        if (focus!=null) focus.remember();
        int r=0; View f=getCurrentFocus();
        if (f!=null && homeGrid!=null && f.getParent()==homeGrid) r=Math.max(0, homeGrid.indexOfChild(f)/6);
        float y = drawerOpen ? -(p(396)+Math.max(0, r-4)*rowH()) : 0;
        android.view.animation.Interpolator ip=new android.view.animation.DecelerateInterpolator();
        fadeChrome(drawerOpen);
        if (drawerOpen){
            for (View v: new View[]{heroG, tabPlay, tabLive, cardWrap, homeGrid}) if (v!=null) v.bringToFront();
        } else {
            if (clk!=null) clk.bringToFront();
            if (gear!=null) gear.bringToFront();
            if (searchV!=null) searchV.bringToFront();
            if (qsPanel!=null && qsOpen) qsPanel.bringToFront();
        }
        for (View v: slideViews()){
            if (v==null) continue;
            android.view.ViewPropertyAnimator an=v.animate().translationY(y).setDuration(250).setInterpolator(ip);
            if (v==homeGrid) an.withEndAction(()->{ if (getCurrentFocus()==null && focus!=null) focus.restore(); });
            an.start();
        }
    }
    void buildHomeGrid(){ buildHomeGrid(-1); }
    void buildHomeGrid(int focusIdx){
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
        g.setOnLongClickListener(v->{ if (!editMode) appMenu(g, a, homeGrid.indexOfChild(g)); return true; });
        g.setOnKeyListener((v,k,e)->{
            int ix=homeGrid.indexOfChild(v);
            boolean dpad = k==KeyEvent.KEYCODE_DPAD_LEFT||k==KeyEvent.KEYCODE_DPAD_RIGHT||k==KeyEvent.KEYCODE_DPAD_UP||k==KeyEvent.KEYCODE_DPAD_DOWN;
            boolean ok = k==KeyEvent.KEYCODE_DPAD_CENTER||k==KeyEvent.KEYCODE_ENTER||k==KeyEvent.KEYCODE_NUMPAD_ENTER;
            if (dragIdx==ix){
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
            if (ok && e.getAction()==KeyEvent.ACTION_DOWN && (e.isLongPress() || e.getRepeatCount()>=3)){ appMenu(g, a, ix); return true; }
            return false;
        });
        return g;
    }
    void markHomeSig(){
        String sig=(editMode?"E":"N")+"|"+dragIdx+"|"+(drawerOpen?"D":"H")+"|";
        if (fullOrder!=null) for (String[] a: fullOrder) sig += a[0]+"/"+a[1]+",";
        homeBindSig=sig;
    }
    void startWiggle(View v){
        if (wig!=null){ wig.cancel(); wig=null; }
        if (v==null) return;
        wig=android.animation.ObjectAnimator.ofFloat(v, View.ROTATION, -1.5f, 1.5f);
        wig.setDuration(72); wig.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        wig.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        wig.setInterpolator(new android.view.animation.LinearInterpolator());
        wig.start();
    }
    void stopWiggle(){ if (wig!=null){ wig.cancel(); wig=null; } }
    void clearLift(View v, boolean settle){
        if (v==null) return;
        if (wig!=null && wig.getTarget()==v) stopWiggle();
        v.animate().cancel();
        boolean foc=v.isFocused() && !editMode;
        float sc=foc?1.07f:1f;
        v.setRotation(0);
        if (settle) v.animate().scaleX(sc).scaleY(sc).translationX(0).translationY(0).rotation(0).setDuration(180).setInterpolator(new android.view.animation.PathInterpolator(0.16f,1f,0.3f,1f)).start();
        else { v.setScaleX(sc); v.setScaleY(sc); v.setTranslationX(0); v.setTranslationY(0); }
        v.setTranslationZ(foc?p(6):0);
        if (v instanceof GlowFrame){ GlowFrame g=(GlowFrame)v; if (!foc){ g.glow=0; g.invalidate(); } }
    }
    void resetAllLifts(boolean settle, View keep){
        stopWiggle();
        if (homeGrid==null) return;
        for (int j=0;j<homeGrid.getChildCount();j++){
            View c=homeGrid.getChildAt(j);
            c.setAlpha(1f);
            clearLift(c, settle && c==keep);
        }
    }
    void enterEdit(int i){ if (editMode) return; closeMenu(false); editMode=true; setEditDim(true); startDrag(i); }
    void startDrag(int i){
        if (i<0 || i>=fullOrder.size() || homeGrid==null) return;
        dragIdx=i;
        if (i>=6 && !drawerOpen) setDrawer(true);
        View v=homeGrid.getChildAt(i);
        if (v==null) return;
        dragV=v;
        for (int j=0;j<homeGrid.getChildCount();j++){
            View c=homeGrid.getChildAt(j);
            if (c==v){
                if (c instanceof GlowFrame){ GlowFrame g=(GlowFrame)c; g.glow=1f; g.invalidate(); g.setTranslationZ(p(18)); }
                c.animate().scaleX(1.16f).scaleY(1.16f).translationY(-p(6)).rotation(0).setDuration(180).setInterpolator(new android.view.animation.DecelerateInterpolator()).withEndAction(()->{ if (dragIdx>=0 && homeGrid!=null && homeGrid.getChildAt(dragIdx)==c) startWiggle(c); }).start();
            } else c.setAlpha(0.5f);
        }
        v.requestFocus();
        markHomeSig();
    }
    void moveDrag(int from, int to){
        if (homeGrid==null || from==to || to<0 || to>=fullOrder.size()) return;
        Collections.swap(fullOrder, from, to); dragIdx=to; saveHome(fullOrder);
        if (to>=6 && !drawerOpen) setDrawer(true);
        else if (to<6 && from>=6 && drawerOpen) setDrawer(false);
        int n=homeGrid.getChildCount();
        final View[] was=new View[n]; final int[] ox=new int[n], oy=new int[n];
        for (int i=0;i<n;i++){ was[i]=homeGrid.getChildAt(i); ox[i]=was[i].getLeft(); oy[i]=was[i].getTop(); }
        View lift=was[from]; dragV=lift; View[] next=was.clone(); View tmp=next[from]; next[from]=next[to]; next[to]=tmp;
        homeGrid.removeAllViews();
        for (int i=0;i<n;i++) homeGrid.addView(next[i], gridLp(i));
        final View hold=lift;
        homeGrid.post(()->{
            android.view.animation.Interpolator ip=new android.view.animation.PathInterpolator(0.16f,1f,0.3f,1f);
            for (int i=0;i<n;i++){
                View v=next[i]; int old=0; while (old<n && was[old]!=v) old++;
                if (old>=n) continue;
                float dx=ox[old]-v.getLeft(), dy=oy[old]-v.getTop();
                if (dx==0 && dy==0) continue;
                boolean L=v==hold;
                v.setScaleX(L?1.16f:1f); v.setScaleY(L?1.16f:1f);
                v.setTranslationX(dx); v.setTranslationY(dy+(L?-p(6):0));
                v.animate().translationX(0).translationY(L?-p(6):0).setDuration(200).setInterpolator(ip).start();
            }
            if (hold.getParent()!=null) hold.requestFocus();
        });
        markHomeSig();
    }
    void drop(int i){
        View keep=homeGrid!=null && i>=0 && i<homeGrid.getChildCount()? homeGrid.getChildAt(i):null;
        dragV=null; dragIdx=-1; editMode=false; saveHome(fullOrder); setEditDim(false);
        if (drawerOpen){ if (gear!=null) gear.setFocusable(false); if (searchV!=null) searchV.setFocusable(false); }
        resetAllLifts(true, keep);
        if (keep!=null){
            if (focus!=null){ focus.savedSec=4+i/6; focus.savedIdx=i%6; }
            keep.post(()->keep.requestFocus());
        }
        markHomeSig();
    }
    void exitEdit(){ drop(Math.max(0, dragIdx)); }
    void setEditDim(boolean on){
        if (dimV==null){ dimV = new View(this); dimV.setBackgroundColor(Color.argb(150,4,4,10)); dimV.setAlpha(0f); dimV.setVisibility(View.GONE); root.addView(dimV, root.indexOfChild(homeGrid), new FrameLayout.LayoutParams(-1,-1)); }
        dimV.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        if (on){ dimV.setVisibility(View.VISIBLE); dimV.animate().alpha(1f).setDuration(280).setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f)).start(); }
        else dimV.animate().alpha(0f).setDuration(280).setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator()).withEndAction(()->dimV.setVisibility(View.GONE)).start();
        if (on){ editBlocked.clear();
            for (int i=0;i<root.getChildCount();i++){ View c=root.getChildAt(i); if (c==homeGrid||c==dimV) continue;
                if (c instanceof ViewGroup){ ((ViewGroup)c).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS); editBlocked.add(c); }
                if (c.isFocusable()){ c.setFocusable(false); if (!editBlocked.contains(c)) editBlocked.add(c); } } }
        else {
            for (View c: editBlocked){ if (c instanceof ViewGroup) ((ViewGroup)c).setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS); if (!(c instanceof ViewGroup) || c instanceof PillView || c instanceof GlowFrame || c==heroG) c.setFocusable(true); } editBlocked.clear(); }
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
    static final int[] MENU_ICONS={R.drawable.ic_menu_edit,R.drawable.ic_menu_stop,R.drawable.ic_menu_delete,R.drawable.ic_menu_info};
    void appMenu(View anchor, String[] a, int idx){
        if (editMode) return;
        if (focus!=null) focus.remember();
        closeMenu(false);
        LinearLayout p=new LinearLayout(this); p.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg=new GradientDrawable(); bg.setColor(Color.argb(235,28,28,32)); bg.setCornerRadius(p(14)); bg.setStroke(Math.max(1,p(1)), Color.argb(51,255,255,255));
        p.setBackground(bg); p.setPadding(p(8),p(10),p(8),p(10)); p.setElevation(p(18));
        LinearLayout head=new LinearLayout(this); head.setGravity(Gravity.CENTER_VERTICAL); head.setPadding(p(8),p(2),p(8),p(8));
        ImageView ic=new ImageView(this); ic.setScaleType(ImageView.ScaleType.FIT_CENTER);
        try { ic.setImageDrawable(pm.getApplicationIcon(a[0])); } catch (Exception e){}
        head.addView(ic, new LinearLayout.LayoutParams(p(28),p(28)));
        TextView nm=text(a[2], 13, Color.WHITE, "sans-serif-medium"); nm.setSingleLine(); nm.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(0,-2,1); np.leftMargin=p(8);
        head.addView(nm, np);
        p.addView(head, new LinearLayout.LayoutParams(p(200),-2));
        String[] labs={"Edit Home Screen","Force Stop","Uninstall","App Info"};
        boolean sys=systemApp(a[0]);
        View first=null;
        for (int n=0;n<labs.length;n++){
            final int id=n;
            boolean off=id==2 && sys;
            LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(p(10),0,p(10),0);
            row.setFocusable(!off); row.setClickable(!off);
            final ImageView li=new ImageView(this); li.setImageResource(MENU_ICONS[n]); li.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int idle=off?Color.argb(80,255,255,255):(id==2?Color.rgb(224,112,112):Color.WHITE);
            li.setColorFilter(idle, PorterDuff.Mode.SRC_IN);
            row.addView(li, new LinearLayout.LayoutParams(p(18),p(18)));
            final TextView t=text(labs[n], 12, idle, "sans-serif");
            LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-2,-2); tp.leftMargin=p(10);
            row.addView(t, tp);
            row.setOnFocusChangeListener((v,f)->{
                GradientDrawable pill=new GradientDrawable(); pill.setCornerRadius(p(8));
                pill.setColor(f?Color.argb(240,255,255,255):Color.TRANSPARENT);
                row.setBackground(pill);
                int fg=off?Color.argb(80,255,255,255):(f?Color.rgb(22,22,30):(id==2?Color.rgb(224,112,112):Color.WHITE));
                t.setTextColor(fg); li.setColorFilter(fg, PorterDuff.Mode.SRC_IN);
            });
            if (!off) row.setOnClickListener(v->menuAct(id, a, idx));
            row.setOnKeyListener((v,k,e)->{ if (k==KeyEvent.KEYCODE_BACK && e.getAction()==KeyEvent.ACTION_DOWN){ closeMenu(); return true; } return false; });
            LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(p(200), p(36));
            if (n==0) rp.topMargin=p(4);
            p.addView(row, rp);
            if (first==null && !off) first=row;
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
        p.setAlpha(0f); p.setScaleX(0.94f); p.setScaleY(0.94f);
        appMenu=p; root.addView(p, lp);
        p.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(120).start();
        final View f0=first;
        h.post(()->{ if (f0!=null) f0.requestFocus(); });
    }
    void menuAct(int id, String[] a, int idx){
        if (id==0){ enterEdit(idx); return; }
        closeMenu();
        if (id==1 || id==3){ try { startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:"+a[0])).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception e){} return; }
        if (id==2 && !systemApp(a[0])){ try { startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:"+a[0])).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception e){} }
    }
    void closeMenu(){ closeMenu(true); }
    void closeMenu(boolean back){
        if (appMenu==null) return;
        root.removeView(appMenu); appMenu=null;
        if (back && focus!=null) focus.restore();
    }
    void appInfo(String pkg){ try { startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:"+pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception e){} }
    // ---------- Featured hero ----------
    ImageView heroArt, heroBadge; TextView heroTitle, heroSub; List<Object[]> heroItems = new ArrayList<>(); int heroIdx=0; LinearLayout heroDots;
    final Runnable heroRotate = new Runnable(){ public void run(){ synchronized(heroLock){ if (heroItems.size()>1) heroIdx=(heroIdx+1)%heroItems.size(); else { h.postDelayed(this, 16000); return; } } showHero(); h.postDelayed(this, 16000); } };
    ImageView heroArt2; boolean heroFront=true; android.animation.ObjectAnimator kb;
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
    // v14: focal point (fractions of the bitmap). Face detection on a downscaled copy, else top-weighted (x centre, y 30%).
    float[] focal(Bitmap bm){
        float[] f = {0.5f, 0.3f};
        try { int w=320, hh=Math.max(2, Math.round(320f*bm.getHeight()/bm.getWidth())); if (hh%2==1) hh++;
            Bitmap sm = Bitmap.createScaledBitmap(bm, w, hh, true).copy(Bitmap.Config.RGB_565, false);
            android.media.FaceDetector.Face[] fs = new android.media.FaceDetector.Face[4];
            int n = new android.media.FaceDetector(w, hh, 4).findFaces(sm, fs); float best=0; PointF pt=new PointF();
            for (int i=0;i<n;i++){ if (fs[i]==null || fs[i].confidence()<0.4f) continue; float d=fs[i].eyesDistance(); if (d>best){ best=d; fs[i].getMidPoint(pt); f[0]=pt.x/w; f[1]=Math.min(1f,(pt.y+d*0.4f)/hh); } }
            android.util.Log.i("ShieldHome","focal faces="+n+" f="+f[0]+","+f[1]);
        } catch (Throwable t){ android.util.Log.w("ShieldHome","focal "+t); }
        return f;
    }
    void focalCrop(ImageView iv, Bitmap bm, float[] f){
        int vw = iv.getWidth()>0?iv.getWidth():p(1024), vh = iv.getHeight()>0?iv.getHeight():p(288); float bw=bm.getWidth(), bh=bm.getHeight();
        float sc = Math.max(vw/bw, vh/bh); float sw=bw*sc, sh=bh*sc;
        boolean face = !(f[0]==0.5f && f[1]==0.3f);
        float tx = face ? vw*0.6f - f[0]*sw : (vw-sw)/2f; tx = Math.max(vw-sw, Math.min(0, tx));
        float ty = face ? vh*0.42f - f[1]*sh : -(sh-vh)*f[1]; ty = Math.max(vh-sh, Math.min(0, ty));
        Matrix m = new Matrix(); m.setScale(sc, sc); m.postTranslate(tx, ty);
        iv.setScaleType(ImageView.ScaleType.MATRIX); iv.setImageMatrix(m);
        iv.setPivotX(Math.max(0, Math.min(vw, f[0]*sw+tx))); iv.setPivotY(Math.max(0, Math.min(vh, f[1]*sh+ty)));
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
    void bindHeroDots(){
        if (!(heroG instanceof ViewGroup)) return;
        int n, ix;
        synchronized(heroLock){ n=heroItems.size(); ix=heroIdx; }
        if (heroDots==null){
            heroDots=new LinearLayout(this); heroDots.setGravity(Gravity.CENTER);
            ((ViewGroup)heroG).addView(heroDots, lp(-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL,0,0,0,p(12)));
        }
        heroDots.removeAllViews();
        if (n<=1){ heroDots.setVisibility(View.GONE); return; }
        heroDots.setVisibility(View.VISIBLE);
        for (int i=0;i<n;i++){
            View d=new View(this);
            GradientDrawable g=new GradientDrawable(); g.setShape(GradientDrawable.OVAL);
            g.setColor(i==ix?Color.argb(220,255,255,255):Color.argb(80,255,255,255));
            d.setBackground(g);
            LinearLayout.LayoutParams dp=new LinearLayout.LayoutParams(p(5),p(5));
            dp.leftMargin=dp.rightMargin=p(3);
            heroDots.addView(d, dp);
        }
    }
    void showHero(){
        final Object[] o;
        synchronized(heroLock){ if (heroItems.isEmpty()) return; o=heroItems.get(Math.min(heroIdx, heroItems.size()-1)); }
        bindHeroDots();
        final String uri = (String)o[3];
        final int tw=p(1024), th=p(288), g=++heroLoadGen;
        pool.execute(()->{
            Bitmap bm = uri==null||images==null?null:images.decode(uri, tw, th);
            final float[] ff = bm==null? null : focal(bm);
            h.post(()->{ if (g!=heroLoadGen) return; heroOnScreen=o; heroFocal=ff; transitionHero(o, bm); });
        });
    }
    float[] heroFocal;
    void transitionHero(final Object[] o, Bitmap bm){
        if (heroLabel!=null) heroLabel.setText(o.length>5 && "NEW".equals(o[5]) ? "NEW" : "FEATURED");
        android.view.animation.Interpolator ease = new android.view.animation.PathInterpolator(0.4f,0f,0.2f,1f);
        final ImageView in = heroFront ? heroArt2 : heroArt, out = heroFront ? heroArt : heroArt2;
        if (bm != null){
            in.setImageBitmap(bm); focalCrop(in, bm, heroFocal==null?new float[]{0.5f,0.3f}:heroFocal); in.setScaleX(1f); in.setScaleY(1f); in.setAlpha(0f); in.bringToFront();
            ViewGroup hp=(ViewGroup)in.getParent();
            for (int i=0;i<hp.getChildCount();i++){ View ch=hp.getChildAt(i); if (!(ch instanceof ImageView)) ch.bringToFront(); }
            in.animate().alpha(1f).setDuration(1600).setInterpolator(ease).start();
            out.animate().alpha(0f).setDuration(1600).setInterpolator(ease).start();
            if (kb!=null) kb.cancel();
            kb = android.animation.ObjectAnimator.ofPropertyValuesHolder(in, android.animation.PropertyValuesHolder.ofFloat("scaleX",1f,1.07f), android.animation.PropertyValuesHolder.ofFloat("scaleY",1f,1.07f));
            kb.setDuration(17600); kb.setInterpolator(new android.view.animation.LinearInterpolator()); kb.start();
            heroFront = !heroFront;
        }
        final View[] texts = {heroTitle, heroSub, heroBadge};
        for (View t: texts) t.animate().alpha(0f).translationX(-p(10)).setDuration(450).setInterpolator(new android.view.animation.AccelerateInterpolator()).start();
        h.postDelayed(()->{
            heroTitle.setText((String)o[1]); heroSub.setText(o[2]==null?"":(String)o[2]);
            try { Drawable bd = pm.getApplicationBanner((String)o[0]); if (bd==null) bd = pm.getApplicationIcon((String)o[0]); heroBadge.setImageBitmap(glyph((String)o[0], bd));} catch(Exception e){}
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
        tabPlay.setVisibility(pn?View.VISIBLE:View.GONE);
        tabPlay.setFocusable(pn);
        if (tabLive.getLayoutParams() instanceof FrameLayout.LayoutParams){
            ((FrameLayout.LayoutParams)tabLive.getLayoutParams()).leftMargin=p(pn?175:35);
            tabLive.requestLayout();
        }
        if (!pn){
            if (tab==0){ tab=1; tabPlay.setOn(false); tabLive.setOn(true); }
            if (tabPlay.hasFocus()) tabLive.requestFocus();
        }
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
                GlowFrame g=(GlowFrame)c; g.fade=a; g.setAlpha(1f); g.inner.setAlpha(a); g.invalidate();
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
        f.setOutlineProvider(ViewOutlineProvider.BACKGROUND); f.setElevation(p(11)); if (Build.VERSION.SDK_INT>=28){ f.setOutlineSpotShadowColor(Color.BLACK); f.setOutlineAmbientShadowColor(Color.BLACK); }
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
        View g = focusable(f, bg, TILE_R, click);
        if (badgePkg!=null) tintCard(g, badgePkg);
        return g;
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
            View v=card(art, null, false, null, cap, -1, ()->launchPkg(pk)); tintCard(v, pk); addCard(v, i++);
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
            h.post(()->{ plutoNow.clear(); plutoNow.putAll(m); plutoArt.clear(); plutoArt.putAll(art); plutoLogo.clear(); plutoLogo.putAll(logos); plutoAt=System.currentTimeMillis(); applyLiveNow();
                if (liveCache==null) return;
                List<String[]> extra=new ArrayList<>();
                for (Map.Entry<String,String> e: plutoSlug.entrySet()) if (!liveSlugs.contains(e.getValue())) extra.add(new String[]{e.getValue(), e.getKey()});
                if (!extra.isEmpty()) appendLive(extra, false);
            });
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
        if (installed("com.google.android.tvlauncher")){ View v=freeplayLiveTile(); tintCard(v,"com.google.android.tvlauncher"); putLive(v, null); }
        List<String[]> rest=new ArrayList<>();
        if (installed("tv.pluto.android")){
            for (Map.Entry<String,String> e: liveSlugNames().entrySet()){
                if (liveCache.size()<6) putLive(plutoTile(e.getKey(), e.getValue(), true), e.getKey());
                else rest.add(new String[]{e.getKey(), e.getValue()});
            }
        }
        boolean hay=installed("com.haystack.android");
        if (hay && liveCache.size()<6){ putLive(haystackTile(), "haystack"); hay=false; }
        if (!rest.isEmpty() || hay){ final List<String[]> r=rest; final boolean h2=hay; h.post(()->appendLive(r,h2)); }
        else if (!plutoCaps.isEmpty()) fillPlutoNow(plutoCaps);
        return liveCache.size();
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
    void appendLive(List<String[]> rest, boolean hay){
        for (String[] e: rest){ if (liveSlugs.contains(e[0])) continue; View v=plutoTile(e[0], e[1], false); liveCache.add(v); liveSlugs.add(e[0]); if (tab==1) addCard(v, liveCache.size()-1); }
        if (hay && !liveSlugs.contains("haystack")){ View v=haystackTile(); liveCache.add(v); liveSlugs.add("haystack"); if (tab==1) addCard(v, liveCache.size()-1); }
        if (tab==1) updateCardAlphas();
        if (!plutoCaps.isEmpty()) fillPlutoNow(plutoCaps);
    }
    View plutoTile(String slug, String nm, boolean loadArt){
        Map<String,Long> plutoIds=plutoIdsCache!=null?plutoIdsCache:new HashMap<>();
        final Intent in=plutoIntent(slug); Long id=plutoId(plutoIds, nm, slug);
        View v=card(null, null, false, null, nm, -1, ()->startSafe(in, "tv.pluto.android")); tintCard(v, "tv.pluto.android");
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
    View haystackTile(){
        Drawable art=null; try { art=pm.getApplicationBanner("com.haystack.android"); } catch(Exception e){}
        View v=card(art, null, true, null, "Haystack News \u00B7 Live", -1, ()->startSafe(pm.getLeanbackLaunchIntentForPackage("com.haystack.android")==null?new Intent():pm.getLeanbackLaunchIntentForPackage("com.haystack.android").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), "com.haystack.android"));
        tintCard(v,"com.haystack.android"); return v;
    }
    boolean installed(String p){ try { pm.getApplicationInfo(p,0); return true; } catch(Exception e){ return false; } }
    void launchPkg(String pkg){ Intent i = pm.getLeanbackLaunchIntentForPackage(pkg); if (i==null) i = pm.getLaunchIntentForPackage(pkg); if (i!=null) startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }

    // ---------- v6: quick settings, featured toggle, dimmer, weather ----------
    PillView searchV; TextView heroLabel;
    // v12: Google TV Freeplay via the stock launcher's exported TvContentIntentReceiver (tv://freeplay -> FreeplayActivity, resumes the last Freeplay channel)
    void playFreeplay(){ try { Intent b=new Intent("com.google.android.tvlauncher.action.VIEW", Uri.parse("tv://freeplay")); b.setComponent(new android.content.ComponentName("com.google.android.tvlauncher","com.google.android.tvlauncher.TvContentIntentReceiver")); b.addFlags(Intent.FLAG_RECEIVER_FOREGROUND); sendBroadcast(b); android.util.Log.i("ShieldHome","freeplay broadcast sent"); } catch(Exception e){ android.util.Log.w("ShieldHome","freeplay "+e); } }
    View freeplayLiveTile(){
        String art=freeplayNowArt();
        Drawable ic=null; if (art==null) try { android.content.res.Resources r=pm.getResourcesForApplication("com.google.android.tvlauncher"); int id=r.getIdentifier("freeplay_icon","drawable","com.google.android.tvlauncher"); if (id!=0) ic=r.getDrawable(id,null); } catch(Exception e){}
        View v=card(ic, art, art==null, null, "Google TV Freeplay \u00B7 Live", -1, ()->playFreeplay());
        TextView tg=text("LIVE", 7, Color.WHITE, "sans-serif-medium"); tg.setLetterSpacing(.08f); tg.setPadding(p(5),p(2),p(5),p(2)); GradientDrawable gb=new GradientDrawable(); gb.setCornerRadius(p(3)); gb.setColor(Color.rgb(200,30,40)); tg.setBackground(gb);
        ((GlowFrame)v).inner.addView(tg, lp(-2,-2,Gravity.TOP|Gravity.START,p(7),p(7),0,0));
        if (art!=null){ TextView ov=chName("Freeplay"); ((GlowFrame)v).inner.addView(ov, lp(-2,-2,Gravity.TOP|Gravity.START,0,0,0,0)); }
        return v; }
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
                nl.add(new Object[]{pkg, o.getString("show"), when+" on "+o.optString("net")+ep+"\n"+sum, o.getString("img"), li, "NEW"});
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

    PillView gear, qsDim; LinearLayout qsPanel; boolean qsOpen=false; View heroG, heroFocus, inAppDim;
    static final int[] DIMS = {0,25,50,70,90};
    TextView wxTemp, wxDesc; View wxSep;
    int dimIdx(){ return Math.max(0, Math.min(DIMS.length-1, prefs().getInt("dim", 0))); }
    void qsLabels(){ int d=DIMS[dimIdx()]; qsDim.label = "Dim: "+(d==0?"Off":d+"%"); qsDim.setOn(d>0); }
    void toggleQs(){
        qsOpen=!qsOpen; gear.setOn(qsOpen);
        android.view.animation.Interpolator ease = new android.view.animation.PathInterpolator(0.2f,0f,0f,1f);
        gear.animate().rotation(qsOpen?90f:0f).setDuration(420).setInterpolator(ease).start();
        if (qsOpen){
            searchV.animate().alpha(0f).translationX(p(12)).setDuration(200).setInterpolator(ease).withEndAction(()->{ searchV.setVisibility(View.INVISIBLE); }).start();
            searchV.setFocusable(false);
            qsPanel.setVisibility(View.VISIBLE); qsPanel.setAlpha(1f); qsPanel.setTranslationX(0);
            int d=60; for (int i=0;i<qsPanel.getChildCount();i++){ View c=qsPanel.getChildAt(i); c.setAlpha(0f); c.setTranslationX(-p(36+16*i)); c.setScaleX(.92f); c.setScaleY(.92f);
                c.animate().alpha(1f).translationX(0).scaleX(1f).scaleY(1f).setStartDelay(d).setDuration(380).setInterpolator(ease).start(); d+=70; }
            h.postDelayed(()->{ if (qsOpen) qsDim.requestFocus(); }, 60); }
        else { if (qsPanel.hasFocus()) gear.requestFocus();
            for (int i=0;i<qsPanel.getChildCount();i++){ View c=qsPanel.getChildAt(i); c.animate().alpha(0f).translationX(-p(30+16*i)).setStartDelay(0).setDuration(220).setInterpolator(ease).start(); }
            h.postDelayed(()->{ if (!qsOpen) qsPanel.setVisibility(View.GONE); }, 230);
            searchV.setVisibility(View.VISIBLE); searchV.setFocusable(true); searchV.setTranslationX(p(12));
            searchV.animate().alpha(1f).translationX(0).setDuration(300).setStartDelay(120).setInterpolator(ease).withEndAction(null).start(); }
    }
    void applyDim(){
        int pct = DIMS[dimIdx()];
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
        String ic, d;
        if (code==0){ ic=day?"\u2600\uFE0F":"\uD83C\uDF19"; d="Clear"; }
        else if (code<=2){ ic=day?"\uD83C\uDF24\uFE0F":"\u2601\uFE0F"; d="Partly cloudy"; }
        else if (code==3){ ic="\u2601\uFE0F"; d="Cloudy"; }
        else if (code==45||code==48){ ic="\uD83C\uDF2B\uFE0F"; d="Fog"; }
        else if (code>=51&&code<=57){ ic="\uD83C\uDF26\uFE0F"; d="Drizzle"; }
        else if (code>=61&&code<=67){ ic="\uD83C\uDF27\uFE0F"; d="Rain"; }
        else if (code>=71&&code<=77){ ic="\u2744\uFE0F"; d="Snow"; }
        else if (code>=80&&code<=82){ ic="\uD83C\uDF26\uFE0F"; d="Showers"; }
        else if (code==85||code==86){ ic="\uD83C\uDF28\uFE0F"; d="Snow showers"; }
        else { ic="\u26C8\uFE0F"; d="Storms"; }
        setFit(wxTemp, ic+"  "+prefs().getInt("wx_t",0)+"\u00B0F");
        String city=prefs().getString("wx_city",""); setFit(wxDesc, city.isEmpty()?d:d+" \u00B7 "+city);
        wxSep.setVisibility(View.VISIBLE);
    }
}
