package com.chris.shieldhome;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.content.Intent;
import android.view.View;
import android.widget.ImageView;
import java.text.SimpleDateFormat;
import java.util.*;

class HeroController {
    /** Alpha (0-255) the left scrim settles to past HERO_LEFT_FADE and holds to the artwork's right edge: a light overall dim (~18%). */
    static final int HERO_DIM_ALPHA=46;
    /** Bottom fade: starts HERO_BOT_FADE of the art height above its bottom edge and eases (smoothstep, HERO_BOT_STOPS stops) to transparent at that edge. */
    static final float HERO_BOT_FADE=0.55f; static final int HERO_BOT_STOPS=9;
    final MainActivity a;
    String featuredBoundSig="";
    boolean heroFront=true;
    HeroController(MainActivity a) { this.a=a; }
    final Runnable heroRotate = new Runnable(){ public void run(){ synchronized(a.heroLock){ if (a.heroItems.size()>1) a.heroIdx=(a.heroIdx+1)%a.heroItems.size(); else { scheduleRotate(); return; } } showHero(); scheduleRotate(); } };
    /** (Re)starts the auto-advance timer; the dots' progress fill runs on the same clock. */
    void scheduleRotate(){ a.h.removeCallbacks(heroRotate); a.h.postDelayed(heroRotate, a.HERO_ROTATE_MS); if (a.heroDots!=null) a.heroDots.startProgress(); }
    /** Stops auto-advance; the progress fill freezes where it is. */
    void pauseRotate(){ a.h.removeCallbacks(heroRotate); if (a.heroDots!=null) a.heroDots.pauseProgress(); }
    static final android.view.animation.Interpolator DOTS_EASE=new android.view.animation.PathInterpolator(0.4f,0f,0.2f,1f);
    void applyFeaturedCache(){
        List<Object[]> feat=a.featuredCache;
        if (feat==null) return;
        String fs=heroSig(feat);
        if (fs.equals(featuredBoundSig)) return;
        featuredBoundSig=fs;
        synchronized(a.heroLock){ a.heroItems.clear(); a.heroItems.addAll(feat); a.heroIdx=0; }
        if (a.heroItems.isEmpty()) pauseRotate(); else { showHero(); scheduleRotate(); }
        a.net.loadWhatsNew();
    }
    static String heroSig(List<Object[]> l){
        StringBuilder b=new StringBuilder();
        for (Object[] o: l) b.append(o[0]).append('|').append(o[1]).append('|').append(o[3]).append(',');
        return b.toString();
    }
    void showHero(){
        final Object[] o;
        synchronized(a.heroLock){ if (a.heroItems.isEmpty()) return; o=a.heroItems.get(Math.min(a.heroIdx, a.heroItems.size()-1)); }
        a.heroGate=android.os.SystemClock.uptimeMillis()+650;
        if (a.heroBtn==null || !a.heroBtn.isFocused()) bindHeroAction(o);
        final String uri = (String)o[3];
        final int tw=a.heroG!=null&&a.heroG.getWidth()>0?a.heroG.getWidth():a.p(1024), th=a.heroG!=null&&a.heroG.getHeight()>0?a.heroG.getHeight():a.p(400), g=++a.heroLoadGen;
        a.pool.execute(()->{
            Bitmap raw = uri==null||a.images==null?null:a.images.decode(uri, tw, th);
            Bitmap bm = raw==null?null:maskHero(uri, raw, tw, th);
            a.h.post(()->{ if (g!=a.heroLoadGen) return; a.heroOnScreen=o; transitionHero(o, bm); });
        });
    }
    void transitionHero(final Object[] o, Bitmap bm){
        android.view.animation.Interpolator ease = new android.view.animation.PathInterpolator(0.4f,0f,0.2f,1f);
        final ImageView in = heroFront ? a.heroArt2 : a.heroArt, out = heroFront ? a.heroArt : a.heroArt2;
        if (bm != null){
            in.setScaleType(ImageView.ScaleType.FIT_XY);
            in.setImageBitmap(bm); in.setScaleX(1f); in.setScaleY(1f); in.setAlpha(0f); in.bringToFront();
            in.animate().alpha(1f).setDuration(1600).setInterpolator(ease).start();
            out.animate().alpha(0f).setDuration(1600).setInterpolator(ease).start();
            heroFront = !heroFront;
        }
        final View[] texts = {a.heroTitle, a.heroSub, a.heroBadge, a.heroFocus, a.heroLabel};
        android.view.animation.Interpolator outIp=new android.view.animation.AccelerateInterpolator();
        for (View t: texts) t.animate().alpha(0f).translationX(-a.p(10)).setDuration(450).setInterpolator(outIp).start();
        final boolean ctaOn=a.heroBtn!=null && a.heroBtn.isFocused();
        if (ctaOn){ a.heroBtn.animate().cancel(); a.heroBtn.animate().alpha(0f).setStartDelay(0).setDuration(450).setInterpolator(outIp).start(); }
        a.h.postDelayed(()->{
            bindHeroCopy(o);
            try { Drawable bd = a.pm.getApplicationBanner((String)o[0]); if (bd==null) bd = a.pm.getApplicationIcon((String)o[0]); a.heroBadge.setImageBitmap(glyph((String)o[0], bd));} catch(Exception e){}
            fitHeroMeta();
            if (a.heroDots!=null && a.heroDots.getVisibility()==View.VISIBLE){ int n; synchronized(a.heroLock){ n=a.heroItems.size(); } a.heroDots.bind(n, a.heroIdx, true); }
            int d=200; for (View t: texts){ t.setTranslationX(a.p(16)); t.animate().alpha(t==a.heroBadge?0.7f:1f).translationX(0).setStartDelay(d).setDuration(900).setInterpolator(new android.view.animation.DecelerateInterpolator(2f)).start(); d+=90; }
            if (ctaOn && a.heroBtn!=null && a.heroBtn.isFocused()){ a.heroBtn.animate().cancel(); a.heroBtn.setAlpha(0f); a.heroBtn.animate().alpha(1f).setStartDelay(200).setDuration(900).setInterpolator(new android.view.animation.DecelerateInterpolator(2f)).start(); }
        }, 480);
    }
    Bitmap alphaMask(int w, int h){
        if (a.vigMask!=null && a.vigMask.getWidth()==w && a.vigMask.getHeight()==h) return a.vigMask;
        Bitmap bm=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(bm);
        c.drawColor(Color.WHITE);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.DITHER_FLAG); p.setDither(true);
        p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        float artW=Math.min(w,a.p(758)), artH=Math.min(h,a.p(360)), artL=Math.max(0,w-artW), artB=artH, leftEnd=artL+artW*MainActivity.HERO_LEFT_FADE;
        p.setShader(new LinearGradient(0,0,leftEnd,0, new int[]{Color.TRANSPARENT,Color.TRANSPARENT,Color.WHITE}, new float[]{0, leftEnd<=0?0:artL/leftEnd, 1}, Shader.TileMode.CLAMP));
        c.drawRect(0,0,w,h,p);
        p.setShader(bottomFade(artB, artH));
        c.drawRect(0,0,w,h,p);
        p.setShader(new LinearGradient(0,0,0,h*0.064f, Color.argb(240,255,255,255), Color.WHITE, Shader.TileMode.CLAMP));
        c.drawRect(0,0,w,h,p);
        float rad=artH*0.37f;
        p.setShader(new RadialGradient(artL, artB, rad, new int[]{Color.TRANSPARENT, Color.argb(50,255,255,255), Color.WHITE}, new float[]{0, 0.5f, 1}, Shader.TileMode.CLAMP));
        c.drawRect(artL, artB-rad, Math.min(w, artL+rad), artB, p);
        a.vigMask=bm; return bm;
    }
    /** White above the fade, eased to transparent at artB and below; used as a DST_IN shader. */
    static Shader bottomFade(float artB, float artH){
        int[] col=new int[HERO_BOT_STOPS]; float[] pos=new float[HERO_BOT_STOPS];
        for (int i=0;i<HERO_BOT_STOPS;i++){ float t=i/(float)(HERO_BOT_STOPS-1); pos[i]=t; col[i]=Color.argb(Math.round(255*(1-t*t*(3-2*t))),255,255,255); }
        return new LinearGradient(0,artB-artH*HERO_BOT_FADE,0,artB, col, pos, Shader.TileMode.CLAMP);
    }
    Bitmap maskHero(String key, Bitmap src, int tw, int th){
        if (tw<=0||th<=0||src==null) return src;
        Bitmap hit=a.heroMasked.get(key);
        if (hit!=null && !hit.isRecycled() && hit.getWidth()==tw && hit.getHeight()==th) return hit;
        Bitmap out=Bitmap.createBitmap(tw,th,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(out);
        float artW=Math.min(tw,a.p(758)), artH=Math.min(th,a.p(360)), artL=tw-artW;
        float s=Math.max(artW/src.getWidth(), artH/src.getHeight());
        float dw=src.getWidth()*s, dh=src.getHeight()*s, cx=artL+artW/2f, cy=artH/2f;
        c.drawBitmap(src, null, new RectF(cx-dw/2,cy-dh/2,cx+dw/2,cy+dh/2), null);
        Paint xp=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.DITHER_FLAG); xp.setDither(true);
        xp.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        c.drawBitmap(alphaMask(tw,th),0,0,xp);
        float leftEnd=artL+artW*MainActivity.HERO_LEFT_FADE;
        xp.setXfermode(null);
        int dim=(HERO_DIM_ALPHA<<24)|MainActivity.HERO_SCRIM_RGB;
        xp.setShader(new LinearGradient(0,0,tw,0, new int[]{0xE6000000|MainActivity.HERO_SCRIM_RGB,0xC2000000|MainActivity.HERO_SCRIM_RGB,dim,dim}, new float[]{0, artL/tw, leftEnd/tw, 1}, Shader.TileMode.CLAMP));
        int layer=c.saveLayer(0,0,tw,th,null); // scrim fades out with the art's bottom edge instead of stopping at th
        c.drawRect(0,0,tw,th,xp);
        xp.setShader(bottomFade(artH, artH)); xp.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        c.drawRect(0,0,tw,th,xp);
        c.restoreToCount(layer);
        a.heroMasked.put(key, out); return out;
    }
    void fitHeroMeta(){
        if (a.heroTitle==null) return;
        a.ts(a.heroTitle, 23); a.ts(a.heroSub, 14);
        if (a.heroTitle.getPaint().measureText(a.heroTitle.getText().toString())>a.p(360)) a.ts(a.heroTitle, 17);
    }
    void bindHeroCopy(Object[] o){
        String raw=o[2]==null?"":(String)o[2];
        String tag=o.length>5?(String)o[5]:"";
        String meta="", desc=raw;
        int nl=raw.indexOf('\n');
        if ("NEW".equals(tag) && nl>=0){ meta=raw.substring(0,nl).trim(); desc=raw.substring(nl+1); }
        desc=a.firstSentence(desc.replaceAll("\\s+"," ").trim());
        if (a.heroLabel!=null){ a.heroLabel.setText(meta); a.heroLabel.setVisibility(meta.isEmpty()?View.GONE:View.VISIBLE); }
        a.heroTitle.setText((String)o[1]); a.heroSub.setText(desc);
        bindHeroAction(o);
    }
    boolean heroPlayable(Object[] o){
        if (o==null || o.length<5 || !(o[4] instanceof Intent)) return false;
        try { if (((Intent)o[4]).resolveActivity(a.pm)==null) return false; } catch(Exception e){ return false; }
        long avail=0; if (o.length>6 && o[6] instanceof Number) avail=((Number)o[6]).longValue();
        return avail<=System.currentTimeMillis();
    }
    void bindHeroAction(Object[] o){ a.setPill(a.heroBtn, heroPlayable(o)?"Play Now":"More Info"); }
    void showHeroAction(boolean on){
        if (a.heroBtn==null || on==a.heroCtaOn) return;
        a.heroCtaOn=on;
        android.view.animation.Interpolator ease=new android.view.animation.PathInterpolator(0.16f,1f,0.3f,1f);
        a.heroBtn.animate().cancel();
        if (on) a.heroBtn.setFocusable(true);
        a.heroBtn.animate().alpha(on?1f:0f).translationY(on?0:a.p(8)).scaleX(on?1.07f:1f).scaleY(on?1.07f:1f)
            .setStartDelay(0).setDuration(on?160:120).setInterpolator(ease).withLayer().start();
        if (a.heroDots!=null){
            int n; synchronized(a.heroLock){ n=a.heroItems.size(); }
            a.heroDots.animate().cancel();
            float slide=a.p(HeroDots.DOTS_SLIDE_DP);
            if (on && n>1){
                a.heroDots.bind(n, a.heroIdx, false);
                if (a.heroDots.getVisibility()!=View.VISIBLE){ a.heroDots.setAlpha(0f); a.heroDots.setTranslationY(slide); a.heroDots.setVisibility(View.VISIBLE); }
                a.heroDots.animate().alpha(1f).translationY(0).setDuration(HeroDots.DOTS_ANIM_MS).setInterpolator(DOTS_EASE).start();
            } else if (a.heroDots.getVisibility()==View.VISIBLE){
                a.heroDots.animate().alpha(0f).translationY(slide).setDuration(HeroDots.DOTS_ANIM_MS).setInterpolator(DOTS_EASE).withEndAction(()->{ if (a.heroDots!=null && (a.heroBtn==null||!a.heroBtn.isFocused())) a.heroDots.setVisibility(View.GONE); }).start();
            }
        }
    }
    void heroAction(){
        Object[] o=a.heroOnScreen;
        if (o==null) synchronized(a.heroLock){ o=a.heroItems.isEmpty()?null:a.heroItems.get(a.heroIdx); }
        if (o==null){ a.launchPkg(a.installed("com.plexapp.android")?"com.plexapp.android":MainActivity.PLAY_PKGS[0]); return; }
        if (heroPlayable(o)){
            try { a.startActivity(((Intent)o[4]).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return; } catch(Exception e){}
        }
        try { if (o[4] instanceof Intent) a.startActivity(((Intent)o[4]).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); else a.launchPkg((String)o[0]); }
        catch(Exception e){ a.launchPkg((String)o[0]); }
    }
    // v12: white monochrome mark extracted from the app banner (pixels that differ from the banner background become white, alpha by contrast), cropped, with a soft shadow
    Bitmap glyph(String pkg, Drawable d){
        Bitmap c=this.a.glyphs.get(pkg); if (c!=null) return c;
        int W=320, H=Math.max(1,Math.round(320f*d.getIntrinsicHeight()/Math.max(1,d.getIntrinsicWidth()))); if (d.getIntrinsicWidth()<=0){ H=180; }
        Bitmap src=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888); android.graphics.Canvas cv=new android.graphics.Canvas(src); d.setBounds(0,0,W,H); d.draw(cv);
        int[] px=new int[W*H]; src.getPixels(px,0,W,0,0,W,H);
        int[] o=px.clone(); int x0=W,y0=H,x1=-1,y1=-1;
        for (int i=0;i<px.length;i++){ int x=i%W, y=i/W, q=o[i]; int a=Color.alpha(q);
            int[] refs={o[y*W+1],o[y*W+W-2],o[W+x],o[(H-2)*W+x]}; float dist=1f;
            for (int r: refs){ float dd; if (Color.alpha(r)<40) dd=a/255f; else dd=(Math.abs(Color.red(q)-Color.red(r))+Math.abs(Color.green(q)-Color.green(r))+Math.abs(Color.blue(q)-Color.blue(r)))/(3*255f)*(a/255f); dist=Math.min(dist,dd); }
            int al=Math.round(Math.min(1f,Math.max(0f,(dist-0.10f)*3.2f))*255);
            px[i]=Color.argb(al,255,255,255);
            if (al>40){ if(x<x0)x0=x; if(x>x1)x1=x; if(y<y0)y0=y; if(y>y1)y1=y; } }
        Bitmap w=Bitmap.createBitmap(px,W,H,Bitmap.Config.ARGB_8888);
        if (x1>x0 && y1>y0) w=Bitmap.createBitmap(w,x0,y0,x1-x0+1,y1-y0+1);
        int pad=8; Bitmap out=Bitmap.createBitmap(w.getWidth()+pad*2,w.getHeight()+pad*2,Bitmap.Config.ARGB_8888); android.graphics.Canvas oc=new android.graphics.Canvas(out);
        android.graphics.Paint sh=new android.graphics.Paint(); sh.setMaskFilter(new android.graphics.BlurMaskFilter(5, android.graphics.BlurMaskFilter.Blur.NORMAL));
        int[] off=new int[2]; Bitmap a=w.extractAlpha(sh,off); android.graphics.Paint sp=new android.graphics.Paint(); sp.setColor(Color.argb(150,0,0,0)); oc.drawBitmap(a,pad+off[0],pad+off[1]+2,sp);
        oc.drawBitmap(w,pad,pad,null);
        this.a.glyphs.put(pkg,out); return out; }

    void heroStep(boolean next){
        if (android.os.SystemClock.uptimeMillis()<a.heroGate) return;
        synchronized(a.heroLock){
            int n=a.heroItems.size();
            if (n<=1) return;
            a.heroIdx = (a.heroIdx + (next?1:-1) + n) % n;
        }
        showHero();
        scheduleRotate();
    }
    void useWhatsNew(String js){
        List<Object[]> nl = new ArrayList<>();
        try { org.json.JSONArray a = new org.json.JSONArray(js==null?"[]":js);
            SimpleDateFormat whatsNewDateFormat=new SimpleDateFormat("yyyy-MM-dd",Locale.US);
            SimpleDateFormat whatsNewDayFormat=new SimpleDateFormat("EEE",Locale.US);
            SimpleDateFormat whatsNewWeekdayFormat=new SimpleDateFormat("EEEE",Locale.US);
            Date whatsNewToday=whatsNewDateFormat.parse(whatsNewDateFormat.format(new Date()));
            for (int i=0;i<a.length();i++){ org.json.JSONObject o=a.getJSONObject(i); String pkg=o.getString("pkg"); if (!this.a.installed(pkg) || o.optString("img").isEmpty()) continue;
                int dd=o.optInt("dd"); String when; Date itemDate=null;
                try { itemDate = whatsNewDateFormat.parse(o.getString("date")); long diff = Math.round((itemDate.getTime() - whatsNewToday.getTime())/86400000.0);
                    when = diff==0?"New today":diff==1?"Coming tomorrow":diff==-1?"Added yesterday":diff<0?"Added "+whatsNewDayFormat.format(itemDate):"Coming "+whatsNewWeekdayFormat.format(itemDate); } catch (Exception e){ when="New"; }
                String ep = o.optInt("season")>0 ? "  \u00B7  S"+o.optInt("season")+(o.optInt("num")>0?" E"+o.optInt("num"):"") : "";
                String sum = this.a.firstSentence(o.optString("summary"));
                Intent li = this.a.pm.getLeanbackLaunchIntentForPackage(pkg); if (li==null) li = this.a.pm.getLaunchIntentForPackage(pkg);
                if (TvData.opensYouTube(pkg, li)) continue; // no YouTube in the hero
                long avail=0; if (itemDate!=null && itemDate.getTime()>System.currentTimeMillis()) avail=itemDate.getTime();
                nl.add(new Object[]{pkg, o.getString("show"), when+" on "+o.optString("net")+ep+"\n"+sum, o.getString("img"), li, "NEW", avail});
            }
        } catch (Exception e){ android.util.Log.w("ShieldHome","whatsnew parse "+e); }
        // v9: one hero, Featured + What's New randomly mixed in the same rotation
        List<Object[]> mix = new ArrayList<>(); for (Object[] o: this.a.heroItems) if (o.length>5 && "FEATURED".equals(o[5])) mix.add(o);
        int nf = mix.size(); Collections.shuffle(nl); for (int i=0;i<Math.min(8,nl.size());i++) mix.add(nl.get(i));
        android.util.Log.i("ShieldHome","hero mix featured="+nf+" new="+(mix.size()-nf));
        if (mix.size()!=nf){
            Collections.shuffle(mix);
            synchronized(this.a.heroLock){ this.a.heroItems.clear(); this.a.heroItems.addAll(mix); this.a.heroIdx=0; }
            showHero(); scheduleRotate();
        }
    }
}
