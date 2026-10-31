package com.gathin.tvhome;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Process;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Continue watching provider (docs/specs/continue.md), content://com.gathin.tvhome.continue/items. Any app can
 * insert (an upsert on its own package and item_id) and delete its own items; only the launcher reads. An app
 * the TV owner has not allowed (ContinueApps) is ignored. With continue_watching off nothing is read or kept.
 */
public class ContinueProvider extends ContentProvider {
    static final String AUTHORITY = "com.gathin.tvhome.continue";
    static final Uri ITEMS = Uri.parse("content://" + AUTHORITY + "/items");
    static final String TAG = "TvHomeContinue";

    static final String ITEM_ID = "item_id";
    static final String TITLE = "title";
    static final String SUBTITLE = "subtitle";
    static final String IMAGE_URL = "image_url";
    static final String DEEP_LINK = "deep_link";
    static final String PROGRESS = "progress";
    static final String DURATION_MS = "duration_ms";
    static final String UPDATED_AT = "updated_at";
    static final String PACKAGE = "package";

    private static final String FILE = "continue.json";
    private static final Object LOCK = new Object();

    @Override
    public boolean onCreate() {
        return true;
    }

    private static boolean isItems(Uri uri) {
        List<String> seg = uri.getPathSegments();
        return seg.size() == 1 && "items".equals(seg.get(0));
    }

    private boolean on() {
        Context ctx = getContext();
        return ctx != null && Config.feature(ctx, ContinueStore.FEATURE);
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        String pkg = getCallingPackage();
        Context ctx = getContext();
        if (!isItems(uri) || values == null || pkg == null || !on()) return null;
        long now = System.currentTimeMillis();
        long token = Binder.clearCallingIdentity();
        try {
            if (!ContinueApps.allowed(ctx, pkg)) {
                Log.i(TAG, "ignored " + pkg + ": not allowed in Settings");
                return null;
            }
            String[] why = new String[1];
            ContinueStore.Item item = ContinueStore.fromValues(pkg, values.getAsString(ITEM_ID),
                    values.getAsString(TITLE), values.getAsString(SUBTITLE), values.getAsString(IMAGE_URL),
                    values.getAsString(DEEP_LINK), values.getAsDouble(PROGRESS), values.getAsLong(DURATION_MS), now, why);
            if (item == null) {
                Log.w(TAG, "rejected item from " + pkg + ": " + why[0]);
                return null;
            }
            if (!upsert(ctx, item)) return null;
            return ITEMS.buildUpon().appendPath(item.id).build();
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        String pkg = getCallingPackage();
        Context ctx = getContext();
        if (!isItems(uri) || pkg == null || !on()) return 0;
        // Only "item_id=?" with one argument, or nothing at all (every item of the caller).
        String id = null;
        if (selection != null && !selection.trim().isEmpty()) {
            String s = selection.replace(" ", "");
            if (!(ITEM_ID + "=?").equals(s) || selectionArgs == null || selectionArgs.length != 1) return 0;
            id = selectionArgs[0];
        }
        long token = Binder.clearCallingIdentity();
        try {
            return remove(ctx, pkg, id);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return insert(uri, values) != null ? 1 : 0;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        Context ctx = getContext();
        if (!isItems(uri) || !on()) return null;
        boolean self = Binder.getCallingUid() == Process.myUid();
        if (!self && !ContinueApps.debugShell(ctx, getCallingPackage())) return null;
        String[] cols = {PACKAGE, ITEM_ID, TITLE, SUBTITLE, IMAGE_URL, DEEP_LINK, PROGRESS, DURATION_MS, UPDATED_AT};
        MatrixCursor c = new MatrixCursor(cols);
        for (ContinueStore.Item it : load(ctx)) {
            c.addRow(new Object[]{it.pkg, it.id, it.title, it.subtitle, it.imageUrl, it.deepLink, it.progress,
                    it.durationMs, it.updatedAt});
        }
        return c;
    }

    @Override
    public String getType(Uri uri) {
        return isItems(uri) ? "vnd.android.cursor.dir/vnd." + AUTHORITY + ".item" : null;
    }

    // ---- store file ------------------------------------------------------------------------------

    static List<ContinueStore.Item> load(Context ctx) {
        synchronized (LOCK) {
            File f = new File(ctx.getFilesDir(), FILE);
            if (!f.isFile()) return new ArrayList<>();
            try {
                return ContinueStore.fromJson(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
            } catch (Exception e) {
                return new ArrayList<>();
            }
        }
    }

    private static void save(Context ctx, List<ContinueStore.Item> items) {
        File f = new File(ctx.getFilesDir(), FILE);
        File tmp = new File(ctx.getFilesDir(), FILE + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(ContinueStore.toJson(items).getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        } catch (Exception e) {
            Log.w(TAG, "save failed: " + e);
            return;
        }
        if (!tmp.renameTo(f)) Log.w(TAG, "rename failed");
    }

    private static void changed(Context ctx) {
        ctx.getContentResolver().notifyChange(ITEMS, null);
    }

    /** Adds or replaces one item (provider or media session). Returns false when the rules dropped it. */
    static boolean upsert(Context ctx, ContinueStore.Item item) {
        boolean added;
        synchronized (LOCK) {
            List<ContinueStore.Item> items = load(ctx);
            added = ContinueStore.upsert(items, item, System.currentTimeMillis());
            if (added) save(ctx, items);
        }
        if (added) changed(ctx);
        return added;
    }

    /** Removes one item, or every item of the package when id is null. */
    static int remove(Context ctx, String pkg, String id) {
        int n;
        synchronized (LOCK) {
            List<ContinueStore.Item> items = load(ctx);
            n = ContinueStore.remove(items, pkg, id);
            if (n > 0) save(ctx, items);
        }
        if (n > 0) changed(ctx);
        return n;
    }

    /** Continue watching switched off: the list and the saved pictures go. */
    static void clear(Context ctx) {
        synchronized (LOCK) {
            new File(ctx.getFilesDir(), FILE).delete();
        }
        ContinueImages.clear(ctx);
    }
}
