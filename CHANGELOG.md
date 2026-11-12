# Changelog

## Unreleased

Changes since v1.1.0. The README's "Build errors and fixes" section explains the cause of each build problem in more detail.

**How this was verified:** clean builds on macOS (JDK 22) and Ubuntu 24.04.5 LTS (OpenJDK 21, arm64 container),
`themeSmokeTest` against a Burp Suite 2026 jar using Burp's real extension class loader (the jar loads, and
46 of 46 themes apply), and offscreen renders of sample components using Burp's UI classes.
The code path for older Burp versions was not tested against an old Burp jar.

### Build
- Upgraded the Gradle wrapper from 8.5 to 9.1.0. Gradle 8.5 could not run on JDK 22 or newer
  (`Unsupported class file major version 66`).
- A Burp Suite jar is no longer needed to compile. The unused `import burp.theme.BurpLaf` was removed, and
  `burpsuite_pro.jar` became an optional, test only dependency (`-PburpJar=/path/to/burpsuite.jar`).
- Removed the Lombok Gradle plugin. The four getters it generated are written by hand.
- Removed the broken `testJar` task, which referenced the `testCompile` configuration removed in Gradle 7.
- Replaced deprecated Gradle syntax (top level `sourceCompatibility`, `url "..."`), and set `options.release = 21`
  so any JDK from 21 to 25 produces Java 21 bytecode.
- JitPack is now only consulted for `com.github.*` dependencies.
- Added `.gitattributes` so `gradlew` keeps Unix line endings on every platform.

### Dependencies and jar contents
- FlatLaf updated from 2.4 to 3.7, in line with the version Burp ships. FlatLaf has dropped some themes
  since 2.4, so 46 are bundled. A saved theme which no longer exists is not reapplied.
- FlatLaf core is no longer bundled in the jar. Burp's own copy is used.
- Lombok, ASM and the Montoya API are no longer bundled. They came in through the `Burp-Montoya-Utilities`
  artifact, which is a fat jar.
- Removed the unused `JFontChooser` dependency.
- The jar is 0.8 MB, down from 2.9 MB.

### Compatibility with current Burp Suite
- Themes can be applied again. Burp replaced `burp.theme.BurpDarkLaf` / `BurpLightLaf` with a single `BurpLaf`
  driven by a `Colors.*` design system, so every theme failed with "Cannot find required Burp themes".
  `CustomTheme` now detects which implementation is present:
  - **Current Burp:** loads Burp's built in dark or light colours through `burp.theme.ThemeFactory`, then replaces
    the mono (backgrounds and text), primary (accent) and secondary (selection) palettes, and a few hardcoded
    editor colours, with colours taken from the selected theme.
  - **Older Burp:** the previous behaviour is kept.
- Burp's classes are looked up through several class loaders rather than only the system class loader.
- Fixed `Extension class is not a recognized type` when adding the jar to Burp. Burp's extension class loader
  hides its bundled FlatLaf and `burp.theme` classes, so no class which links against them can be the entry point.
  v1.1.0 only loaded because it bundled a second copy of FlatLaf. `burp.BurpExtender` is now a bootstrap which
  depends only on the JDK and the Montoya API, and starts `BurpCustomizer` from the same jar through a class
  loader whose parent is Burp's own. `BurpCustomizer` no longer implements `BurpExtension` itself, and closes
  that class loader when the extension is unloaded.

### Fixed
- The theme preview went blank as soon as a theme was selected. It now draws the selected theme's main colours
  above the component samples.
- An error dialog appeared every time a theme was selected in the list if the preview could not be built.
  Errors are now only reported when a theme is applied.
- Errors other than I/O errors while loading a theme file (e.g. malformed JSON) were not caught.
- Theme files are now closed after being read.
- A failed theme load restored Burp's look and feel without repainting the UI.
- The extension's popup factory was left installed in Burp after the extension was unloaded.
- Cancelling the theme file chooser reset the button's label while the previous file stayed selected, and a
  file which failed to load was shown as selected.
- An unrecognised saved `source` preference stopped the extension loading.
- A Burp build without the FlatLaf inspector classes stopped the extension loading.
- The UI is now built on the Swing event dispatch thread.
- `WrappedTextPane(StyledDocument)` ignored its argument.
- The theme list relied on an unchecked cast of `Collectors.toList()` to `ArrayList`.

### Added
- `themeSmokeTest` Gradle task (`src/test/java/ThemeSmokeTest.java`). It loads the built jar through a class
  loader with the same restrictions as Burp's (or Burp's real one, with `-PburpExtensionLoader=<class>`),
  initializes the extension against a stub Montoya API, then installs every bundled theme against a real Burp
  jar and fails if Burp's look and feel properties no longer resolve.
- The extension sets its name to "Customizer" when loaded.
- The "Notes" paragraph about switching between dark and light themes is shown in the Customizer tab, with the
  current location of Burp's display settings.

### Documentation
- README: rewritten build instructions, an Ubuntu 24.04 LTS section, a "Build errors and fixes" section, updated
  usage steps, and the dead GitHub workflow badge removed.
