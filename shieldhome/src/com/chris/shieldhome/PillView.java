package com.chris.shieldhome;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

class PillView extends View {
        // Search pill: Assistant icon at the left inner padding (where the magnifier began), label SEARCH_GAP after it, same padding after the label.
        static final float SEARCH_PAD=14, SEARCH_ASST=15, SEARCH_GAP=4, LABEL_SIZE=12;
        final MainActivity activity; String label; int icon; boolean on; Drawable asst; Path track, fill; Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);
        PillView(MainActivity activity, Context c, String l, int ic, boolean o){ super(c); this.activity=activity; label=l; icon=ic; on=o; setFocusable(true); setClickable(true); }
        /** The search pill wraps its content: padding, Assistant icon, gap, measured label, equal padding. */
        @Override protected void onMeasure(int ws, int hs){
            if (icon!=3 || getLayoutParams()==null || getLayoutParams().width!=ViewGroup.LayoutParams.WRAP_CONTENT){ super.onMeasure(ws, hs); return; }
            labelPaint(); int w=Math.round(activity.p(SEARCH_PAD+SEARCH_ASST+SEARCH_GAP)+pt.measureText(label)+activity.p(SEARCH_PAD));
            setMeasuredDimension(w, getDefaultSize(getSuggestedMinimumHeight(), hs));
        }
        void labelPaint(){ pt.setTextSize(activity.p(this==activity.heroBtn?14:icon==2?12.5f:LABEL_SIZE)); pt.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL)); }
        @Override protected void onFocusChanged(boolean f, int d, Rect r){ super.onFocusChanged(f,d,r); if (this==activity.heroBtn) return; animate().scaleX(f?1.07f:1f).scaleY(f?1.07f:1f).setDuration(160).setInterpolator(activity.focusInterpolator).start(); setElevation(f?activity.p(4):0); invalidate(); }
        @Override protected void onDraw(Canvas c){
            float w=getWidth(), hh=getHeight(), r=this==activity.heroBtn?activity.p(14):hh/2;
            pt.setStyle(Paint.Style.FILL);
            boolean fo = isFocused();
            boolean bare = this==activity.gear && !fo;
            if (!bare){
                if (icon==2) pt.setColor(fo?Color.WHITE:Color.rgb(225,224,228));
                else if (on) pt.setColor(fo?Color.WHITE:Color.rgb(229,227,228));
                else if (icon==5 && this!=activity.dimBar) pt.setColor(fo?Color.argb(155,170,170,191):Color.argb(95,162,162,183));
                else pt.setColor(fo?Color.argb(185,102,100,119):Color.argb(140,60,58,75));
                c.drawRoundRect(0,0,w,hh,r,r,pt);
                if (icon==5 && !on && this!=activity.dimBar){ pt.setShader(new LinearGradient(0,0,0,hh,Color.argb(70,255,255,255),Color.argb(0,255,255,255),Shader.TileMode.CLAMP)); c.drawRoundRect(0,0,w,hh,r,r,pt); pt.setShader(null); }
                if (!on && icon!=2){ pt.setStyle(Paint.Style.STROKE); pt.setStrokeWidth(1); pt.setColor(Color.argb(40,255,255,255)); c.drawRoundRect(.5f,.5f,w-.5f,hh-.5f,r,r,pt); }
            }
            int fg = (icon==2||on) ? Color.rgb(22,22,30) : Color.rgb(232,232,240);
            if (this==activity.dimBar){
                pt.setStyle(Paint.Style.FILL); pt.setTextSize(activity.p(12)); pt.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                float labW=pt.measureText("90%"), pad=activity.p(12), gap=activity.p(8), th=activity.p(6), tw=w-pad*2-gap-labW, ty=(hh-th)/2, rr=th/2, t=Math.max(0, Math.min(1, activity.dimFill/0.9f));
                pt.setColor(Color.argb(50, Color.red(fg), Color.green(fg), Color.blue(fg)));
                c.drawRoundRect(pad, ty, pad+tw, ty+th, rr, rr, pt);
                if (t>0){ // full-height pill whose right end tracks t, intersected with the rounded track: shrinks smoothly to nothing at 0%
                    if (track==null){ track=new Path(); fill=new Path(); } // intersected as geometry (not clipPath) so both ends stay anti-aliased at low fill
                    track.rewind(); track.addRoundRect(pad, ty, pad+tw, ty+th, rr, rr, Path.Direction.CW);
                    fill.rewind(); fill.addRoundRect(pad+t*tw-tw, ty, pad+t*tw, ty+th, rr, rr, Path.Direction.CW);
                    fill.op(track, Path.Op.INTERSECT); pt.setColor(fg); c.drawPath(fill, pt); }
                pt.setColor(fg); pt.setTextAlign(Paint.Align.LEFT);
                Paint.FontMetrics fm=pt.getFontMetrics();
                c.drawText(activity.dimPct()+"%", pad+tw+gap, hh/2f-(fm.ascent+fm.descent)/2, pt);
                return;
            }
            pt.setColor(fg); pt.setStrokeWidth(activity.p(1.1f)); pt.setStyle(Paint.Style.STROKE); pt.setStrokeCap(Paint.Cap.ROUND);
            float cx = activity.p(icon==2?18:28) - (icon==2?activity.p(4):0), cy = hh/2;
            if (icon==3){ // Assistant mic
                if (asst==null) asst = getContext().getDrawable(R.drawable.assistant);
                int ax=activity.p(SEARCH_PAD); asst.setBounds(ax, (int)(cy-activity.p(SEARCH_ASST/2)), ax+activity.p(SEARCH_ASST), (int)(cy+activity.p(SEARCH_ASST/2))); asst.draw(c); }
            else if (icon==5){ float gx=w/2f, sr=activity.p(4.0f);
                pt.setStrokeWidth(activity.p(1.5f));
                c.drawCircle(gx, cy, sr, pt);
                pt.setStrokeWidth(activity.p(1.3f));
                for (int i=0;i<8;i++){ double a=Math.toRadians(i*45); float s=sr+activity.p(1.5f), e=sr+activity.p(3.3f); c.drawLine(gx+(float)Math.cos(a)*s, cy+(float)Math.sin(a)*s, gx+(float)Math.cos(a)*e, cy+(float)Math.sin(a)*e, pt); }
                if (activity.dimFill>0.01f){ c.save(); c.clipRect(gx+sr-2*sr*activity.dimFill, cy-sr-1, gx+sr+1, cy+sr+1); pt.setStyle(Paint.Style.FILL); c.drawCircle(gx, cy, sr-activity.p(0.3f), pt); pt.setStyle(Paint.Style.STROKE); c.restore(); } }
            else if (icon==6){
                if (asst==null) asst = getContext().getDrawable(R.drawable.assistant);
                int ax=(int)(w/2f-activity.p(7.5f)); asst.setBounds(ax, (int)(cy-activity.p(7.5f)), ax+activity.p(15), (int)(cy+activity.p(7.5f))); asst.draw(c); }
            else if (icon==7){
                float kx=w/2f, l=kx-activity.p(8), rr=kx+activity.p(8), t=cy-activity.p(5.2f), b=cy+activity.p(5.2f);
                pt.setStyle(Paint.Style.STROKE); pt.setStrokeWidth(activity.p(1.1f)); pt.setStrokeJoin(Paint.Join.ROUND);
                c.drawRoundRect(l, t, rr, b, activity.p(2.2f), activity.p(2.2f), pt);
                pt.setStyle(Paint.Style.FILL);
                float inset=activity.p(2.4f), kl=l+inset, kr=rr-inset, kt=t+inset, gapx=(kr-kl)/4f, gapy=activity.p(2.35f), kd=activity.p(0.85f);
                for (int row=0; row<2; row++) for (int col=0; col<5; col++) c.drawCircle(kl+col*gapx, kt+row*gapy, kd, pt);
                float by=kt+2*gapy, sh=activity.p(1.15f);
                c.drawCircle(kl, by, kd, pt); c.drawCircle(kr, by, kd, pt);
                c.drawRoundRect(kl+gapx, by-sh/2, kr-gapx, by+sh/2, activity.p(0.6f), activity.p(0.6f), pt);
            }
            else if (icon!=4) { pt.setStyle(Paint.Style.FILL); Path tr=new Path(); tr.moveTo(cx-activity.p(3),cy-activity.p(5)); tr.lineTo(cx+activity.p(5),cy); tr.lineTo(cx-activity.p(3),cy+activity.p(5)); tr.close(); c.drawPath(tr,pt); }
            pt.setStyle(Paint.Style.FILL); labelPaint();
            Paint.FontMetrics fm = pt.getFontMetrics();
            if (this==activity.heroBtn && ("Play Now".equals(label) || "More Info".equals(label))){
                float tw=pt.measureText(label), cap=-fm.ascent, th=cap, twt=cap*0.78f, gap=activity.p(10), x0=(w-twt-gap-tw)/2;
                if ("Play Now".equals(label)){
                    pt.setPathEffect(new CornerPathEffect(Math.max(1, cap*0.12f)));
                    Path tr=new Path(); tr.moveTo(x0, cy-th/2); tr.lineTo(x0+twt, cy); tr.lineTo(x0, cy+th/2); tr.close();
                    c.drawPath(tr, pt); pt.setPathEffect(null);
                } else {
                    float icx=x0+twt/2;
                    pt.setStyle(Paint.Style.STROKE); pt.setStrokeWidth(Math.max(1, th*0.12f));
                    c.drawCircle(icx, cy, th/2, pt);
                    pt.setStyle(Paint.Style.FILL); pt.setTextAlign(Paint.Align.CENTER);
                    pt.setTextSize(th*0.72f);
                    Paint.FontMetrics ifm=pt.getFontMetrics();
                    c.drawText("i", icx, cy-(ifm.ascent+ifm.descent)/2, pt);
                    pt.setTextSize(activity.p(14));
                }
                pt.setTextAlign(Paint.Align.LEFT);
                c.drawText(label, x0+twt+gap, cy-(fm.ascent+fm.descent)/2, pt);
                return;
            }
            if (icon==4) pt.setTextAlign(Paint.Align.CENTER); else pt.setTextAlign(Paint.Align.LEFT);
            if (label.length()>0) c.drawText(label, icon==4? w/2f : activity.p(icon==2?24:icon==3?SEARCH_PAD+SEARCH_ASST+SEARCH_GAP:43), cy-(fm.ascent+fm.descent)/2, pt);
        }
    }
