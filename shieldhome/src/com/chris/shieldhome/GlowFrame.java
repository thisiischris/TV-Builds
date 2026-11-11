package com.chris.shieldhome;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

class GlowFrame extends FrameLayout {
        /** v87: colour halo on focus, tinted from the tile's own content: peak alpha, sample size for the colour read, vertical nudge (units). */
        static final int HALO_ALPHA=105, SAMPLE_W=32, SAMPLE_H=18; static final float HALO_DY=3f;
        final MainActivity activity; FrameLayout inner; float radius; Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG), hp = new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG); float glow=0, fade=1; Bitmap shBm, haloBm; int shPad, tint=Color.WHITE; android.animation.ValueAnimator glowAnimator;
        Bitmap sample; Canvas sampleC;
        GlowFrame(MainActivity activity, Context c, FrameLayout in, float r){ super(c); this.activity=activity; inner=in; radius=r; setClipChildren(false); setClipToPadding(false); setClipToOutline(false); setWillNotDraw(false); setLayerType(View.LAYER_TYPE_NONE, null); forceHasOverlappingRendering(false);
            shPad=activity.p(48);
            addView(in, new FrameLayout.LayoutParams(-1,-1));
            in.setLayerType(View.LAYER_TYPE_HARDWARE, null); }
        void warm(int w, int h){ if (w<=0||h<=0) return; shBm=activity.shadowBmp(w,h,radius); haloBm=activity.haloBmp(w,h,radius); }
        void syncHalo(){ invalidate(); }
        /** Re-read the tile's colour (called as focus arrives, so video cards pick up their current thumbnail). */
        void sampleTint(){
            int w=inner.getWidth(), h=inner.getHeight(); if (w<=0||h<=0) return;
            if (sample==null){ sample=Bitmap.createBitmap(SAMPLE_W, SAMPLE_H, Bitmap.Config.ARGB_8888); sampleC=new Canvas(sample); }
            sample.eraseColor(Color.TRANSPARENT);
            int save=sampleC.save();
            try { sampleC.scale(SAMPLE_W/(float)w, SAMPLE_H/(float)h); inner.draw(sampleC); }
            catch (RuntimeException e){ return; } // e.g. a hardware bitmap in the tile: keep the previous tint
            finally { sampleC.restoreToCount(save); }
            tint=activity.glowColor(sample);
        }
        @Override protected void onSizeChanged(int w, int h, int ow, int oh){ super.onSizeChanged(w,h,ow,oh); shBm=null; haloBm=null; if (w>0&&h>0) warm(w,h); }
        @Override public void draw(Canvas c){
            float g=glow*fade;
            if (g>0.02f){
                if (shBm==null) shBm=activity.shadowBmp(getWidth(),getHeight(),radius);
                if (shBm!=null){ sp.setAlpha((int)(160*g)); c.drawBitmap(shBm,-shPad,-shPad+activity.p(19),sp); }
                if (haloBm==null) haloBm=activity.haloBmp(getWidth(),getHeight(),radius);
                if (haloBm!=null){ hp.setColor(tint); hp.setAlpha((int)(HALO_ALPHA*g)); /* ALPHA_8 mask: drawn in the paint colour */ c.drawBitmap(haloBm,-shPad,-shPad+activity.p(HALO_DY),hp); }
            }
            super.draw(c);
        }
    }
