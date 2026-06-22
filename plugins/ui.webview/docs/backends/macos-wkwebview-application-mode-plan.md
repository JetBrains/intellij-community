# macOS WKWebView Application-Mode Plan

Status: ✅ implemented

## Goal

Make the macOS WKWebView backend behave like an embedded application surface, not like a browser tab. The default WKWebView page should not expose browser context menus, browser page zoom, back/forward swipe navigation, elastic scroll effects at page boundaries, developer browser entry points, or credential-storage prompts where the available WebKit API allows suppression.

This plan applies only to the macOS system WebView backend. It does not change Windows WebView2, JCEF, Linux WebKitGTK, or the public Kotlin/TypeScript WebView APIs.

## Native Defaults

Configure these defaults in `community/plugins/ui.webview/src/com/intellij/ui/webview/impl/mac/WKWebViewBridge.kt` while creating each `WKWebView`, before content can load:

- Keep `javaScriptEnabled=true`; the WebView runtime and bridge require JavaScript.
- Set `javaScriptCanOpenWindowsAutomatically=false`.
- Disable user-visible developer entry points by not enabling `developerExtrasEnabled` and by setting `inspectable=false` when the selector is available.
- Disable browser navigation and page zoom through `allowsBackForwardNavigationGestures=false`, `allowsMagnification=false`, and `pageZoom=1.0` when the selector is available.
- Disable elastic rubber-band overscroll at page boundaries through the private `_setRubberBandingEnabled:` selector (a `_WKRectEdge` bitmask; pass `_WKRectEdgeNone` / `0` to disable bouncing on all edges), applied once at creation as best effort after `respondsToSelector:`. This is the engine-level WebKit analogue of the Windows WebView2 `--disable-features=ElasticOverscroll` flag, and unlike per-edge state it survives navigations. macOS `WKWebView` exposes no public `NSScrollView` for the main frame, so this per-edge SPI is the only reliable native lever; absence or failure must not fail WebView creation.
- Apply the private `_setCanUseCredentialStorage:false` selector only as best effort after `respondsToSelector:`. Absence or failure must not fail WebView creation.

Do not use blind private KVC keys for this work. Unknown KVC keys can raise Objective-C exceptions that are not safely catchable through the current JNA bridge.

## Runtime Script

Install a document-start `WKUserScript` through `WKUserContentController` for browser-like behavior that WebKit does not expose as stable macOS settings:

- Prevent cancelable `contextmenu` defaults in capture phase without stopping propagation.
- Set public DOM input-assist hints on form controls present at load, applied at document-start and once on `DOMContentLoaded`: `autocomplete="off"`, `autocorrect="off"`, `autocapitalize="off"`, and `spellcheck="false"`. Controls inserted into the DOM later are intentionally not covered.

Browser page zoom and elastic overscroll are handled entirely by the native defaults above (`allowsMagnification`, `pageZoom`, `_setRubberBandingEnabled:`), so the script carries no wheel, scroll, or keyboard listeners.

The DOM input hints remain installed even when `_setCanUseCredentialStorage:false` succeeds. The private selector suppresses credential storage, not every autofill UI source.

## Out Of Scope

- Do not disable JavaScript, message handlers, or the WebView bridge.
- Do not disable WebKit security protections such as fraudulent-content warnings.
- Do not add blind private KVC, native Objective-C exception wrappers, or WebKit SPI beyond the guarded `_setRubberBandingEnabled:` and `_setCanUseCredentialStorage:` selectors used here.
- Do not change user agent, download policy, or PDF behavior.

## Verification

- `./tests.cmd --module intellij.platform.ui.webview.tests --test com.intellij.ui.webview.MacWebViewSmokeTest`
- `lint_files` for changed Kotlin files.
- Manual macOS smoke test in a WebView demo page:
  - no browser context menu;
  - no browser page zoom;
  - no browser back/forward swipe navigation;
  - no elastic stretch at scroll boundaries;
  - no obvious credential prompt;
  - normal scrolling, typing, bridge calls, and component-owned local zoom still work.
