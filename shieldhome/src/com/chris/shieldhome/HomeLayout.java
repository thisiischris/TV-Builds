package com.chris.shieldhome;

import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

class HomeLayout {
    final MainActivity a;
    HomeLayout(MainActivity a) { this.a = a; }
    void place(View v, float x, float y, float w, float hh){ FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w<0?-2:a.p(w), hh<0?-2:a.p(hh)); lp.leftMargin=a.p(x); lp.topMargin=a.p(y); a.root.addView(v, lp); }
    void build() {
        a.root = new FrameLayout(a);
        a.root.setBackgroundResource(R.drawable.bg);
        a.root.setClipChildren(false);

        a.wxPill = new PillView(a, a, "", 4, false); a.wxPill.setFocusable(false); a.wxPill.setClickable(false);
        a.timePill = new PillView(a, a, "", 4, false); a.timePill.setFocusable(false); a.timePill.setClickable(false);
        LinearLayout tr = new LinearLayout(a); tr.setGravity(Gravity.CENTER_VERTICAL);
        tr.addView(a.wxPill, new LinearLayout.LayoutParams(a.p(28), a.p(28)));
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(a.p(8), a.p(28));
        tr.addView(new View(a), gap);
        tr.addView(a.timePill, new LinearLayout.LayoutParams(a.p(88), a.p(28)));
        a.clk = tr;
        a.root.addView(a.clk, a.lp(-2,-2,Gravity.TOP|Gravity.END,0,a.p(17),a.p(46),0));

        // Quick settings a.gear + inline options
        a.gear = new PillView(a, a, "", 5, false); a.gear.setId(View.generateViewId());
        a.gear.setOnClickListener(v->a.openDimSlide());
        a.gear.setOnFocusChangeListener((v,f)->{ if (!f && a.dimSlide) a.closeDimSlide(); });
        place(a.gear, 35, 17, 28, 28);
        PillView search = a.searchV = new PillView(a, a, "Search", 3, false);
        search.setOnFocusChangeListener((v,f)->{ if (f && !a.dimSlide) a.expandSearch(); });
        place(search, 71, 17, 104, 28);
        a.searchSplit = new LinearLayout(a); a.searchSplit.setOrientation(LinearLayout.HORIZONTAL); a.searchSplit.setGravity(Gravity.CENTER_VERTICAL);
        a.searchSplit.setVisibility(View.GONE);
        a.speakV = new PillView(a, a, "", 6, false); a.typeV = new PillView(a, a, "", 7, false);
        a.speakV.setId(View.generateViewId()); a.typeV.setId(View.generateViewId());
        a.speakV.setNextFocusRightId(a.typeV.getId()); a.typeV.setNextFocusLeftId(a.speakV.getId());
        a.speakV.setNextFocusDownId(View.NO_ID); a.typeV.setNextFocusDownId(View.NO_ID);
        a.speakV.setNextFocusUpId(View.NO_ID); a.typeV.setNextFocusUpId(View.NO_ID);
        a.speakV.setOnClickListener(v->a.openAssistant()); a.typeV.setOnClickListener(v->a.openSearchField());
        a.speakV.setOnFocusChangeListener((v,f)->{ if (!f) a.h.post(a::maybeCollapseSearch); });
        a.typeV.setOnFocusChangeListener((v,f)->{ if (!f) a.h.post(a::maybeCollapseSearch); });
        a.searchSplit.addView(a.speakV, new LinearLayout.LayoutParams(a.p(49), a.p(28)));
        a.searchSplit.addView(new View(a), new LinearLayout.LayoutParams(a.p(6), a.p(28)));
        a.searchSplit.addView(a.typeV, new LinearLayout.LayoutParams(a.p(49), a.p(28)));
        place(a.searchSplit, 71, 17, -2, 28);
        a.searchField = new EditText(a);
        a.searchField.setSingleLine(true);
        a.searchField.setHint("Search");
        a.searchField.setTextColor(Color.rgb(232,232,240));
        a.searchField.setHintTextColor(Color.argb(140,232,232,240));
        a.searchField.setHighlightColor(Color.argb(80,64,156,255));
        a.ts(a.searchField, 12);
        a.searchField.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        a.searchField.setIncludeFontPadding(false);
        a.searchField.setGravity(Gravity.CENTER_VERTICAL);
        a.searchField.setPadding(a.p(14), 0, a.p(14), 0);
        a.searchField.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        a.searchField.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        a.searchField.setMinHeight(0); a.searchField.setMinimumHeight(0);
        a.searchField.setFocusableInTouchMode(true);
        GradientDrawable fieldBg=new GradientDrawable();
        fieldBg.setCornerRadius(a.p(14));
        fieldBg.setColor(Color.argb(185,102,100,119));
        fieldBg.setStroke(1, Color.argb(40,255,255,255));
        a.searchField.setBackground(fieldBg);
        a.searchField.setVisibility(View.GONE);
        a.searchField.setOnEditorActionListener((v,id,ev)->{
            if (id!=android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH && id!=android.view.inputmethod.EditorInfo.IME_ACTION_DONE) return false;
            String q=v.getText().toString().trim();
            if (!q.isEmpty()) a.launchSearch(q);
            return true;
        });
        a.searchField.setOnFocusChangeListener((v,f)->{ if (!f) a.closeSearchField(); });
        place(a.searchField, 71, 17, 200, 28);
        a.dimBar = new PillView(a, a, "", 5, false);
        a.dimBar.setFocusable(false); a.dimBar.setClickable(false); a.dimBar.setVisibility(View.GONE);
        a.dimBar.setPivotX(a.p(100)); a.dimBar.setPivotY(a.p(14));
        place(a.dimBar, 71, 17, 200, 28);

        FrameLayout hero = new FrameLayout(a); hero.setClipChildren(false);
        FrameLayout artHost = new FrameLayout(a); artHost.setClipChildren(true);
        ImageView art = new ImageView(a); art.setImageResource(R.drawable.hero); art.setScaleType(ImageView.ScaleType.FIT_XY);
        a.heroArt2 = new ImageView(a); a.heroArt2.setScaleType(ImageView.ScaleType.FIT_XY); a.heroArt2.setAlpha(0f);
        artHost.addView(art, new FrameLayout.LayoutParams(-1,-1));
        artHost.addView(a.heroArt2, new FrameLayout.LayoutParams(-1,-1));
        hero.addView(artHost, a.lp(-1, a.p(400), Gravity.TOP, 0,0,0,0));
        LinearLayout meta = new LinearLayout(a); meta.setOrientation(LinearLayout.VERTICAL);
        a.heroArt = art;
        a.heroBadge = new ImageView(a); a.heroBadge.setScaleType(ImageView.ScaleType.FIT_START); a.heroBadge.setAlpha(0.7f);
        a.heroLabel = a.text("", 11, Color.rgb(190,188,205), "sans-serif-medium");
        a.heroTitle = a.text("Your Library", 23, Color.rgb(220,220,230), "sans-serif-medium");
        a.heroTitle.setSingleLine(false); a.heroTitle.setMaxLines(2); a.heroTitle.setEllipsize(android.text.TextUtils.TruncateAt.END); a.heroTitle.setHorizontallyScrolling(false);
        a.heroTitle.setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NONE); a.heroTitle.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_SIMPLE);
        a.heroSub = a.text("Movies and shows from your Plex Media Server", 14, Color.rgb(190,188,205), "sans-serif");
        a.heroSub.setLineSpacing(a.p(3),1f); a.heroSub.setSingleLine(false); a.heroSub.setMaxLines(3); a.heroSub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        a.heroSub.setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NONE); a.heroSub.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_SIMPLE);
        a.heroLabel.setSingleLine(false); a.heroLabel.setMaxLines(2); a.heroLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);
        a.heroLabel.setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NONE); a.heroLabel.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_SIMPLE);
        a.heroLabel.setVisibility(View.GONE);
        a.heroFocus = new View(a); a.heroFocus.setBackgroundColor(Color.WHITE); a.heroFocus.setAlpha(1f);
        meta.addView(a.heroBadge, new LinearLayout.LayoutParams(a.p(80), a.p(22)));
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(-2,-2); ml.topMargin=a.p(6); meta.addView(a.heroLabel, ml);
        LinearLayout.LayoutParams mt = new LinearLayout.LayoutParams(-1,-2); mt.topMargin=a.p(10); meta.addView(a.heroTitle, mt);
        LinearLayout.LayoutParams mf = new LinearLayout.LayoutParams(a.p(40), Math.max(2,a.p(1.4f))); mf.topMargin=a.p(6); meta.addView(a.heroFocus, mf);
        LinearLayout.LayoutParams ms = new LinearLayout.LayoutParams(-1,-2); ms.topMargin=a.p(8); meta.addView(a.heroSub, ms);
        a.heroMeta = meta;
        hero.addView(meta, a.lp(a.p(360),-2,Gravity.TOP|Gravity.START,a.p(35),a.p(72),0,0));
        a.heroBtn = new PillView(a, a, "Play Now", 4, true);
        a.heroBtn.setAlpha(0f); a.heroBtn.setTranslationY(a.p(8));
        a.heroBtn.setOnClickListener(v->a.heroAction());
        hero.addView(a.heroBtn, a.lp(a.p(138), a.p(34), Gravity.TOP|Gravity.START, a.p(35), a.p(237), 0, 0));
        a.heroDots = new HeroDots(a);
        hero.addView(a.heroDots, a.lp(-2, a.p(28), Gravity.TOP|Gravity.START, 0, a.p(270), 0, 0));
        View.OnLayoutChangeListener dotsLay=(v,a,b,c,d,e,f,g,h2)->HomeLayout.this.a.placeDots();
        hero.addOnLayoutChangeListener(dotsLay);
        a.heroDots.addOnLayoutChangeListener(dotsLay);
        art.post(()->{ int w=art.getWidth(), h=art.getHeight(); if (w>0){ Drawable d=art.getDrawable(); if (d!=null){ int iw=Math.max(1,d.getIntrinsicWidth()), ih=Math.max(1,d.getIntrinsicHeight()); Bitmap src=Bitmap.createBitmap(iw,ih,Bitmap.Config.ARGB_8888); Canvas cv=new Canvas(src); d.setBounds(0,0,iw,ih); d.draw(cv); art.setImageBitmap(a.maskHero("hero", src, w, h)); } } });
        a.heroG = hero; hero.setFocusable(false); hero.setClickable(false);
        a.heroBtn.setOnFocusChangeListener((x,f)-> a.showHeroAction(f));
        a.root.addView(hero, 0, a.lp(-1, a.p(400), Gravity.TOP, 0,0,0,0));

        a.tabBar = new SegBar(a); a.tabPlay=a.tabBar.play; a.tabLive=a.tabBar.live;
        place(a.tabBar, 35, 298, -2, 31);

        // Cards row: 5-tile viewport; clip off so scale+glow can draw; 6th hidden by alpha
        a.cardWrap = new FrameLayout(a);
        a.cardWrap.setClipChildren(false); a.cardWrap.setClipToPadding(false);
        a.cardScroll = new HorizontalScrollView(a){
            @Override protected int computeScrollDeltaToGetChildRectOnScreen(Rect r){ return 0; }
        };
        a.cardScroll.setHorizontalScrollBarEnabled(false);
        a.cardScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        a.cardScroll.setClipChildren(false); a.cardScroll.setClipToPadding(false);
        a.cardScroll.setPadding(a.p(20),a.p(20),a.p(20),a.p(20));
        a.cardRow = new LinearLayout(a); a.cardRow.setClipChildren(false); a.cardRow.setClipToPadding(false);
        a.cardScroll.addView(a.cardRow, new ViewGroup.LayoutParams(-2,-1));
        a.cardWrap.addView(a.cardScroll, new FrameLayout.LayoutParams(-1,-1));
        place(a.cardWrap, 15, 320, 994, 160);
        a.cardScroll.getViewTreeObserver().addOnScrollChangedListener(a::updateCardAlphas);

        a.homeGrid = new GridLayout(a); a.homeGrid.setColumnCount(6); a.homeGrid.setClipChildren(false); a.homeGrid.setClipToPadding(false);
        place(a.homeGrid, 35, MainActivity.APP_GRID_Y, 954, MainActivity.APP_H+MainActivity.APP_VG);
        a.ensureDim();
        a.buildHomeGrid();

        a.setContentView(a.root);
        a.h.post(()->{ if (a.tabPlay.getVisibility()==View.VISIBLE) a.tabPlay.requestFocus(); else a.tabLive.requestFocus(); });
    
    }
}
