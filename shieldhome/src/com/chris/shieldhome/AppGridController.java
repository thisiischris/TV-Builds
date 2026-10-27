package com.chris.shieldhome;

import android.view.View;

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
        if (f!=null && this.a.homeGrid!=null && f.getParent()==this.a.homeGrid) r=Math.max(0, (this.a.editMode?this.a.tileIx(f):this.a.homeGrid.indexOfChild(f))/6);
        final boolean open=this.a.drawerOpen;
        float y = open ? -(this.a.p(this.a.APP_GRID_Y-17)+Math.max(0, r-4)*this.a.rowH()) : 0;
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
        if (this.a.editMode) this.a.stackEditDim();
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
}
