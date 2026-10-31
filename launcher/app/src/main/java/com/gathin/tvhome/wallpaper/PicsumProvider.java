package com.gathin.tvhome.wallpaper;

import java.time.LocalDate;

/** Lorem Picsum: the date is the seed, so the picture is the same all day. No network call: it never fails. */
final class PicsumProvider implements WallpaperProvider {
    @Override public String id() { return "picsum"; }

    @Override
    public WallpaperItem fetch(LocalDate date, Http http) {
        String seed = "https://picsum.photos/seed/" + date + "/";
        return new WallpaperItem(id(), "Picsum", seed + "1920/1080", seed + "3840/2160", null, "picsum.photos",
                date.toString());
    }
}
