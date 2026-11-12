import com.coreyd97.burpcustomizer.CustomTheme;
import com.formdev.flatlaf.IntelliJTheme;
import com.formdev.flatlaf.intellijthemes.FlatAllIJThemes;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Installs every bundled theme on top of a real Burp Suite jar, and checks that Burp's look and feel
 * properties still resolve. Catches Burp releases which change the internal theme classes we rely on.
 *
 * Run with: ./gradlew themeSmokeTest -PburpJar=/path/to/burpsuite.jar
 */
public class ThemeSmokeTest {

    public static void main(String[] args) throws Exception {
        //FlatLaf logs, rather than throws, when a property cannot be parsed or a reference is missing.
        List<String> problems = new ArrayList<>();
        Logger flatLafLogger = Logger.getLogger("com.formdev.flatlaf");
        flatLafLogger.setUseParentHandlers(false);
        flatLafLogger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel().intValue() >= Level.WARNING.intValue()) problems.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });

        int failed = 0, total = 0;
        boolean legacy = false;
        for (UIManager.LookAndFeelInfo info : FlatAllIJThemes.INFOS) {
            total++;
            problems.clear();
            try {
                IntelliJTheme.ThemeLaf base = (IntelliJTheme.ThemeLaf) Class.forName(info.getClassName())
                        .getDeclaredConstructor().newInstance();
                CustomTheme theme = new CustomTheme(base);
                legacy = theme.isLegacyBurp();
                UIManager.setLookAndFeel(theme);

                if (UIManager.getColor("Panel.background") == null) problems.add("Panel.background is missing");
                if (UIManager.getColor("Label.foreground") == null) problems.add("Label.foreground is missing");
                String tabbedPaneUI = String.valueOf(UIManager.get("TabbedPaneUI"));
                if (!tabbedPaneUI.startsWith("burp.")) problems.add("Burp's UI delegates were not loaded: " + tabbedPaneUI);
                if (!legacy && UIManager.getColor("Colors.ui.background.2") == null) {
                    problems.add("Burp's Colors.* design system did not resolve");
                }
            } catch (Throwable t) {
                problems.add(t.toString());
            }

            if (!problems.isEmpty()) {
                failed++;
                System.out.println("FAIL " + info.getName());
                problems.stream().distinct().limit(5).forEach(problem -> System.out.println("     " + problem));
            }
        }

        System.out.println((total - failed) + "/" + total + " themes loaded against " +
                (legacy ? "legacy" : "modern") + " Burp theme classes.");
        System.exit(failed == 0 ? 0 : 1);
    }
}
