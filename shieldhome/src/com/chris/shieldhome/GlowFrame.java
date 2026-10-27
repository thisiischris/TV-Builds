package com.chris.shieldhome;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

class GlowFrame extends FrameLayout {
        final MainActivity activity; FrameLayout inner; View badgeV; float radius; Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG); float glow=0, fade=1; Bitmap shBm; int shPad; android.animation.ValueAnimator glowAnimator;
        GlowFrame(MainActivity activity, Context c, FrameLayout in, float r){ super(c); this.activity=activity; inner=in; radius=r; setClipChildren(false); setClipToPadding(false); setClipToOutline(false); setWillNotDraw(false); setLayerType(View.LAYER_TYPE_NONE, null); forceHasOverlappingRendering(false);
            shPad=activity.p(48);
            addView(in, new FrameLayout.LayoutParams(-1,-1));
            in.setLayerType(View.LAYER_TYPE_HARDWARE, null); }
        void warm(int w, int h){ if (w<=0||h<=0) return; shBm=activity.shadowBmp(w,h,radius); }
        void syncHalo(){ invalidate(); }
        @Override protected void onSizeChanged(int w, int h, int ow, int oh){ super.onSizeChanged(w,h,ow,oh); shBm=null; if (w>0&&h>0) warm(w,h); }
        @Override public void draw(Canvas c){
            float g=glow*fade;
            if (g>0.02f){ if (shBm==null) shBm=activity.shadowBmp(getWidth(),getHeight(),radius); if (shBm!=null){ sp.setAlpha((int)(160*g)); c.drawBitmap(shBm,-shPad,-shPad+activity.p(19),sp); } }
            super.draw(c);
            if (badgeV!=null && badgeV.getAlpha()>0.01f){ c.saveLayerAlpha(badgeV.getLeft(), badgeV.getTop(), badgeV.getRight(), badgeV.getBottom(), Math.round(255*badgeV.getAlpha())); c.translate(badgeV.getLeft(), badgeV.getTop()); badgeV.draw(c); c.restore(); }
        }
    }
