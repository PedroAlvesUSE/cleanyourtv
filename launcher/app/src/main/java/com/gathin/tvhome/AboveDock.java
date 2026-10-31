package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * The shelf above the dock: Continue watching and Recommended as two pages with a tab header, so both fit in
 * the room the dock leaves above itself. Only one page is visible at a time; the tabs switch between them and
 * the page follows the dock when the grid scrolls. Nothing is shown while both rows are empty.
 *
 * Focus: Up from a card goes to the header, Down goes to the dock tile under the card. On the header, Left and
 * Right switch pages, Down goes to the first card of the page and Up goes back to the dock.
 */
final class AboveDock {

    interface Host {
        /** Up from the tab header: the top bar (Inputs, Wi-Fi, Settings). */
        void onTopBar();

        /** The shelf changed height: the name and the top shelf above it must move again. */
        void onShelfLayout();
    }

    private static final int PAGE_CONTINUE = 0, PAGE_RECOMMENDED = 1;
    private static final float HEADER_DP = 30, GAP_DP = 10, TAB_PAD_DP = 14, TAB_RADIUS_DP = 9;

    private final Activity activity;
    private final Host host;
    private final ContinueRow continueRow;
    private final RecommendedRow recommendedRow;
    private final RecyclerView grid;
    private final float density;

    private final LinearLayout view;
    private final LinearLayout header;
    private final TextView tabContinue, tabRecommended;
    private final FrameLayout pages;
    private int page = PAGE_CONTINUE;
    /** True once the user switched pages by hand: refresh then keeps the page they chose. */
    private boolean pagePicked;
    private int lastHeight = -1;

    AboveDock(Activity activity, Host host, ContinueRow continueRow, RecommendedRow recommendedRow,
              RecyclerView grid) {
        this.activity = activity;
        this.host = host;
        this.continueRow = continueRow;
        this.recommendedRow = recommendedRow;
        this.grid = grid;
        this.density = activity.getResources().getDisplayMetrics().density;

        view = new LinearLayout(activity);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setClipChildren(false);
        view.setClipToPadding(false);
        view.setVisibility(View.GONE);

        header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        header.setPadding(Math.round(56 * density), 0, 0, 0);
        header.setVisibility(View.GONE);
        tabContinue = tab(R.string.cw_title);
        tabRecommended = tab(R.string.rec_title);
        header.addView(tabContinue, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        second.leftMargin = Math.round(8 * density);
        header.addView(tabRecommended, second);
        view.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.round(HEADER_DP * density)));

        pages = new FrameLayout(activity);
        pages.setClipChildren(false);
        pages.setClipToPadding(false);
        if (continueRow != null) {
            pages.addView(continueRow.view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        if (recommendedRow != null) {
            pages.addView(recommendedRow.view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        view.addView(pages, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout parent = (FrameLayout) grid.getParent();
        parent.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));

        if (continueRow != null) continueRow.setOnChanged(this::refresh);
        if (recommendedRow != null) recommendedRow.setOnChanged(this::refresh);

        grid.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> place());
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) { place(); }
        });
        // The cards are laid out after the first place(): put the shelf in its final spot and tell Home.
        view.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int h = b - t;
            if (h == lastHeight) return;
            lastHeight = h;
            place();
            host.onShelfLayout();
        });
    }

    private TextView tab(int textRes) {
        TextView t = new TextView(activity);
        t.setText(textRes);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        t.setSingleLine(true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(Math.round(TAB_PAD_DP * density), 0, Math.round(TAB_PAD_DP * density), 0);
        t.setFocusable(true);
        t.setClickable(true);
        t.setBackground(pill(false));
        Apple.style(t, Apple.REGULAR, Apple.TEXT_2, density);
        t.setOnFocusChangeListener((v, has) -> {
            t.setBackground(pill(has));
            t.animate().cancel();
            if (has) {
                grid.playSoundEffect(android.view.SoundEffectConstants.NAVIGATION_RIGHT);
                t.animate().scaleX(1.04f).scaleY(1.04f).setDuration(120).start();
            } else {
                t.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
            }
        });
        t.setOnClickListener(v -> {
            showPage(t == tabContinue ? PAGE_CONTINUE : PAGE_RECOMMENDED);
        });
        t.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                host.onTopBar();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                return focusFirst(0);
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                boolean forward = keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;
                showPage(forward ? PAGE_RECOMMENDED : PAGE_CONTINUE);
                grid.playSoundEffect(forward ? android.view.SoundEffectConstants.NAVIGATION_RIGHT
                        : android.view.SoundEffectConstants.NAVIGATION_LEFT);
                (forward ? tabRecommended : tabContinue).requestFocus();
                return true;
            }
            return false;
        });
        return t;
    }

    private GradientDrawable pill(boolean focused) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(TAB_RADIUS_DP * density);
        d.setColor(focused ? 0x33FFFFFF : 0x00000000);
        return d;
    }

    void start() {
        if (continueRow != null) continueRow.start();
        if (recommendedRow != null) recommendedRow.start();
        refresh();
    }

    void stop() {
        if (continueRow != null) continueRow.stop();
        if (recommendedRow != null) recommendedRow.stop();
    }

    void destroy() {
        if (continueRow != null) continueRow.destroy();
        if (recommendedRow != null) recommendedRow.destroy();
    }

    boolean shown() {
        return view.getVisibility() == View.VISIBLE;
    }

    /** Height of the shelf in pixels, including the gap above the dock; 0 when it is not shown. */
    int visibleHeight() {
        return shown() ? view.getHeight() + Math.round(GAP_DP * density) : 0;
    }

    /** Called by the rows when their items change: picks the page and shows or hides the shelf. */
    private void refresh() {
        boolean cont = continueRow != null && continueRow.hasItems();
        boolean rec = recommendedRow != null && recommendedRow.hasItems();
        if (!cont && !rec) {
            view.setVisibility(View.GONE);
            return;
        }
        if (page == PAGE_CONTINUE && !cont) page = PAGE_RECOMMENDED;
        else if (page == PAGE_RECOMMENDED && !rec) page = PAGE_CONTINUE;
        else if (!pagePicked && cont) page = PAGE_CONTINUE;
        view.setVisibility(View.VISIBLE);
        header.setVisibility(cont && rec ? View.VISIBLE : View.GONE);
        updateTabs();
        applyPage();
        place();
    }

    private void showPage(int p) {
        boolean cont = continueRow != null && continueRow.hasItems();
        boolean rec = recommendedRow != null && recommendedRow.hasItems();
        if (p == PAGE_CONTINUE && !cont || p == PAGE_RECOMMENDED && !rec) return;
        page = p;
        pagePicked = true;
        updateTabs();
        applyPage();
    }

    private void applyPage() {
        if (continueRow != null) {
            continueRow.view.setVisibility(page == PAGE_CONTINUE && continueRow.hasItems()
                    ? View.VISIBLE : View.GONE);
        }
        if (recommendedRow != null) {
            recommendedRow.view.setVisibility(page == PAGE_RECOMMENDED && recommendedRow.hasItems()
                    ? View.VISIBLE : View.GONE);
        }
    }

    private void updateTabs() {
        boolean c = page == PAGE_CONTINUE;
        Apple.style(tabContinue, c ? Apple.MEDIUM : Apple.REGULAR, c ? Apple.TEXT : Apple.TEXT_2, density);
        Apple.style(tabRecommended, !c ? Apple.MEDIUM : Apple.REGULAR, !c ? Apple.TEXT : Apple.TEXT_2, density);
    }

    /** Puts the shelf just above the dock row, wherever the grid has scrolled it. */
    private void place() {
        if (view.getVisibility() != View.VISIBLE) return;
        View dock = dockChild();
        if (dock == null) {
            view.setTranslationY(-10000f);
            return;
        }
        RecyclerView.LayoutManager lm = grid.getLayoutManager();
        int top = lm == null ? dock.getTop() : lm.getDecoratedTop(dock);
        float y = grid.getTop() + top - view.getHeight() - Math.round(GAP_DP * density);
        view.setTranslationY(y);
    }

    private View dockChild() {
        if (grid.getAdapter() == null) return null;
        for (int i = 0; i < grid.getChildCount(); i++) {
            View c = grid.getChildAt(i);
            int pos = grid.getChildAdapterPosition(c);
            if (pos >= 0 && pos < grid.getAdapter().getItemCount() && pos < continueColumns()) return c;
        }
        return null;
    }

    private int continueColumns() {
        return continueRow != null ? continueRow.columns() : recommendedRow != null ? recommendedRow.columns() : 4;
    }

    /** Down from the dock tile in this column: the current page's first card. */
    boolean focusFirst(int column) {
        if (!shown()) return false;
        if (page == PAGE_CONTINUE && continueRow != null) return continueRow.focusColumn(column);
        if (page == PAGE_RECOMMENDED && recommendedRow != null) return recommendedRow.focusIndex(0);
        return false;
    }

    /** Up from a card: the header when both rows have items, else straight to the top bar. */
    boolean focusUp(int column) {
        if (!shown()) return false;
        if (header.getVisibility() == View.VISIBLE) {
            return (page == PAGE_CONTINUE ? tabContinue : tabRecommended).requestFocus();
        }
        host.onTopBar();
        return true;
    }
}
