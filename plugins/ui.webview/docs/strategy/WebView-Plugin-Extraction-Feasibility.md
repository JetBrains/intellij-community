# WebView Plugin Extraction Feasibility

Status: 📊 **FEASIBILITY ANALYSIS ONLY** (2026-04-30 snapshot). No extraction work has started. Current runtime API is summarized in [WebView Runtime Architecture](../architecture/WebView-Runtime-Architecture.md). Use this doc to understand what would block plugin-ization, not as a current-state snapshot.

Branch/context: `n500/262/light-webview-poc`, `community/plugins/ui.webview`, and adjacent integration changes at the time of the snapshot.

## Executive Summary

The current `ui.webview` mechanism can probably be extracted into a separately installable plugin for `2026.2+`, but the current branch is not shaped as a plugin yet. It is a platform-module POC: it wires `intellij.platform.ui.webview` into product layout, changes `platform/ui.jcef`, adds native bridge code, and connects early consumers directly to the module.

For already released IDEs, the feasibility depends on how much of the current branch is considered mandatory:

- **`2026.2+`: feasible with moderate reshaping.** The WebView API can become a plugin-provided facade if native libraries are packaged with the plugin and JCEF changes are not required for the default path.
- **`2026.1 / 261`: feasible only as a constrained backport.** A realistic MVP is macOS `WKWebView` plus a conservative JCEF fallback when the released IDE already provides the needed JCEF API. Full Windows/Linux/system-backend parity needs additional work and ABI validation.
- **Current branch: not plugin-install ready.** It assumes platform-module availability, IDE-bin/native-source lookup for bridge libraries, a JCEF platform patch, direct consumer module dependencies, and a Linux backend that is not production-interactive yet.

The recommended extraction strategy is to treat WebView as a plugin-owned rich-UI island runtime, not as a new platform dependency baked into released IDEs.

## What The Branch Changes

### 1. `community/plugins/ui.webview`

This is the core mechanism and the only part that should be considered the primary extraction candidate.

The module currently provides:

- public experimental API under `com.intellij.ui.webview`, centered around `WebViewRuntime`, `WebView`, `WebViewEngine`, `createWebViewPanel`, asset loading, and message delivery;
- backend implementations for macOS `WKWebView`, Windows `WebView2`, Linux `WebKitGTK`, and JCEF fallback paths;
- registry-controlled engine override via `ide.webview.engine`;
- tests, sample app, and internal demo plugin.

Important current coupling:

- `intellij.platform.ui.webview.iml` depends directly on `intellij.libraries.jcef`, `intellij.platform.core`, `intellij.platform.ide.core`, `intellij.platform.util.ui`, `intellij.platform.util`, JNA, coroutines, and serialization.
- `resources/intellij.platform.ui.webview.xml` registers the `ide.webview.engine` registry key as a platform module descriptor.
- `WebViewRuntime` currently selects between system, JCEF in-process, and JCEF OSR providers, which means plugin extraction has to define what is required, optional, and fallback-only.

Pluginization implication: this module can become a plugin module, but it must stop assuming that consumers can depend on `intellij.platform.ui.webview` as a built-in platform module.

### 2. JCEF Platform Patches

The branch also changes `community/platform/ui.jcef`. Against `origin/master`, the feature-relevant platform delta is concentrated in `JBCefApp.java`:

- it adds macOS in-process bundle discovery/configuration and passes framework/helper args into JCEF startup;
- it looks under the JBR `Frameworks` directory and under `cef_server.app/Contents/Frameworks` for `Chromium Embedded Framework.framework` and `jcef Helper.app`;
- it logs JCEF initialization failure instead of silently swallowing `IllegalStateException`.

These are platform changes, not plugin-deliverable changes for already released IDEs. A plugin installed into `261` or an already released `262` cannot patch `JBCefApp` internals.

Pluginization implication: the extracted plugin must not require these JCEF patches on its default execution path. If in-process JCEF is useful, keep it as an optional optimization for IDE builds that already contain the platform changes, or move it to a separate platform patch stream.

When comparing the current tree against a `261` backport base, more `platform/ui.jcef` drift appears: cache-cleanup notification code, an OSR wheel-event adjustment, and Bazel test-target generation. Those are relevant for cherry-pick/backport risk, but they should not be counted as the minimal WebView plugin requirement.

### 3. Native Bridges

The branch adds native bridge code for system WebView backends:

- macOS uses ObjC/JNA against `WKWebView`.
- Windows uses a Rust `WinWebView2Bridge` native library.
- Linux uses a Rust `LinuxWebKitGtkBridge` native library.

The Windows and Linux Kotlin bridge loaders search WebView plugin-local native resources, with source-tree fallback paths for running from a checkout.

That lookup model is plugin-install ready only if the plugin distribution contains matching loose native libraries under its own plugin home, or if a platform-supported native library packaging convention places them there.

Pluginization implication: native library discovery/loading is no longer tied to IDE `bin/`, but distribution packaging still has to copy the native libraries into the WebView plugin layout.

### 4. Markdown And ACP Consumers

The branch includes early consumers:

- Markdown preview integration adds a direct dependency on `intellij.platform.ui.webview` in `community/plugins/markdown/core/intellij.markdown.iml`.
- Markdown plugin.xml contributes a WebView-based preview provider behind a registry key.
- ACP demo and `ui.webview` demo descriptors depend on `<module name="intellij.platform.ui.webview"/>`.

These consumers validate the runtime, but they should not be part of the minimum extraction.

Pluginization implication: consumers must be decoupled from the platform module story. For a plugin-compatible model they should either depend on the WebView plugin id or remain outside the backport MVP.

### 5. Product Layout / Dev Wiring

The branch wires `intellij.platform.ui.webview` into product layout, for example through `CoreModuleSets.coreIde()` and generated module-set files.

This is not a feasibility blocker for plugin extraction. It is useful for making modules run in the current checkout, but it should be ignored when evaluating whether the WebView mechanism can ship as an external/backport plugin.

## Linux Deep Dive

The Linux implementation is materially less complete than macOS and should not be described as a production system-WebView backend yet.

Current shape:

- `LinuxWebKitGtkBridge` is a Rust `cdylib` loaded through JNI.
- Its build script links through `pkg-config` against `webkit2gtk-4.1`, `gtk+-x11-3.0`, and `x11`.
- The Kotlin side has two backend ids: `X11` and `WaylandSnapshot`.
- `WebViewRuntime` does not choose Linux system WebView in `Auto`; it can be requested explicitly through `System` / `SystemLinux` preferences.
- The Linux provider supports only JBR `WLToolkit`/Wayland today. X11 is scaffolded but intentionally rejected by `linuxBackend()`.

The Wayland path is an offscreen snapshot renderer, not a native embedded child-view backend:

- Rust creates `gtk_offscreen_window_new()` for `WaylandSnapshot`.
- It disables WebKitGTK hardware acceleration for that path and sets `WEBKIT_DISABLE_COMPOSITING_MODE=1` and `WEBKIT_DISABLE_DMABUF_RENDERER=1` by default.
- It uses `webkit_web_view_get_snapshot(...)`, converts the resulting Cairo surface into an `IntArray`, and paints the image through `SwingWebViewHostPanel.setSnapshotImage(...)`.
- Mouse and keyboard input are not forwarded into WebKitGTK in this mode. The existing Linux runtime doc explicitly states that visible content is display-only from the user's point of view.
- Programmatic JS evaluation and JS-to-Kotlin messages work, so smoke tests can validate load/evaluate/message flows without proving real user interaction.

The X11 path is closer to a native child-window strategy, but it is not enabled:

- Kotlin resolves an X11 parent window id through reflective access to `sun.awt.X11.XBaseWindow.getWindow()`.
- Rust creates a `GTK_WINDOW_POPUP`, resolves its XID, and uses `XReparentWindow`, `XMoveResizeWindow`, `XMapRaised`, and `XSetInputFocus`.
- This path needs separate validation for z-order, focus, IME, monitor scaling, hide/show, window reparenting, and disposal. It is currently a scaffold, not a compatibility promise.

Linux asset loading is also incomplete:

- `LinuxWebKitWebViewEngine.loadAsset(...)` fails with `Linux WebKitGTK WebView asset handlers are not implemented; use JCEF on Linux`.
- There is no WebKitGTK custom scheme/resource-handler parity with macOS `WebViewAssetResolver` scheme handling or the JCEF resource handler.
- A plugin that serves bundled web UI through `loadAsset(...)` cannot enable Linux system WebView until this is implemented.

External runtime notes:

- WebKitGTK `4.1` is the GTK 3 + libsoup 3 API line. Upstream WebKitGTK docs describe `webkitgtk-6.0` as the GTK 4 successor that obsoletes `webkit2gtk-4.1`, but current mainstream distributions still package `4.1`.
- The Rust bridge uses `webkit_web_view_evaluate_javascript(...)`, whose WebKitGTK docs mark it as available since 2.40, and `webkit_web_view_get_snapshot(...)`, which is the exact API used by the Wayland snapshot path.
- Ubuntu 22.04/24.04, Debian 12/13, and Fedora currently have WebKitGTK 4.1 packages, but version and security-update cadence are distro-managed. A plugin cannot pin this runtime the way the IDE pins JCEF/JBR.

Linux conclusion:

- For `2026.2+`, Linux should remain JCEF OSR fallback-only unless the target is a controlled internal experiment.
- For `261`, Linux system WebView should be cut from the plugin MVP.
- To make Linux system WebView production-capable, the missing work is not just packaging. It needs an interactive Wayland design or a validated X11 path, WebKitGTK asset handling, runtime detection, user-facing fallback, and a distro support matrix.

## JCEF Deep Dive

There are two separate JCEF surfaces in the current work, and they have different plugin-compatibility implications.

### `ui.webview` JCEF Backend

The new backend under `community/plugins/ui.webview/src/com/intellij/ui/webview/internal/jcef` is plugin-extractable in principle because it lives in the WebView module rather than patching `platform/ui.jcef`.

It adds:

- `JcefWebViewRuntime`, which checks JBCEF availability and obtains the IDE-owned `JBCefApp`;
- `JcefWebViewEngine`, which creates a `JBCefClient`, `JBCefBrowser`, message router, request handler, and context-menu suppressor through the JBCEF wrapper layer;
- `JcefBytesResourceHandler`, a small CEF resource-handler adapter for `WebViewAssetResolver` responses.

The backend should not bypass the high-level IntelliJ JCEF wrappers for browser creation, component hosting, rendering mode, or lifecycle. That keeps compatibility risk concentrated in the JBCEF API surface; raw `org.cef.*` usage should stay limited to callback handle types that the JBCEF APIs expose.

Important current assumptions:

- CEF is process-global. If some IDE feature has already initialized CEF in remote/out-of-process mode, the WebView plugin must reuse the existing JBCEF runtime rather than trying to switch the raw CEF mode.
- `JcefWebViewRuntime` routes startup through `JBCefApp.getInstance()` and uses raw `CefApp` only for unavoidable process-global state checks.
- Rendering mode is selected by `JBCefBrowserBuilder` according to JBCEF flags and runtime state. The WebView plugin must not own separate OSR/windowed implementations.
- The asset path is implemented for JCEF through `WebViewAssetResolver` and a local `CefResourceHandler`, so JCEF currently has better asset parity than Linux system WebView.

Plugin implication: JCEF is the best cross-platform fallback candidate, but only through the platform JBCEF API and after compiling and running against the exact released SDK/JCEF jars. WebView should not guarantee raw in-process CEF as a separate compatibility mode.

### `platform/ui.jcef` Patch

The platform patch in `JBCefApp.java` is not plugin-deliverable. Its purpose is to make the IDE-managed `JBCefApp` startup know how to locate a macOS in-process CEF bundle when the configured framework path is absent.

This matters because CEF startup is global:

- The WebView plugin should not initialize raw CEF first; it should route startup through `JBCefApp`.
- If the platform initializes `JBCefApp` first in a released IDE, the plugin must use the initialized JBCEF runtime as-is.
- If the platform has already initialized remote/out-of-process CEF, the plugin should rely on JBCEF browser creation to select the compatible rendering path or avoid JCEF if JBCEF reports unsupported.

Therefore, the plugin-compatible interpretation is:

- do not require the `JBCefApp` patch;
- do not promise in-process JCEF on already released IDEs;
- expose only one JCEF WebView engine and let JBCEF choose the rendering mode;
- make JCEF availability capability-checked and fail-soft.

### 261-Specific JCEF Risk

For `261`, the risky part is not only source availability in the IntelliJ repo. The plugin must match the exact JCEF binaries shipped with the target IDE/JBR.

Minimum ABI checks:

- `JBCefApp.isSupported()` and `JBCefApp.getInstance()` are available and route startup through platform JBCEF;
- `JBCefApp.createClient()` and `JBCefApp.createMessageRouter(...)` are available;
- `JBCefBrowser.createBuilder()`, `JBCefBrowserBuilder.setClient(...)`, and `JBCefBrowserBuilder.build()` are available;
- `JBCefBrowser.getCefBrowser()`, `JBCefBrowser.getComponent()`, and `JBCefBrowser.isOffScreenRendering()` are available;
- resource handler signatures match the current `CefResourceHandler` implementation;
- resource handler signatures match the current `CefResourceHandler` implementation.

If any of these fail on `261`, the backport plugin should either use the public `JBCefBrowser` wrapper instead of the raw backend, or remove JCEF fallback from the first MVP.

## Alternative: Keep It In Platform And Backport To 261

Keeping WebView in the platform makes the packaging model simpler than external plugin extraction, but it does not make the current branch safe to backport as-is. The feature delta outside `community/plugins/ui.webview` is concentrated in product/dev wiring, native bridge crates, JCEF startup changes, Markdown integration, and the ACP demo consumer.

For a `261` platform backport, do not use a raw `origin/261...HEAD` branch diff as the feature scope. That diff includes unrelated `262` platform drift. The safer process is to backport the WebView feature delta from the current branch, then manually re-check only the touched platform files against `origin/261`.

### Risk Of Changes Outside `plugins/ui.webview`

| Area | Needed for a `261` platform backport? | Risk | Assessment |
|---|---|---|---|
| Product layout and dev run configs | Only if the module must be bundled or runnable from dev setup. | Low runtime risk, medium build/product-scope risk. | Safe to omit from feasibility and first backport unless product distribution needs the module embedded. |
| `CoreModuleSets` / generated module-set XML | Needed only for platform-bundled delivery. | Low runtime risk, medium build graph risk. | If WebView stays in platform, this is probably the only product-layout part that matters; keep the change minimal. |
| Windows/Linux native Rust bridge crates | Needed only if system backends are enabled. | Low platform API risk, high build/distribution risk. | The crates do not depend on platform Rust code, but they need a `261` build and packaging story. Cut them from the first backport if macOS-only is acceptable. |
| `platform/ui.jcef` `JBCefApp.java` | Needed only for in-process JCEF behavior. | High platform risk. | Affects global CEF startup for the IDE. For `261`, apply only a surgical patch if absolutely required and preserve `261`-specific cache-clear/restart behavior. |
| `platform/ui.jcef` OSR behavior differences from `261` | Not needed for WebView unless a specific OSR fallback bug requires it. | Medium to high platform risk. | `JBCefOsrComponent` wheel behavior changes affect all JCEF OSR users; omit from first backport unless validated separately. |
| Markdown integration | Not needed for WebView runtime. | Low default runtime risk, medium compile/coupling risk. | The registry key defaults off, but the module dependency couples Markdown to WebView. Cut from first backport and re-add as an optional consumer later. |
| ACP demo | Not needed. | High churn, low production value. | Omit entirely from `261` backport. |
| Bazel/JPS metadata | Needed only for files that are actually backported. | Build-only risk. | Regenerate/check only after deciding the exact module set; avoid carrying generated noise from the current branch. |

The least risky `261` platform backport is therefore smaller than the current branch:

1. Backport `community/plugins/ui.webview` core API and macOS `WKWebView` backend.
2. Add only the minimal platform module/product registration required to compile and bundle it.
3. Keep JCEF OSR fallback behind capability checks, and do not require the `JBCefApp` in-process patch.
4. Exclude Markdown, ACP demo, Windows system backend, and Linux system backend from the first backport unless their packaging/runtime gaps are closed.
5. If a JCEF patch is still needed, port it manually into `261` instead of replacing `JBCefApp.java` with the current branch version.

## Minimal Plugin-Compatible Shape

A plugin-compatible extraction should look like this:

1. Create a real plugin identity for the runtime, for example `com.intellij.platform.ui.webview`, and expose the WebView API from that plugin.
2. Keep public API in `com.intellij.ui.webview.*`, but treat it as plugin API rather than built-in platform API.
3. Replace direct platform-module consumer dependencies with plugin dependencies where consumers are included at all.
4. Load native bridge libraries from plugin-owned locations, with dev-checkout fallback kept only as a development convenience.
5. Keep engine selection inside the plugin, but make JCEF modes optional and capability-checked.
6. Default to system WebView where the backend is actually complete; otherwise use a conservative fallback or disable that backend.
7. Keep the registry/kill-switch story, but register it from the plugin descriptor rather than a platform module descriptor.

The extraction should not require changes to product layout or generated module sets. Those are implementation details for bundling into IDE distributions, not for compatibility with already released IDEs.

## Compatibility Assessment

### `2026.2+`

Likely feasible as a separate plugin, assuming the target IDE build already contains the platform APIs used by the module dependencies.

Required adjustments:

- convert the module descriptor into plugin packaging with a stable plugin id;
- package and load Windows/Linux native libraries from the plugin;
- remove hard dependency on current-branch `JBCefApp` changes;
- make JCEF fallback use only APIs available in the target released IDE;
- decide whether Markdown/ACP consumers are excluded or converted to plugin dependencies;
- verify that `ide.webview.engine` registration from the plugin works in the target IDEs.

Expected MVP shape for `2026.2+`:

- macOS system backend: primary candidate;
- Windows system backend: possible after native packaging and asset-handler completion;
- Linux system backend: keep disabled or fallback-only until WebKitGTK runtime/support risk is validated;
- JCEF OSR fallback: useful if it works without current branch's JCEF patches;
- JCEF in-process: do not require it for plugin compatibility.

### `2026.1 / 261`

Feasible only as a narrower backport.

The initial `261` POC was macOS-only and had a much smaller API surface. The current branch added cross-platform backends, asset loading, registry engine selection, component-backed facade support, JCEF runtime selection, and native Rust bridge libraries. Those additions raise compatibility risk.

Known checks from the current investigation:

- `Foundation.executeOnMainThread(...)` exists in `origin/261`.
- `AppMode.isRunningFromDevBuild()` exists in `origin/261`.
- `PathManager.getJarForClass(Class)` exists in `origin/261`.
- `JBCefApp` in `origin/261` has remote-mode support, but not the current branch's macOS in-process bundle helper.
- `CefApp.getInstanceIfAny()` and app-args/config access used by the current JCEF path need ABI validation against the exact `261` SDK/JCEF jars.

Recommended `261` MVP:

- keep macOS `WKWebView` as the primary system backend;
- keep `loadHtml`, `loadFile`, basic JS evaluation, and message delivery;
- include asset loading only if the current `WKURLSchemeHandler` path is verified against `261`;
- keep JCEF fallback optional and disable it if ABI validation fails;
- exclude Windows/Linux system backends from the first backport plugin unless their native packaging and asset loading are completed;
- exclude Markdown/ACP integration from the backport MVP.

## Blockers And Required Cuts

### Must Change Before Plugin Extraction

- **Native loading:** Windows/Linux bridge loaders must search plugin-owned locations, not only IDE `bin/` directories.
- **JCEF dependency:** JCEF fallback must not depend on current-branch `JBCefApp` patches.
- **Plugin identity:** consumers need plugin-id dependencies or optional integration, not `<module name="intellij.platform.ui.webview"/>`.
- **Packaging:** native bridge artifacts need a build/package story for plugin distribution.
- **API compatibility:** target SDK compatibility must be checked against exact released IDE builds, especially for JCEF and registry APIs.

### Can Be Cut From Backport MVP

- product layout changes and generated module-set changes;
- Markdown preview integration;
- ACP demo integration;
- JCEF in-process bundle patching;
- Linux system WebView backend;
- Windows/Linux `loadAsset` support until resource-handler parity is implemented;
- cache cleanup notification changes in `platform/ui.jcef`;
- `JBCefOsrComponent` wheel behavior changes, unless a fallback-specific bug proves they are required.

### Backend-Specific Status

| Backend | Current state | Plugin/backport implication |
|---|---|---|
| macOS `WKWebView` | Most mature system backend; current branch includes asset scheme work. | Best MVP candidate for `261` and `262+`. |
| Windows `WebView2` | Native bridge exists, but `loadAsset` errors out because `WebResourceRequested` handling is not implemented. | Not ready for asset-bundled web UI plugin until handler and packaging are done. |
| Linux `WebKitGTK` | Wayland is display-only snapshot rendering; X11 is scaffolded; `loadAsset` errors out; auto selection uses JCEF instead. | Keep disabled for production and cut from `261` MVP. |
| JCEF OSR | Implemented in `ui.webview` with raw `org.cef.*` APIs and asset-handler support. | Best fallback candidate, but requires exact released-SDK/JCEF ABI validation. |
| JCEF in-process | Depends on process-global CEF startup state; platform `JBCefApp` patch cannot be delivered by plugin. | Cut from compatibility promise; keep as patched-IDE/internal mode. |

## Recommended Documented Decision

The WebView mechanism is extractable, but the extractable unit is smaller than the current branch.

The plugin-compatible core is:

- `WebViewRuntime` / `WebView` API;
- `SwingWebViewHostPanel` host integration;
- macOS system backend;
- optional JCEF OSR fallback if ABI-compatible;
- asset/message APIs that are fully implemented on the enabled backend;
- plugin-owned registry and native packaging.

The non-plugin-compatible or non-MVP parts are:

- platform JCEF patches;
- product layout embedding;
- direct Markdown/ACP module integration;
- Linux system backend as a production default;
- Windows/Linux asset loading until resource handlers are implemented;
- in-process JCEF as a required fallback.

This gives a practical path:

1. First prove a `2026.2+` plugin with macOS system WebView and optional JCEF OSR fallback.
2. Then evaluate `261` by compiling/running against the exact released SDK/JCEF jars.
3. Add Windows only after plugin-native packaging and WebView2 asset handling are complete.
4. Keep Linux system backend behind an explicit gate until runtime and distribution behavior are validated.

## Validation Needed Before Implementation

Before implementing extraction or a platform backport, run these checks and record the results:

- compile the extracted plugin against the target `2026.2+` SDK;
- compile the constrained plugin against `261` SDK;
- verify JCEF fallback without modifying `platform/ui.jcef`;
- for a `261` platform backport, compare every `platform/ui.jcef` edit against `origin/261` and preserve existing `261` fixes;
- verify that Markdown behavior is unchanged if Markdown integration is cut from the backport;
- verify plugin descriptor registry-key registration;
- verify native library loading from installed plugin location on macOS, Windows, and Linux where enabled;
- run smoke tests for create/load/evaluate/close/recreate;
- run asset loading smoke tests on every enabled backend;
- run focus/shortcut/copy-paste smoke tests inside a Swing host panel.
- on Linux specifically, validate both display and actual input delivery; snapshot-only rendering is not enough for production support;
- for JCEF specifically, run startup-order tests where CEF is uninitialized, already initialized in remote mode, and already initialized by platform `JBCefApp`.

For this feasibility pass, no tests are required because the change is documentation-only. Tests become mandatory when the extraction or packaging changes are implemented.

## Bottom Line

`ui.webview` should not be treated as impossible to backport, but the current branch should not be treated as a ready plugin either.

The safe conclusion is:

- **yes** for a `2026.2+` plugin after packaging/coupling cleanup;
- **yes, but limited** for `261`, probably macOS-first;
- **yes** for a constrained `261` platform backport if the outside-WebView changes are reduced to minimal module/product registration and optional, surgically reviewed JCEF changes;
- **no** for full current-branch parity as an external plugin without additional platform and backend work;
- **no** for Linux system WebView as a production backend in the current state;
- **no** for in-process JCEF as a compatibility guarantee for already released IDEs.

## References

- [JetBrains IntelliJ Platform SDK: Embedded Browser, JCEF](https://plugins.jetbrains.com/docs/intellij/embedded-browser-jcef.html)
- [JetBrains Support: JCEF missing libs problem on Linux](https://intellij-support.jetbrains.com/hc/en-us/articles/360016421559-JCEF-the-missing-libs-problem-on-Linux)
- [WebKitGTK: `webkit_web_view_get_snapshot`](https://webkitgtk.org/reference/webkit2gtk/stable/method.WebView.get_snapshot.html)
- [WebKitGTK: `webkit_web_view_evaluate_javascript`](https://webkitgtk.org/reference/webkit2gtk/stable/method.WebView.evaluate_javascript.html)
- [WebKitGTK migration notes: GTK 3 `webkit2gtk-4.1` vs GTK 4 `webkitgtk-6.0`](https://webkitgtk.org/reference/webkit2gtk/2.39.91/migrating-to-webkitgtk-6.0.html)
- [Ubuntu package: `libwebkit2gtk-4.1-0` for 24.04](https://packages.ubuntu.com/noble/libwebkit2gtk-4.1-0)
- [Debian package: `libwebkit2gtk-4.1-0` for Debian 12](https://packages.debian.org/bookworm/libs/libwebkit2gtk-4.1-0)
- [Fedora package: `webkit2gtk4.1`](https://packages.fedoraproject.org/pkgs/webkitgtk/webkit2gtk4.1/)
