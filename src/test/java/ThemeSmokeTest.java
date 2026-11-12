import javax.swing.*;
import java.awt.Component;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Loads the built extension jar the way Burp Suite does, then installs every bundled theme on top of a real
 * Burp jar and checks that Burp's look and feel properties still resolve. Catches Burp releases which change
 * the internal classes we rely on.
 *
 * Run with: ./gradlew themeSmokeTest -PburpJar=/path/to/burpsuite.jar
 *
 * Only the JDK and the Burp jar are on the classpath. The extension's classes are deliberately not, so that
 * they can only come from the jar.
 */
public class ThemeSmokeTest {

    public static void main(String[] args) throws Exception {
        File extensionJar = new File(System.getProperty("extensionJar"));
        if (!extensionJar.isFile()) throw new IllegalStateException("Extension jar not found: " + extensionJar);

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

        ClassLoader extensionLoader = loadExtensionLikeBurp(extensionJar);
        System.out.println("Extension loaded and initialized through a restricted class loader.");

        Class<?> customTheme = extensionLoader.loadClass("com.coreyd97.burpcustomizer.CustomTheme");
        Constructor<?> newCustomTheme = customTheme.getConstructor(
                extensionLoader.loadClass("com.formdev.flatlaf.IntelliJTheme$ThemeLaf"));
        UIManager.LookAndFeelInfo[] themes = (UIManager.LookAndFeelInfo[]) extensionLoader
                .loadClass("com.formdev.flatlaf.intellijthemes.FlatAllIJThemes").getField("INFOS").get(null);

        int failed = 0;
        boolean legacy = false;
        for (UIManager.LookAndFeelInfo info : themes) {
            problems.clear();
            try {
                Object base = extensionLoader.loadClass(info.getClassName()).getDeclaredConstructor().newInstance();
                LookAndFeel theme = (LookAndFeel) newCustomTheme.newInstance(base);
                legacy = (Boolean) customTheme.getMethod("isLegacyBurp").invoke(theme);
                UIManager.setLookAndFeel(theme);

                if (UIManager.getColor("Panel.background") == null) problems.add("Panel.background is missing");
                if (UIManager.getColor("Label.foreground") == null) problems.add("Label.foreground is missing");
                String tabbedPaneUI = String.valueOf(UIManager.get("TabbedPaneUI"));
                if (!tabbedPaneUI.startsWith("burp.")) problems.add("Burp's UI delegates were not loaded: " + tabbedPaneUI);
                if (!legacy && UIManager.getColor("Colors.ui.background.2") == null) {
                    problems.add("Burp's Colors.* design system did not resolve");
                }
                new JTabbedPane().add("Tab", new JButton("Button")); //Instantiates Burp's UI delegates.
            } catch (Throwable t) {
                Throwable cause = t.getCause() != null ? t.getCause() : t;
                problems.add(cause.toString());
            }

            if (!problems.isEmpty()) {
                failed++;
                System.out.println("FAIL " + info.getName());
                problems.stream().distinct().limit(5).forEach(problem -> System.out.println("     " + problem));
            }
        }

        System.out.println((themes.length - failed) + "/" + themes.length + " themes loaded against " +
                (legacy ? "legacy" : "modern") + " Burp theme classes.");
        System.exit(failed == 0 ? 0 : 1);
    }

    /**
     * Does what Burp does with an extension jar: loads burp.BurpExtender through a class loader which cannot see
     * Burp's internals, constructs it, and calls initialize. Returns the class loader the extension's UI ended up in.
     */
    private static ClassLoader loadExtensionLikeBurp(File extensionJar) throws Exception {
        URL[] urls = {extensionJar.toURI().toURL()};
        //Burp's real (obfuscated, so version specific) loader can be named with -DburpExtensionLoader=burp.Xxxx
        String realLoader = System.getProperty("burpExtensionLoader", "");
        ClassLoader burpsLoader = realLoader.isEmpty()
                ? new RestrictedClassLoader(urls)
                : (ClassLoader) Class.forName(realLoader).getConstructor(URL[].class).newInstance((Object) urls);

        Class<?> entry = Class.forName("burp.BurpExtender", false, burpsLoader);
        Class<?> burpExtension = Class.forName("burp.api.montoya.BurpExtension");
        Class<?> montoyaApi = Class.forName("burp.api.montoya.MontoyaApi");
        if (!burpExtension.isAssignableFrom(entry)) throw new IllegalStateException("burp.BurpExtender is not a BurpExtension");
        Object extension = entry.getConstructor().newInstance();

        AtomicReference<Component> suiteTab = new AtomicReference<>();
        Object api = stub(montoyaApi, suiteTab);
        burpExtension.getMethod("initialize", montoyaApi).invoke(extension, api);
        SwingUtilities.invokeAndWait(() -> {
        }); //The UI is built on the event dispatch thread.

        if (suiteTab.get() == null) throw new IllegalStateException("The extension did not register its suite tab.");
        return suiteTab.get().getClass().getClassLoader();
    }

    /** A Montoya API which does nothing, other than remember the tab the extension registers. */
    private static Object stub(Class<?> type, AtomicReference<Component> suiteTab) {
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getName().equals("registerSuiteTab")) suiteTab.set((Component) args[1]);
            if (method.getName().equals("logToError")) System.out.println("     [extension error] " + args[0]);
            if (method.getDeclaringClass() == Object.class) {
                switch (method.getName()) {
                    case "hashCode": return System.identityHashCode(proxy);
                    case "equals": return proxy == args[0];
                    default: return "stub";
                }
            }
            Class<?> returns = method.getReturnType();
            if (returns == boolean.class) return false;
            if (returns == char.class) return '\0';
            if (returns == long.class) return 0L;
            if (returns == float.class) return 0f;
            if (returns == double.class) return 0d;
            if (returns == byte.class) return (byte) 0;
            if (returns == short.class) return (short) 0;
            if (returns == int.class) return 0;
            if (returns.isInterface() && returns.getName().startsWith("burp.api.montoya.")) return stub(returns, suiteTab);
            return null;
        };
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    /**
     * Mimics the class loader Burp gives to extensions, which hides Burp's bundled FlatLaf and its own look and
     * feel classes. Classes in those packages are only visible if they are inside the extension jar.
     */
    private static class RestrictedClassLoader extends URLClassLoader {
        RestrictedClassLoader(URL[] urls) {
            super(urls, ThemeSmokeTest.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("com.formdev.flatlaf.") || name.startsWith("burp.theme.")) {
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) loaded = findClass(name);
                    if (resolve) resolveClass(loaded);
                    return loaded;
                }
            }
            return super.loadClass(name, resolve);
        }
    }
}
