package com.coreyd97.burpcustomizer;

import com.formdev.flatlaf.IntelliJTheme;

import javax.swing.*;
import java.awt.Color;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;

/**
 * An IntelliJ theme layered on top of Burp's own look and feel defaults.
 *
 * Burp has shipped two different theme implementations, both of which are supported:
 * <ul>
 *   <li><b>Legacy</b>: <code>burp.theme.BurpDarkLaf</code> / <code>BurpLightLaf</code> classes, each with their own
 *   properties file, and custom keys such as <code>BurpPalette.*</code>.</li>
 *   <li><b>Modern</b>: a single <code>burp.theme.BurpLaf</code> whose properties reference a
 *   <code>Colors.*</code> design system (swatches, palettes and UI colours) which is loaded from
 *   <code>burp/theme/DarkTheme.json</code> / <code>LightTheme.json</code>.</li>
 * </ul>
 * Burp's classes are internal and not part of the Montoya API, so everything is done reflectively.
 */
public class CustomTheme extends IntelliJTheme.ThemeLaf {

    private static final String BURP_LAF = "burp.theme.BurpLaf";
    private static final String LEGACY_DARK_LAF = "burp.theme.BurpDarkLaf";
    private static final String LEGACY_LIGHT_LAF = "burp.theme.BurpLightLaf";
    private static final String THEME_FACTORY = "burp.theme.ThemeFactory";
    private static final String DARK_THEME_JSON = "burp/theme/DarkTheme.json";
    private static final String LIGHT_THEME_JSON = "burp/theme/LightTheme.json";

    private final Class<?> burpLaf;
    /** Burp's dark/light LaF class. Only present in legacy Burp versions. */
    private final Class<?> legacyVariantLaf;
    /** Burp's built-in Colors.* properties with our palette applied. Only used for modern Burp versions. */
    private final Properties modernDefaults;

    public CustomTheme(IntelliJTheme.ThemeLaf base) {
        super(base.getTheme());

        this.burpLaf = findBurpClass(BURP_LAF);
        if (this.burpLaf == null) {
            throw new IllegalStateException("Could not find Burp's look and feel (" + BURP_LAF + "). " +
                    "This version of Burp Suite is not supported.");
        }

        this.legacyVariantLaf = findBurpClass(isDark() ? LEGACY_DARK_LAF : LEGACY_LIGHT_LAF);
        if (this.legacyVariantLaf != null) {
            this.modernDefaults = null;
        } else {
            this.modernDefaults = loadBurpThemeProperties(isDark());
            this.modernDefaults.putAll(derivePalette(base.getDefaults(), isDark()));
        }
    }

    public boolean isLegacyBurp() {
        return legacyVariantLaf != null;
    }

    @Override
    protected ArrayList<Class<?>> getLafClassesForDefaultsLoading() {
        //Burp's properties are loaded over the top of FlatLaf's, then the IntelliJ theme is applied on top of both.
        ArrayList<Class<?>> lafClasses = super.getLafClassesForDefaultsLoading();
        lafClasses.add(burpLaf);
        if (legacyVariantLaf != null) lafClasses.add(legacyVariantLaf);
        return lafClasses;
    }

    @Override
    protected Properties getAdditionalDefaults() {
        if (modernDefaults != null) {
            Properties defaults = new Properties();
            defaults.putAll(modernDefaults);
            return defaults;
        }
        return legacyDefaults();
    }

    // ---- Burp class lookup ----

    private static Class<?> findBurpClass(String name) {
        Set<ClassLoader> loaders = new LinkedHashSet<>();
        loaders.add(ClassLoader.getSystemClassLoader());
        LookAndFeel current = UIManager.getLookAndFeel();
        if (current != null) loaders.add(current.getClass().getClassLoader());
        loaders.add(CustomTheme.class.getClassLoader());

        for (ClassLoader loader : loaders) {
            if (loader == null) continue;
            try {
                return Class.forName(name, false, loader);
            } catch (ClassNotFoundException | LinkageError ignored) {
            }
        }
        return null;
    }

    // ---- Modern Burp (Colors.* design system) ----

    /**
     * Asks Burp for the properties of its built-in dark or light theme.
     * These define every Colors.* key which BurpLaf.properties depends upon.
     */
    private static Properties loadBurpThemeProperties(boolean dark) {
        try {
            Class<?> factory = findBurpClass(THEME_FACTORY);
            if (factory == null) throw new ClassNotFoundException(THEME_FACTORY);

            Object builtIn = factory.getMethod("loadBuiltIn", String.class)
                    .invoke(null, dark ? DARK_THEME_JSON : LIGHT_THEME_JSON);

            Method getAdditionalDefaults = null;
            for (Class<?> c = builtIn.getClass(); c != null && getAdditionalDefaults == null; c = c.getSuperclass()) {
                try {
                    getAdditionalDefaults = c.getDeclaredMethod("getAdditionalDefaults");
                } catch (NoSuchMethodException ignored) {
                }
            }
            if (getAdditionalDefaults == null) throw new NoSuchMethodException("getAdditionalDefaults");
            getAdditionalDefaults.setAccessible(true);

            Properties properties = new Properties();
            properties.putAll((Properties) getAdditionalDefaults.invoke(builtIn));
            return properties;
        } catch (Exception | LinkageError e) {
            throw new IllegalStateException("Could not load Burp's built-in theme colours. " +
                    "This version of Burp Suite is not supported.", e);
        }
    }

    /**
     * Burp builds its whole UI from a handful of nine-colour palettes. We replace the three which define the overall
     * feel of the UI with colours taken from the IntelliJ theme, and leave the semantic ones (error, warning, etc.) alone.
     * <ul>
     *     <li>mono: backgrounds (1-5) through to text (6-8)</li>
     *     <li>primary: the accent colour. Burp's orange.</li>
     *     <li>secondary: selection and hover backgrounds. Burp's blue.</li>
     * </ul>
     */
    static Properties derivePalette(UIDefaults theme, boolean dark) {
        Properties palette = new Properties();

        Color background = color(theme, "Panel.background", "control");
        Color foreground = color(theme, "Label.foreground", "Panel.foreground", "controlText");
        if (background == null || foreground == null) return palette; //Nothing to work with. Stick with Burp's colours.

        Color editor = orElse(color(theme, "TextArea.background", "TextField.background"), background);
        Color disabled = orElse(color(theme, "Label.disabledForeground"), mix(background, foreground, 0.5));
        Color accent = orElse(color(theme, "Component.accentColor", "TabbedPane.underlineColor",
                "Component.focusColor", "Button.default.background"), foreground);
        Color selection = orElse(color(theme, "List.selectionBackground", "Table.selectionBackground",
                "textHighlight"), accent);

        Color[] mono = new Color[9];
        mono[1] = editor;
        mono[2] = background;
        mono[3] = mix(background, foreground, 0.08);
        mono[4] = mix(background, foreground, 0.16);
        mono[5] = mix(background, foreground, 0.30);
        if (dark) { //Burp's dark theme uses 6 for disabled text, 7 for body text and 8 for emphasis.
            mono[6] = disabled;
            mono[7] = foreground;
            mono[8] = mix(foreground, Color.WHITE, 0.5);
        } else { //Whereas its light theme uses 7 for disabled text, and 8 for body text.
            mono[6] = mix(background, foreground, 0.40);
            mono[7] = mix(background, foreground, 0.65);
            mono[8] = foreground;
        }
        putPalette(palette, "mono", dark ? Color.BLACK : Color.WHITE, mono);

        Color[] primary = new Color[9];
        primary[1] = mix(accent, Color.BLACK, 0.30);
        primary[2] = mix(accent, Color.BLACK, 0.12);
        primary[3] = mix(accent, Color.WHITE, 0.12);
        primary[4] = mix(accent, Color.WHITE, 0.22);
        primary[5] = mix(accent, Color.WHITE, 0.32);
        primary[6] = mix(accent, Color.WHITE, 0.45);
        primary[7] = mix(accent, Color.WHITE, 0.58);
        primary[8] = primary[7];
        putPalette(palette, "primary", accent, primary);

        Color[] secondary = new Color[9];
        if (dark) { //1 and 2 are used as hover and selection backgrounds.
            secondary[1] = mix(background, selection, 0.35);
            secondary[2] = mix(background, selection, 0.75);
            secondary[3] = mix(selection, Color.WHITE, 0.25);
            secondary[4] = mix(selection, Color.WHITE, 0.40);
            secondary[5] = mix(selection, Color.WHITE, 0.55);
            secondary[6] = mix(selection, Color.WHITE, 0.75);
            secondary[7] = mix(selection, Color.WHITE, 0.90);
        } else { //7 and 6 are used as hover and selection backgrounds, and must stay readable under dark text.
            secondary[1] = mix(selection, Color.BLACK, 0.50);
            secondary[2] = mix(selection, Color.BLACK, 0.25);
            secondary[3] = selection;
            secondary[4] = mix(background, selection, 0.70);
            secondary[5] = mix(background, selection, 0.50);
            secondary[6] = mix(background, selection, 0.30);
            secondary[7] = mix(background, selection, 0.15);
        }
        secondary[8] = secondary[7];
        putPalette(palette, "secondary", selection, secondary);

        //Burp hardcodes these for its own dark and light backgrounds, which clash with other backgrounds.
        palette.put("Colors.ui.editor.message.text", hex(foreground));
        palette.put("Colors.ui.editor.message.lineNumbers", hex(disabled));
        palette.put("Colors.ui.editor.message.currentLineBackground", hex(mix(editor, foreground, 0.06)));
        palette.put("Colors.ui.editor.message.gutterBorder", hex(mono[4]));
        palette.put("Colors.ui.editor.message.lozengeBackground", hex(mono[4]));

        return palette;
    }

    private static void putPalette(Properties properties, String name, Color core, Color[] shades) {
        properties.put("Colors.palette." + name + ".core", hex(core));
        for (int i = 1; i < shades.length; i++) {
            properties.put("Colors.palette." + name + "." + i, hex(shades[i]));
        }
    }

    private static Color color(UIDefaults defaults, String... keys) {
        for (String key : keys) {
            try {
                Color color = defaults.getColor(key);
                if (color != null) return color;
            } catch (RuntimeException ignored) {
            }
        }
        return null;
    }

    private static Color orElse(Color color, Color fallback) {
        return color != null ? color : fallback;
    }

    /** Linear blend from <code>from</code> (ratio 0) to <code>to</code> (ratio 1). */
    static Color mix(Color from, Color to, double ratio) {
        return new Color(
                (int) Math.round(from.getRed() + (to.getRed() - from.getRed()) * ratio),
                (int) Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * ratio),
                (int) Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * ratio));
    }

    private static String hex(Color color) {
        return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }

    // ---- Legacy Burp (BurpDarkLaf / BurpLightLaf) ----

    private static Properties legacyDefaults() {
        //This is actually run BEFORE the theme is loaded, so we need to use lazy loading to pull values from the theme.
        Properties defaults = new Properties();

        defaults.put("@accent", "lazy(Button.focusedBorderColor)");

        //Color Palettes. Dark needs lightening, light needs darkening
        putLegacyPalette(defaults, "ColourPalette.mono", 8);
        putLegacyPalette(defaults, "BurpPalette.mono", 11);
        putLegacyPalette(defaults, "DesignSystemPalette.grey", 12);

        defaults.put("ColourPalette.background5", "lazy(BurpPalette.mono0)");
        defaults.put("BurpPalette.blue1", "lazy(BurpPalette.mono2)");
        defaults.put("BurpPalette.blue4", "lazy(BurpPalette.mono4)");
        defaults.put("ColourPalette.blue1", "lazy(BurpPalette.mono2)");
        defaults.put("Burp.dualEmptyPanelLeftBackground", "lazy(BurpPalette.mono2)");
        defaults.put("Burp.collapsibleSidebarSelectedLabelBackground", "@accent");
        defaults.put("Burp.burpOrange", "@accent");
        defaults.put("Burp.primaryButtonBackground", "@accent");
        defaults.put("Burp.tabFlashColour", "@accent");
        defaults.put("Burp.tableFilterBarBorder", "@accent");
        defaults.put("Burp.searchBarBorder", "@accent");

        defaults.put("[dark]Burp.backgrounder", "lighten(Label.background,2%,lazy)");
        defaults.put("[light]Burp.backgrounder", "darken(Label.background,2%,lazy)");
        defaults.put("@toolBackground", "$Burp.backgrounder");
        defaults.put("Burp.taskListEntrySelectedHighlight", "lazy(Component.accentColor)");

        defaults.put("Burp.taskListEntry", "lazy(ColourPalette.mono2)");
        defaults.put("Burp.textEditorBackground", "lazy(EditorPane.background)");
        defaults.put("Burp.textEditorCurrentLineBackground", "lazy(EditorPane.background)");

        return defaults;
    }

    /** Shade 0 is the theme's background, with each following shade moving 5% further away from it. */
    private static void putLegacyPalette(Properties defaults, String prefix, int shades) {
        defaults.put(prefix + "0", "lazy(Label.background)");
        for (int i = 1; i <= shades; i++) {
            defaults.put("[dark]" + prefix + i, "lighten(" + prefix + "0," + (i * 5) + "%,lazy)");
            defaults.put("[light]" + prefix + i, "darken(" + prefix + "0," + (i * 5) + "%,lazy)");
        }
    }
}
