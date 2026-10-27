package com.chris.shieldhome;

import android.content.Intent;
import android.view.KeyEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

/** D-pad sections + one BACK path. ACTION_UP is not swallowed. */
class FocusController {
    final MainActivity a;
    final int[] lastIdx = new int[16];
    int lastCardX;
    int savedSec = -1, savedIdx = 0;
    boolean backLong, pendingRestore;

    FocusController(MainActivity a) { this.a = a; }

    void prepare() {
        pendingRestore = true;
        if (savedSec < 0) {
            savedSec = a.prefs().getInt("f_sec", -1);
            savedIdx = a.prefs().getInt("f_idx", 0);
            int ft = a.prefs().getInt("f_tab", 0);
            if (a.wnPickCache != null && a.wnPickCache.isEmpty()) ft = 1;
            if (ft != a.tab) a.tab = ft;
        }
    }

    void afterData() {
        if (!pendingRestore) return;
        pendingRestore = false;
        restoreNow();
    }

    /** Remember focused section+index before any overlay/transition. */
    void remember() {
        try {
            View f = a.getCurrentFocus();
            if (f == null) return;
            for (View x = f; x != null; x = x.getParent() instanceof View ? (View) x.getParent() : null)
                if (x == a.appMenu) return;
            int sc = secOf(f);
            if (sc < 0) return;
            View root = secRoot(f, sc);
            int idx = sc == 0 ? topIdx(root) : secItems(sc).indexOf(root);
            if (idx < 0) return;
            savedSec = sc;
            savedIdx = idx;
            if (sc == 3 && a.cardScroll != null) lastCardX = a.cardScroll.getScrollX();
        } catch (Exception e) {}
    }

    /** Posted requestFocus after layout. */
    void restore() {
        if (savedSec < 0 || a.appMenu != null) return;
        final View t = savedView();
        if (t == null) return;
        t.setFocusable(true);
        t.post(() -> {
            if (savedSec == 3 && a.cardScroll != null) a.cardScroll.scrollTo(lastCardX, 0);
            t.requestFocus();
            android.util.Log.i("ShieldHome", "focus restored " + name(t));
        });
    }

    void save() {
        remember();
        if (savedSec < 0) return;
        try {
            a.prefs().edit().putInt("f_sec", savedSec).putInt("f_idx", savedIdx).putInt("f_tab", a.tab).apply();
            android.util.Log.i("ShieldHome", "focus saved sec=" + savedSec + " idx=" + savedIdx);
        } catch (Exception e) {}
    }

    void restoreNow() {
        if (savedSec < 0) return;
        if (a.appMenu != null || a.editMode) return;
        if (savedSec >= 5) a.setDrawer(true);
        restore();
    }

    boolean dispatch(KeyEvent e) {
        int k = e.getKeyCode();
        boolean v = k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN;
        boolean hz = k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT;
        if (!(v || hz) || a.homeGrid == null || a.appMenu != null || a.editMode || a.dragIdx >= 0)
            return false;
        View f = a.getCurrentFocus();
        int sec = f == null ? -1 : secOf(f);
        if (sec < 0) return false;
        if (e.getAction() != KeyEvent.ACTION_DOWN) return false; // ponytail: let ACTION_UP through
        if (sec == 1 && hz) { a.heroStep(k == KeyEvent.KEYCODE_DPAD_RIGHT); return true; }
        if (k == KeyEvent.KEYCODE_DPAD_DOWN && sec >= 4 && sec == nSecs() - 1)
            return true;
        View t;
        if (v) t = navTarget(f, sec, k == KeyEvent.KEYCODE_DPAD_UP);
        else {
            List<View> it = secItems(sec);
            int ix = it.indexOf(secRoot(f, sec));
            int ni = ix + (k == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
            t = (ix >= 0 && ni >= 0 && ni < it.size()) ? it.get(ni) : f;
            if (t != f && ni >= 0 && sec >= 0 && sec < lastIdx.length) lastIdx[sec] = ni;
        }
        if (t != null && t != f) {
            if (t == a.heroBtn) { t.setVisibility(View.VISIBLE); t.setFocusable(true); }
            t.requestFocus();
            if (t.getParent() == a.homeGrid){
                int i = a.homeGrid.indexOfChild(t);
                if (a.drawerOpen && i < 6) a.setDrawer(false);
                else a.slideHome();
            }
            android.util.Log.i("ShieldHome", "nav " + name(f) + " -> " + name(t));
        }
        return true;
    }

    /** Overlays close themselves. Returns true if consumed (not the home short/long path). */
    boolean closeOverlay() {
        if (a.appMenu != null) { a.closeMenu(); return true; }
        if (a.dimSlide) { a.closeDimSlide(); return true; }
        if (a.editMode || a.dragIdx >= 0) { a.exitEdit(); return true; }
        if (a.drawerOpen) { a.setDrawer(false); a.focusHomeApp(); return true; }
        return false;
    }

    boolean keyDown(int k, KeyEvent e) {
        if (k != KeyEvent.KEYCODE_BACK) return false;
        if (closeOverlay()) return true;
        if (e.getRepeatCount() == 0) { e.startTracking(); backLong = false; }
        return true;
    }

    boolean keyLong(int k, KeyEvent e) {
        if (k != KeyEvent.KEYCODE_BACK || closeOverlay()) return k == KeyEvent.KEYCODE_BACK;
        backLong = true;
        android.util.Log.i("ShieldHome", "back long -> settings");
        try { a.startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception x) {}
        return true;
    }

    boolean keyUp(int k, KeyEvent e) {
        if (k != KeyEvent.KEYCODE_BACK) return false;
        if (e.isTracking()) {
            if (!backLong && !e.isCanceled() && !closeOverlay()) a.back();
            backLong = false;
            return true;
        }
        if (backLong) { backLong = false; return true; }
        return false;
    }

    List<View> secItems(int sec) {
        List<View> o = new ArrayList<>();
        if (sec == 0) {
            if (a.gear != null) o.add(a.gear);
            if (a.searchOpen){ if (a.speakV!=null) o.add(a.speakV); if (a.typeV!=null) o.add(a.typeV); }
            else if (a.searchV != null && a.searchV.getVisibility() == View.VISIBLE) o.add(a.searchV);
        } else if (sec == 1) { if (a.heroShown() && a.heroBtn != null) o.add(a.heroBtn); }
        else if (sec == 2) { if (a.tabPlay.getVisibility()==View.VISIBLE) o.add(a.tabPlay); o.add(a.tabLive); }
        else if (sec == 3) {
            for (int i = 0; i < a.cardRow.getChildCount(); i++) {
                View c = a.cardRow.getChildAt(i);
                if (c.isFocusable() && c.getVisibility() == View.VISIBLE) o.add(c);
            }
        } else {
            int r = sec - 4;
            for (int i = r * 6; i < Math.min(r * 6 + 6, a.homeGrid.getChildCount()); i++) {
                View c = a.homeGrid.getChildAt(i);
                if (c.isFocusable() && c.getVisibility() == View.VISIBLE) o.add(c);
            }
        }
        return o;
    }

    int nSecs() { return 4 + (a.homeGrid.getChildCount() + 5) / 6; }

    View secRoot(View f, int sec) {
        List<View> it = secItems(sec);
        View x = f;
        while (x != null) {
            if (it.contains(x)) return x;
            x = x.getParent() instanceof View ? (View) x.getParent() : null;
        }
        return f;
    }

    int secOf(View f) {
        if (a.homeGrid == null || a.cardRow == null) return -1;
        for (View x = f; x != null; x = x.getParent() instanceof View ? (View) x.getParent() : null) {
            if (x == a.gear || x == a.searchV || x == a.speakV || x == a.typeV) return 0;
            if (x == a.heroG) return 1;
            if (x == a.tabPlay || x == a.tabLive) return 2;
            if (x.getParent() == a.cardRow) return 3;
            if (x.getParent() == a.homeGrid) return 4 + a.homeGrid.indexOfChild(x) / 6;
        }
        return -1;
    }

    int topIdx(View v) {
        if (v == a.gear) return 0;
        if (v == a.searchV || v == a.speakV) return 1;
        if (v == a.typeV) return 2;
        return -1;
    }
    View savedView() {
        if (savedSec == 0) {
            if (savedIdx == 0) return a.gear;
            return a.searchV;
        }
        List<View> it = secItems(savedSec);
        int s2 = savedSec;
        while (it.isEmpty() && s2 < nSecs() - 1) it = secItems(++s2);
        if (it.isEmpty()) return null;
        return it.get(Math.min(savedIdx, it.size() - 1));
    }
    View tabV() { return a.tab == 0 && a.tabPlay.getVisibility()==View.VISIBLE ? a.tabPlay : a.tabLive; }
    View topTarget() { return a.searchOpen && a.speakV!=null ? a.speakV : (a.searchV!=null && a.searchV.getVisibility()==View.VISIBLE ? a.searchV : a.gear); }

    View navTarget(View f, int sec, boolean up) {
        View fr = secRoot(f, sec);
        List<View> cur = secItems(sec);
        int ix = Math.max(0, cur.indexOf(fr));
        if (sec >= 0 && sec < lastIdx.length) lastIdx[sec] = ix;
        if (sec == 3 && a.cardScroll != null) lastCardX = a.cardScroll.getScrollX();
        boolean multi = sec >= 3;
        for (int s2 = sec + (up ? -1 : 1); s2 >= 0 && s2 < nSecs(); s2 += up ? -1 : 1) {
            List<View> it = secItems(s2);
            if (it.isEmpty()) continue;
            if (s2 == 0) return (sec == 1) ? a.gear : topTarget();
            if (s2 == 2) return tabV();
            int ti = s2 == 3 ? lastIdx[3] : (s2 == 4 && sec == 3) ? 0 : (multi ? ix : (s2 < lastIdx.length ? lastIdx[s2] : 0));
            View dest = it.get(Math.max(0, Math.min(ti, it.size() - 1)));
            if (s2 == 3 && a.cardScroll != null) a.cardScroll.scrollTo(lastCardX, 0);
            if (s2 >= 5) a.setDrawer(true);
            return dest;
        }
        return f;
    }

    String name(View v) {
        if (v == a.gear) return "gear";
        if (v == a.searchV) return "search";
        if (v == a.speakV) return "speak";
        if (v == a.typeV) return "type";
        if (v == a.heroG || v == a.heroBtn) return "hero";
        if (v == a.tabPlay) return "tabPlay";
        if (v == a.tabLive) return "tabLive";
        if (v.getParent() == a.cardRow) return "card" + a.cardRow.indexOfChild(v);
        if (v.getParent() == a.homeGrid) return "app" + a.homeGrid.indexOfChild(v);
        return v.getClass().getSimpleName();
    }
}
