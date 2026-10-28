package com.chris.shieldhome;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

class HeroDots extends View {
        static final float DOT_D=6f, DOT_GAP=8f, PILL_PAD_X=24f; // v76: dots 5->6, gap 7->8
        static final float PILL_H=24f, PILL_CENTER_Y=284f; // v77: pill 28->24 tall, centre kept at 270+28/2
        static final int PILL_FILL_A=70; // v77: was 88
        static final long DOTS_ANIM_MS=240; static final float DOTS_SLIDE_DP=8f; // show/hide: fade while sliding up from / down by DOTS_SLIDE_DP
        /** Active dot stretches to ACTIVE_W and fills left-to-right over HERO_ROTATE_MS; MOVE_MS = stretch hand-off to the next dot. */
        static final float ACTIVE_W=22f; static final long MOVE_MS=250; static final int DOT_IDLE_A=89;
        static final android.view.animation.Interpolator MOVE_EASE=new android.view.animation.PathInterpolator(0.16f,1f,0.3f,1f);
        final MainActivity activity; int n, cur, prev; float mix=1f; android.animation.ValueAnimator an;
        long progStart; float pausedAt=-1f; // pausedAt>=0: fill frozen at that fraction
        final Paint dotsPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
        HeroDots(MainActivity activity){ super(activity); this.activity=activity; setAlpha(0f); setVisibility(GONE); setFocusable(false); }
        float rowW(){ float d=activity.p(DOT_D); return n<=1?d:(n-1)*(d+activity.p(DOT_GAP))+activity.p(ACTIVE_W); }
        int pillW(){ return Math.round(rowW()+activity.p(PILL_PAD_X)); }
        /** Width of dot i: the stretch moves from prev to cur as mix goes 0..1, so the row width stays constant. */
        float dotW(int i){ float d=activity.p(DOT_D), x=activity.p(ACTIVE_W)-d; return d+x*(i==cur?mix:0)+x*(i==prev&&prev!=cur?1-mix:0); }
        void startProgress(){ progStart=android.os.SystemClock.uptimeMillis(); pausedAt=-1f; invalidate(); }
        void pauseProgress(){ if (pausedAt<0) pausedAt=progress(); invalidate(); }
        float progress(){ return pausedAt>=0?pausedAt:Math.min(1f,(android.os.SystemClock.uptimeMillis()-progStart)/(float)MainActivity.HERO_ROTATE_MS); }
        void stop(){ animate().cancel(); if (an!=null) an.cancel(); }
        @Override protected void onMeasure(int wms, int hms){
            int h=MeasureSpec.getMode(hms)==MeasureSpec.EXACTLY?MeasureSpec.getSize(hms):activity.p(PILL_H);
            setMeasuredDimension(pillW(), h);
        }
        void bind(int nn, int i, boolean anim){
            int nn2=Math.max(0,nn); i=nn2==0?0:Math.max(0, Math.min(i,nn2-1));
            if (nn2!=n) requestLayout();
            n=nn2;
            if (an!=null) an.cancel();
            if (!anim || getAlpha()<0.05f || n<=1 || i==cur){ prev=cur=i; mix=1f; invalidate(); return; }
            prev=cur; cur=i; mix=0f;
            an=android.animation.ValueAnimator.ofFloat(0,1); an.setDuration(MOVE_MS); an.setInterpolator(MOVE_EASE);
            an.addUpdateListener(a->{ mix=(Float)a.getAnimatedValue(); invalidate(); });
            an.start();
        }
        @Override protected void onDraw(Canvas c){
            if (n<=1) return;
            float d=activity.p(DOT_D), gap=activity.p(DOT_GAP), hh=getHeight(), rr=hh/2, x=(getWidth()-rowW())/2f, cy=hh/2f, r=d/2;
            Paint pt=dotsPaint;
            pt.setColor(Color.argb(PILL_FILL_A,60,58,75));
            c.drawRoundRect(0,0,getWidth(),hh,rr,rr,pt);
            for (int i=0;i<n;i++){
                float w=dotW(i);
                pt.setColor(Color.argb(DOT_IDLE_A,255,255,255));
                c.drawRoundRect(x, cy-r, x+w, cy+r, r, r, pt);
                if (i==cur){ // progress fill: white from the left, at least a dot wide so it reads as the active dot
                    float fw=d+(w-d)*progress();
                    pt.setColor(Color.WHITE); c.drawRoundRect(x, cy-r, x+fw, cy+r, r, r, pt);
                }
                x+=w+gap;
            }
            if (pausedAt<0 && getVisibility()==VISIBLE && progress()<1f) postInvalidateOnAnimation();
        }
    }
