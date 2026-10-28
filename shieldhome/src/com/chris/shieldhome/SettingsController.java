package com.chris.shieldhome;

import android.animation.ValueAnimator;
import android.graphics.*;
import android.view.*;
import android.view.animation.*;
import android.view.animation.Interpolator;
import android.widget.*;

/** v83: top-centre menu icon + Apple-style settings panel that morphs out of the icon (Featured card / Wallpaper toggles). */
class SettingsController {
    static final String PREF_HERO="hero_on", PREF_WALL="wallpaper_on";
    static final float ICON_D=28f, ICON_Y=17f, PANEL_W=240f, PANEL_PAD=8f, ROW_H=AppGridController.MENU_ROW_H, ROW_GAP=4f, PANEL_R=AppGridController.MENU_R;
    static final long OPEN_MS=480, CLOSE_MS=260, ICON_ANIM_MS=HeroDots.DOTS_ANIM_MS;
    static final float OPEN_OVERSHOOT=1.6f, ROWS_IN_AT=0.45f; // rows fade in over the last (1-ROWS_IN_AT) of the morph
    /** Extra view bounds left/right/below the panel so the spring overshoot (~9% of the morph at OPEN_OVERSHOOT 1.6) is not clipped. */
    static final float PANEL_BLEED=12f;
    /** Menu icon hands off to the morph: fades out over the first ICON_FADE_T of the panel's t while growing by ICON_MORPH_SCALE; driven by t so it rides the spring both ways. */
    static final float ICON_FADE_T=0.35f, ICON_MORPH_SCALE=0.6f;
    static final Interpolator OPEN_EASE=new OvershootInterpolator(OPEN_OVERSHOOT), CLOSE_EASE=new PathInterpolator(0.4f,0f,0.2f,1f);
    final MainActivity a;
    final ViewTreeObserver.OnGlobalFocusChangeListener focusL=(o,n)->syncIcon();
    PillView icon; Panel panel; ToggleRow heroRow, wallRow; ValueAnimator morph; boolean open;
    SettingsController(MainActivity a){ this.a=a; }

    boolean heroOn(){ return a.prefs().getBoolean(PREF_HERO, true); }
    boolean wallOn(){ return a.prefs().getBoolean(PREF_WALL, false); }

    void build(){
        icon=new PillView(a, a, "", 8, false); icon.setId(View.generateViewId());
        icon.setAlpha(0f); icon.setVisibility(View.GONE);
        icon.setOnClickListener(v->openPanel());
        a.root.addView(icon, a.lp(a.p(ICON_D), a.p(ICON_D), Gravity.TOP|Gravity.CENTER_HORIZONTAL, 0, a.p(ICON_Y), 0, 0));
        panel=new Panel(); panel.setVisibility(View.GONE);
        heroRow=new ToggleRow("Featured card", PREF_HERO); wallRow=new ToggleRow("Wallpaper", PREF_WALL);
        int rw=a.p(PANEL_W-2*PANEL_PAD);
        panel.addView(heroRow, a.lp(rw, a.p(ROW_H), Gravity.TOP|Gravity.CENTER_HORIZONTAL, 0, a.p(PANEL_PAD), 0, 0));
        panel.addView(wallRow, a.lp(rw, a.p(ROW_H), Gravity.TOP|Gravity.CENTER_HORIZONTAL, 0, a.p(PANEL_PAD+ROW_H+ROW_GAP), 0, 0));
        a.root.addView(panel, a.lp(a.p(PANEL_W+2*PANEL_BLEED), a.p(panelH()+PANEL_BLEED), Gravity.TOP|Gravity.CENTER_HORIZONTAL, 0, a.p(ICON_Y), 0, 0));
        a.root.getViewTreeObserver().addOnGlobalFocusChangeListener(focusL);
        if (heroOn() && wallOn()) a.prefs().edit().putBoolean(PREF_WALL, false).apply(); // load: keep hero_on, drop wallpaper
        applyHero();
    }
    float panelH(){ return 2*PANEL_PAD+2*ROW_H+ROW_GAP; }

    /** Icon shows while Play Now / the icon has focus, or (featured card off) while focus is in the top bar so the menu stays reachable. */
    void syncIcon(){
        if (icon==null || open || (morph!=null && morph.isRunning())) return; // the morph owns the icon while the panel is open/animating
        View f=a.getCurrentFocus();
        boolean show=open || f==icon || (heroOn() ? f==a.heroBtn : a.focus.secOf(f)==0);
        icon.setFocusable(show || !heroOn());
        boolean vis=icon.getVisibility()==View.VISIBLE && icon.getAlpha()>0f;
        if (show==vis) return;
        float slide=a.p(HeroDots.DOTS_SLIDE_DP);
        icon.animate().cancel();
        if (show){ if (icon.getVisibility()!=View.VISIBLE){ icon.setTranslationY(-slide); icon.setVisibility(View.VISIBLE); } icon.animate().alpha(1f).translationY(0).setDuration(ICON_ANIM_MS).setInterpolator(HeroController.DOTS_EASE).start(); }
        else icon.animate().alpha(0f).translationY(-slide).setDuration(ICON_ANIM_MS).setInterpolator(HeroController.DOTS_EASE).withEndAction(()->{ if (!open && a.getCurrentFocus()!=icon) icon.setVisibility(View.GONE); }).start();
    }
    void stop(){ if (morph!=null) morph.cancel(); if (icon!=null) icon.animate().cancel(); if (a.root!=null) a.root.getViewTreeObserver().removeOnGlobalFocusChangeListener(focusL); }
    void focusIcon(){ icon.setVisibility(View.VISIBLE); icon.setFocusable(true); icon.requestFocus(); }

    /** D-pad from the icon while the featured card is on (it is not part of a FocusController section then). */
    boolean iconKey(int k){
        if (k==KeyEvent.KEYCODE_DPAD_DOWN && a.heroShown() && a.heroBtn!=null){ a.heroBtn.setFocusable(true); a.heroBtn.requestFocus(); }
        else if (k==KeyEvent.KEYCODE_DPAD_UP && a.gear!=null) a.gear.requestFocus(); // Play Now -> menu -> dim pill
        return true;
    }

    void openPanel(){
        if (open) return;
        open=true; heroRow.sync(); wallRow.sync(); icon.animate().cancel(); icon.setTranslationY(0);
        panel.setVisibility(View.VISIBLE); panel.bringToFront(); icon.bringToFront();
        runMorph(1f, OPEN_MS, OPEN_EASE, null);
        heroRow.requestFocus();
    }
    void closePanel(){
        if (!open) return;
        open=false;
        focusIcon();
        runMorph(0f, CLOSE_MS, CLOSE_EASE, ()->panel.setVisibility(View.GONE));
    }
    void iconMorph(float t){
        float h=Math.max(0f, Math.min(1f, t/ICON_FADE_T));
        icon.setAlpha(1f-h); icon.setScaleX(1f+ICON_MORPH_SCALE*h); icon.setScaleY(1f+ICON_MORPH_SCALE*h);
    }
    void runMorph(float to, long ms, Interpolator ip, Runnable end){
        if (morph!=null) morph.cancel();
        morph=ValueAnimator.ofFloat(panel.t, to); morph.setDuration(ms); morph.setInterpolator(ip);
        morph.addUpdateListener(v->{ float t=(Float)v.getAnimatedValue(); panel.setT(t); iconMorph(t); });
        if (end!=null) morph.addListener(new android.animation.AnimatorListenerAdapter(){ boolean cancelled; @Override public void onAnimationCancel(android.animation.Animator x){ cancelled=true; } @Override public void onAnimationEnd(android.animation.Animator x){ if (!cancelled) end.run(); } });
        morph.start();
    }

    /** While open the panel owns the d-pad: Up/Down between rows, Centre toggles, Back collapses into the icon. */
    boolean key(KeyEvent e){
        int k=e.getKeyCode();
        if (k==KeyEvent.KEYCODE_BACK){ if (e.getAction()==KeyEvent.ACTION_UP) closePanel(); return true; }
        if (k==KeyEvent.KEYCODE_DPAD_CENTER || k==KeyEvent.KEYCODE_ENTER) return a.superDispatchKey(e);
        if (e.getAction()==KeyEvent.ACTION_DOWN){
            if (k==KeyEvent.KEYCODE_DPAD_UP) heroRow.requestFocus();
            else if (k==KeyEvent.KEYCODE_DPAD_DOWN) wallRow.requestFocus();
        }
        return true;
    }

    /** Featured card off: hide art/text/button/dots (heroG), keep the root background; rows keep their absolute positions. */
    void applyHero(){
        boolean on=heroOn();
        a.heroG.setVisibility(on?View.VISIBLE:View.INVISIBLE);
        if (a.heroBtn!=null) a.heroBtn.setFocusable(on);
        if (on){ if (a.hero.artSkipped) a.hero.showHero(); a.hero.scheduleRotate(); } else a.hero.pauseRotate();
        syncIcon();
    }
    /** Featured card and Wallpaper are exclusive: turning one on turns the other off; both off = plain background. */
    void toggle(String key){
        boolean on=!a.prefs().getBoolean(key, PREF_HERO.equals(key));
        android.content.SharedPreferences.Editor e=a.prefs().edit().putBoolean(key, on);
        if (on) e.putBoolean(PREF_HERO.equals(key)?PREF_WALL:PREF_HERO, false);
        e.apply();
        applyHero(); a.wallpaper.apply(); heroRow.sync(); wallRow.sync();
    }

    /** Morphing container: rounded rect interpolates from the icon bounds (t=0) to the panel bounds (t=1); overshoot gives the bounce. */
    class Panel extends FrameLayout {
        float t; final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG), edge=new Paint(Paint.ANTI_ALIAS_FLAG); final RectF r=new RectF();
        Panel(){ super(a); setWillNotDraw(false); setClipChildren(false);
            fill.setColor(Color.argb(140,60,58,75)); edge.setStyle(Paint.Style.STROKE); edge.setStrokeWidth(1); edge.setColor(Color.argb(40,255,255,255)); }
        void setT(float v){ t=v; float ra=Math.max(0f,Math.min(1f,(v-ROWS_IN_AT)/(1f-ROWS_IN_AT))); for (int i=0;i<getChildCount();i++) getChildAt(i).setAlpha(ra); setAlpha(Math.min(1f, v*4f)); invalidate(); }
        @Override protected void onDraw(Canvas c){
            float bl=a.p(PANEL_BLEED), w=getWidth()-2*bl, h=getHeight()-bl, iw=a.p(ICON_D), ix=(w-iw)/2f;
            c.save(); c.translate(bl, 0);
            float l=ix+(0-ix)*t, rr=ix+iw+(w-ix-iw)*t, b=iw+(h-iw)*t, rad=iw/2f+(a.p(PANEL_R)-iw/2f)*Math.max(0f,Math.min(1f,t));
            r.set(l, 0, rr, Math.max(1f,b));
            c.drawRoundRect(r, rad, rad, fill); r.inset(.5f,.5f); c.drawRoundRect(r, rad, rad, edge);
            c.restore();
        }
    }

    /** Pill row: label + switch; focused row gets the quick-menu white pill highlight. */
    class ToggleRow extends View {
        static final float SW_W=30f, SW_H=16f, LABEL=12f, PAD=14f;
        final String label, key; boolean on; final Paint pt=new Paint(Paint.ANTI_ALIAS_FLAG);
        { pt.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL)); }
        ToggleRow(String label, String key){ super(a); this.label=label; this.key=key; setFocusable(true); setClickable(true); setOnClickListener(v->toggle(key)); }
        void sync(){ on=a.prefs().getBoolean(key, PREF_HERO.equals(key)); invalidate(); }
        @Override protected void onFocusChanged(boolean f, int d, Rect r){ super.onFocusChanged(f,d,r); invalidate(); }
        @Override protected void onDraw(Canvas c){
            float w=getWidth(), h=getHeight(); boolean fo=isFocused();
            if (fo){ pt.setColor(Color.argb(240,255,255,255)); c.drawRoundRect(0,0,w,h,h/2,h/2,pt); }
            int fg=fo?Color.rgb(22,22,30):Color.WHITE;
            pt.setColor(fg); pt.setTextSize(a.p(LABEL));
            Paint.FontMetrics fm=pt.getFontMetrics(); c.drawText(label, a.p(PAD), h/2-(fm.ascent+fm.descent)/2, pt);
            float sw=a.p(SW_W), sh=a.p(SW_H), sx=w-a.p(PAD)-sw, sy=(h-sh)/2;
            pt.setColor(on?Color.rgb(52,199,89):(fo?Color.argb(60,22,22,30):Color.argb(70,255,255,255)));
            c.drawRoundRect(sx, sy, sx+sw, sy+sh, sh/2, sh/2, pt);
            float kr=sh/2-a.p(2), kx=on?sx+sw-sh/2:sx+sh/2;
            pt.setColor(Color.WHITE); c.drawCircle(kx, sy+sh/2, kr, pt);
        }
    }
}
