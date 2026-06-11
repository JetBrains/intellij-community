# Windows WebView2 Application-Mode Plan

Status: ✅ implemented

## Goal

Make the Windows WebView2 backend behave like an embedded application surface, not like a browser tab. The default WebView2 page should not expose browser navigation gestures, browser zoom, browser context menus, status UI, autofill prompts, password-save prompts, or elastic scroll effects.

This plan applies only to the Windows system WebView backend. It does not change JCEF, macOS WKWebView, Linux WebKitGTK, or the public Kotlin/TypeScript WebView APIs.

## Native Defaults

Configure these defaults in `community/platform/ui.webview/native/WinWebView2Bridge/src/lib.rs` before the page is reported as created to Kotlin:

- Set WebView2 environment browser arguments to `--disable-features=ElasticOverscroll` before `CreateCoreWebView2EnvironmentWithOptions`.
- After `controller.CoreWebView2()` succeeds, call a dedicated helper such as `configure_webview_application_settings(...)` before attaching readiness-sensitive state.
- Keep `IsScriptEnabled=true` and `IsWebMessageEnabled=true`; the WebView runtime and bridge require them.
- Disable browser chrome and browser behavior: script dialogs, status bar, user-opened DevTools entry points, default context menus, host objects, zoom controls, built-in error pages, browser accelerator keys, general autofill, password autosave, and swipe navigation.
- Use optional settings interfaces by casting from `ICoreWebView2Settings`; unsupported optional interfaces should not block creation on older runtimes. If a supported setter fails, fail creation because the runtime accepted the capability but rejected the policy.

Do not change the Java/native method signatures for this work. The ABI version only needs a bump if the JNI boundary changes.

Implementation is split between the native Windows bridge and the common WebView runtime: shared environment creation sets the `ElasticOverscroll` browser feature off, each created WebView applies the native application-mode settings before handlers and page-ready state are attached, and the runtime cancels browser-default ctrl-wheel page zoom.

## Zoom Policy

Browser/page zoom controls are disabled globally for WebView2 application UI. Ctrl+plus, Ctrl+minus, and Ctrl+wheel should not resize the whole WebView document. Native pinch input remains enabled so interactive surfaces can handle app-level gestures inside the page; the common WebView runtime cancels browser-default ctrl-wheel zoom for the WebView2 transport in JavaScript without stopping event propagation.

DevTools remain a host-controlled debugging tool. WebView2 `AreDevToolsEnabled=false` disables user entry points through the default context menu and keyboard shortcuts; it does not remove the native `OpenDevToolsWindow` API for an explicit IDE-side debug action.

Local zoom remains a component concern. A graph, canvas, diagram, image preview, or similar surface may implement its own zoom by handling wheel or pointer gestures inside that component and updating its own viewport, scale, transform, or renderer state. The global runtime guard only cancels browser-default page zoom; it must not stop event propagation or emulate element-level zoom.

If a whole WebView page later needs browser/page zoom as an intentional feature, add an explicit creation policy rather than a DOM-level exception.

## Out Of Scope

- Do not disable JavaScript, web messages, or the WebView bridge.
- Do not disable reputation checking or other security protections just to remove browser-like behavior.
- Do not change user agent, PDF toolbar behavior, or download policy in this plan.
- Do not add CSS `overscroll-behavior` unless native `ElasticOverscroll` is insufficient in a specific follow-up.

## Verification

- `cargo check --manifest-path community/platform/ui.webview/native/WinWebView2Bridge/Cargo.toml`
- `./tests.cmd --module intellij.platform.ui.webview.tests --test com.intellij.ui.webview.impl.windows.WinWebViewEngineTest`
- Manual Windows smoke test in a WebView demo page:
  - no elastic stretch at scroll boundaries;
  - no browser back/forward swipe navigation;
  - no browser context menu, status bar, page zoom, autofill dropdown, or password-save prompt;
  - normal scrolling, typing, bridge calls, and component-owned local zoom still work.
