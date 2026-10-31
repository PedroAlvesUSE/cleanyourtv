# Continue watching, spec 1

An Android TV app can put what the user was watching on the launcher's "Continue watching" row. The row sits
under the dock. The launcher shows an app's items only after the TV owner allows the app in
Settings > Watch > Allowed apps, and it pins that decision to the app's signing certificate (as for
[module cards](card.md)).

The whole function is behind the `continue_watching` switch (off by default). With the switch off, the provider
accepts nothing and returns nothing.

## Provider

- Authority: `com.gathin.tvhome.continue`
- URI: `content://com.gathin.tvhome.continue/items`
- Exported, no permission. The caller is identified with `getCallingPackage()`.

| Operation | Who | Effect |
| :- | :- | :- |
| `insert` | any app | Upsert on (calling package, `item_id`). Returns the item URI, or `null` when ignored. |
| `update` | any app | Same as `insert`; the selection is ignored. |
| `delete` | any app | Deletes only the caller's items: `item_id=?` with one argument, or no selection for all of them. |
| `query` | the launcher only | Other callers get `null`. |

An app that is not allowed yet is listed in Settings as waiting, and its insert is ignored (`null`). Insert
again later, for example each time playback stops. If the app's signing key changes, it waits again.

## Columns

| Column | Type | Required | Rule |
| :- | :- | :- | :- |
| `item_id` | text | yes | 1 to 128 printable ASCII characters, no spaces. |
| `title` | text | yes | Up to 120 characters; control characters are removed. Shown on up to 2 lines. |
| `subtitle` | text | no | Up to 120 characters. Shown next to the app icon; the app name when empty. |
| `image_url` | text | no | `http` or `https`, up to 2048 characters, no spaces. 16:9 works best. Any other scheme rejects the item. |
| `deep_link` | text | no | Any scheme except `file`, up to 2048 characters. Opened as `ACTION_VIEW` with `setPackage(<your package>)`. Without one, or when nothing in your app takes it, the card opens your app. |
| `progress` | real | no | 0..1; values outside are clamped. An item at 0.98 or more counts as finished and is not shown. |
| `duration_ms` | integer | no | Media length. |
| `updated_at` | integer | — | Set by the launcher (ms since epoch) at each insert; the value you send is ignored. |

Limits: 20 items per app (the oldest go first), 200 in all, and an item not updated for 30 days is dropped.
The row shows the 12 newest items. The user can hold OK on a card to remove it.

## Media sessions (optional source)

With the `continue_media_session` switch on (off by default), the launcher also adds what plays in any app
through its media session: title, artwork (bitmap or `METADATA_KEY_*_URI`) and position. Sessions shorter than
2 minutes are skipped. These items have no deep link: the card opens the session's activity, or the app.
An app that writes through the provider wins: its media session items are removed and not added again.

The launcher reads sessions through a notification listener, which the TV has no screen to grant. Grant it
over adb:

```
adb shell cmd notification allow_listener com.gathin.tvhome/.ContinueListener
```

Settings > Watch > Notification access shows whether it is granted. No notification is read.

## Client example (Kotlin)

`AndroidManifest.xml` of the client (Android 11+ package visibility):

```xml
<queries>
    <provider android:authorities="com.gathin.tvhome.continue" />
</queries>
```

```kotlin
object ContinueWatching {
    private val ITEMS = Uri.parse("content://com.gathin.tvhome.continue/items")

    fun save(context: Context, id: String, title: String, imageUrl: String?, deepLink: String?,
             positionMs: Long, durationMs: Long) {
        val values = ContentValues().apply {
            put("item_id", id)
            put("title", title)
            put("image_url", imageUrl)
            put("deep_link", deepLink)
            put("progress", if (durationMs > 0) positionMs.toDouble() / durationMs else 0.0)
            put("duration_ms", durationMs)
        }
        try {
            context.contentResolver.insert(ITEMS, values)   // null: launcher absent, off, or app not allowed yet
        } catch (e: Exception) {
            // The launcher is not installed.
        }
    }

    fun remove(context: Context, id: String) {
        try {
            context.contentResolver.delete(ITEMS, "item_id=?", arrayOf(id))
        } catch (e: Exception) {
        }
    }
}
```

## Testing (debug builds)

`adb shell content insert` cannot pass a value that contains a colon. Debug builds add the receiver
`ContinueDebugReceiver`:

```
adb shell am broadcast -n com.gathin.tvhome/.ContinueDebugReceiver --es pkg com.google.android.youtube.tv \
    --ez approve true --es item_id ep1 --es title "A title" \
    --es image_url https://picsum.photos/id/1015/960/540 --ef progress 0.6
adb shell content query --uri content://com.gathin.tvhome.continue/items
```

## System Watch Next

The launcher does not read `TvContract.WatchNextPrograms`: other apps' rows need `ACCESS_ALL_EPG_DATA`, a
system permission.
