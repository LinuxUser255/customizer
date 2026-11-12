package burp;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * The class Burp loads. It must not reference anything other than the JDK and the Montoya API.
 *
 * Burp loads extensions with a class loader which hides Burp's internals, including its bundled copy of FlatLaf
 * (com.formdev.flatlaf) and its own look and feel (burp.theme). Any class which links against those cannot be
 * loaded by it, and Burp then reports "Extension class is not a recognized type".
 *
 * Customizer has to extend Burp's look and feel, so this bootstrap loads the real extension from the same jar
 * using a second class loader, whose parent is the class loader that Burp itself was loaded by.
 */
public class BurpExtender implements BurpExtension {

    private static final String IMPLEMENTATION = "com.coreyd97.burpcustomizer.BurpCustomizer";

    @Override
    public void initialize(MontoyaApi api) {
        api.extension().setName("Customizer");
        try {
            URL jar = BurpExtender.class.getProtectionDomain().getCodeSource().getLocation();
            ClassLoader loader = new ExtensionClassLoader(jar, findBurpClassLoader(api));
            Class<?> implementation = Class.forName(IMPLEMENTATION, true, loader);
            Object customizer = implementation.getConstructor().newInstance();
            implementation.getMethod("initialize", MontoyaApi.class).invoke(customizer, api);
        } catch (Throwable t) {
            Throwable cause = t.getCause() != null ? t.getCause() : t;
            StringWriter trace = new StringWriter();
            cause.printStackTrace(new PrintWriter(trace));
            api.logging().logToError("Customizer could not start. This version of Burp Suite may not be supported.");
            api.logging().logToError(trace.toString());
            throw new IllegalStateException("Customizer could not start: " + cause, cause);
        }
    }

    /** Finds a class loader which can see Burp's bundled FlatLaf and its look and feel classes. */
    private static ClassLoader findBurpClassLoader(MontoyaApi api) {
        ClassLoader[] candidates = {
                api.getClass().getClassLoader(), //Burp's implementation of the API lives alongside its internals.
                ClassLoader.getSystemClassLoader(),
                Thread.currentThread().getContextClassLoader()
        };
        for (ClassLoader candidate : candidates) {
            if (candidate == null) continue;
            try {
                Class.forName("com.formdev.flatlaf.FlatLaf", false, candidate);
                Class.forName("burp.theme.BurpLaf", false, candidate);
                return candidate;
            } catch (ClassNotFoundException | LinkageError ignored) {
            }
        }
        throw new IllegalStateException("Could not find Burp's look and feel classes (FlatLaf and burp.theme.BurpLaf).");
    }

    /**
     * Loads the extension's own classes, and the libraries bundled with it, from the extension jar before
     * asking Burp. Everything else, importantly FlatLaf and the Montoya API, comes from Burp.
     */
    private static class ExtensionClassLoader extends URLClassLoader {
        private static final String[] OWN_PACKAGES = {
                "com.coreyd97.",
                "com.formdev.flatlaf.intellijthemes.",
                "com.google.gson."
        };

        static {
            ClassLoader.registerAsParallelCapable();
        }

        ExtensionClassLoader(URL jar, ClassLoader parent) {
            super(new URL[]{jar}, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            for (String ownPackage : OWN_PACKAGES) {
                if (!name.startsWith(ownPackage)) continue;
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) {
                        try {
                            loaded = findClass(name);
                        } catch (ClassNotFoundException e) {
                            break; //Not in our jar after all. Ask Burp.
                        }
                    }
                    if (resolve) resolveClass(loaded);
                    return loaded;
                }
            }
            return super.loadClass(name, resolve);
        }
    }
}
