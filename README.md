<p align="center">
  <h1 align="center">Burp Customizer</h1>
  <h5 align="center">Because just a dark theme wasn't enough!</h5>
</p>
<hr>
<p align="center">
  <img src="https://img.shields.io/github/watchers/CoreyD97/BurpCustomizer?label=Watchers&style=for-the-badge" alt="GitHub Watchers">
  <img src="https://img.shields.io/github/stars/CoreyD97/BurpCustomizer?style=for-the-badge" alt="GitHub Stars">
  <img src="https://img.shields.io/github/downloads/CoreyD97/BurpCustomizer/total?style=for-the-badge" alt="GitHub All Releases">
  <img src="https://img.shields.io/github/license/CoreyD97/BurpCustomizer?style=for-the-badge" alt="GitHub License">
</p>
<hr>

**Created By: CoreyD97 [![@CoreyD97](https://img.shields.io/twitter/follow/CoreyD97?style=social)](https://twitter.com/coreyd97/)**

_Everybody knows hackers only work at night, so for years people asked PortSwigger to implement a dark theme.
When they did, hackers rejoiced everywhere! But, some still wanted more... Until... Burp Customizer!_  

Burp Suite 2020.12 replaced the old Look and Feel classes with FlatLaf, an open source Look and Feel class
which also supports 3rd party [themes developed for the IntelliJ Platform][1]. This extension allows you to use
these themes in Burp Suite, and includes a number of bundled themes to try.

[1]:https://github.com/JFormDesigner/FlatLaf/tree/main/flatlaf-intellij-themes

**Images:**
<table>
<tr><td>Atom One Dark</td>
<td>

![Atom One Dark Customizer](images/AtomOneDarkCustomizer.png)
</td>
<td>

![Atom One Dark Repeater](images/AtomOneDarkRepeater.png)
</td>
<td>

![Atom One Dark Logger](images/AtomOneDarkLogger.png)
</td></tr>
<tr><td>Dark Purple</td>
<td>

![Dark Purple Customizer](images/DarkPurpleCustomizer.png)
</td>
<td>

![Dark Purple Repeater](images/DarkPurpleRepeater.png)
</td>
<td>

![Dark Purple Logger](images/DarkPurpleLogger.png)
</td></tr>
<tr>
<td colspan="4" align="right">And 40+ more themes!</td>
</tr>
</table>

**Limitations:**  
Since Burp uses a number of custom GUI elements, PortSwigger extended the default
Look and Feel classes with a number of additional properties. In order to try to make these blend in, I've
tried to find standard elements who's colors can be used to replace the custom properties. Some themes might
not have these properties, or might not fit the theme perfectly. If there are any elements which don't fit,
please submit an issue on GitHub including the theme name, and a screenshot.

**Future Plans:**
* Allow manual specification of additional defaults and overrides

**Credits:**  
FlatLaf - https://www.formdev.com/flatlaf/  
All theme credits go to their original authors.

---

### Installing:
1. Download the latest jar from https://github.com/CoreyD97/BurpCustomizer/releases
2. Add the jar to Burp Suite.

### Usage:
1. Select the correct base theme (light or dark) from Burp's "Settings -> User interface -> Display" menu, to set the correct icon pack.
2. Choose a bundled theme from within the Customizer tab, or load an IntelliJ `.theme.json` file.
3. Click "Apply". The theme is remembered, and reapplied when Burp starts.
4. If you like the project, please give the repo a star! <3

[![Stargazers over time](https://starchart.cc/CoreyD97/BurpCustomizer.svg)](https://starchart.cc/CoreyD97/BurpCustomizer)

---

### Building:
If you want to build the project from source, e.g. for tweaking, or development

**Requirements:** a JDK, version 21 to 25. Nothing else. Gradle is downloaded by the included wrapper,
and no Burp Suite jar is needed to compile.

1. Clone the repo
2. Build the jar with the Gradle wrapper: `./gradlew jar` (`gradlew.bat jar` on Windows)
3. Add the built jar (`./releases/BurpCustomizer.jar`) to Burp Suite via "Extensions -> Installed -> Add",
   with the extension type set to Java.

**Ubuntu 24.04 LTS:**
Verified on a clean Ubuntu 24.04.5 install. The default OpenJDK 21 package is all that is needed.
The `-headless` variant is enough to build, as nothing graphical runs during the build.

```
sudo apt update
sudo apt install -y git openjdk-21-jdk-headless
git clone <this repo> && cd customizer
./gradlew jar
```

* If `java -version` reports something older than 21 (e.g. OpenJDK 17 is also installed), the build stops with
  `error: release version 21 not supported`. Select JDK 21 with `sudo update-alternatives --config java`, or for
  just this build: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew jar` (`-arm64` on ARM machines).
* If you get `./gradlew: Permission denied`, or `bad interpreter: /bin/sh^M`, the file lost its executable bit or
  its line endings on the way to your machine (zip downloads and Windows shares do this).
  Run `chmod +x gradlew`, or just `sh gradlew jar`. A `.gitattributes` file keeps the line endings correct for git clones.
* The first build downloads Gradle and the dependencies, so needs access to `services.gradle.org`,
  `repo.maven.apache.org` and `jitpack.io`.
* Burp's Linux installer puts its jar in `~/BurpSuitePro/burpsuite_pro.jar` (or
  `~/BurpSuiteCommunity/burpsuite_community.jar`), or under `/opt` if installed as root.
  That is the path to use for `-PburpJar` below.

**Testing against your Burp version (optional):**
The extension depends on Burp's internal theme classes, which are not part of the Montoya API and can change
between releases. To check that every bundled theme still loads against a particular Burp jar, run:

```
./gradlew themeSmokeTest -PburpJar=/path/to/burpsuite.jar
```

The jar can be found inside your Burp installation, e.g. `/Applications/Burp Suite.app/Contents/Resources/app/burpsuite.jar`
on macOS. The same `-PburpJar` property (or a `burpsuite_pro.jar` / `burpsuite_community.jar` / `burpsuite.jar`
in the project root) puts Burp on the test classpath, so that `TestPlugin` can launch Burp from your IDE.

---

### Build errors and fixes
Building v1.1.0 from a fresh clone failed. These are the errors which were found, what caused them, and how
they were fixed. If you are building an older checkout and hit one of these, the fix column tells you what to change.

| # | Error | Cause | Fix |
|---|-------|-------|-----|
| 1 | `gradle: command not found` when following the old instructions (`gradle jar`). | The instructions assumed a system wide Gradle install. | Use the wrapper: `./gradlew jar`. |
| 2 | `BUG! exception in phase 'semantic analysis' ... Unsupported class file major version 66` | The wrapper pinned Gradle 8.5, which cannot run on JDK 22 or newer (major version 66 is Java 22). | Wrapper upgraded to Gradle 9.1.0, which runs on JDK 17 to 25. |
| 3 | `CustomTheme.java:3: error: package burp.theme does not exist` | An unused `import burp.theme.BurpLaf` could only be resolved by a `burpsuite_pro.jar` in the project root, which is proprietary and so not in the repo. | Import removed. Burp's classes are only loaded reflectively, so no Burp jar is needed to compile. The jar is now an optional, test only dependency (`-PburpJar`). |
| 4 | The `testJar` task could not run (found by reading the build script, not by running it). | It referenced the `testCompile` configuration, which was removed in Gradle 7. | The `testJar` task has been removed. It only packaged the IDE launcher. |
| 5 | Deprecation warnings: "Deprecated Gradle features were used in this build, making it incompatible with Gradle 9.0". | Top level `sourceCompatibility` / `targetCompatibility`, and `url "..."` without `=`. | Moved into a `java { }` block and switched to assignment syntax. `options.release = 21` lets any JDK from 21 up produce Java 21 bytecode. |
| 6 | Risk of a Lombok / JDK mismatch (`io.freefair.lombok` 8.6 predates JDK 22+). | Lombok hooks into compiler internals, so each new JDK needs a new Lombok. It was only used for four getters. | Lombok removed, and the getters written out by hand. |

Problems which did not stop the build, but were wrong in the jar it produced:

| # | Problem | Cause | Fix |
|---|---------|-------|-----|
| 7 | **No theme could be applied on current Burp versions.** Every attempt failed with "Cannot find required Burp themes", and the error dialog also appeared each time a theme was selected in the list. | Burp replaced `burp.theme.BurpDarkLaf` / `BurpLightLaf`, and the `BurpPalette.*` / `ColourPalette.*` keys, with a single `BurpLaf` driven by a `Colors.*` design system loaded from `DarkTheme.json` / `LightTheme.json`. | `CustomTheme` now detects which implementation Burp has. On current versions it loads Burp's built in dark or light colours, then replaces the mono, primary and secondary palettes with colours taken from the selected theme. The old behaviour is kept for older Burp versions. |
| 8 | The jar bundled a second, older copy of FlatLaf (2.4) alongside the one inside Burp. | `flatlaf-intellij-themes` pulled FlatLaf core in as a transitive dependency. | Core excluded, and FlatLaf updated from 2.4 to 3.7 (Burp currently ships 3.6 or newer). Themes which FlatLaf has since dropped are no longer available. |
| 9 | The jar bundled the whole of Lombok and a copy of the Montoya API (2.9 MB in total). | The `Burp-Montoya-Utilities` artifact is itself a fat jar. | Those classes are excluded when the jar is assembled (now 0.8 MB). |
| 10 | The unused `JFontChooser` dependency was bundled. | Left over from an earlier version. | Removed. |

Other bugs fixed along the way:
* The theme preview went blank as soon as a theme was selected. It now shows the selected theme's colours.
* The extension's popup factory was left installed in Burp after the extension was unloaded.
* Errors other than I/O errors when loading a theme file (e.g. malformed JSON) were not caught, and theme files were not explicitly closed.
* A failed theme load restored Burp's look and feel without repainting the UI.
* Cancelling the theme file chooser reset the button's label, while the previously chosen file stayed selected.
* An unrecognised saved preference, or a Burp build without the FlatLaf inspectors, stopped the extension loading.
* The UI was built off the Swing event dispatch thread.

**Not yet verified:** the fixes above were checked by compiling, by running `themeSmokeTest` against Burp Suite's jar
(46 of 46 themes load), and by rendering sample components offscreen with Burp's UI classes. The extension has not
been clicked through inside a running Burp, so individual Burp screens may still have elements which don't match the theme.
