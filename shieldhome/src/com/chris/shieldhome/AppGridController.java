package com.chris.shieldhome;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.*;

class AppGridController {
    final MainActivity a;
    AppGridController(MainActivity a) { this.a = a; }

    View[] slideViews(){ return new View[]{this.a.clk, this.a.gear, this.a.searchV, this.a.searchSplit, this.a.searchField, this.a.dimBar, this.a.heroG, this.a.tabBar, this.a.cardWrap, this.a.homeGrid}; }
    void setDrawer(boolean on){ boolean edge=this.a.drawerOpen!=on; this.a.drawerOpen=on; this.slideHome(edge); }
    void fadeChrome(boolean hide){
        float a=hide?0f:1f;
        for (View v: new View[]{this.a.clk, this.a.gear, this.a.searchV, this.a.searchSplit, this.a.searchField, this.a.dimBar}){
            if (v==null) continue;
            if (v==this.a.dimBar && !this.a.dimSlide) continue;
            if (v==this.a.searchSplit && !this.a.searchOpen) continue;
            if (v==this.a.searchField && !this.a.searchTyping) continue;
            v.setVisibility(View.VISIBLE);
            v.animate().alpha(a).setDuration(600).setInterpolator(this.a.drawerIp).withEndAction(()->{ if (this.a.drawerOpen) v.setVisibility(View.INVISIBLE); }).start();
        }
        if (this.a.gear!=null) this.a.gear.setFocusable(!hide);
        if (this.a.searchV!=null) this.a.searchV.setFocusable(!hide && !this.a.dimSlide && !this.a.searchOpen);
        if (this.a.speakV!=null){ this.a.speakV.setFocusable(!hide && this.a.searchOpen && !this.a.dimSlide); this.a.typeV.setFocusable(!hide && this.a.searchOpen && !this.a.dimSlide); }
    }
    void boxLayer(View v, boolean on){ if (v!=null) v.setLayerType(on?View.LAYER_TYPE_HARDWARE:View.LAYER_TYPE_NONE, null); }
    void staggerDrawer(boolean open){
        if (this.a.homeGrid==null) return;
        int n=this.a.homeGrid.getChildCount(), rows=Math.max(1,(n+5)/6);
        for (int row=2; row<rows; row++){ // row 1 is the peek; leave it visible when closed
            int delay=open?(row-1)*40:(rows-1-row)*40;
            for (int c=0;c<6;c++){
                int i=row*6+c; if (i>=n) break;
                View t=this.a.homeGrid.getChildAt(i); t.animate().cancel();
                if (open){ t.setAlpha(0f); t.setTranslationY(this.a.p(12)); }
                t.animate().alpha(open?1f:0f).translationY(open?0:this.a.p(12)).setStartDelay(delay).setDuration(600).setInterpolator(this.a.drawerIp).start();
            }
        }
    }
    void slideHome(){ this.slideHome(false); }
    void slideHome(boolean edge){
        if (this.a.focus!=null && !this.a.editMode) this.a.focus.remember();
        int r=0; View f=this.a.getCurrentFocus();
        if (f!=null && this.a.homeGrid!=null && f.getParent()==this.a.homeGrid) r=Math.max(0, (this.a.editMode?this.tileIx(f):this.a.homeGrid.indexOfChild(f))/6);
        final boolean open=this.a.drawerOpen;
        float y = open ? -(this.a.p(this.a.APP_GRID_Y-17)+Math.max(0, r-4)*this.rowH()) : 0;
        this.fadeChrome(open);
        if (open){
            for (View v: new View[]{this.a.tabBar, this.a.cardWrap, this.a.homeGrid}) if (v!=null) v.bringToFront();
        } else {
            if (this.a.clk!=null) this.a.clk.bringToFront();
            if (this.a.gear!=null) this.a.gear.bringToFront();
            if (this.a.searchV!=null) this.a.searchV.bringToFront();
            if (this.a.searchSplit!=null && this.a.searchOpen) this.a.searchSplit.bringToFront();
            if (this.a.searchField!=null && this.a.searchTyping) this.a.searchField.bringToFront();
            if (this.a.dimBar!=null && this.a.dimSlide) this.a.dimBar.bringToFront();
        }
        if (this.a.editMode) this.stackEditDim();
        for (View v: this.slideViews()){
            if (v==null) continue;
            boolean up=v==this.a.heroG||v==this.a.tabBar||v==this.a.cardWrap;
            if (v==this.a.heroG) this.boxLayer(v, true);
            android.view.ViewPropertyAnimator an=v.animate().translationY(y).setDuration(600).setInterpolator(this.a.drawerIp);
            if (up) an.alpha(open?0f:1f).scaleX(open?0.98f:1f).scaleY(open?0.98f:1f);
            if (v==this.a.homeGrid) an.withEndAction(()->{ if (!this.a.editMode && this.a.getCurrentFocus()==null && this.a.focus!=null) this.a.focus.restore(); });
            else if (v==this.a.heroG) an.withEndAction(()->this.boxLayer(v, false));
            an.start();
        }
        if (edge) this.staggerDrawer(open);
    }

    long use(String p){ Long v = p==null?null:this.a.usage.get(p); long base = v==null?0:v; if ("com.google.android.youtube.tv".equals(p)) base = base*3+1; return base; }
    final android.view.Choreographer.FrameCallback glideCb=new android.view.Choreographer.FrameCallback(){
        public void doFrame(long t){
            if (a.tiles==null){ a.glideOn=false; return; }
            boolean more=false;
            for (int i=0;i<a.tiles.length;i++){
                View v=a.tiles[i];
                float x=v.getTranslationX(), y=v.getTranslationY();
                float dx=a.tgtX[i]-x, dy=a.tgtY[i]-y;
                if (dx*dx+dy*dy<0.25f){ if (dx!=0||dy!=0){ v.setTranslationX(a.tgtX[i]); v.setTranslationY(a.tgtY[i]); } }
                else { v.setTranslationX(x+dx*0.28f); v.setTranslationY(y+dy*0.28f); more=true; }
            }
            if (more) android.view.Choreographer.getInstance().postFrameCallback(this);
            else a.glideOn=false;
        }
    };
    List<String[]> homeApps(){
        List<String[]> all = new ArrayList<>(this.a.apps());
        String saved = this.a.prefs().getString("order", null);
        if (saved == null){
            final List<String> pref = Arrays.asList(this.a.PLAY_PKGS);
            Collections.sort(all,(x,y)->{
                if (x[0].equals("com.android.tv.settings")) return 1;
                if (y[0].equals("com.android.tv.settings")) return -1;
                long ua=use(x[0]), ub=use(y[0]); if (ua<300000L) ua=0; if (ub<300000L) ub=0;
                int c=Long.compare(ub,ua); if (c!=0) return c;
                int ia=pref.indexOf(x[0]), ib=pref.indexOf(y[0]); if(ia<0)ia=99; if(ib<0)ib=99;
                return ia!=ib?ia-ib:x[2].compareToIgnoreCase(y[2]);
            });
            return all;
        }
        List<String[]> out=new ArrayList<>(); Set<String> seen=new HashSet<>();
        for (String key: saved.split(",")){
            if (key.isEmpty() || key.equals("_")) continue;
            for (String[] app: all) if ((app[0]+"/"+app[1]).equals(key) && seen.add(key)) out.add(app);
        }
        for (String[] app: all) if (seen.add(app[0]+"/"+app[1])) out.add(app);
        return out;
    }
    void saveHome(List<String[]> l){ StringBuilder sb=new StringBuilder(); for (String[] app: l){ if (app==null) continue; if (sb.length()>0) sb.append(','); sb.append(app[0]).append('/').append(app[1]); } this.a.prefs().edit().putString("order", sb.toString()).commit(); }
    GridLayout.LayoutParams gridLp(int i){ GridLayout.LayoutParams gl = new GridLayout.LayoutParams(); gl.width=this.a.p(this.a.APP_W); gl.height=this.a.p(this.a.APP_H); gl.rightMargin = (i%6==5)?0:this.a.p(this.a.APP_HG); gl.bottomMargin = this.a.p(this.a.APP_VG); return gl; }
    int rowH(){ return this.a.p(this.a.APP_H+this.a.APP_VG); }
    void buildHomeGrid(){ buildHomeGrid(-1); }
    void buildHomeGrid(int focusIdx){
        if (this.a.editMode) return;
        if (this.a.homeGrid==null) return;
        List<String[]> all = homeApps();
        this.a.fullOrder = all;
        String sig=(this.a.editMode?"E":"N")+"|"+this.a.dragIdx+"|"+(this.a.drawerOpen?"D":"H")+"|";
        for (String[] app: all) sig += app[0]+"/"+app[1]+",";
        if (focusIdx<0 && sig.equals(this.a.homeBindSig) && this.a.homeGrid.getChildCount()==all.size()) return;
        this.a.homeBindSig=sig;
        this.a.homeGrid.removeAllViews();
        for (int i=0;i<all.size();i++) this.a.homeGrid.addView(homeTile(all.get(i), i), gridLp(i));
        ViewGroup.LayoutParams lp=this.a.homeGrid.getLayoutParams();
        if (lp!=null){ int height=rowH()*Math.max(1,(all.size()+5)/6); if (lp.height!=height){ lp.height=height; this.a.homeGrid.setLayoutParams(lp); } }
        if (focusIdx>=0 && this.a.homeGrid.getChildCount()>0){
            final int f=Math.min(focusIdx, this.a.homeGrid.getChildCount()-1);
            this.a.homeGrid.getChildAt(f).requestFocus();
            this.a.h.post(()->{ if (f<this.a.homeGrid.getChildCount()) this.a.homeGrid.getChildAt(f).requestFocus(); });
        }
    }
    View homeTile(final String[] app, final int i){
        final GlowFrame g = (GlowFrame)this.a.appTile(app[0], app[1], app[2]);
        g.setOnLongClickListener(v->{ if (!this.a.editMode) appMenu(g, app, tileIx(g)); return true; });
        g.setOnKeyListener((v,k,e)->{
            int ix=tileIx(v);
            boolean dpad = k==KeyEvent.KEYCODE_DPAD_LEFT||k==KeyEvent.KEYCODE_DPAD_RIGHT||k==KeyEvent.KEYCODE_DPAD_UP||k==KeyEvent.KEYCODE_DPAD_DOWN;
            boolean ok = k==KeyEvent.KEYCODE_DPAD_CENTER||k==KeyEvent.KEYCODE_ENTER||k==KeyEvent.KEYCODE_NUMPAD_ENTER;
            if (v==this.a.dragV){
                if (e.getAction()!=KeyEvent.ACTION_DOWN) return dpad||ok;
                if (ok){ if (e.getRepeatCount()==0) drop(ix); return true; }
                if (k==KeyEvent.KEYCODE_BACK){ drop(ix); return true; }
                int to = k==KeyEvent.KEYCODE_DPAD_LEFT? ix-1 : k==KeyEvent.KEYCODE_DPAD_RIGHT? ix+1 : k==KeyEvent.KEYCODE_DPAD_UP? ix-6 : k==KeyEvent.KEYCODE_DPAD_DOWN? ix+6 : -99;
                if (to==-99) return false;
                if (to>=0 && to<this.a.fullOrder.size()) moveDrag(ix, to);
                else if (k==KeyEvent.KEYCODE_DPAD_UP && ix<6 && this.a.drawerOpen) setDrawer(false);
                return true;
            }
            if (this.a.editMode) return false;
            if (ok){
                if (e.getAction()==KeyEvent.ACTION_DOWN && e.getRepeatCount()==0 && !e.isLongPress()){ pressHold(g); return false; }
                if (e.getAction()==KeyEvent.ACTION_DOWN && (e.isLongPress() || e.getRepeatCount()>=3)){ appMenu(g, app, ix); return true; }
                if (e.getAction()==KeyEvent.ACTION_UP){
                    if (this.a.appMenu!=null) return true;
                    if (this.a.pressHeld) pressUp(g);
                    return false;
                }
            }
            return false;
        });
        return g;
    }
    void markHomeSig(){
        String sig=(this.a.editMode?"E":"N")+"|"+this.a.dragIdx+"|"+(this.a.drawerOpen?"D":"H")+"|";
        if (this.a.fullOrder!=null) for (String[] app: this.a.fullOrder) sig += app[0]+"/"+app[1]+",";
        this.a.homeBindSig=sig;
    }
    int tileIx(View v){
        if (this.a.tiles!=null) for (int i=0;i<this.a.tiles.length;i++) if (this.a.tiles[i]==v) return i;
        return this.a.homeGrid==null?-1:this.a.homeGrid.indexOfChild(v);
    }
    int slotX(int i){ return (i%6)*this.a.slotW; }
    int slotY(int i){ return (i/6)*this.a.slotH; }
    void layer(View v, boolean on){ View t=v instanceof GlowFrame? ((GlowFrame)v).inner:v; if (t!=null) t.setLayerType(on?View.LAYER_TYPE_HARDWARE:View.LAYER_TYPE_NONE, null); }
    void retarget(int i){
        View v=this.a.tiles[i];
        this.a.tgtX[i]=slotX(i)-v.getLeft();
        this.a.tgtY[i]=slotY(i)-v.getTop()+(v==this.a.dragV?-this.a.liftY:0);
        if (v.getLayerType()!=View.LAYER_TYPE_HARDWARE) layer(v, true);
    }
    void kickGlide(){
        if (!this.a.glideOn){ this.a.glideOn=true; android.view.Choreographer.getInstance().postFrameCallback(glideCb); }
    }
    void clearLift(View v, boolean settle){
        if (v==null) return;
        v.animate().cancel();
        boolean foc=(v.isFocused()||v==this.a.menuOwner) && !this.a.editMode;
        float sc=foc?this.a.APP_FOCUS:1f;
        v.setRotation(0);
        if (settle) v.animate().scaleX(sc).scaleY(sc).translationX(0).translationY(0).rotation(0).setDuration(180).setInterpolator(this.a.dragIp).start();
        else { v.setScaleX(sc); v.setScaleY(sc); v.setTranslationX(0); v.setTranslationY(0); v.setRotation(0); }
        v.setTranslationZ(foc?this.a.p(6):0);
        if (v instanceof GlowFrame){ GlowFrame g=(GlowFrame)v; g.glow=foc?1f:0f; g.syncHalo(); g.invalidate(); }
    }
    void resetAllLifts(boolean settle, View keep){
        if (this.a.homeGrid==null) return;
        for (int j=0;j<this.a.homeGrid.getChildCount();j++){
            View c=this.a.homeGrid.getChildAt(j);
            c.setAlpha(1f);
            clearLift(c, settle && c==keep);
        }
    }
    void commitTiles(){
        this.a.glideOn=false;
        if (this.a.tiles==null || this.a.homeGrid==null) return;
        for (int i=0;i<this.a.tiles.length;i++){
            View v=this.a.tiles[i]; v.animate().cancel();
            v.setTranslationX(0); v.setTranslationY(0); v.setRotation(0); layer(v, false);
        }
        this.a.homeGrid.removeAllViews();
        for (int i=0;i<this.a.tiles.length;i++) this.a.homeGrid.addView(this.a.tiles[i], gridLp(i));
        this.a.tiles=null; this.a.tgtX=null; this.a.tgtY=null;
    }
    void enterEdit(int i){ if (this.a.editMode) return; this.a.editMode=true; closeMenu(false); startDrag(i); setEditDim(true); }
    void startDrag(int i){
        if (i<0 || i>=this.a.fullOrder.size() || this.a.homeGrid==null) return;
        int n=this.a.homeGrid.getChildCount();
        this.a.tiles=new View[n]; this.a.tgtX=new float[n]; this.a.tgtY=new float[n];
        this.a.slotW=this.a.p(this.a.APP_W)+this.a.p(this.a.APP_HG); this.a.slotH=rowH(); this.a.liftY=this.a.p(6);
        for (int j=0;j<n;j++){ this.a.tiles[j]=this.a.homeGrid.getChildAt(j); this.a.tgtX[j]=0; this.a.tgtY[j]=0; }
        this.a.dragIdx=i; this.a.dragV=this.a.tiles[i];
        if (i>=6 && !this.a.drawerOpen) setDrawer(true);
        layer(this.a.dragV, true);
        for (int j=0;j<n;j++){
            View c=this.a.tiles[j];
            if (c==this.a.dragV){
                if (c instanceof GlowFrame){ GlowFrame g=(GlowFrame)c; g.glow=1f; g.syncHalo(); g.setTranslationZ(this.a.p(18)); }
                c.animate().cancel();
                float from=c.getScaleX();
                c.setRotation(0); c.setTranslationY(-this.a.liftY); this.a.tgtY[j]=-this.a.liftY;
                c.setScaleX(from); c.setScaleY(from);
                c.animate().scaleX(this.a.APP_HOLD).scaleY(this.a.APP_HOLD).setDuration(160).setInterpolator(this.a.dragIp).start();
            } else c.setAlpha(0.5f);
        }
        if (!this.a.dragV.isFocused()) this.a.dragV.requestFocus();
    }
    void moveDrag(int from, int to){
        if (this.a.tiles==null || from==to || to<0 || to>=this.a.tiles.length) return;
        Collections.swap(this.a.fullOrder, from, to);
        View tmp=this.a.tiles[from]; this.a.tiles[from]=this.a.tiles[to]; this.a.tiles[to]=tmp;
        this.a.dragIdx=to; this.a.dragV=tmp;
        if (to>=6 && !this.a.drawerOpen) setDrawer(true);
        else if (to<6 && from>=6 && this.a.drawerOpen) setDrawer(false);
        retarget(from); retarget(to); kickGlide();
    }
    void drop(int i){
        View keep=this.a.tiles!=null && i>=0 && i<this.a.tiles.length? this.a.tiles[i] : (this.a.homeGrid!=null && i>=0 && i<this.a.homeGrid.getChildCount()? this.a.homeGrid.getChildAt(i):null);
        this.a.dragV=null; this.a.dragIdx=-1; this.a.editMode=false; saveHome(this.a.fullOrder); markHomeSig();
        commitTiles(); setEditDim(false);
        if (this.a.drawerOpen){ if (this.a.gear!=null) this.a.gear.setFocusable(false); if (this.a.searchV!=null) this.a.searchV.setFocusable(false); if (this.a.speakV!=null){ this.a.speakV.setFocusable(false); this.a.typeV.setFocusable(false); } }
        resetAllLifts(true, keep);
        if (keep!=null){
            if (this.a.focus!=null){ this.a.focus.savedSec=4+i/6; this.a.focus.savedIdx=i%6; this.a.focus.savedKey=(String)keep.getTag(); }
            keep.requestFocus();
        }
    }
    void exitEdit(){ drop(Math.max(0, this.a.dragIdx)); }
    void ensureDim(){
        if (this.a.dimV!=null || this.a.root==null) return;
        this.a.dimV=new View(this.a); this.a.dimV.setBackgroundColor(Color.argb(150,4,4,10)); this.a.dimV.setAlpha(0f);
        this.a.dimV.setVisibility(View.INVISIBLE); this.a.dimV.setClickable(false); this.a.dimV.setFocusable(false);
        this.a.dimV.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        int at=this.a.homeGrid==null?-1:this.a.root.indexOfChild(this.a.homeGrid);
        this.a.root.addView(this.a.dimV, at<0?0:at, new FrameLayout.LayoutParams(-1,-1));
    }
    void stackEditDim(){
        if (this.a.dimV!=null) this.a.dimV.bringToFront();
        if (this.a.homeGrid!=null) this.a.homeGrid.bringToFront();
    }
    void setEditDim(boolean on){
        ensureDim(); if (this.a.dimV==null) return;
        this.a.dimV.animate().cancel();
        if (on){ stackEditDim(); this.a.dimV.setVisibility(View.VISIBLE); this.a.dimV.animate().alpha(1f).setDuration(160).setInterpolator(this.a.dragIp).start(); }
        else this.a.dimV.animate().alpha(0f).setDuration(140).setInterpolator(this.a.dragIp).withEndAction(()->this.a.dimV.setVisibility(View.INVISIBLE)).start();
    }
    void fadeRemove(final GridLayout g, final String pkg, final Runnable after){
        View tv=null; for (int i=0;i<g.getChildCount();i++) if (pkg.equals(g.getChildAt(i).getTag())) { tv=g.getChildAt(i); break; }
        if (tv==null || !tv.isFocusable()){ if (after!=null) after.run(); return; }
        final View t=tv;
        t.setFocusable(false);
        t.animate().alpha(0f).scaleX(0.85f).scaleY(0.85f).setDuration(250).setInterpolator(new android.view.animation.AccelerateInterpolator()).withEndAction(()->{ if (after!=null) after.run(); }).start();
    }
    boolean systemApp(String pkg){
        try { int f=this.a.pm.getApplicationInfo(pkg,0).flags; return (f&(ApplicationInfo.FLAG_SYSTEM|ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))!=0; }
        catch (Exception e){ return true; }
    }
    static final int[] MENU_ICONS={R.drawable.ic_menu_edit,R.drawable.ic_menu_stop,R.drawable.ic_menu_info,R.drawable.ic_menu_delete};
    void pressHold(View v){
        this.a.pressHeld=true;
        v.animate().cancel();
        v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(ViewConfiguration.getLongPressTimeout()).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
    }
    void pressUp(View v){
        this.a.pressHeld=false;
        v.animate().cancel();
        v.animate().scaleX(this.a.APP_FOCUS).scaleY(this.a.APP_FOCUS).setDuration(180).setInterpolator(this.a.dragIp).start();
    }
    void pressPop(View v){
        this.a.pressHeld=false;
        if (v==null) return;
        this.a.menuPop=v;
        tileSpring(v, this.a.APP_HOLD, 1f);
    }
    void pressSettle(View v){
        if (v==null) return;
        this.a.menuPop=v;
        tileSpring(v, this.a.APP_FOCUS, 1f);
    }
    void tileSpring(View v, float sc, float glowTo){
        android.view.animation.Interpolator ip=new android.view.animation.OvershootInterpolator(1.3f);
        v.animate().cancel();
        v.setTranslationZ(sc>1f?this.a.p(8):0);
        v.animate().scaleX(sc).scaleY(sc).setDuration(420).setInterpolator(ip).start();
        if (v instanceof GlowFrame){
            GlowFrame g=(GlowFrame)v;
            android.animation.ValueAnimator va=android.animation.ValueAnimator.ofFloat(g.glow, glowTo);
            va.setDuration(420); va.setInterpolator(ip);
            va.addUpdateListener(an->{ g.glow=Math.max(0f, Math.min(1f,(Float)an.getAnimatedValue())); g.syncHalo(); });
            va.start();
        }
    }
    void appMenu(View anchor, String[] app, int idx){
        if (this.a.editMode || this.a.appMenu!=null) return;
        if (this.a.focus!=null) this.a.focus.remember();
        this.a.menuOwner=anchor;
        pressPop(anchor);
        LinearLayout p=new LinearLayout(this.a); p.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg=new GradientDrawable(); bg.setColor(Color.argb(235,28,28,32)); bg.setCornerRadius(this.a.p(14)); bg.setStroke(Math.max(1,this.a.p(1)), Color.argb(51,255,255,255));
        p.setBackground(bg); p.setPadding(this.a.p(8),this.a.p(8),this.a.p(8),this.a.p(8)); p.setElevation(this.a.p(18));
        String[] labs={"Edit Home Screen","Force Stop","App Info","Uninstall"};
        boolean sys=systemApp(app[0]);
        View first=null;
        java.util.ArrayList<View> rows=new java.util.ArrayList<>();
        for (int n=0;n<labs.length;n++){
            final int id=n;
            boolean off=id==3 && sys;
            LinearLayout row=new LinearLayout(this.a); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(this.a.p(10),0,this.a.p(10),0);
            row.setFocusable(!off); row.setClickable(!off);
            final ImageView li=new ImageView(this.a); li.setImageResource(MENU_ICONS[n]); li.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int idle=off?Color.argb(80,255,255,255):(id==3?Color.rgb(224,112,112):Color.WHITE);
            li.setColorFilter(idle, PorterDuff.Mode.SRC_IN);
            row.addView(li, new LinearLayout.LayoutParams(this.a.p(18),this.a.p(18)));
            final TextView t=this.a.text(labs[n], 12, idle, "sans-serif");
            LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-2,-2); tp.leftMargin=this.a.p(10);
            row.addView(t, tp);
            row.setOnFocusChangeListener((v,f)->{
                GradientDrawable pill=new GradientDrawable(); pill.setCornerRadius(this.a.p(8));
                pill.setColor(f?Color.argb(240,255,255,255):Color.TRANSPARENT);
                row.setBackground(pill);
                int fg=off?Color.argb(80,255,255,255):(f?Color.rgb(22,22,30):(id==3?Color.rgb(224,112,112):Color.WHITE));
                t.setTextColor(fg); li.setColorFilter(fg, PorterDuff.Mode.SRC_IN);
            });
            if (!off) row.setOnClickListener(v->menuAct(id, app, idx));
            row.setOnKeyListener((v,k,e)->{ if (k==KeyEvent.KEYCODE_BACK && e.getAction()==KeyEvent.ACTION_DOWN){ closeMenu(); return true; } return false; });
            p.addView(row, new LinearLayout.LayoutParams(this.a.p(200), this.a.p(36)));
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
        int sw=this.a.root.getWidth()>0?this.a.root.getWidth():this.a.p(1024), sh=this.a.root.getHeight()>0?this.a.root.getHeight():this.a.p(576);
        int x=loc[0]+anchor.getWidth()+this.a.p(10), y=loc[1];
        if (x+mw>sw-this.a.p(8)) x=loc[0]-mw-this.a.p(10);
        if (x<this.a.p(8)) x=this.a.p(8);
        if (y+mh>sh-this.a.p(8)) y=sh-mh-this.a.p(8);
        if (y<this.a.p(8)) y=this.a.p(8);
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(mw, mh);
        lp.leftMargin=x; lp.topMargin=y;
        p.setPivotX(x>=loc[0]?0:mw);
        p.setPivotY(Math.max(0, Math.min(mh, loc[1]+anchor.getHeight()/2f-y)));
        p.setAlpha(0f); p.setScaleX(0.3f); p.setScaleY(0.3f);
        this.a.appMenu=p; this.a.root.addView(p, lp);
        lockBehind(true);
        p.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(new android.view.animation.OvershootInterpolator(0.9f)).start();
        final View f0=first;
        this.a.h.post(()->{ if (this.a.appMenu!=null && f0!=null) f0.requestFocus(); });
    }
    boolean dimSlideKey(KeyEvent e){
        int k=e.getKeyCode();
        boolean ok=k==KeyEvent.KEYCODE_DPAD_CENTER||k==KeyEvent.KEYCODE_ENTER||k==KeyEvent.KEYCODE_NUMPAD_ENTER;
        if (ok){
            if (e.getAction()==KeyEvent.ACTION_UP){ this.a.dimSlideIgnore=false; return true; }
            if (e.getAction()!=KeyEvent.ACTION_DOWN) return true;
            if (this.a.dimSlideIgnore && e.getRepeatCount()>0) return true;
            this.a.closeDimSlide(); return true;
        }
        if (e.getAction()!=KeyEvent.ACTION_DOWN) return true;
        if (k==KeyEvent.KEYCODE_BACK){ this.a.closeDimSlide(); return true; }
        if (k==KeyEvent.KEYCODE_DPAD_LEFT){ this.a.dimSlideBy(-1); return true; }
        if (k==KeyEvent.KEYCODE_DPAD_RIGHT){ this.a.dimSlideBy(1); return true; }
        if (k==KeyEvent.KEYCODE_DPAD_UP||k==KeyEvent.KEYCODE_DPAD_DOWN){ this.a.closeDimSlide(); return this.a.dispatchKeyEvent(e); }
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
        return this.a.superDispatchKey(e);
    }
    void menuMove(boolean down){
        if (this.a.appMenu==null) return;
        java.util.ArrayList<View> items=new java.util.ArrayList<>();
        for (int i=0;i<this.a.appMenu.getChildCount();i++){ View c=this.a.appMenu.getChildAt(i); if (c.isFocusable()) items.add(c); }
        if (items.isEmpty()) return;
        View f=this.a.getCurrentFocus(); int ix=items.indexOf(f);
        if (ix<0){ items.get(0).requestFocus(); return; }
        int n=ix+(down?1:-1);
        if (n>=0 && n<items.size()) items.get(n).requestFocus();
    }
    void lockBehind(boolean on){
        if (this.a.root==null) return;
        for (int i=0;i<this.a.root.getChildCount();i++){
            View c=this.a.root.getChildAt(i);
            if (c==this.a.appMenu) continue;
            if (c instanceof ViewGroup){
                ((ViewGroup)c).setDescendantFocusability(on?ViewGroup.FOCUS_BLOCK_DESCENDANTS:ViewGroup.FOCUS_AFTER_DESCENDANTS);
                if (c==this.a.heroG){ c.setFocusable(false); if (this.a.heroBtn!=null) this.a.heroBtn.setFocusable(!on); }
            }
            else if (c instanceof PillView){
                if (on) c.setFocusable(false);
                else if (c==this.a.gear) c.setFocusable(!this.a.drawerOpen);
                else if (c==this.a.searchV) c.setFocusable(!this.a.drawerOpen && !this.a.dimSlide && !this.a.searchOpen);
                else if (c==this.a.speakV||c==this.a.typeV) c.setFocusable(!this.a.drawerOpen && this.a.searchOpen && !this.a.dimSlide);
                else if (c==this.a.dimBar) c.setFocusable(false);
                else c.setFocusable(true);
            }
        }
    }
    void menuAct(int id, String[] app, int idx){
        if (id==0){ enterEdit(idx); return; }
        closeMenu();
        if (id==1 || id==2){ try { this.a.startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:"+app[0])).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception e){} return; }
        if (id==3 && !systemApp(app[0])){ try { this.a.startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:"+app[0])).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception e){} }
    }
    void closeMenu(){ closeMenu(true); }
    void closeMenu(boolean back){
        if (this.a.dimSlide) this.a.closeDimSlide();
        if (this.a.appMenu==null) return;
        lockBehind(false);
        View owner=this.a.menuOwner; this.a.menuOwner=null;
        final View m=this.a.appMenu; this.a.appMenu=null;
        m.animate().cancel();
        m.animate().alpha(0f).scaleX(0.3f).scaleY(0.3f).setDuration(140).setInterpolator(new android.view.animation.AccelerateInterpolator()).withEndAction(()->{ if (m.getParent()!=null) this.a.root.removeView(m); }).start();
        if (owner!=null) pressSettle(owner);
        if (back && this.a.focus!=null) this.a.focus.restore();
    }
}
