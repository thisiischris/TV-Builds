package com.chris.shieldhome;

import android.content.Intent;
import android.graphics.Color;
import android.view.View;
import android.widget.FrameLayout;

class SearchDimController {
    final MainActivity a;
    SearchDimController(MainActivity a) { this.a=a; }
    /** Dim slider: fill eases toward its target each frame (fraction of the remaining distance per 16 ms frame), snapping within DIM_FILL_EPS.
     *  The service/overlay apply is throttled to one per DIM_APPLY_MS so fast key repeats coalesce. */
    static final float DIM_FILL_RATE=0.22f, DIM_FILL_EPS=0.0015f, FRAME_MS=16.67f; static final long DIM_APPLY_MS=60;
    float dimTarget; long lastFrameNs; boolean fillRunning, applyPending;
    final Runnable fillFrame=new Runnable(){ public void run(){
        long now=System.nanoTime(); float dt=lastFrameNs==0?1f:Math.min(4f,(now-lastFrameNs)/1e6f/FRAME_MS); lastFrameNs=now;
        float d=dimTarget-a.dimFill;
        a.dimFill = Math.abs(d)<DIM_FILL_EPS ? dimTarget : a.dimFill+d*(1f-(float)Math.pow(1f-DIM_FILL_RATE, dt));
        if (a.gear!=null) a.gear.invalidate(); if (a.dimBar!=null) a.dimBar.invalidate();
        if (a.dimFill!=dimTarget) a.root.postOnAnimation(this); else fillRunning=false;
    }};
    final Runnable applyNow=new Runnable(){ public void run(){ applyPending=false; a.applyDim(); }};
    /** Drops pending frame and apply callbacks (activity teardown). */
    void stopDimCallbacks(){ if (a.root!=null) a.root.removeCallbacks(fillFrame); a.h.removeCallbacks(applyNow); fillRunning=false; applyPending=false; }
    void openAssistant(){
        Intent[] tries = {
            new Intent(Intent.ACTION_ASSIST).setPackage("com.google.android.katniss"),
            new Intent("android.speech.action.WEB_SEARCH").setPackage("com.google.android.katniss"),
            new Intent(Intent.ACTION_SEARCH_LONG_PRESS).setPackage("com.google.android.katniss")
        };
        for (Intent i: tries){ try { if (i.resolveActivity(a.pm)!=null){ a.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return; } } catch (Exception e) {} }
    }
    void expandSearch(){
        if (a.searchOpen || a.dimSlide || a.searchTyping || a.searchSplit==null) return;
        a.searchOpen=true;
        a.searchV.animate().cancel();
        a.searchV.setFocusable(false); a.searchV.setAlpha(1f); a.searchV.setVisibility(View.INVISIBLE);
        a.searchSplit.setVisibility(View.VISIBLE); a.searchSplit.setAlpha(1f);
        a.speakV.setFocusable(true); a.typeV.setFocusable(true);
        float mid=a.p(28);
        android.view.animation.Interpolator ip=new android.view.animation.OvershootInterpolator(1.2f);
        for (PillView pv: new PillView[]{a.speakV,a.typeV}){ pv.animate().cancel(); pv.setScaleX(0.86f); pv.setScaleY(0.86f); pv.setAlpha(0.35f); }
        a.speakV.setTranslationX(mid); a.typeV.setTranslationX(-mid);
        a.speakV.animate().translationX(0).scaleX(1f).scaleY(1f).alpha(1f).setDuration(300).setInterpolator(ip).start();
        a.typeV.animate().translationX(0).scaleX(1f).scaleY(1f).alpha(1f).setDuration(300).setInterpolator(ip).start();
        a.speakV.requestFocus();
    }
    void maybeCollapseSearch(){
        if (a.searchTyping || !a.searchOpen || a.dimSlide) return;
        if (a.speakV!=null && (a.speakV.isFocused()||a.typeV.isFocused())) return;
        a.collapseSearch();
    }
    void showKb(){
        android.view.inputmethod.InputMethodManager imm=(android.view.inputmethod.InputMethodManager)a.getSystemService(a.INPUT_METHOD_SERVICE);
        if (imm==null || a.searchField==null) return;
        a.searchField.post(()->{
            a.searchField.requestFocus();
            if (!imm.showSoftInput(a.searchField, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT))
                imm.showSoftInput(a.searchField, android.view.inputmethod.InputMethodManager.SHOW_FORCED);
        });
    }
    void hideKb(){
        android.view.inputmethod.InputMethodManager imm=(android.view.inputmethod.InputMethodManager)a.getSystemService(a.INPUT_METHOD_SERVICE);
        if (imm==null || a.searchField==null) return;
        imm.hideSoftInputFromWindow(a.searchField.getWindowToken(), 0);
    }
    void launchSearch(String q){
        Intent[] tries = {
            new Intent("android.search.action.GLOBAL_SEARCH").setPackage("com.google.android.katniss").putExtra(android.app.SearchManager.QUERY, q),
            new Intent("android.speech.action.WEB_SEARCH").setPackage("com.google.android.katniss").putExtra(android.app.SearchManager.QUERY, q),
            new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://assistant.google.com/search?q="+android.net.Uri.encode(q))).setPackage("com.google.android.katniss")
        };
        for (Intent i: tries){ try { if (i.resolveActivity(a.pm)!=null){ a.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); a.closeSearchField(); return; } } catch (Exception e) {} }
    }
    void openSearchField(){
        if (a.searchTyping || a.dimSlide || a.searchField==null) return;
        a.searchTyping=true; a.searchOpen=false;
        if (a.speakV!=null){ a.speakV.animate().cancel(); a.typeV.animate().cancel(); a.speakV.setFocusable(false); a.typeV.setFocusable(false); }
        if (a.searchSplit!=null) a.searchSplit.setVisibility(View.GONE);
        if (a.searchV!=null){ a.searchV.setVisibility(View.INVISIBLE); a.searchV.setFocusable(false); }
        a.searchField.setText(""); a.searchField.setVisibility(View.VISIBLE); a.searchField.bringToFront();
        a.searchField.requestFocus(); a.showKb();
    }
    void closeSearchField(){
        if (!a.searchTyping) return;
        a.searchTyping=false; a.hideKb();
        if (a.searchField!=null){ a.searchField.setVisibility(View.GONE); a.searchField.setText(""); }
        if (!a.dimSlide && a.searchV!=null){ a.searchV.setVisibility(View.VISIBLE); a.searchV.setFocusable(!a.drawerOpen); }
    }
    void collapseSearch(){
        a.searchOpen=false;
        if (a.speakV!=null){ a.speakV.setFocusable(false); a.typeV.setFocusable(false); }
        if (a.searchSplit==null || a.searchSplit.getVisibility()!=View.VISIBLE){
            if (!a.dimSlide && a.searchV!=null){ a.searchV.setVisibility(View.VISIBLE); a.searchV.setAlpha(1f); a.searchV.setFocusable(!a.drawerOpen); }
            return;
        }
        float mid=a.p(28);
        android.view.animation.Interpolator ip=new android.view.animation.DecelerateInterpolator();
        a.speakV.animate().cancel(); a.typeV.animate().cancel();
        a.speakV.animate().translationX(mid).scaleX(0.86f).scaleY(0.86f).alpha(0f).setDuration(280).setInterpolator(ip).start();
        a.typeV.animate().translationX(-mid).scaleX(0.86f).scaleY(0.86f).alpha(0f).setDuration(280).setInterpolator(ip).withEndAction(()->{
            if (a.searchOpen) return;
            a.searchSplit.setVisibility(View.GONE);
            if (!a.dimSlide && a.searchV!=null){
                a.searchV.animate().cancel();
                a.searchV.setVisibility(View.VISIBLE); a.searchV.setFocusable(!a.drawerOpen);
                a.searchV.setAlpha(0f); a.searchV.animate().alpha(1f).setDuration(160).start();
            }
        }).start();
    }
    int dimPct(){ return Math.max(0, Math.min(90, a.prefs().getInt("dim_pct",0))); }
    void setDimPct(int pct){
        a.prefs().edit().putInt("dim_pct", Math.max(0, Math.min(90, pct))).apply();
        a.syncDimFill(); if (a.dimBar!=null) a.dimBar.invalidate(); // label updates now
        if (!applyPending){ applyPending=true; a.h.postDelayed(applyNow, DIM_APPLY_MS); }
    }
    /** Retargets the running per-frame ease; never restarts, so fast repeats stay continuous. */
    void syncDimFill(){
        dimTarget=a.dimPct()/100f;
        if (fillRunning || a.root==null) return;
        fillRunning=true; lastFrameNs=0; a.root.postOnAnimation(fillFrame);
    }
    void dimSlideBy(int dir){ a.setDimPct(a.dimPct()+dir*5); }
    void hideSearch(boolean hide){
        if (a.searchV==null) return;
        android.view.animation.Interpolator ease=new android.view.animation.DecelerateInterpolator();
        if (hide){
            if (a.searchTyping){ a.searchTyping=false; a.hideKb(); if (a.searchField!=null){ a.searchField.setVisibility(View.GONE); a.searchField.setText(""); } }
            a.searchOpen=false;
            if (a.speakV!=null){ a.speakV.animate().cancel(); a.typeV.animate().cancel(); a.speakV.setFocusable(false); a.typeV.setFocusable(false); }
            if (a.searchSplit!=null) a.searchSplit.setVisibility(View.GONE);
            a.searchV.setFocusable(false);
            a.searchV.animate().alpha(0f).setDuration(180).setInterpolator(ease).withEndAction(()->{ if (a.dimSlide) a.searchV.setVisibility(View.INVISIBLE); }).start();
        } else {
            a.searchV.setVisibility(View.VISIBLE); a.searchV.setFocusable(!a.drawerOpen);
            a.searchV.animate().alpha(1f).setDuration(180).setInterpolator(ease).start();
        }
    }
    void popDimBar(boolean on){
        if (a.dimBar==null) return;
        android.view.animation.Interpolator ease=new android.view.animation.DecelerateInterpolator();
        a.dimBar.animate().cancel();
        if (on){
            a.dimBar.setVisibility(View.VISIBLE);
            a.dimBar.setAlpha(0f); a.dimBar.setScaleX(0.88f); a.dimBar.setScaleY(0.88f);
            a.dimBar.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180).setInterpolator(ease).start();
        } else {
            a.dimBar.animate().alpha(0f).scaleX(0.88f).scaleY(0.88f).setDuration(140).setInterpolator(ease).withEndAction(()->{ if (!a.dimSlide) a.dimBar.setVisibility(View.GONE); }).start();
        }
    }
    void openDimSlide(){
        if (a.dimSlide || a.gear==null) return;
        a.dimSlide=true; a.dimSlideIgnore=true;
        a.gear.setNextFocusLeftId(a.gear.getId()); a.gear.setNextFocusRightId(a.gear.getId());
        a.hideSearch(true);
        a.popDimBar(true);
        a.gear.requestFocus();
    }
    void closeDimSlide(){
        if (!a.dimSlide) return;
        a.dimSlide=false; a.dimSlideIgnore=false;
        a.gear.setNextFocusLeftId(View.NO_ID); a.gear.setNextFocusRightId(View.NO_ID);
        a.popDimBar(false);
        a.hideSearch(false);
    }
    void applyDim(){
        int pct = a.dimPct(); a.syncDimFill();
        boolean overlay = android.provider.Settings.canDrawOverlays(a);
        Intent si = new Intent(a, DimService.class).putExtra("pct", pct);
        try { if (overlay){ if (pct>0) a.startForegroundService(si); else a.stopService(si); } } catch (Exception e){ android.util.Log.w("ShieldHome","dimsvc "+e); overlay=false; }
        if (a.inAppDim==null){ a.inAppDim = new View(a); a.inAppDim.setBackgroundColor(Color.BLACK); a.inAppDim.setClickable(false); a.inAppDim.setFocusable(false); a.root.addView(a.inAppDim, new FrameLayout.LayoutParams(-1,-1)); a.inAppDim.setTranslationZ(a.p(60)); }
        a.inAppDim.setAlpha(overlay?0f:pct/100f); a.inAppDim.setVisibility(!overlay && pct>0 ? View.VISIBLE : View.GONE);
        android.util.Log.i("ShieldHome","dim pct="+pct+" overlay="+overlay);
    }
}
