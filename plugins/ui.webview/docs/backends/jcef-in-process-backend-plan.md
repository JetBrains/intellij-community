# JCEF Backend

Status: ✅ **IMPLEMENTED** as a single `ui.webview` JCEF engine provider. Asset serving, message passing, Swing embedding, and interactive input all ship. No outstanding plan work specific to this backend.

This document is the current backend note. The old implementation plan was removed because it described the deleted `WebViewFacade` API and file-extracting asset helpers.

## Purpose

JCEF is the cross-platform fallback and diagnostics backend for `intellij.platform.ui.webview`. System WebViews remain the startup-performance target where they support the needed capabilities, but JCEF provides a stable asset-backed path on platforms where the system backend is incomplete.

## Rendering Ownership

`ui.webview` exposes JCEF as one WebView engine. It does not expose or select separate OSR/windowed modes.

Rendering mode belongs to JBCEF. The backend creates a `JBCefBrowser` through `JBCefBrowserBuilder`, and JBCEF decides whether the browser is windowed, off-screen, or remote/out-of-process according to the existing JBCEF flags and runtime state.

Both providers currently report:

- `assetServing = true`
- `messagePassing = true`
- `swingEmbedding = true`
- `interactiveInput = true`

## Selection

The JCEF provider participates as one candidate:

- `JcefEngineProvider`

Selection priorities:

- `WebViewEnginePreference.Jcef` selects the JCEF provider when JBCEF is available.
- `WebViewEnginePreference.System` can use JCEF as a fallback when platform policy gives JCEF a participating priority.

The `ide.webview.engine` registry key can force `JCEF` for local diagnostics. Unsupported JBCEF environments fail with a clear reason from applicability diagnostics.

## Runtime Shape

Implementation files:

- `src/com/intellij/ui/webview/internal/jcef/JcefWebViewRuntime.kt` - CEF initialization and availability checks.
- `src/com/intellij/ui/webview/internal/jcef/JcefWebViewEngine.kt` - `WebViewEngine` implementation backed by `JBCefBrowser`.
- `src/com/intellij/ui/webview/internal/jcef/JcefBytesResourceHandler.kt` - asset and in-memory page response helper.

The engine uses JBCEF wrappers (`JBCefApp`, `JBCefClient`, `JBCefBrowser`) for lifecycle, browser creation, component hosting, and rendering-mode selection. Raw `org.cef.*` types are used only where JBCEF callback APIs require CEF handles, handlers, requests, and message routers.

## Asset Serving

JCEF asset loads use an internal secure origin:

```text
https://ij-webview-assets.local/<asset-path>
```

`JcefWebViewEngine.loadAsset(...)` installs a `WebViewAssetResolver` for the active root and serves requests through CEF request/resource handlers. This path also serves common runtime assets such as `/__webview/wvi-bridge.js`.

No localhost server is used.

## Messaging

The JCEF browser side exposes `window.__wviJcefQuery`, which the shared `window.__WVI__` bridge uses as its native transport. Kotlin receives frames through `WebViewMessageBus.transferFromJs`; Kotlin-to-JS delivery goes through `WebViewEngine.transferToJs(rawJson)`, which calls `window.__WVI__.__deliver(rawJson)` by JavaScript evaluation.

The transport is JSON-RPC at the `WebViewMessageBus` layer. Do not add call/response state to the JCEF backend directly; keep JSON-RPC behind `WebViewMessageBus` so all engines share the same public contract.

## Non-Goals

- Do not silently enable remote/out-of-process JCEF.
- Do not duplicate JBCEF's OSR/windowed implementation or expose rendering mode as a WebView engine mode.
- Do not expose generic arbitrary URL navigation as product API.
- Do not copy markdown preview's `PreviewStaticServer` / `BuiltInServerManager` model.
- Do not port IDE-specific browser behaviors such as proxy, certificate, auth, dialogs, context menus, or downloads without a concrete `ui.webview` requirement.

## Verification

For JCEF runtime or asset changes, run:

```bash
./tests.cmd --module intellij.platform.ui.webview.tests --test com.intellij.ui.webview.WebViewRuntimeTest
./tests.cmd --module intellij.platform.ui.webview.jcef.tests --test com.intellij.ui.webview.JcefWebViewRuntimeSelectionTest
```
