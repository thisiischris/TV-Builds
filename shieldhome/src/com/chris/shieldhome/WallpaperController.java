package com.chris.shieldhome;

import android.content.*;
import android.graphics.*;
import android.media.MediaPlayer;
import android.os.*;
import android.view.*;
import java.util.ArrayList;
import java.util.List;

/**
 * v83: wallpaper behind everything, sourced from Aerial Views through its exported Projectivy
 * wallpaper-provider plugin service (tv.projectivy.plugin.WALLPAPER_PROVIDER). We speak the
 * plugin's AIDL contract over a raw Binder (getWallpapers(TimeElapsed)) and play the returned
 * video URIs muted and looping-through-the-list in a TextureView, centre-cropped.
 */
// Projectivy plugin API: wallpaper-provider api v1 (IWallpaperProviderService getWallpapers/getPreferences/setPreferences), as exported by Aerial Views 1.8.5.
class WallpaperController implements TextureView.SurfaceTextureListener {
    static final String AERIAL_PKG="com.neilturner.aerialviews", PLUGIN_ACTION="tv.projectivy.plugin.WALLPAPER_PROVIDER";
    static final String PLUGIN_DESC="tv.projectivy.plugin.wallpaperprovider.api.IWallpaperProviderService";
    static final int TX_GET_WALLPAPERS=IBinder.FIRST_CALL_TRANSACTION, EVENT_TIME_ELAPSED=1, TYPE_VIDEO=4, MAX_ITEMS=500, MAX_REBINDS=1;
    static final long FADE_MS=800;
    /** Bottom scrim over the video: WALL_TOP_ALPHA above WALL_DIM_START (fraction of screen height), smoothstep over WALL_DIM_STOPS stops to WALL_DIM_ALPHA black at WALL_DIM_END, solid below. */
    static final float WALL_DIM_START=0.45f, WALL_DIM_END=0.92f; static final int WALL_DIM_ALPHA=225, WALL_DIM_STOPS=24;
    /** Light dim (~6% black) over the rest of the screen: the gradient starts from this alpha and CLAMP carries it to the top edge, so there is no band. */
    static final int WALL_TOP_ALPHA=15;
    /** v84 lock button row [prev][lock][next] starting in Play Now's slot (same x/y/height as the featured card CTA). Invisible (alpha 0, still focusable) unless focused;
     *  fades in with a slide/scale from LOCK_IN_DY/LOCK_IN_SCALE when focus arrives, fades back out when it leaves. */
    static final float LOCK_D=34f, LOCK_X=35f, LOCK_Y=237f, LOCK_IN_DY=8f, LOCK_IN_SCALE=0.85f;
    static final long LOCK_IN_MS=360, LOCK_OUT_MS=220;
    /** Prev/next arrows: same circle, LOCK_GAP either side of the lock (group starts at LOCK_X); press pop = scale to ARROW_PRESS_SCALE + ARROW_NUDGE toward its side. */
    static final float LOCK_GAP=8f, ARROW_PRESS_SCALE=0.84f, ARROW_NUDGE=3f;
    static final long ARROW_PRESS_MS=90, ARROW_RELEASE_MS=180;
    /** Locked = the current video loops; the locked URI is remembered so it comes back after a restart. */
    static final String PREF_LOCK="wall_locked", PREF_LOCK_URI="wall_lock_uri";
    final MainActivity a;
    LockButton lock, prev, nextBtn;
    TextureView view; View scrim; Surface surface; MediaPlayer mp; final List<String> uris=new ArrayList<>(); int idx, fails, resumePos, rebinds; boolean bound, visible, prepared;
    WallpaperController(MainActivity a){ this.a=a; }

    void build(){
        view=new TextureView(a); view.setAlpha(0f); view.setFocusable(false); view.setClickable(false); // stays attached (never GONE) so its surface survives toggles
        view.setSurfaceTextureListener(this);
        a.root.addView(view, 0, new android.widget.FrameLayout.LayoutParams(-1,-1));
        scrim=new View(a){
            final Paint sp=new Paint(); { sp.setDither(true); }
            @Override protected void onSizeChanged(int w, int h, int ow, int oh){ sp.setShader(scrimShader(h)); }
            @Override protected void onDraw(Canvas c){ c.drawRect(0,0,getWidth(),getHeight(),sp); }
        };
        scrim.setFocusable(false); scrim.setAlpha(0f);
        a.root.addView(scrim, 1, new android.widget.FrameLayout.LayoutParams(-1,-1));
        prev=new LockButton(a, LockButton.GLYPH_PREV); nextBtn=new LockButton(a, LockButton.GLYPH_NEXT);
        lock=new LockButton(a, LockButton.GLYPH_LOCK); lock.setVisibility(View.GONE); lock.setFocusable(false); lock.setLocked(locked(), false);
        lock.setOnClickListener(v->toggleLock());
        lock.setOnFocusChangeListener((v,f)->focusLock(f));
        LockButton[] row={prev, lock, nextBtn};
        for (int i=0;i<row.length;i++){
            if (row[i]!=lock){ row[i].setVisibility(View.GONE); row[i].setAlpha(0f); }
            a.root.addView(row[i], 2+i, a.lp(a.p(LOCK_D), a.p(LOCK_D), Gravity.TOP|Gravity.START, a.p(LOCK_X+i*(LOCK_D+LOCK_GAP)), a.p(LOCK_Y), 0, 0));
        }
    }
    boolean locked(){ return a.prefs().getBoolean(PREF_LOCK, false); }
    boolean lockShown(){ return lock!=null && lock.getVisibility()==View.VISIBLE; }
    /** Lock button exists (focusable, transparent until focused) only while the wallpaper is on (featured card off). */
    void syncLock(boolean on){
        if (on==lockShown()) return;
        lock.animate().cancel(); lock.setFocusable(on);
        if (!on && lock.isFocused()) a.focus.tabV().requestFocus();
        for (View v: new View[]{prev, lock, nextBtn}){ v.animate().cancel(); v.setAlpha(0f); v.setVisibility(on?View.VISIBLE:View.GONE); }
    }
    /** The lock and its arrows fade/slide in together when the lock gets focus and out when it loses it. */
    void focusLock(boolean f){
        for (LockButton v: new LockButton[]{prev, lock, nextBtn}){
            v.animate().cancel(); v.setFlash(false); v.setTranslationX(0);
            if (f){ v.setTranslationY(a.p(LOCK_IN_DY)); v.setScaleX(LOCK_IN_SCALE); v.setScaleY(LOCK_IN_SCALE);
                v.animate().alpha(1f).translationY(0).scaleX(1f).scaleY(1f).setDuration(LOCK_IN_MS).setInterpolator(HeroController.DOTS_EASE).start(); }
            else v.animate().alpha(0f).translationY(a.p(LOCK_IN_DY)).scaleX(LOCK_IN_SCALE).scaleY(LOCK_IN_SCALE).setDuration(LOCK_OUT_MS).setInterpolator(HeroController.DOTS_EASE).start();
        }
    }
    /** Quick pop on the arrow matching a Left/Right: scale down + brighter fill + nudge toward its side, then back. */
    void pressArrow(LockButton b){
        b.animate().cancel(); b.setFlash(true); b.setAlpha(1f); b.setTranslationY(0);
        b.animate().scaleX(ARROW_PRESS_SCALE).scaleY(ARROW_PRESS_SCALE).translationX(a.p(ARROW_NUDGE)*b.glyph).setDuration(ARROW_PRESS_MS).setInterpolator(HeroController.DOTS_EASE)
            .withEndAction(()->{ b.setFlash(false); b.animate().scaleX(1f).scaleY(1f).translationX(0).setDuration(ARROW_RELEASE_MS).setInterpolator(HeroController.DOTS_EASE).start(); }).start();
    }
    void toggleLock(){
        boolean on=!locked();
        android.content.SharedPreferences.Editor e=a.prefs().edit().putBoolean(PREF_LOCK, on);
        if (on && !uris.isEmpty()) e.putString(PREF_LOCK_URI, uris.get(idx%uris.size()));
        e.apply();
        if (mp!=null && prepared) mp.setLooping(on); // still preparing: onPrepared applies locked()
        lock.setLocked(on, true);
    }
    /** Left/Right on the lock: previous/next wallpaper, also while locked (the new one becomes the looped one). */
    void step(int dir){
        if (uris.isEmpty()) return;
        pressArrow(dir<0?prev:nextBtn);
        idx=((idx+dir)%uris.size()+uris.size())%uris.size(); fails=0; resumePos=0;
        release(); play();
    }

    /** Pref changed or home became visible: show + play when on, stop + release when off. */
    void apply(){
        syncLock(a.settings.wallOn());
        boolean on=a.settings.wallOn() && visible;
        if (!on){ stop(); return; }
        fails=0;
        if (uris.isEmpty()) fetch(); else play();
    }
    void onResume(){ visible=true; apply(); }
    /** Another app in front (onStop, not onPause, so a Home press on home never touches playback): free the hardware decoder entirely
     *  (remember where we were); apply() on resume rebuilds the player and fades back in. */
    void onStop(){ visible=false; if (mp!=null){ try { resumePos=mp.getCurrentPosition(); } catch (Exception e){ resumePos=0; } } stop(); }
    void onDestroy(){ visible=false; stop(); unbind(); }

    final ServiceConnection conn=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName n, IBinder b){
            try { a.pool.execute(()->{ List<String> got=query(b); a.h.post(()->{ unbind(); rebinds=0; uris.clear(); uris.addAll(got); int li=uris.indexOf(a.prefs().getString(PREF_LOCK_URI, null)); if (locked() && li>=0) idx=li; /* locked video no longer offered: keep the shuffled idx, onPrepared re-locks to whatever plays */ android.util.Log.i("ShieldHome","wallpaper aerial items="+got.size()); if (visible && a.settings.wallOn()) play(); }); }); } catch (java.util.concurrent.RejectedExecutionException x){ unbind(); }
        }
        /** Provider process died; the binding stays registered and reconnects, so keep `bound` until unbind() runs. */
        @Override public void onServiceDisconnected(ComponentName n){}
        @Override public void onBindingDied(ComponentName n){ unbind(); if (rebinds++<MAX_REBINDS && uris.isEmpty() && visible && a.settings.wallOn()) fetch(); }
        @Override public void onNullBinding(ComponentName n){ unbind(); android.util.Log.i("ShieldHome","wallpaper: Aerial Views provider returned no binder"); }
    };
    void fetch(){
        if (bound) return;
        Intent i=new Intent(PLUGIN_ACTION).setPackage(AERIAL_PKG);
        try { bound=a.bindService(i, conn, Context.BIND_AUTO_CREATE); } catch (Exception e){ bound=false; }
        if (!bound) android.util.Log.i("ShieldHome","wallpaper: Aerial Views provider unavailable");
    }
    void unbind(){ if (bound){ try { a.unbindService(conn); } catch (Exception e){} bound=false; } }

    /** Raw-Binder getWallpapers(in Event): Event = typed TimeElapsed object (Parcelize writes its eventType int). Reply: typed list of Wallpaper. */
    List<String> query(IBinder b){
        List<String> out=new ArrayList<>();
        Parcel d=Parcel.obtain(), r=Parcel.obtain();
        try {
            d.writeInterfaceToken(PLUGIN_DESC);
            d.writeInt(1); d.writeInt(EVENT_TIME_ELAPSED);
            b.transact(TX_GET_WALLPAPERS, d, r, 0);
            r.readException();
            int n=Math.min(r.readInt(), MAX_ITEMS); // remaining entries are simply not read
            for (int i=0;i<n;i++){
                if (r.readInt()==0) continue;
                String uri=r.readString(); int type=r.readInt();
                r.readInt(); r.readString(); r.readString(); r.readString(); r.readString(); // displayMode, title, source, author, actionUri
                if (type==TYPE_VIDEO && uri!=null) out.add(uri);
            }
        } catch (Exception e){ android.util.Log.i("ShieldHome","wallpaper query failed "+e); }
        finally { d.recycle(); r.recycle(); }
        java.util.Collections.shuffle(out);
        return out;
    }

    void play(){
        if (surface==null || uris.isEmpty()) return;
        if (mp!=null){ if (prepared && !mp.isPlaying()) mp.start(); return; } // still preparing: onPrepared starts it
        mp=new MediaPlayer();
        mp.setSurface(surface); mp.setVolume(0f,0f);
        mp.setOnPreparedListener(p->{ prepared=true; fails=0; p.setLooping(locked()); lockCurrent(); crop(p.getVideoWidth(), p.getVideoHeight()); if (resumePos>0){ p.seekTo(resumePos); resumePos=0; } if (visible) p.start(); view.animate().alpha(1f).setDuration(FADE_MS).start(); scrim.animate().alpha(1f).setDuration(FADE_MS).start(); });
        mp.setOnCompletionListener(p->{ fails=0; next(); });
        mp.setOnErrorListener((p,w,x)->{ android.util.Log.i("ShieldHome","wallpaper error "+w+"/"+x); next(); return true; });
        try { mp.setDataSource(a, android.net.Uri.parse(uris.get(idx%uris.size()))); mp.prepareAsync(); } catch (Exception e){ next(); }
    }
    static Shader scrimShader(float h){
        int[] col=new int[WALL_DIM_STOPS]; float[] pos=new float[WALL_DIM_STOPS];
        for (int i=0;i<WALL_DIM_STOPS;i++){ float t=i/(float)(WALL_DIM_STOPS-1); pos[i]=t; col[i]=Color.argb(Math.round(WALL_TOP_ALPHA+(WALL_DIM_ALPHA-WALL_TOP_ALPHA)*t*t*(3-2*t)),0,0,0); }
        return new LinearGradient(0,h*WALL_DIM_START,0,h*WALL_DIM_END, col, pos, Shader.TileMode.CLAMP);
    }
    /** The playing video becomes the locked one (after Left/Right, or when error/missing-URI skipping moved past the locked video). */
    void lockCurrent(){ if (!locked()) return; String u=uris.get(idx%uris.size()); if (!u.equals(a.prefs().getString(PREF_LOCK_URI, null))) a.prefs().edit().putString(PREF_LOCK_URI, u).apply(); }
    /** Advance on completion/error (while locked, only errors reach here: the next playable video becomes the locked one via lockCurrent); async, and gives up after a full pass of failures (no recursion on bad URIs). */
    void next(){ release(); resumePos=0; idx=(idx+1)%Math.max(1,uris.size()); if (++fails>uris.size()) return; a.h.post(()->{ if (visible && a.settings.wallOn()) play(); }); }
    void release(){ prepared=false; if (mp!=null){ try { mp.reset(); mp.release(); } catch (Exception e){} mp=null; } }
    void stop(){ view.animate().cancel(); view.setAlpha(0f); scrim.animate().cancel(); scrim.setAlpha(0f); release(); }

    /** Centre-crop the video to fill the view. */
    void crop(int vw, int vh){
        float w=view.getWidth(), h=view.getHeight(); if (vw<=0||vh<=0||w<=0||h<=0) return;
        float s=Math.max(w/vw, h/vh); Matrix m=new Matrix(); m.setScale(vw*s/w, vh*s/h, w/2f, h/2f); view.setTransform(m);
    }

    @Override public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h){ surface=new Surface(st); if (visible && a.settings.wallOn()) play(); }
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h){ if (mp!=null) crop(mp.getVideoWidth(), mp.getVideoHeight()); }
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture st){ release(); if (surface!=null) surface.release(); surface=null; return true; }
    @Override public void onSurfaceTextureUpdated(SurfaceTexture st){}
}
