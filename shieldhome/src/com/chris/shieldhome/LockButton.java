package com.chris.shieldhome;

import android.animation.ValueAnimator;
import android.graphics.*;
import android.view.View;
import android.view.animation.OvershootInterpolator;

/** v84: wallpaper controls — translucent circle (search-pill fill) with either an outline padlock (shackle drops/rises with a bouncy morph)
 *  or a prev/next chevron (GLYPH_PREV/GLYPH_NEXT; not focusable, flashes a brighter fill on press). */
class LockButton extends View {
    static final float BODY_W=12f, BODY_H=9f, BODY_R=2f, SHACKLE_W=7.5f, SHACKLE_H=6f, SHACKLE_LIFT=3.2f, STROKE=1.4f, ICON_DY=1.5f;
    static final float CHEVRON_W=4f, CHEVRON_H=8f;
    static final int GLYPH_LOCK=0, GLYPH_PREV=-1, GLYPH_NEXT=1;
    static final long MORPH_MS=420; static final OvershootInterpolator MORPH_EASE=new OvershootInterpolator(3f);
    static final int FILL=Color.argb(140,60,58,75), FILL_FOCUS=Color.argb(185,102,100,119), EDGE=Color.argb(40,255,255,255), FG=Color.rgb(232,232,240), FILL_PRESS=Color.argb(215,150,148,170);
    final MainActivity a; final Paint pt=new Paint(Paint.ANTI_ALIAS_FLAG); final Path shackle=new Path(); final RectF rf=new RectF(); final int glyph; boolean flash;
    /** 0 = open (shackle raised), 1 = closed; overshoots past the ends during the morph. */
    float m; ValueAnimator morph;
    LockButton(MainActivity a, int glyph){ super(a); this.a=a; this.glyph=glyph; setFocusable(glyph==GLYPH_LOCK); setClickable(glyph==GLYPH_LOCK); pt.setStrokeCap(Paint.Cap.ROUND); pt.setStrokeJoin(Paint.Join.ROUND); }
    void setFlash(boolean f){ flash=f; invalidate(); }

    void setLocked(boolean locked, boolean animate){
        if (morph!=null) morph.cancel();
        float to=locked?1f:0f;
        if (!animate){ m=to; invalidate(); return; }
        morph=ValueAnimator.ofFloat(m, to); morph.setDuration(MORPH_MS); morph.setInterpolator(MORPH_EASE);
        morph.addUpdateListener(v->{ m=(Float)v.getAnimatedValue(); invalidate(); });
        morph.start();
    }

    @Override protected void onDraw(Canvas c){
        float w=getWidth(), h=getHeight(), cx=w/2f, cy=h/2f, r=Math.min(w,h)/2f;
        pt.setStyle(Paint.Style.FILL); pt.setColor(flash?FILL_PRESS:isFocused()?FILL_FOCUS:FILL); c.drawCircle(cx, cy, r, pt);
        pt.setStyle(Paint.Style.STROKE); pt.setStrokeWidth(1); pt.setColor(EDGE); c.drawCircle(cx, cy, r-.5f, pt);
        pt.setColor(FG); pt.setStrokeWidth(a.p(STROKE));
        if (glyph!=GLYPH_LOCK){ // chevron pointing in the glyph direction, optically centred
            float cw=a.p(CHEVRON_W)*glyph, ch=a.p(CHEVRON_H)/2f, x0=cx-cw/2f;
            shackle.rewind(); shackle.moveTo(x0, cy-ch); shackle.lineTo(x0+cw, cy); shackle.lineTo(x0, cy+ch); c.drawPath(shackle, pt); return;
        }
        float bw=a.p(BODY_W), bh=a.p(BODY_H), top=cy-bh/2f+a.p(ICON_DY);
        rf.set(cx-bw/2f, top, cx+bw/2f, top+bh); c.drawRoundRect(rf, a.p(BODY_R), a.p(BODY_R), pt);
        float sw=a.p(SHACKLE_W), sh=a.p(SHACKLE_H), lift=a.p(SHACKLE_LIFT)*(1f-m), sx=cx-sw/2f, base=top-lift;
        shackle.rewind();
        shackle.moveTo(sx, top); shackle.lineTo(sx, base-sh+sw/2f);              // left leg stays in the body
        shackle.arcTo(sx, base-sh, sx+sw, base-sh+sw, 180, 180, false);
        shackle.lineTo(sx+sw, base);                                            // right leg: meets the body when closed, `lift` above it while open
        c.drawPath(shackle, pt);
    }
}
