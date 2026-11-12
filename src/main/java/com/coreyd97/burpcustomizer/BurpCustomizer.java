package com.coreyd97.burpcustomizer;

import burp.api.montoya.MontoyaApi;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.IntelliJTheme;
import com.formdev.flatlaf.extras.FlatInspector;
import com.formdev.flatlaf.extras.FlatUIDefaultsInspector;
import com.formdev.flatlaf.intellijthemes.FlatAllIJThemes;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Collectors;

/**
 * The extension itself. This is not the class Burp loads: it links against Burp's bundled FlatLaf, which Burp
 * hides from extensions, so it is started reflectively by {@link burp.BurpExtender} from a class loader which can see it.
 */
public class BurpCustomizer {

    enum ThemeSource {BUILTIN, FILE}

    private LookAndFeel originalBurpTheme;
    private final ArrayList<UIManager.LookAndFeelInfo> themes;
    private UIManager.LookAndFeelInfo selectedBuiltIn;
    private File selectedThemeFile;
    private ThemeSource themeSource = ThemeSource.BUILTIN;
    private CustomizerPanel ui;
    public static MontoyaApi montoya;

    public BurpCustomizer() {
        themes = Arrays.stream(FlatAllIJThemes.INFOS)
                .filter(lookAndFeelInfo -> !lookAndFeelInfo.getName().equalsIgnoreCase("Xcode-Dark"))
                .sorted(Comparator.comparing(UIManager.LookAndFeelInfo::getName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public ArrayList<UIManager.LookAndFeelInfo> getThemes() {
        return themes;
    }

    public UIManager.LookAndFeelInfo getSelectedBuiltIn() {
        return selectedBuiltIn;
    }

    public File getSelectedThemeFile() {
        return selectedThemeFile;
    }

    public ThemeSource getThemeSource() {
        return themeSource;
    }

    /** Called reflectively by {@link burp.BurpExtender}. */
    public void initialize(MontoyaApi montoyaApi) {
        BurpCustomizer.montoya = montoyaApi;
        originalBurpTheme = UIManager.getLookAndFeel();

        String sourceEnum = montoya.persistence().preferences().getString("source");
        if (sourceEnum != null && !sourceEnum.isEmpty()) {
            try {
                themeSource = ThemeSource.valueOf(sourceEnum);
            } catch (IllegalArgumentException ignored) { //Unknown value saved. Stick with the default.
            }
        }

        String builtIn = montoya.persistence().preferences().getString("theme");
        themes.stream()
                .filter(lookAndFeelInfo -> lookAndFeelInfo.getClassName().equalsIgnoreCase(builtIn))
                .findFirst()
                .ifPresent(lookAndFeelInfo -> selectedBuiltIn = lookAndFeelInfo);

        String themeFilePref = montoya.persistence().preferences().getString("themeFile");
        if (themeFilePref != null && !themeFilePref.isEmpty()) {
            selectedThemeFile = new File(themeFilePref);
            if (!selectedThemeFile.exists()) selectedThemeFile = null;
        }

        montoya.extension().registerUnloadingHandler(this::extensionUnloaded);

        SwingUtilities.invokeLater(() -> {
            if (BurpCustomizer.montoya == null) return; //Unloaded before we got here.

            try { //The inspectors are only a debugging aid. Don't fail to load if Burp stops shipping them.
                FlatUIDefaultsInspector.install("ctrl shift alt Y");
                FlatInspector.install("ctrl shift alt U");
                HtmlTooltipPopupFactory.install();
            } catch (Throwable t) {
                montoya.logging().logToError("FlatLaf inspectors are unavailable: " + t);
            }

            this.ui = new CustomizerPanel(this);

            if (themeSource == ThemeSource.BUILTIN && selectedBuiltIn != null) {
                setTheme(selectedBuiltIn);
            } else if (themeSource == ThemeSource.FILE && selectedThemeFile != null) {
                setTheme(selectedThemeFile);
            }

            montoya.userInterface().registerSuiteTab("Customizer", this.ui);
        });
    }

    public void setTheme(UIManager.LookAndFeelInfo lookAndFeelInfo) {
        try {
            applyLookAndFeel(createThemeFromDefaults(lookAndFeelInfo));
            selectedBuiltIn = lookAndFeelInfo;
            themeSource = ThemeSource.BUILTIN;
            montoya.persistence().preferences().setString("theme", lookAndFeelInfo.getClassName());
            montoya.persistence().preferences().setString("source", ThemeSource.BUILTIN.toString());
        } catch (Exception | LinkageError ex) {
            handleThemeFailure(ex);
        }
    }

    public void setTheme(File themeJsonFile) {
        try {
            applyLookAndFeel(createThemeFromFile(themeJsonFile));
            selectedThemeFile = themeJsonFile;
            themeSource = ThemeSource.FILE;
            montoya.persistence().preferences().setString("themeFile", themeJsonFile.getAbsolutePath());
            montoya.persistence().preferences().setString("source", ThemeSource.FILE.toString());
        } catch (Exception | LinkageError ex) {
            handleThemeFailure(ex);
        }
    }

    public LookAndFeel createThemeFromDefaults(UIManager.LookAndFeelInfo lookAndFeelInfo) throws ReflectiveOperationException {
        Class<?> themeClass = Class.forName(lookAndFeelInfo.getClassName());
        IntelliJTheme.ThemeLaf theme = (IntelliJTheme.ThemeLaf) themeClass.getDeclaredConstructor().newInstance();
        return new CustomTheme(theme);
    }

    public LookAndFeel createThemeFromFile(File themeJsonFile) throws IOException, UnsupportedLookAndFeelException {
        IntelliJTheme intelliJTheme;
        try (InputStream in = new FileInputStream(themeJsonFile)) {
            intelliJTheme = new IntelliJTheme(in);
        }
        if (intelliJTheme.name == null && intelliJTheme.author == null) {
            throw new UnsupportedLookAndFeelException(themeJsonFile.getName() + " does not appear to be a valid theme file.\n" +
                    "If it is, make sure it has json attributes \"name\" and \"author\".");
        }

        return new CustomTheme(new IntelliJTheme.ThemeLaf(intelliJTheme));
    }

    private void applyLookAndFeel(LookAndFeel lookAndFeel) throws UnsupportedLookAndFeelException {
        //FlatLaf swaps the popup factory whenever a look and feel is (un)installed, remembering the previous one.
        //Make sure it never remembers ours, or it would be reinstated after the extension has been unloaded.
        HtmlTooltipPopupFactory.uninstall();
        try {
            UIManager.setLookAndFeel(lookAndFeel);
            FlatLaf.updateUI();
        } finally {
            HtmlTooltipPopupFactory.install();
        }
    }

    private void handleThemeFailure(Throwable ex) {
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        montoya.logging().logToError("Could not load theme.");
        montoya.logging().logToError(sw.toString());
        try { //Fall back to built in theme if we encounter an issue.
            if (UIManager.getLookAndFeel() != originalBurpTheme) applyLookAndFeel(originalBurpTheme);
        } catch (Exception ignored) {
        }
        JOptionPane.showMessageDialog(ui, "Could not load the specified theme:\n" + ex.getMessage(), "Burp Customizer", JOptionPane.ERROR_MESSAGE);
    }

    public void extensionUnloaded() {
        BurpCustomizer.montoya = null;
        SwingUtilities.invokeLater(() -> {
            HtmlTooltipPopupFactory.uninstall();
            try {
                if (UIManager.getLookAndFeel() != originalBurpTheme) {
                    UIManager.setLookAndFeel(originalBurpTheme);
                    FlatLaf.updateUI();
                }
            } catch (UnsupportedLookAndFeelException ignored) {
            }
            //Release the extension jar, which our class loader (created by burp.BurpExtender) holds open.
            if (BurpCustomizer.class.getClassLoader() instanceof Closeable) {
                try {
                    ((Closeable) BurpCustomizer.class.getClassLoader()).close();
                } catch (IOException ignored) {
                }
            }
        });
    }

    /**
     * Since Burp explicitly disables HTML in components we need to manually re-enable HTML for the inspector tooltip.
     * Shouldn't reintroduce any vulnerabilities unless somehow a malicious value is used in a tooltip somewhere which is unlikely.
     * Remembers the factory it replaced so that it can be put back when the theme changes or the extension is unloaded.
     */
    private static class HtmlTooltipPopupFactory extends PopupFactory {
        private final PopupFactory previous;

        private HtmlTooltipPopupFactory(PopupFactory previous) {
            this.previous = previous;
        }

        static void install() {
            PopupFactory current = PopupFactory.getSharedInstance();
            if (!(current instanceof HtmlTooltipPopupFactory)) {
                PopupFactory.setSharedInstance(new HtmlTooltipPopupFactory(current));
            }
        }

        static void uninstall() {
            PopupFactory current = PopupFactory.getSharedInstance();
            if (current instanceof HtmlTooltipPopupFactory) {
                PopupFactory.setSharedInstance(((HtmlTooltipPopupFactory) current).previous);
            }
        }

        @Override
        public Popup getPopup(Component owner, Component contents, int x, int y) throws IllegalArgumentException {
            if (contents instanceof JToolTip) {
                ((JToolTip) contents).putClientProperty("html.disable", false);
            }
            return super.getPopup(owner, contents, x, y);
        }
    }
}
