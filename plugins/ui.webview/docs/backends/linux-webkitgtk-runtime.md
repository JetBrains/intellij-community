# Linux WebKitGTK Backend

Status: ⏳ **PARTIAL**. Wayland snapshot renderer ✅ (display, JS evaluation, JS message bus). Interactive input ⬜ (mouse and keyboard not forwarded to WebKitGTK in snapshot mode). Asset serving via custom scheme ⬜ (`loadAsset` rejected; pages must use JCEF on Linux for asset-backed UI). X11/XToolkit path 🚫 BLOCKED behind dedicated testing and factory enablement — the scaffold exists but the runtime never selects it.

Stage-level legend: ✅ done · ⏳ partial · ⬜ todo · 🚫 blocked.

The Linux System WebView backend is an experimental local-desktop backend implemented by `LinuxWebKitGtkBridge`, a Rust `cdylib` loaded through JNI.

## Supported Runtime

The MVP supports local Linux desktop sessions where the IDE runs under JBR `WLToolkit`/Wayland and the system provides GTK3 and WebKitGTK 4.1.

The backend uses an offscreen WebKitGTK renderer and paints snapshots into the Swing host. This keeps content visible under native Wayland, but it is not a true Wayland child-surface embedder yet.

The X11/XToolkit path is kept as an isolated scaffold: Kotlin host-peer code and Rust native attach code have separate X11 branches, but `WebViewRuntime` does not enable that backend in the MVP.

Examples of target distributions:

- Ubuntu 22.04 / 24.04
- Debian 12 / 13
- Fedora
- Arch / Manjaro
- openSUSE Tumbleweed

The backend is intentionally unsupported in the MVP on X11/XToolkit sessions, headless sessions, remote-dev backend sessions, Alpine/musl, WSLg as a guaranteed platform, and old enterprise distributions without suitable WebKitGTK packages.

## Current Limitations

- Mouse and keyboard input do not reach WebKitGTK in the Wayland snapshot backend yet. The visible content is display-only from the user's point of view.
- Programmatic JavaScript evaluation and the JS message bus are still available, so tests and demos can verify loaded content without interactive input.
- A real interactive Wayland backend needs a separate embedded child-surface design or an equivalent JBR API; it should not be represented as supported by this snapshot renderer.
- The X11 code path is intentionally isolated from Wayland and should be treated as future work until it gets dedicated testing and factory enablement.

## Native Build

Build the native bridge locally with:

```shell
cargo build --manifest-path community/platform/ui.webview/native/LinuxWebKitGtkBridge/Cargo.toml
```

The Kotlin loader resolves the bridge from WebView plugin-local native resources. Until product/build wiring copies Cargo output into that layout, place local builds under `community/platform/ui.webview/lib/webview-native/linux/<plugin-arch>/`. It validates the bridge ABI after loading so stale local builds fail with a rebuild hint.

## Failure Mode

If GTK3, WebKitGTK 4.1, Wayland/WLToolkit, or the native bridge are unavailable, the backend should report a clear provider availability or Kotlin/JNI error instead of crashing the JVM.
