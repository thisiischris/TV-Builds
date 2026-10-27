package com.chris.shieldhome;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

class SegBar extends LinearLayout {
        final MainActivity activity; TextView play, live; float hx, hw, hs=1f; android.animation.ValueAnimator ha, pa;
        final Paint trackPaint=new Paint(Paint.ANTI_ALIAS_FLAG), highlightPaint=new Paint(Paint.ANTI_ALIAS_FLAG), shadowPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
        SegBar(MainActivity activity){
            super(activity); this.activity=activity;
            setOrientation(HORIZONTAL); setGravity(Gravity.CENTER_VERTICAL);
            setPadding(0,0,0,0); setWillNotDraw(false); setClipChildren(false); setClipToPadding(false);
            play=mk("Play Next"); live=mk("Live TV");
            int sw=Math.max(Math.round(play.getPaint().measureText("Play Next")), Math.round(live.getPaint().measureText("Live TV")))+activity.p(36);
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
            TextView t=activity.text(s,12,Color.rgb(232,232,240),"sans-serif");
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
            boolean ch=t!=activity.tab;
            if (ch){ activity.tab=t; activity.refreshCards(); activity.resetCardRow(); }
            slide(hw>0); color();
        }
        void color(){
            int d=Color.rgb(22,22,30), l=Color.rgb(232,232,240);
            play.setTextColor(activity.tab==0?d:l); live.setTextColor(activity.tab==1?d:l);
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
            TextView s=(activity.tab==0 && play.getVisibility()==VISIBLE)?play:live;
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
            Paint pt=trackPaint;
            pt.setColor(Color.argb(140,60,58,75));
            c.drawRoundRect(0,0,w,hh,rr,rr,pt);
            pt.setStyle(Paint.Style.FILL);
            if (hw>0){
                float cx=hx+hw/2, cy=hh/2, sw=hw*hs, sh=hh*hs, sr=sh/2;
                Bitmap sb=activity.shadowBmp(Math.max(1,(int)sw), Math.max(1,(int)sh), sr);
                if (sb!=null){ shadowPaint.setAlpha(Math.max(0,Math.min(130,(int)(130*(hs-1f)/0.04f)))); int pad=activity.p(48); c.drawBitmap(sb, cx-sw/2-pad, cy-sh/2-pad+activity.p(19), shadowPaint); }
                highlightPaint.setColor(Color.rgb(229,227,228));
                c.drawRoundRect(cx-sw/2, cy-sh/2, cx+sw/2, cy+sh/2, sr, sr, highlightPaint);
            }
            super.dispatchDraw(c);
        }
    }
