package com.chris.shieldhome;

import android.app.*;
import android.content.Intent;
import android.graphics.*;
import android.os.IBinder;
import android.view.*;

/** Screen dimmer: a full-screen, non-touchable translucent black overlay (like Twilight). Needs SYSTEM_ALERT_WINDOW. */
public class DimService extends Service {
    View v; WindowManager wm;
    @Override public IBinder onBind(Intent i){ return null; }
    @Override public int onStartCommand(Intent i, int f, int id){
        int pct = i==null ? getSharedPreferences("home",MODE_PRIVATE).getInt("dimpct",0) : i.getIntExtra("pct", getSharedPreferences("home",MODE_PRIVATE).getInt("dimpct",0));
        getSharedPreferences("home",MODE_PRIVATE).edit().putInt("dimpct",pct).apply();
        if (pct<=0){ stopSelf(); return START_NOT_STICKY; }
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("dim","Screen dimmer",NotificationManager.IMPORTANCE_MIN));
        startForeground(1, new Notification.Builder(this,"dim").setContentTitle("Screen dimmer on").setSmallIcon(android.R.drawable.ic_menu_view).build());
        wm = getSystemService(WindowManager.class);
        if (v==null){
            v = new View(this); v.setBackgroundColor(Color.BLACK);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
            lp.alpha = android.os.Build.VERSION.SDK_INT>=31 ? 0.8f : 1f; // Android 12+ caps untrusted overlays at 0.8
            wm.addView(v, lp);
        }
        v.setAlpha(pct/100f);
        return START_STICKY;
    }
    @Override public void onDestroy(){ if (v!=null && wm!=null) wm.removeView(v); v=null; super.onDestroy(); }
}
