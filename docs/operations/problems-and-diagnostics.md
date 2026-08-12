# Problems and Diagnostics

Running catalogue of build/run problems and their diagnostics. Each entry records
symptom, root cause, diagnosis steps, fix, and verification so the same issue can
be resolved quickly on a fresh machine or after a toolchain change.

---

## 1. `The supplied phased action failed with an exception` (two-layer cause)

This one error message can wrap **two distinct underlying failures** on the same
project. Always find the real `Caused by:` chain — the wrapper alone is never
enough.

### Common wrapper symptom (IDE)

IDE (Trae / VS Code Java / Android Studio) Gradle sync fails with the Gradle
Tooling API wrapper error. Phased action means a sequenced model-query + build
action run by the Tooling API, and the wrapper hides the true failure.

```text
org.gradle.tooling.BuildActionFailureException: The supplied phased action failed with an exception.
```

Always surface the `Caused by:` chain from either the CLI (run
`./gradlew assembleDebug --console=plain --stacktrace`) or the Java/Gradle
extension log.

---

### Layer A — CLI build: `jlink executable ... does not exist`

#### Symptoms

```text
> Failed to transform core-for-system-modules.jar to match attributes {artifactType=_internal_android_jdk_image, ...}
   > Execution failed for JdkImageTransform: <SDK>/platforms/android-37.0/core-for-system-modules.jar.
      > jlink executable /Users/<user>/.trae/extensions/redhat.java-<ver>-darwin-arm64/jre/21.0.11-macosx-aarch64/bin/jlink does not exist.
```

#### Root cause (Layer A)

The Gradle daemon was launched with a **JRE bundled by the VS Code Java
extension** (`redhat.java`), located under `~/.trae/extensions/redhat.java-*/jre/...`.
A JRE ships no JDK command-line tools, so `jlink` is absent.

AGP 9.x resolves `:app:androidJdkImage` (transforming
`core-for-system-modules.jar` during `compileDebugJavaWithJavac`) through the
`JdkImageTransform`, which invokes `jlink` from the daemon JVM. Without a full
JDK the transform fails, and the Tooling API surfaces it as the opaque "phased
action" error in the IDE.

The machine did have full JDKs installed, but the daemon picked the extension
JRE instead, so this is an environment/JDK-resolution issue, not a project
configuration issue.

---

### Layer B — IDE sync: `Unsupported class file major version 68`

This layer appears only in the IDE when the Gradle Tooling API's build-script
semantic-analysis phase runs on **Java 24 (class file major 68)** even though
the daemon itself runs on JDK 21. It was recovered from `redhat.java` client
log under the "Error occured while building workspace" entry:

```text
BUG! exception in phase 'semantic analysis' in source unit '_BuildScript_' Unsupported class file major version 68
```

Class file major 68 = **Java 24**. The Kotlin Compose plugin declared in the
project (`org.jetbrains.kotlin.plugin.compose 2.2.10` in `libs.versions.toml`)
ships class files built with target 24 on the classpath visible to the
Tooling-API host (Gradle Server / extension host JVM), and the Groovy compiler
inside Gradle's Tooling API build-script analysis cannot yet read major 68.

Crucially, Layer B is **not a daemon problem** — the daemon itself can and
should stay on JDK 21 per `gradle-daemon-jvm.properties`. It is the IDE's
**process-internal build-script / model-request phase** that runs on Java 24
because `redhat.java` auto-detected `openjdk-24.0.1` (the newest JDK on the
machine) as `java.home`. Auto-detection picks the highest version, not the one
compatible with this project's Gradle/AGP toolchain.

Layer A + Layer B often appear together: a bundled-JRE GradleServer (Layer A)
plus a `java.home` auto-detect to JDK 24 (Layer B) guarantee that the IDE
phased-action error persists even after daemon-side fixes.

#### Fix (Layer B)

Pin **all** Java/Gradle entry-points in the IDE to the same full JDK 21 and
disable auto-detection so `openjdk-24.0.1` never becomes the host JVM:

```jsonc
// ~/Library/Application Support/Trae/User/settings.json  (or VS Code)
{
  "java.home": "/Users/<user>/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home",
  "java.configuration.detectJdksAtStart": false,
  "java.jdt.ls.java.home": "/Users/<user>/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home",
  "java.import.gradle.java.home": "/Users/<user>/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home",
  "gradle.java.home": "/Users/<user>/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home"
}
```

Then **reload the window / restart the IDE** — GradleServer (Gradle Tooling
host) and JDT LS inherit their JVM at spawn time and keep it for their whole
lifecycle.

---

### Diagnosis (both layers together)

1. Reproduce on the CLI first to isolate Layer A vs B:

   ```bash
   ./gradlew assembleDebug --console=plain --no-configuration-cache
   ```

   - CLI `BUILD SUCCESSFUL` but IDE still shows phased action → **Layer B only**.
   - CLI also fails on `jlink executable ... does not exist` → **Layer A (or A+B)**.

2. If Layer A: confirm the failing executable path points under
   `~/.trae/extensions/redhat.java-*/jre/...` (bundled JRE, no `jlink`).
3. List installed JDKs:

   ```bash
   /usr/libexec/java_home -V
   ```

   Known-good full JDKs on this machine:
   - `ms-21.0.11` → `~/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home`
   - `openjdk-24.0.1` → `~/Library/Java/JavaVirtualMachines/openjdk-24.0.1/Contents/Home`

4. If Layer B (CLI OK, IDE fails), inspect `redhat.java` client log under:

   ```text
   ~/Library/Application Support/Trae/User/workspaceStorage/<id>/redhat.java/client.log.<date>
   ```

   and grep for `Error occured while building workspace` followed by the
   real `Caused by:` (e.g. `Unsupported class file major version 68`).

5. Confirm which JVM each IDE helper process is actually on (sanity check after
   settings are applied):

   ```bash
   ps aux | grep -v grep | grep -E "GradleServer|GradleDaemon|jdt.ls.core.product|kotlin-language-server"
   ```

   Expected post-fix state (all `ms-21.0.11` **except** the bundled-JRE Spring
   Boot LS, which is non-critical for Android builds):

   ```text
   GradleDaemon      ms-21.0.11 ✅
   JDT LS            ms-21.0.11 ✅
   GradleServer      ms-21.0.11 ✅   (was redhat.java JRE / JDK 24 before fix)
   Spring Boot LS    redhat.java JRE  (non-build path; harmless)
   ```

---

### Fix (full combined recipe)

**1. Shell / CLI (`~/.zshrc`)** — fixes CLI Layer A:

```bash
export JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
```

Reload with `source ~/.zshrc`.

**2. Gradle daemon user-level fallback (`~/.gradle/gradle.properties`,
machine-scope, not committed)** — fixes Layer A for **every** daemon regardless
of who spawned it:

```properties
org.gradle.java.home=/Users/<user>/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home
```

**3. IDE (Trae / VS Code) User settings.json** — fixes Layer B (auto-detection
of JDK 24 as host JVM) and remaining Layer A spawn paths:

```jsonc
{
  "java.home": "/Users/<user>/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home",
  "java.configuration.detectJdksAtStart": false,
  "java.jdt.ls.java.home": "/Users/<user>/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home",
  "java.import.gradle.java.home": "/Users/<user>/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home",
  "gradle.java.home": "/Users/<user>/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home"
}
```

**4. Apply #3:** Reload Window / restart the IDE (mandatory: GradleServer and
JDT LS pick their JVM once at spawn and keep it).

**5. Clean and re-sync:**

```bash
cd foodmind-android
./gradlew --stop
./gradlew assembleDebug --console=plain --no-configuration-cache
```

---

### Verification

```bash
cd foodmind-android
./gradlew assembleDebug --console=plain --no-configuration-cache
# BUILD SUCCESSFUL — :app:compileDebugJavaWithJavac passes

./gradlew help   # quick sanity
# BUILD SUCCESSFUL
```

Then re-trigger IDE Gradle sync and confirm no phased action wrapper.

---

### Layer C — IDE host sandbox: `GradleServer` still uses the redhat.java bundled JRE

If you apply Steps 1–4 above and GradleServer is **still** running under
`~/.trae/extensions/redhat.java-<ver>-darwin-arm64/jre/.../bin/java` (check with
`ps aux | grep GradleServer`), then you have reached Layer C: the Gradle for
Java extension (`vscjava.vscode-gradle`) starts the Tooling API host
(`GradleServer`) by directly spawning the `redhat.java` bundled JRE java
binary — it does **not** use `gradle.java.home` / `java.home` settings for its
own process JVM, and it does **not** honour `JAVA_HOME` / `VSCODE_JAVA_HOME`
from the shell because GUI apps (Trae / VS Code) are launched by `launchd`
without inheriting `~/.zshrc`.

The bundled directory is **not** a real JRE — it contains `javac`, `jconsole`,
`jcmd` etc — it is a near-complete JDK 21 distribution that is **missing only
4 command-line tools**:

```text
missings vs ms-21.0.11: jimage, jlink, jmod, jpackage
```

`jlink` is the critical one for Layer A (`JdkImageTransform`). If Layer A/B
still surface even after `~/.gradle/gradle.properties` forced the daemon onto
a full JDK, the remaining fix is to **patch the bundled near-JDK directory so
its `bin/` contains the missing `jlink`** (the extension directory is
user-writable).

#### Layer C Fix (user's shell, NOT inside the Trae sandbox — the sandbox
prohibits writing into `redhat.java/jre/*/bin`):

```bash
FAKE_JRE="$HOME/.trae/extensions/redhat.java-1.55.0-darwin-arm64/jre/21.0.11-macosx-aarch64"
REAL_JDK="$HOME/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home"

comm -23 <(ls "$REAL_JDK/bin" | sort) <(ls "$FAKE_JRE/bin" | sort)
# Should print: jimage jlink jmod jpackage

for TOOL in jlink jmod jimage jpackage; do
  sudo cp -p "$REAL_JDK/bin/$TOOL" "$FAKE_JRE/bin/$TOOL"
done

"$FAKE_JRE/bin/jlink" --version   # confirm: 21.0.11
```

After copying: Reload Window once more so a freshly-spawned GradleServer (still
using the bundled binary path) now finds `jlink` when it walks
`$FAKE_JRE/bin/jlink`.

#### Why this works

Layer A fails because a code path resolves `JAVA_HOME` from the running JVM
(i.e. the bundled dir) and then asks for `$JAVA_HOME/bin/jlink` without
falling back to the daemon JVM home. Patching the bundled near-JDK `bin/`
satisfies that lookup directly, at the cost of ~4 extra binaries inside the
extension folder. The patch is safe because both distributions are the **exact
same 21.0.11 minor/build** (you can confirm with `--version` on each copied
binary). If the `redhat.java` extension ever upgrades itself to a new version
number the directory changes and the copy must be re-run for the new bundle.

---

### Notes / Prevention

- `java.home` is the extension-wide "default JDK" setting; when left unset it
  auto-detects and picks the **newest** JDK on the system (here Java 24). For
  this project **disable detection** (`java.configuration.detectJdksAtStart: false`)
  and pin it to a full JDK 21 that matches the pinned toolchain in
  `gradle-daemon-jvm.properties`.
- `gradle.java.home` is the key honoured by `vscjava.vscode-gradle`
  (GradleServer). `java.import.gradle.java.home` is a related but
  redhat.java-specific import-phase setting — set **both** for consistency.
- Do not hardcode `org.gradle.java.home` with a machine-specific path in
  committed `gradle.properties`; use `~/.gradle/gradle.properties` for that.
- `gradle-daemon-jvm.properties` pins daemon toolchain JVM to 21; a full JDK 21
  must be resolvable (e.g. via the machine JDKs above or foojay). If you ever
  add or remove a JDK on the machine, re-check `java.home` is still pointing at
  JDK 21, because auto-detection will otherwise drift to the highest version.
