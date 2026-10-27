package com.chris.shieldhome;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.content.Intent;
import android.view.View;
import android.widget.ImageView;
import java.text.SimpleDateFormat;
import java.util.*;

class HeroController {
    final MainActivity a;
    String featuredBoundSig="";
    boolean heroFront=true;
    HeroController(MainActivity a) { this.a=a; }
    final Runnable heroRotate = new Runnable(){ public void run(){ synchronized(a.heroLock){ if (a.heroItems.size()>1) a.heroIdx=(a.heroIdx+1)%a.heroItems.size(); else { a.h.postDelayed(this, a.HERO_ROTATE_MS); return; } } showHero(); a.h.postDelayed(this, a.HERO_ROTATE_MS); } };
    void loadHero(){ applyFeaturedCache(); }
    void applyFeaturedCache(){
        List<Object[]> feat=a.featuredCache;
        if (feat==null) return;
        String fs=heroSig(feat);
        if (fs.equals(featuredBoundSig)) return;
        featuredBoundSig=fs;
        synchronized(a.heroLock){ a.heroItems.clear(); a.heroItems.addAll(feat); a.heroIdx=0; }
        a.h.removeCallbacks(heroRotate);
        if (!a.heroItems.isEmpty()){ showHero(); a.h.postDelayed(heroRotate, a.HERO_ROTATE_MS); }
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
        if (a.heroBtn==null || !a.heroBtn.isFocused()) a.bindHeroAction(o);
        final String uri = (String)o[3];
        final int tw=a.heroG!=null&&a.heroG.getWidth()>0?a.heroG.getWidth():a.p(1024), th=a.heroG!=null&&a.heroG.getHeight()>0?a.heroG.getHeight():a.p(400), g=++a.heroLoadGen;
        a.pool.execute(()->{
            Bitmap raw = uri==null||a.images==null?null:a.images.decode(uri, tw, th);
            Bitmap bm = raw==null?null:a.maskHero(uri, raw, tw, th);
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
            a.bindHeroCopy(o);
            try { Drawable bd = a.pm.getApplicationBanner((String)o[0]); if (bd==null) bd = a.pm.getApplicationIcon((String)o[0]); a.heroBadge.setImageBitmap(a.glyph((String)o[0], bd));} catch(Exception e){}
            a.fitHeroMeta();
            if (a.heroDots!=null && a.heroDots.getVisibility()==View.VISIBLE){ int n; synchronized(a.heroLock){ n=a.heroItems.size(); } a.heroDots.bind(n, a.heroIdx, true); }
            int d=200; for (View t: texts){ t.setTranslationX(a.p(16)); t.animate().alpha(t==a.heroBadge?0.7f:1f).translationX(0).setStartDelay(d).setDuration(900).setInterpolator(new android.view.animation.DecelerateInterpolator(2f)).start(); d+=90; }
            if (ctaOn && a.heroBtn!=null && a.heroBtn.isFocused()){ a.heroBtn.animate().cancel(); a.heroBtn.setAlpha(0f); a.heroBtn.animate().alpha(1f).setStartDelay(200).setDuration(900).setInterpolator(new android.view.animation.DecelerateInterpolator(2f)).start(); }
        }, 480);
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
            synchronized(this.a.heroLock){ this.a.h.removeCallbacks(heroRotate); this.a.heroItems.clear(); this.a.heroItems.addAll(mix); this.a.heroIdx=0; }
            showHero(); this.a.h.postDelayed(heroRotate, this.a.HERO_ROTATE_MS);
        }
    }
}
