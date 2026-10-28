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
    final MainActivity a;
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
    }

    /** Pref changed or home became visible: show + play when on, stop + release when off. */
    void apply(){
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
            try { a.pool.execute(()->{ List<String> got=query(b); a.h.post(()->{ unbind(); rebinds=0; uris.clear(); uris.addAll(got); android.util.Log.i("ShieldHome","wallpaper aerial items="+got.size()); if (visible && a.settings.wallOn()) play(); }); }); } catch (java.util.concurrent.RejectedExecutionException x){ unbind(); }
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
        mp.setOnPreparedListener(p->{ prepared=true; fails=0; crop(p.getVideoWidth(), p.getVideoHeight()); if (resumePos>0){ p.seekTo(resumePos); resumePos=0; } if (visible) p.start(); view.animate().alpha(1f).setDuration(FADE_MS).start(); scrim.animate().alpha(1f).setDuration(FADE_MS).start(); });
        mp.setOnCompletionListener(p->{ fails=0; next(); });
        mp.setOnErrorListener((p,w,x)->{ android.util.Log.i("ShieldHome","wallpaper error "+w+"/"+x); next(); return true; });
        try { mp.setDataSource(a, android.net.Uri.parse(uris.get(idx%uris.size()))); mp.prepareAsync(); } catch (Exception e){ next(); }
    }
    static Shader scrimShader(float h){
        int[] col=new int[WALL_DIM_STOPS]; float[] pos=new float[WALL_DIM_STOPS];
        for (int i=0;i<WALL_DIM_STOPS;i++){ float t=i/(float)(WALL_DIM_STOPS-1); pos[i]=t; col[i]=Color.argb(Math.round(WALL_TOP_ALPHA+(WALL_DIM_ALPHA-WALL_TOP_ALPHA)*t*t*(3-2*t)),0,0,0); }
        return new LinearGradient(0,h*WALL_DIM_START,0,h*WALL_DIM_END, col, pos, Shader.TileMode.CLAMP);
    }
    /** Advance on completion/error; async, and gives up after a full pass of failures (no recursion on bad URIs). */
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
