# Glyph Cake Wait

A **Glyph Toy** for the Nothing Phone (3) **Glyph Matrix** — Inspired by Rhythm Heaven Groove. A timing game where you aim to stop exactly on 0.

---

## Overview & how to play


**Cake Wait** is a "stop exactly on zero" game:

1. **Long-press** the Glyph Button to start a **10-second countdown** with a metronome at
   **60 BPM**: a kick **drum on every beat**, a closed **cymbal on every off-beat**.
2. The display ticks down the seconds but **fades to black during it**, so you have to keep time yourself.
3. **Hold down** the Glyph Button to stop and see how close you were.

---

## Install (no build)

1. Download the latest **`glyph-cake-wait.apk`** from the [Releases](../../releases) page.
2. On the phone, open the downloaded APK and allow installs from that source when prompted.
3. Enable the toy — **Settings → Glyph Interface** (see *Enable the toy* below).

> Sideloaded builds are signed with the debug key unless a release keystore is configured.
> If Android reports a signature mismatch, uninstall any previously installed copy first.

## Build from source

**Requirements**

- **JDK 17** (Android Gradle Plugin 8.x does not support newer JDKs)
- **Android SDK** with `platforms;android-35` and `build-tools;35.0.0`
- A **Nothing Phone (3)** with Developer Options / USB debugging enabled
- The Nothing Glyph SDK AAR is **bundled** at `app/libs/glyphsdk_0606.aar` — no extra download

**Android Studio**

1. Open the project folder and let Gradle sync.
2. Run the `app` configuration on the connected phone.

**Command line**

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/Android/Sdk
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"

./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**Enable the toy**

On the phone: **Settings → Glyph Interface**, drag **Cake Wait** from *Disabled* to
*Active*, then short-press the Glyph Button to cycle to it.

> App id: `com.nothinglondon.countdown` · requires permission
> `com.nothing.ketchum.permission.ENABLE`

**Publishing a release**

Push a tag (e.g. `git tag v1.0 && git push --tags`): the
[`release` workflow](.github/workflows/release.yml) builds `app-release.apk` and attaches it
to a GitHub Release as `glyph-cake-wait.apk`. To sign with a stable key (so users can update
in place), add repo secrets `KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_ALIAS`, and
`KEY_PASSWORD`; without them the build falls back to the debug key.

---

## Sprite & icon workflow

The ready-state logo is a 25×25 grayscale sprite. Two small tools drive it:

**`tools/glyph-sprite-editor.html`** — open it in any browser:

- Draw on a 25×25 grid (left-drag paint, right-drag erase)
- Set per-pixel **brightness** (1–255); eraser, clear, invert
- **Clip to circle** and **Show mask** toggles so you only draw on live LEDs
- Live round-LED "phone preview"
- Export: **Copy Kotlin array**, **Download .kt**, **Download PNG**

Paste the copied array over `READY_SPRITE` in
`app/src/main/java/com/nothinglondon/countdown/Sprites.kt`.

**`tools/gen-icons.mjs`** — regenerates the app icons from the sprite:

```bash
node tools/gen-icons.mjs
```

This rewrites:

- `app/src/main/res/drawable/countdown_thumbnail.xml` — the icon shown in the Glyph Toys manager
- `app/src/main/res/drawable/ic_launcher_foreground.xml` + `ic_launcher_background.xml` — the
  adaptive launcher icon

Then rebuild. (The launcher icon is masked by the system, so the black background + white
dots read like the matrix.)

---

## Credits & attribution

- Built on **Nothing's Glyph Matrix Developer Kit** and the bundled
  `glyphsdk_0606.aar` (© Nothing Technology Ltd).
- Percussion (kick drum + closed cymbal) is **synthesized in-app** — no audio assets.
- Not affiliated with or endorsed by Nothing Technology.

---

## License

MIT — see [LICENSE](LICENSE).

> The bundled Nothing Glyph SDK (`app/libs/glyphsdk_0606.aar`) is © Nothing Technology and
> is **not** covered by this project's MIT license.
