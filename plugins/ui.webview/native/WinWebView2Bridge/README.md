# WinWebView2Bridge

Rust/JNI bridge for the Windows WebView2 system WebView backend.

## Output

The crate is a `cdylib` and produces `win_webview2_bridge.dll`:

```text
pwsh -File community/plugins/ui.webview/native/WinWebView2Bridge/build.ps1
```

The build script runs Cargo in release mode and writes the intermediate DLL to:

```text
community/plugins/ui.webview/native/WinWebView2Bridge/target/<rust-target>/release/win_webview2_bridge.dll
```

The committed x64 runtime artifact lives in the WebView plugin directory:

```text
community/plugins/ui.webview/lib/webview-native/win/x86_64/win_webview2_bridge.dll
```

The Kotlin bridge loads this loose file from WebView plugin resources via `PluginPathManager.getPluginResource(...)`, with a source-tree fallback for running from sources.

## Updating The Committed DLL

Build and copy the release DLL from the repository root:

```text
pwsh -File community/plugins/ui.webview/native/WinWebView2Bridge/build.ps1
```

On an x64 machine the script builds `x86_64-pc-windows-msvc` and copies the DLL to `community/plugins/ui.webview/lib/webview-native/win/x86_64/win_webview2_bridge.dll`. To update another committed architecture explicitly, pass the Rust target triple:

```text
pwsh -File community/plugins/ui.webview/native/WinWebView2Bridge/build.ps1 -Target aarch64-pc-windows-msvc
```

Stop any dev IDE process that has loaded the DLL before copying; Windows locks loaded DLL files.

The MSVC build statically links `WebView2LoaderStatic.lib` through `webview2-com-sys`, so do not commit a separate `WebView2Loader.dll` next to the bridge DLL.

If arm64 support is added, install the Rust `aarch64-pc-windows-msvc` standard library and the ARM64 MSVC toolchain before running the arm64 command above. The `cargo` selected by `PATH` must be the same toolchain where the target is installed; if Cargo still reports that `std` or `core` cannot be found for `aarch64-pc-windows-msvc`, put the rustup-managed `%USERPROFILE%\.cargo\bin` before any standalone Rust installation in `PATH`. Commit the matching DLL under `community/plugins/ui.webview/lib/webview-native/win/aarch64/win_webview2_bridge.dll`.

Run arm64 builds from the Visual Studio `x64_arm64` developer environment, or initialize it first:

```text
set "PATH=%USERPROFILE%\.cargo\bin;%PATH%"
"%ProgramFiles(x86)%\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvarsall.bat" x64_arm64
pwsh -File community/plugins/ui.webview/native/WinWebView2Bridge/build.ps1 -Target aarch64-pc-windows-msvc
```

## Verification

```text
cargo check --manifest-path community/plugins/ui.webview/native/WinWebView2Bridge/Cargo.toml
pwsh -File community/plugins/ui.webview/native/WinWebView2Bridge/build.ps1
```

After changing the JNI boundary, update both native and Kotlin ABI constants before copying a new DLL.

JNI callbacks from Rust to Kotlin are part of that boundary. When adding a callback method, for example `onBeforeMouseFocus()`, update `NATIVE_ABI_VERSION` in `src/lib.rs`, update `EXPECTED_NATIVE_ABI_VERSION` in `WinWebView2Bridge.kt`, rebuild with `build.ps1`, and commit the copied DLL.

The mouse-focus callback is called from the container HWND mouse activation path before WebView2 dispatches the page pointer event. Keep that callback limited to synchronizing Swing focus to the host panel. Do not call WebView2 `MoveFocus(COREWEBVIEW2_MOVE_FOCUS_REASON_PROGRAMMATIC)` from this mouse path; the click already activates the native child window, and an extra programmatic focus move can blur pointer-opened browser popups.
