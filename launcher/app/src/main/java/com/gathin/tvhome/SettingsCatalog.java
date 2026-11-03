package com.gathin.tvhome;

/**
 * Every row of the launcher's Settings screen, in one list. A function with an on/off switch in
 * {@link Config#FEATURES} has exactly one BOOL row here (SettingsCatalogTest checks both ways), so nothing
 * the launcher does is left without a switch.
 */
final class SettingsCatalog {
    static final int BOOL = 0;
    static final int CHOICE = 1;
    static final int ACTION = 2;
    static final int SUBSCREEN = 3;
    /** The list of 4K sources: one switch per source, reordered with a long press on OK. */
    static final int SOURCES = 4;

    // Keys of the rows that are not feature switches. CHOICE keys are the launcher_settings keys they write.
    static final String WALLPAPER = Config.WALLPAPER_CHOICE_KEY;
    static final String ROTATION = Config.WALLPAPER_ROTATION_KEY;
    static final String TRANSITION = Config.WALLPAPER_TRANSITION_KEY;
    static final String OPACITY = Config.DOCK_GLASS_OPACITY_KEY;
    static final String COLUMNS = "columns";
    static final String ROWS = Config.ROWS_BELOW_DOCK_KEY;
    static final String FOLLOW_THEME = "follow_theme";
    static final String HOME_PICKER = "home_picker";
    static final String TV_SETTINGS = "tv_settings";
    static final String SCREENSAVER = "screensaver_settings";
    static final String SOURCES_LIST = "sources_list";
    static final String DIAG_RESULTS = "diag_results";
    static final String VERSION = "version";
    static final String LICENSE = "license";
    static final String PROJECT = "project";

    static final class SettingRow {
        final int title;
        final int description;
        final int type;
        /** Feature or launcher_settings key; null for a SUBSCREEN. */
        final String key;
        /** Large preview icon; 0 takes the icon of the screen the row is on. */
        final int icon;
        /** SUBSCREEN only: the rows it opens. */
        final SettingRow[] children;
        /** The switch is stored, but the function arrives in a later version. */
        final boolean soon;

        SettingRow(int title, int description, int type, String key, int icon, boolean soon, SettingRow... children) {
            this.title = title;
            this.description = description;
            this.type = type;
            this.key = key;
            this.icon = icon;
            this.soon = soon;
            this.children = children;
        }
    }

    private static SettingRow bool(String key, int title, int description) {
        return new SettingRow(title, description, BOOL, key, 0, false);
    }

    private static SettingRow soon(String key, int title, int description) {
        return new SettingRow(title, description, BOOL, key, 0, true);
    }

    private static SettingRow choice(String key, int title, int description) {
        return new SettingRow(title, description, CHOICE, key, 0, false);
    }

    private static SettingRow action(String key, int title, int description) {
        return new SettingRow(title, description, ACTION, key, 0, false);
    }

    private static SettingRow sources(String key, int title, int description) {
        return new SettingRow(title, description, SOURCES, key, 0, false);
    }

    private static SettingRow screen(int icon, int title, int description, SettingRow... rows) {
        return new SettingRow(title, description, SUBSCREEN, null, icon, false, rows);
    }

    /** The first screen: one row per section. */
    static final SettingRow ROOT = screen(R.drawable.ic_settings, R.string.settings, R.string.set_root_d,
            screen(R.drawable.ic_picture, R.string.wallpaper_label, R.string.set_wallpaper_d,
                    choice(WALLPAPER, R.string.set_source, R.string.set_source_d),
                    choice(ROTATION, R.string.wallpaper_rotation_label, R.string.set_rotation_d),
                    choice(TRANSITION, R.string.wallpaper_transition_label, R.string.set_transition_d),
                    bool(Config.FEATURE_BING, R.string.set_bing, R.string.set_bing_d),
                    bool("wallpaper_sources", R.string.set_sources, R.string.set_sources_d),
                    sources(SOURCES_LIST, R.string.set_src_order, R.string.set_src_order_d),
                    bool("wallpaper_credit", R.string.set_credit, R.string.set_credit_d),
                    bool("seasonal_wallpaper", R.string.set_seasonal, R.string.set_seasonal_d)),
            screen(R.drawable.ic_appearance, R.string.set_appearance, R.string.set_appearance_d,
                    bool("apple_style", R.string.set_apple, R.string.set_apple_d),
                    bool("parallax", R.string.set_parallax, R.string.set_parallax_d),
                    bool(Config.FEATURE_DOCK_GLOW, R.string.set_glow, R.string.set_glow_d),
                    bool(Config.AMBIENT_TINT_KEY, R.string.set_tint, R.string.set_tint_d),
                    bool("auto_accent", R.string.set_accent, R.string.set_accent_d),
                    bool(Config.DOCK_GLASS_KEY, R.string.set_glass, R.string.set_glass_d),
                    choice(OPACITY, R.string.dock_glass_opacity_label, R.string.set_opacity_d),
                    bool(Config.FEATURE_SKY, R.string.set_sky, R.string.set_sky_d),
                    bool(Config.FEATURE_ANIMATIONS, R.string.set_animations, R.string.set_animations_d),
                    bool(Config.FEATURE_ADVANCED_EFFECTS, R.string.advanced_effects, R.string.advanced_effects_warning)),
            screen(R.drawable.ic_apps, R.string.set_home, R.string.set_home_d,
                    choice(COLUMNS, R.string.columns_label, R.string.set_columns_d),
                    choice(ROWS, R.string.rows_below_dock_label, R.string.set_rows_d),
                    bool(Config.FEATURE_FILL_DOCK, R.string.set_fill, R.string.set_fill_d),
                    bool(Config.FEATURE_MODULE_CARDS, R.string.module_cards, R.string.set_cards_d),
                    bool("top_shelf", R.string.set_shelf, R.string.set_shelf_d),
                    soon("recents_row", R.string.set_recents, R.string.set_recents_d),
                    soon("clock_weather", R.string.set_weather, R.string.set_weather_d),
                    soon("greeting", R.string.set_greeting, R.string.set_greeting_d),
                    soon("global_search", R.string.set_search, R.string.set_search_d)),
            screen(R.drawable.ic_livetv, R.string.set_watch, R.string.set_watch_d,
                    bool(ContinueStore.FEATURE, R.string.set_continue, R.string.set_continue_d),
                    action(SystemActions.CONTINUE_APPS, R.string.set_cw_apps, R.string.set_cw_apps_d),
                    bool(ContinueStore.FEATURE_MEDIA, R.string.set_cw_media, R.string.set_cw_media_d),
                    action(SystemActions.CONTINUE_ACCESS, R.string.set_cw_access, R.string.set_cw_access_d),
                    bool("recommended_row", R.string.set_recommended, R.string.set_recommended_d),
                    soon("watch_later", R.string.set_later, R.string.set_later_d),
                    soon("quick_resume", R.string.set_resume, R.string.set_resume_d)),
            screen(R.drawable.ic_remote, R.string.set_remote, R.string.set_remote_d,
                    bool(Config.FEATURE_APP_KEYS, R.string.set_keys, R.string.set_keys_d),
                    bool("mic_youtube", R.string.set_mic, R.string.set_mic_d),
                    bool("quick_menu", R.string.set_quick_menu, R.string.set_quick_menu_d),
                    bool("phone_remote", R.string.set_phone, R.string.set_phone_d),
                    action(SystemActions.REMOTE_INFO, R.string.set_remote_info, R.string.set_remote_info_d)),
            screen(R.drawable.ic_home_settings, R.string.set_family, R.string.set_family_d,
                    soon("profiles", R.string.set_profiles, R.string.set_profiles_d),
                    bool("parental", R.string.set_parental, R.string.set_parental_d),
                    action(SystemActions.PARENTAL_PIN, R.string.set_parental_pin, R.string.set_parental_pin_d),
                    action(SystemActions.PARENTAL_APPS, R.string.set_parental_apps, R.string.set_parental_apps_d),
                    action(SystemActions.PARENTAL_LIMIT, R.string.set_parental_limit, R.string.set_parental_limit_d),
                    bool("guest_mode", R.string.set_guest, R.string.set_guest_d),
                    soon("night_mode", R.string.set_night, R.string.set_night_d),
                    bool("sleep_timer", R.string.set_sleep, R.string.set_sleep_d)),
            screen(R.drawable.ic_moon, R.string.set_saver, R.string.set_saver_d,
                    soon("ambient_mode", R.string.set_ambient, R.string.set_ambient_d),
                    soon("own_screensaver", R.string.set_own_saver, R.string.set_own_saver_d),
                    action(SCREENSAVER, R.string.set_saver_system, R.string.set_saver_system_d)),
            screen(R.drawable.ic_settings, R.string.set_system, R.string.set_system_d,
                    bool("tv_panel", R.string.set_panel, R.string.set_panel_d),
                    action(SystemActions.TV_INFO, R.string.set_tv_info, R.string.set_tv_info_d),
                    bool("quick_clean", R.string.set_clean, R.string.set_clean_d),
                    action(SystemActions.CLEAN_NOW, R.string.set_clean_now, R.string.set_clean_now_d),
                    bool("wallpaper_diag", R.string.set_diag, R.string.set_diag_d),
                    action(DIAG_RESULTS, R.string.set_diag_view, R.string.set_diag_view_d),
                    bool("config_backup", R.string.set_backup, R.string.set_backup_d),
                    action(SystemActions.BACKUP_OPEN, R.string.set_backup_open, R.string.set_backup_open_d),
                    action(FOLLOW_THEME, R.string.follow_theme, R.string.follow_theme_hint),
                    action(HOME_PICKER, R.string.home_settings, R.string.home_settings_help),
                    action(TV_SETTINGS, R.string.tv_settings, R.string.set_tv_d)),
            screen(R.drawable.ic_info, R.string.set_about, R.string.set_about_d,
                    action(VERSION, R.string.set_version, R.string.set_version_d),
                    action(LICENSE, R.string.set_license, R.string.set_license_d),
                    action(PROJECT, R.string.set_project, R.string.set_project_d)));

    private SettingsCatalog() {
    }
}
