package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Paint;
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
 * Prateleira Continuar/Recomendados acima da dock, num container próprio no mesmo material
 * de vidro da dock (frost + borda). Duas seções em páginas; 2 posters grandes por página.
 * Left/Right no título ou na borda dos cards troca a seção. Segue a dock no scroll.
 */
final class AboveDock {

    interface Host {
        /** Up from the section title: the top bar (Inputs, Wi-Fi, Settings). */
        void onTopBar();

        /** The shelf changed height: the name and the top shelf above it must move again. */
        void onShelfLayout();
    }

    private static final int PAGE_CONTINUE = 0, PAGE_RECOMMENDED = 1;
    static final int MAX_CARDS = 2;
    private static final float TITLE_DP = 24, GAP_DP = 14;
    /** Folga sob o relógio antes do container. */
    private static final float TOP_SAFE_DP = 100;
    /** Padding interno do container (como a folga da dock em volta dos ícones). */
    static final float SHELL_PAD_H_DP = 16, SHELL_PAD_V_DP = 14;
    static final float SHELL_RADIUS_DP = 26;
    /** Título (2 linhas) + canal sob o poster. */
    static final float LABEL_DP = 56;
    /** Folga extra no topo do container para o foco (scale) não cobrir o título. */
    private static final float FOCUS_ROOM_DP = 6;
    private static final float CHROME_DP =
            SHELL_PAD_V_DP * 2f + FOCUS_ROOM_DP + TITLE_DP + 8f + 4f + LABEL_DP + 8f + 14f + GAP_DP;

    /** Texto claro sobre o container de vidro (mesma família da dock apple_style). */
    private static final int TITLE_ON_SHELL = Apple.TEXT;
    private static final int HINT_ON_SHELL = Apple.TEXT_2;

    /**
     * Padding-top mínimo da grade para caber o container com 2 posters acima da dock.
     */
    static int shelfClearance(int innerWidth, int gridHeight, float density) {
        int gap = Math.round(16 * density);
        int shell = Math.round(SHELL_PAD_H_DP * density) * 2;
        int w = Math.max(1, (innerWidth - shell - gap) / 2);
        int posterH = Math.round(w * 9f / 16f);
        int cap = Math.round(gridHeight * 0.24f);
        if (posterH > cap) posterH = Math.max(Math.round(110 * density), cap);
        int need = Math.round((TOP_SAFE_DP + CHROME_DP) * density) + posterH;
        return Math.min(need, Math.round(gridHeight * 0.64f));
    }

    /**
     * Altura máxima do poster: o que sobra no container entre o relógio e a dock.
     * Nunca força um piso maior que o espaço livre (evita cobrir o relógio).
     */
    static int maxPosterHeight(RecyclerView grid, float density) {
        int room = grid.getPaddingTop();
        if (room <= 0) room = Math.round(grid.getHeight() * 0.42f);
        int max = room - Math.round((TOP_SAFE_DP + CHROME_DP) * density);
        int floor = Math.round(100 * density);
        if (max < floor) return Math.max(Math.round(80 * density), max);
        return max;
    }

    /** Largura útil dentro do container (desconta gutter da grade + pad do shell). */
    static int contentWidth(RecyclerView grid, float density) {
        int shell = Math.round(SHELL_PAD_H_DP * density) * 2;
        return Math.max(1, grid.getWidth() - grid.getPaddingLeft() - grid.getPaddingRight() - shell);
    }

    private final Activity activity;
    private final Host host;
    private final ContinueRow continueRow;
    private final RecommendedRow recommendedRow;
    private final RecyclerView grid;
    private final float density;
    private final boolean animations;

    private final LinearLayout view;
    private final LinearLayout shell;
    private final TextView title;
    private final TextView hint;
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
        this.animations = activity.getResources().getBoolean(R.bool.animations);

        view = new LinearLayout(activity);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setClipChildren(false);
        view.setClipToPadding(false);
        view.setVisibility(View.GONE);

        shell = new LinearLayout(activity);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setClipChildren(false);
        shell.setClipToPadding(false);
        int padH = Math.round(SHELL_PAD_H_DP * density);
        int padV = Math.round(SHELL_PAD_V_DP * density);
        shell.setPadding(padH, padV, padH, padV);
        GradientDrawable shellBg = new GradientDrawable();
        shellBg.setColor(activity.getColor(R.color.apple_glass));
        shellBg.setCornerRadius(SHELL_RADIUS_DP * density);
        shellBg.setStroke(Math.max(1, Math.round(density)), activity.getColor(R.color.apple_glass_border));
        shell.setBackground(shellBg);

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setClipChildren(false);

        title = new TextView(activity);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setSingleLine(true);
        title.setFocusable(true);
        title.setClickable(true);
        Apple.style(title, Apple.MEDIUM, TITLE_ON_SHELL, density);
        title.setOnFocusChangeListener((v, has) -> {
            updateHeader();
            if (has) grid.playSoundEffect(android.view.SoundEffectConstants.NAVIGATION_UP);
        });
        title.setOnClickListener(v -> focusFirst(0));
        title.setOnKeyListener((v, keyCode, event) -> {
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
                if (!switchPage(forward)) return true;
                grid.playSoundEffect(forward ? android.view.SoundEffectConstants.NAVIGATION_RIGHT
                        : android.view.SoundEffectConstants.NAVIGATION_LEFT);
                title.requestFocus();
                return true;
            }
            return false;
        });
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        hint = new TextView(activity);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        hint.setSingleLine(true);
        hint.setFocusable(false);
        hint.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        Apple.style(hint, Apple.REGULAR, HINT_ON_SHELL, density);
        hint.setVisibility(View.GONE);
        header.addView(hint, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.round(TITLE_DP * density));
        headerLp.topMargin = Math.round(FOCUS_ROOM_DP * density);
        headerLp.bottomMargin = Math.round(10 * density);
        shell.addView(header, headerLp);

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
        shell.addView(pages, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        view.addView(shell, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        Chevron chevron = new Chevron(activity, density);
        LinearLayout.LayoutParams chevLp = new LinearLayout.LayoutParams(Math.round(36 * density),
                Math.round(16 * density));
        chevLp.topMargin = Math.round(8 * density);
        chevLp.gravity = Gravity.CENTER_HORIZONTAL;
        view.addView(chevron, chevLp);

        FrameLayout parent = (FrameLayout) grid.getParent();
        parent.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));

        if (continueRow != null) continueRow.setOnChanged(this::refresh);
        if (recommendedRow != null) recommendedRow.setOnChanged(this::refresh);

        grid.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            syncInsets();
            place();
        });
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) { place(); }
        });
        view.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int h = b - t;
            if (h == lastHeight) return;
            lastHeight = h;
            place();
            host.onShelfLayout();
        });
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
        title.setFocusable(cont && rec);
        title.setClickable(cont && rec);
        updateHeader();
        applyPage(false);
        syncInsets();
        place();
    }

    private void showPage(int p) {
        if (!switchTo(p)) return;
        applyPage(true);
    }

    private boolean switchPage(boolean forward) {
        boolean cont = continueRow != null && continueRow.hasItems();
        boolean rec = recommendedRow != null && recommendedRow.hasItems();
        if (!cont || !rec) return false;
        int next = forward ? PAGE_RECOMMENDED : PAGE_CONTINUE;
        if (page == next) return true;
        if (!switchTo(next)) return false;
        applyPage(true);
        return true;
    }

    private boolean switchTo(int p) {
        boolean cont = continueRow != null && continueRow.hasItems();
        boolean rec = recommendedRow != null && recommendedRow.hasItems();
        if (p == PAGE_CONTINUE && !cont || p == PAGE_RECOMMENDED && !rec) return false;
        page = p;
        pagePicked = true;
        updateHeader();
        return true;
    }

    private void applyPage(boolean animate) {
        final View continueView = continueRow == null ? null : continueRow.view;
        final View recommendedView = recommendedRow == null ? null : recommendedRow.view;
        final View show = page == PAGE_CONTINUE ? continueView : recommendedView;
        final View hide = page == PAGE_CONTINUE ? recommendedView : continueView;

        if (hide != null && hide != show) {
            hide.animate().cancel();
            if (animate && Apple.motion(animations) && hide.getVisibility() == View.VISIBLE) {
                hide.animate().alpha(0f).setDuration(120).withEndAction(() -> {
                    hide.setVisibility(View.GONE);
                    hide.setAlpha(1f);
                }).start();
            } else {
                hide.setVisibility(View.GONE);
                hide.setAlpha(1f);
            }
        }
        if (show != null) {
            show.animate().cancel();
            boolean visible = show.getVisibility() == View.VISIBLE;
            if (animate && Apple.motion(animations) && !visible) {
                show.setAlpha(0f);
                show.setVisibility(View.VISIBLE);
                show.animate().alpha(1f).setDuration(180).start();
            } else {
                show.setAlpha(1f);
                show.setVisibility(View.VISIBLE);
            }
        }
    }

    private void updateHeader() {
        boolean c = page == PAGE_CONTINUE;
        boolean both = continueRow != null && continueRow.hasItems()
                && recommendedRow != null && recommendedRow.hasItems();
        int titleRes = c ? R.string.cw_title : R.string.rec_title;
        CharSequence next = title.getText();
        title.setText(titleRes);
        if (Apple.motion(animations) && next != null && !next.equals(title.getText())) {
            title.setAlpha(0.4f);
            title.animate().alpha(1f).setDuration(160).start();
        }
        boolean focused = title.isFocused();
        Apple.style(title, Apple.MEDIUM, TITLE_ON_SHELL, density);
        title.setAlpha(focused ? 1f : 0.88f);
        if (both) {
            hint.setVisibility(View.VISIBLE);
            hint.setText(c ? "›  " + activity.getString(R.string.rec_title)
                    : activity.getString(R.string.cw_title) + "  ‹");
            Apple.style(hint, Apple.REGULAR, HINT_ON_SHELL, density);
        } else {
            hint.setVisibility(View.GONE);
        }
    }

    /**
     * From a card at the row edge: Right on Continue opens Recommended (first card); Left on Recommended
     * opens Continue (last card). Returns false when there is no other section.
     */
    boolean switchFromEdge(boolean right) {
        boolean cont = continueRow != null && continueRow.hasItems();
        boolean rec = recommendedRow != null && recommendedRow.hasItems();
        if (!cont || !rec) return false;
        if (right && page == PAGE_CONTINUE) {
            showPage(PAGE_RECOMMENDED);
            pages.post(() -> {
                if (recommendedRow != null) recommendedRow.focusIndex(0);
            });
            return true;
        }
        if (!right && page == PAGE_RECOMMENDED) {
            showPage(PAGE_CONTINUE);
            pages.post(() -> {
                if (continueRow != null) continueRow.focusLast();
            });
            return true;
        }
        return false;
    }

    /** Alinha o container com o gutter horizontal da dock. */
    private void syncInsets() {
        int pad = grid.getPaddingLeft();
        if (view.getPaddingLeft() != pad) {
            view.setPadding(pad, 0, grid.getPaddingRight(), 0);
        }
    }

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
        float minY = TOP_SAFE_DP * density;
        // Prefere colar na dock; só sobe até o relógio se ainda couber sem cobrir os ícones.
        if (y < minY) y = minY;
        float dockTop = grid.getTop() + top - Math.round(GAP_DP * density);
        if (y + view.getHeight() > dockTop) y = dockTop - view.getHeight();
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

    boolean focusFirst(int column) {
        if (!shown()) return false;
        if (page == PAGE_CONTINUE && continueRow != null) return continueRow.focusColumn(column);
        if (page == PAGE_RECOMMENDED && recommendedRow != null) return recommendedRow.focusIndex(column);
        return false;
    }

    /** Up from a card: the section title when both sections exist, else the top bar. */
    boolean focusUp(int column) {
        if (!shown()) return false;
        if (title.isFocusable()) return title.requestFocus();
        host.onTopBar();
        return true;
    }

    /** Seta entre o container e a dock. */
    private static final class Chevron extends android.view.View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Chevron(android.content.Context context, float density) {
            super(context);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeWidth(Math.max(2f, 1.6f * density));
            paint.setColor(0xCCFFFFFF);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            canvas.drawLine(w * 0.22f, h * 0.72f, w * 0.5f, h * 0.28f, paint);
            canvas.drawLine(w * 0.5f, h * 0.28f, w * 0.78f, h * 0.72f, paint);
        }
    }
}
