# WinWebView2Bridge

Rust/JNI bridge for the Windows WebView2 system WebView backend.

Runtime design, including keyboard and shortcut interop, is documented in `../../docs/backends/windows-webview2-implementation-plan.md`.

## Output

The crate is a `cdylib` and produces `win_webview2_bridge.dll`.

The build script runs Cargo in release mode and writes the intermediate DLL to:

```text
community/plugins/ui.webview/native/WinWebView2Bridge/target/<rust-target>/release/win_webview2_bridge.dll
```

The committed runtime artifacts live in the WebView plugin directory:

```text
community/plugins/ui.webview/lib/webview-native/win/x86_64/win_webview2_bridge.dll
community/plugins/ui.webview/lib/webview-native/win/aarch64/win_webview2_bridge.dll
```

The Kotlin bridge loads this loose file from the WebView plugin path, with a source-tree fallback for running from sources.

## Prerequisites

Build from Windows with:

- PowerShell 7 (`pwsh`) available on `PATH`;
- Rust installed through `rustup`, with the MSVC toolchain selected;
- Visual Studio 2022 Build Tools or Visual Studio 2022 Community with the C++ desktop toolchain;
- MSVC v143 x64/x86 build tools and a Windows SDK installed by the Visual Studio setup;
- MSVC ARM64 cross-build tools when building `aarch64-pc-windows-msvc` from an x64 host.

Install both Rust targets before running `-All`:

```text
rustup target add x86_64-pc-windows-msvc aarch64-pc-windows-msvc
```

The build links `WebView2LoaderStatic.lib` through `webview2-com-sys`, so no separate WebView2 loader DLL is required next to the bridge DLL.

## Updating The Committed DLLs

Rebuild both committed Windows artifacts from the repository root:

```text
pwsh -File community/plugins/ui.webview/native/WinWebView2Bridge/build.ps1 -All
```

The script builds `x86_64-pc-windows-msvc` and `aarch64-pc-windows-msvc`, then copies each DLL to the matching committed plugin path.

To rebuild only one architecture for local debugging, pass a Rust target triple:

```text
pwsh -File community/plugins/ui.webview/native/WinWebView2Bridge/build.ps1 -Target x86_64-pc-windows-msvc
pwsh -File community/plugins/ui.webview/native/WinWebView2Bridge/build.ps1 -Target aarch64-pc-windows-msvc
```

Stop any dev IDE process that has loaded the DLL before copying; Windows locks loaded DLL files.

## Toolchain Notes

The build script prepends the rustup-managed `%USERPROFILE%\.cargo\bin` to `PATH` when it exists. This avoids picking up standalone Rust installations that may not have the requested targets installed.

For the arm64 target, the script tries to find Visual Studio `vcvarsall.bat` and runs Cargo from the `x64_arm64` developer environment. If that fails, initialize the environment manually and rerun the arm64 target command:

```text
set "PATH=%USERPROFILE%\.cargo\bin;%PATH%"
"%ProgramFiles(x86)%\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvarsall.bat" x64_arm64
pwsh -File community/plugins/ui.webview/native/WinWebView2Bridge/build.ps1 -Target aarch64-pc-windows-msvc
```

## Verification

```text
cargo check --manifest-path community/plugins/ui.webview/native/WinWebView2Bridge/Cargo.toml
pwsh -File community/plugins/ui.webview/native/WinWebView2Bridge/build.ps1 -All
git diff --numstat -- community/plugins/ui.webview/lib/webview-native/win
git diff --stat -- community/plugins/ui.webview/lib/webview-native/win
./bazel.cmd build @community//plugins/ui.webview:webview_plugin_zip
```

`git diff --numstat` should show binary changes for both committed DLLs after rebuilding both architectures. The file sizes in `git diff --stat` may stay unchanged even when the binary contents changed.

After changing the JNI boundary, update both native and Kotlin ABI constants before copying a new DLL.

JNI callbacks from Rust to Kotlin are part of that boundary. When adding a callback method, for example `onBeforeMouseFocus()`, update `NATIVE_ABI_VERSION` in `src/lib.rs`, update `EXPECTED_NATIVE_ABI_VERSION` in `WinWebView2Bridge.kt`, rebuild with `build.ps1`, and commit the copied DLL.

The mouse-focus callback is called from the container HWND mouse activation path before WebView2 dispatches the page pointer event. Keep that callback limited to synchronizing Swing focus to the host panel. Do not call WebView2 `MoveFocus(COREWEBVIEW2_MOVE_FOCUS_REASON_PROGRAMMATIC)` from this mouse path; the click already activates the native child window, and an extra programmatic focus move can blur pointer-opened browser popups.
