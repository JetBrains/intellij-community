# Windows WebView2 Backend

Status: ✅ **IMPLEMENTED** as the `SYSTEM_WINDOWS` engine provider. Asset serving is wired through WebView2 `WebResourceRequested`; native bridge loading checks WebView plugin-local native resources and validates ABI compatibility. No remaining blocking work in this doc — follow-ups are tracked in [windows-webview2-off-edt-plan.md](windows-webview2-off-edt-plan.md) and [windows-webview2-rust-review.md](windows-webview2-rust-review.md).

## Current Backend

The Windows backend is implemented by:

- `src/com/intellij/ui/webview/internal/windows/WinWebViewEngine.kt`
- `src/com/intellij/ui/webview/internal/windows/WinNativeWebViewHostPeer.kt`
- `src/com/intellij/ui/webview/internal/windows/WinWebView2Bridge.kt`
- `src/com/intellij/ui/webview/internal/windows/WinWebViewShortcutInterop.kt`
- `community/plugins/ui.webview/native/WinWebView2Bridge`

`WindowsWebView2EngineProvider` registers it as `WebViewEngineId.SYSTEM_WINDOWS`.

Current capabilities:

- `messagePassing = true`
- `swingEmbedding = true`
- `interactiveInput = true`
- `assetServing = true`

Because `assetServing` is true, `createWebViewPanel` may choose the Windows system engine for asset-backed panels when the Windows provider satisfies the requested engine preference and availability checks.

## Runtime Shape

The backend uses a direct Rust/JNI bridge to WebView2 rather than WRY itself:

- Rust owns the WebView2 environment, controller, WebView, child container `HWND`, message handler, and execute-script callbacks.
- Kotlin owns the `WebViewEngine` lifecycle, pending loads/evaluations, message-bus connection, host peer, and shortcut routing.
- `SwingWebViewHostPanel` delegates native attach/detach/bounds/visibility/focus work through `NativeWebViewHostPeer`.

The backend supports:

- local file loading through `loadFile`;
- asset loading through `loadAsset`, `WebViewAssetResolver`, and WebView2 `WebResourceRequested`;
- in-memory HTML through `loadHtml`;
- JavaScript evaluation;
- JavaScript-to-Kotlin messages through WebView2 postMessage;
- Kotlin-to-JavaScript delivery through `window.__WVI__.__deliver(rawJson)`;
- accelerator shortcut forwarding from WebView2 into the IDE event queue where applicable.

## Keyboard And Shortcut Interop

The Windows backend keeps browser editing native and forwards only the keys that belong to IDE or OS-level handling:

- WebView2 keeps normal typing, text editing, IME/dead-key handling, and browser text navigation shortcuts such as `Ctrl+Left`, `Ctrl+Right`, and related selection/deletion variants.
- WebView2 `AcceleratorKeyPressed` is routed through Kotlin for IDE accelerators. Browser-owned editing shortcuts are filtered out before the IDE consumes them, and Windows `SYSTEM_KEY_*` callbacks are first matched against the active IDE keymap.
- Bare Shift is supplied by the native bridge because WebView2 does not report it through `AcceleratorKeyPressed`; this is required for double-Shift IDE gestures. Bare Ctrl stays on the WebView2 accelerator path.
- Unclaimed Windows system keys are forwarded as native `WM_SYSKEYDOWN`/`WM_SYSKEYUP` messages to the root AWT window after Kotlin declines them, so OS-level behavior such as window close still runs through the AWT peer and `DefWindowProc` path.

Do not replace this with full Swing keyboard ownership for WebView content. That would break native browser editing behavior and IME handling.

## Asset Serving

Asset-backed pages navigate to the shared internal `ij-webview-asset://assets/...` origin. The native bridge registers that WebView2 custom scheme when creating the shared environment, then registers a per-WebView2 `WebResourceRequested` filter and delegates matching requests back to Kotlin through that view's `WinWebView2Bridge.Callbacks.resolveAsset`.

The authority is intentionally present: WebView2 ES module loading needs a non-opaque custom-scheme origin, otherwise a page can load the HTML, CSS, and classic runtime scripts while never requesting the module entry bundle.

The temporary `https://ij-webview-assets.local/...` filter remains registered as a rollback path while `ide.webview.windows.asset.custom.scheme.enabled` exists. The rollback only changes the URL chosen by `WinWebViewEngine.loadAsset`; routing still stays per native WebView instance through its own callback and active asset resolver.

Kotlin resolves requests through `WebViewAssetResolver`, so classpath roots, directory roots, and common runtime assets such as `/__webview/wvi-bridge.js` follow the same path normalization and escape checks as the other asset-serving backends. WebView2 responses carry status, content type, and no-cache headers from the resolved `WebViewAssetResponse`.

Do not reintroduce extracted-file asset loaders or a localhost server.

## Native Build

Build the native bridge locally with:

```text
pwsh -File community/plugins/ui.webview/native/WinWebView2Bridge/build.ps1
```

The script runs Cargo in release mode for the current Windows architecture and copies the resulting DLL into `community/plugins/ui.webview/lib/webview-native/win/<plugin-arch>/`. Pass `-Target aarch64-pc-windows-msvc` to update the arm64 committed artifact after installing the matching Rust standard library and ARM64 MSVC toolchain. Run arm64 builds from the Visual Studio `x64_arm64` developer environment, and if Rust is installed both through rustup and as a standalone toolchain, make sure the `cargo` selected by `PATH` is the one where the target is installed.

If a dev IDE process has loaded the bridge DLL, stop that process before replacing the committed copy; Windows keeps loaded DLLs locked. At runtime the provider loads `win_webview2_bridge.dll` from WebView plugin-local native resources. In a checkout, the source-tree fallback checks `community/plugins/ui.webview/lib/webview-native/win/<plugin-arch>/`, so the committed `community/plugins/ui.webview/lib/webview-native/win/x86_64/win_webview2_bridge.dll` is the canonical x64 artifact.

## Verification

For Windows backend changes, run on Windows:

```powershell
.\tests.cmd --module intellij.platform.ui.webview.tests --test com.intellij.ui.webview.WindowsWebViewSmokeTest
.\tests.cmd --module intellij.platform.ui.webview.tests --test com.intellij.ui.webview.WinWebViewShortcutInteropTest
.\tests.cmd --module intellij.platform.ui.webview.tests --test com.intellij.ui.webview.WebViewFocusInteropRobotTest
.\tests.cmd --module intellij.platform.ui.webview.tests --test com.intellij.ui.webview.WebViewRuntimeTest
```

Windows-specific asset coverage should include `loadAsset`, `createWebViewPanel` selection, common runtime asset serving, missing resources, and path traversal rejection.

## Reference Inputs

WRY/Tauri remain useful design references for child-window embedding, WebView2 IPC, and custom protocol handling, but the IntelliJ implementation is direct Rust/JNI and must follow `WebViewRuntime` provider boundaries.

Local references used during the MVP:

- `../wry/src/webview2/mod.rs`
- `../wry/src/custom_protocol_workaround.rs`
- `../tauri/crates/tauri-runtime-wry/src/lib.rs`
- `../tauri/crates/tauri/src/manager/webview.rs`
