package com.chris.shieldhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.concurrent.ExecutorService;

/** Sampled decode + LruCache. Detached views are ignored. */
class ImageLoader {
    final Context ctx;
    final ExecutorService pool;
    final Handler h;
    final LruCache<String, Bitmap> cache;

    ImageLoader(Context ctx, ExecutorService pool, Handler h) {
        this.ctx = ctx.getApplicationContext();
        this.pool = pool;
        this.h = h;
        int kb = (int) (Runtime.getRuntime().maxMemory() / 1024 / 8);
        cache = new LruCache<String, Bitmap>(Math.max(1024, kb)) {
            protected int sizeOf(String k, Bitmap b) { return Math.max(1, b.getByteCount() / 1024); }
        };
    }

    void load(ImageView iv, String uri0, boolean fit, int tw, int th) {
        if (uri0 == null || iv == null) return;
        iv.setTag(uri0);
        String k = key(uri0, tw, th);
        Bitmap hit = cache.get(k);
        if (hit != null) { apply(iv, hit, fit); return; }
        pool.execute(() -> {
            Bitmap bm = decode(uri0, tw, th);
            if (bm != null) cache.put(k, bm);
            h.post(() -> {
                if (bm == null || iv.getWindowToken() == null || !uri0.equals(iv.getTag())) return;
                apply(iv, bm, fit);
            });
        });
    }

    void loadFirstAvailable(ImageView iv, String[] uris, boolean fit, int tw, int th) {
        if (iv == null || uris == null || uris.length == 0) return;
        StringBuilder tag = new StringBuilder("fallback:");
        for (String uri : uris) if (uri != null && !uri.isEmpty()) tag.append(uri).append('|');
        iv.setTag(tag.toString());
        pool.execute(() -> {
            Bitmap bm = null;
            for (String uri : uris) {
                if (uri == null || uri.isEmpty()) continue;
                String k = key(uri, tw, th);
                bm = cache.get(k);
                if (bm == null) {
                    bm = decode(uri, tw, th);
                    if (bm != null) cache.put(k, bm);
                }
                if (bm != null) break;
            }
            final Bitmap result = bm;
            h.post(() -> {
                if (result == null || iv.getWindowToken() == null || !tag.toString().equals(iv.getTag())) return;
                apply(iv, result, fit);
            });
        });
    }

    static void apply(ImageView iv, Bitmap bm, boolean fit) {
        if (iv.getScaleType() != ImageView.ScaleType.FIT_END)
            iv.setScaleType(fit ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.CENTER_CROP);
        iv.setImageBitmap(bm);
    }

    static String key(String u, int w, int h) { return u + "|" + w + "x" + h; }

    Bitmap decode(String uri0, int tw, int th) {
        boolean crop = uri0.endsWith("#cwcrop");
        String uri = crop ? uri0.substring(0, uri0.length() - 7) : uri0;
        InputStream in = null;
        HttpURLConnection conn = null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            in = open(uri);
            if (in == null) return null;
            BitmapFactory.decodeStream(in, null, bounds);
            close(in, null);
            in = null;
            int ss = 1;
            if (tw > 0 && th > 0 && bounds.outWidth > 0 && bounds.outHeight > 0)
                while (bounds.outWidth / ss / 2 >= tw && bounds.outHeight / ss / 2 >= th) ss *= 2;
            in = open(uri);
            if (in == null) return null;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = ss;
            Bitmap b0 = BitmapFactory.decodeStream(in, null, o);
            if (crop && b0 != null) {
                int nh = Math.max(1, Math.round(b0.getHeight() * 0.80f));
                Bitmap cut = Bitmap.createBitmap(b0, 0, 0, b0.getWidth(), nh);
                if (cut != b0) b0.recycle();
                b0 = cut;
            }
            return b0;
        } catch (Exception e) {
            android.util.Log.w("ShieldHome", "img " + uri + " " + e);
            return null;
        } finally {
            close(in, conn);
        }
    }

    InputStream open(String uri) throws Exception {
        if (uri.startsWith("http")) {
            HttpURLConnection c = (HttpURLConnection) new java.net.URL(uri).openConnection();
            c.setConnectTimeout(5000);
            c.setReadTimeout(8000);
            return c.getInputStream();
        }
        return ctx.getContentResolver().openInputStream(Uri.parse(uri));
    }

    static void close(InputStream in, HttpURLConnection c) {
        try { if (in != null) in.close(); } catch (Exception ignored) {}
        if (c != null) c.disconnect();
    }
}
