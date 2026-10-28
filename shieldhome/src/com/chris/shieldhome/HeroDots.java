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
        final MainActivity activity; int n, cur; float ax; android.animation.ValueAnimator an;
        final Paint dotsPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
        HeroDots(MainActivity activity){ super(activity); this.activity=activity; setAlpha(0f); setVisibility(GONE); setFocusable(false); }
        float slot(int i){ return i*(activity.p(DOT_D)+activity.p(DOT_GAP)); }
        float rowW(){ float d=activity.p(DOT_D); return n<=1?d:(n-1)*(d+activity.p(DOT_GAP))+d; }
        void snap(int i){ cur=i; ax=slot(i); invalidate(); }
        int pillW(){ return Math.round(rowW()+activity.p(PILL_PAD_X)); }
        @Override protected void onMeasure(int wms, int hms){
            int h=MeasureSpec.getMode(hms)==MeasureSpec.EXACTLY?MeasureSpec.getSize(hms):activity.p(PILL_H);
            setMeasuredDimension(pillW(), h);
        }
        void bind(int nn, int i, boolean anim){
            int nn2=Math.max(0,nn); i=nn2==0?0:Math.max(0, Math.min(i,nn2-1));
            if (nn2!=n) requestLayout();
            n=nn2;
            boolean wrap=n>2 && ((cur==n-1 && i==0) || (cur==0 && i==n-1));
            if (!anim || getAlpha()<0.05f || n<=1 || wrap){ snap(i); return; }
            if (an!=null) an.cancel();
            float to=slot(i), x0=ax; cur=i;
            an=android.animation.ValueAnimator.ofFloat(0,1); an.setDuration(250);
            an.setInterpolator(new android.view.animation.PathInterpolator(0.16f,1f,0.3f,1f));
            an.addUpdateListener(a->{ float u=(Float)a.getAnimatedValue(); ax=x0+(to-x0)*u; invalidate(); });
            an.start();
        }
        @Override protected void onDraw(Canvas c){
            if (n<=1) return;
            float d=activity.p(DOT_D), row=rowW(), hh=getHeight(), rr=hh/2, x0=(getWidth()-row)/2f, cy=hh/2f;
            Paint pt=dotsPaint;
            pt.setColor(Color.argb(PILL_FILL_A,60,58,75));
            c.drawRoundRect(0,0,getWidth(),hh,rr,rr,pt);
            pt.setColor(Color.argb(89,255,255,255));
            for (int i=0;i<n;i++) c.drawCircle(x0+slot(i)+d/2, cy, d/2, pt);
            pt.setColor(Color.WHITE);
            c.drawCircle(x0+ax+d/2, cy, d/2, pt);
        }
    }
