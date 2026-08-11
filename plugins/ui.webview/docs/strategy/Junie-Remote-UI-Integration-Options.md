# Junie Remote UI Integration Options

Status: ⬜ **DESIGN ONLY**. Comprehensive analysis of integration approaches; Approach 6 (trusted loopback Junie app with injected IntelliJ runtime) is recommended. No code implementing any approach has landed in this module.

Audience: `intellij.platform.ui.webview` maintainers and Junie UI integrators.

This document compares ways to mix the IntelliJ WebView runtime with UI code served by Junie from a local or remote web server. The immediate target is a trusted Junie UI server running on the local machine, while keeping the design clear enough to avoid accidentally granting IDE capabilities to arbitrary remote pages.

## Current `ui.webview` Baseline

`intellij.platform.ui.webview` currently optimizes for local, deterministic WebView UI.

- `WebViewAssetRoot.fromClasspath(...)` and `WebViewAssetRoot.fromDirectory(...)` describe local asset roots.
- `WebView.loadAsset(...)` loads an entry point from that root through an engine asset handler, not by navigating to `file:` or by starting a local HTTP server.
- macOS uses the custom virtual scheme `ij-webview-asset:/...`.
- Windows WebView2 and JCEF use the HTTPS virtual origin `https://ij-webview-assets.local/...`.
- The common JavaScript bridge is served as `/__webview/wvi-bridge.js` from platform resources.
- The JavaScript bridge installs `window.__WVI__`, which routes JSON-RPC frames to the native WebView transport and then to `WebViewMessageBus`.

The bridge separates request classes:

- UI asset requests are browser resource requests handled by `WebViewAssetResolver`.
- IDE calls are not HTTP. They are JSON-RPC messages sent through `window.__WVI__` and delivered to Kotlin handlers.
- Kotlin-to-page events are JSON-RPC notifications delivered back through the same bridge.

That model works well for bundled UI, but a Junie page served from `http://127.0.0.1:<port>/` cannot load `/__webview/wvi-bridge.js` from the IDE virtual origin by relative URL. For that mode, the bridge must be injected by the WebView engine.

## Approach 1: Fully Local Bundled UI

The whole WebView UI is built into plugin resources and loaded through `WebViewAssetRoot`.

Request routing:

- JS/CSS/images load from `ij-webview-asset:/...` or `https://ij-webview-assets.local/...`.
- IDE calls use `window.__WVI__` JSON-RPC.
- Any server data is fetched by explicit backend APIs or by Kotlin handlers.

Bridge availability:

- The page includes `<script src="/__webview/wvi-bridge.js"></script>`.
- The bridge is available before app code if the script tag is placed before the app bundle.

Theme and IJ controls:

- Best supported model.
- The local shell can apply theme tokens before app startup.
- Shared controls and TypeScript wrappers can be versioned with the IDE runtime.

CORS, proxy, and auth:

- Minimal browser CORS surface because assets are same-origin under the virtual WebView origin.
- Any external network access can be mediated by Kotlin and IDE proxy settings.

Security boundary:

- Strongest boundary for trusted IDE UI.
- All executable UI code ships with the IDE or plugin distribution.

Development ergonomics:

- Requires building and bundling assets into resources.
- Less convenient for teams that already have a standalone web server and route structure.

Offline and cache behavior:

- Fully offline.
- Cache behavior is controlled by the asset resolver and distribution version.

Verdict:

- Recommended for production IDE UI that can be built and shipped as plugin assets.
- Not ideal for Junie if the team needs to keep its existing local web server and API routing.

## Approach 2: Remote Data With Local UI

The WebView app remains local, but it fetches data from a remote or local service.

Request routing:

- JS/CSS/images load from local `WebViewAssetRoot`.
- IDE calls use `window.__WVI__` JSON-RPC.
- Product data comes from typed host APIs or from an explicit remote fetch facility exposed by Kotlin.

Bridge availability:

- Same as fully local bundled UI.

Theme and IJ controls:

- Strong, because the local app owns startup and can apply theme state synchronously.

CORS, proxy, and auth:

- Best handled by host-mediated fetch through Kotlin.
- Browser-direct `fetch("https://...")` is possible, but then CORS, cookies, IDE proxy settings, and corporate auth become browser concerns.

Security boundary:

- UI code is trusted local code.
- Remote service is treated as data provider, not executable UI provider.

Development ergonomics:

- Good if the team can expose data endpoints.
- Poor if the existing app is tightly coupled to its own local server and expects normal browser routing.

Offline and cache behavior:

- UI is offline-capable.
- Data availability depends on the remote service and host-mediated cache policy.

Verdict:

- Recommended when the remote side can be cleanly represented as data.
- Not the best first step for Junie if rewriting to data-only endpoints is too expensive.

## Approach 3: Remote Assets Through `WebViewAssetResolver`

The main document stays under the IDE WebView virtual origin, while selected JS/CSS/assets are fetched by the Kotlin asset resolver from a remote server.

Request routing:

- The browser requests remote module assets from a virtual path such as `https://ij-webview-assets.local/remote-assets/<module-id>/view.js`.
- `WebViewAssetResolver` maps those paths to allowed remote URLs and returns bytes to the browser.
- IDE calls still use `window.__WVI__` JSON-RPC.
- Remote data can either use host-mediated fetch or additional resolver-backed proxy routes.

Bridge availability:

- The local shell loads or injects the bridge before loading remote modules.
- Remote code runs in the same document as the local shell.

Theme and IJ controls:

- Strong if the local shell passes a stable SDK context into the remote module.
- Remote modules can implement `mount(container, context)` and consume TypeScript declarations for the IntelliJ WebView SDK.

CORS, proxy, and auth:

- Browser CORS is avoided for assets because the browser sees the IDE virtual origin.
- Kotlin controls remote download, IDE proxy settings, auth, integrity checks, and cache.

Security boundary:

- Remote JavaScript executes inside the IDE WebView document and can use any capability exposed by the shell.
- Requires allowlisted remote origins, version pinning, integrity verification, and scoped capabilities.

Development ergonomics:

- Good for remote microfrontend delivery.
- Requires remote bundle packaging discipline and a manifest format.
- Less natural for apps that expect to own page routing, API routes, and dev-server behavior.

Offline and cache behavior:

- Can support cache and offline fallback if the resolver stores validated assets.
- Cache invalidation must be explicit, usually manifest-version or content-hash based.

Verdict:

- Recommended for trusted remote UI modules that can be packaged as mountable bundles.
- Less suitable for Junie if the team wants to keep a complete local web app server unchanged.

## Approach 4: Full Remote Navigation

The WebView navigates directly to a remote or local HTTP URL, and that page owns the document.

Request routing:

- Relative browser requests such as `fetch("/api/...")`, chunks, CSS, images, and route navigation go to the remote page origin.
- IDE calls require an injected bridge because `/__webview/wvi-bridge.js` would otherwise resolve against the remote server.

Bridge availability:

- Must be provided by the engine as a document-start preload script.
- Relying on the remote page to load the bridge from a script URL is fragile and mixes IDE runtime delivery with the remote server.

Theme and IJ controls:

- Weak unless the IDE injects initial theme/runtime state and the remote page consumes a stable SDK.
- The remote app must be taught to read IntelliJ tokens, subscribe to theme changes, and use compatible controls.

CORS, proxy, and auth:

- The remote page's normal browser networking rules apply.
- Browser-direct APIs need CORS and browser-visible auth state.
- Loopback apps usually avoid CORS for their own APIs because assets and APIs share the same origin.

Security boundary:

- Dangerous for arbitrary internet pages because injected IDE capabilities would be available to that page.
- Acceptable only with strict origin policy and scoped APIs.

Development ergonomics:

- Excellent for a team that already has a web app and dev server.
- Supports existing HMR, routes, chunks, and local API endpoints.

Offline and cache behavior:

- Depends on the remote or local server.
- IDE does not naturally own asset caching unless an additional cache/proxy layer is introduced.

Verdict:

- Not recommended for arbitrary remote URLs.
- Reasonable for a trusted loopback server started by the IDE or Junie process, with explicit guardrails.

## Approach 5: Local Shell Plus Remote Mountable Bundle

The IDE owns the HTML document and app shell, while remote code is loaded as a module and mounted into a container.

Request routing:

- Shell assets and bridge load from the IDE virtual origin.
- Remote bundle assets can be routed through `WebViewAssetResolver` or explicit absolute URLs.
- IDE calls use a context object wrapping `window.__WVI__`.
- Remote data should use an explicit context API such as `context.remoteFetch(...)`, not implicit relative `fetch(...)`.

Bridge availability:

- Strong because the local shell initializes the bridge and context before loading remote code.

Theme and IJ controls:

- Strong if remote modules accept a context like `mount(container, ijContext)`.
- The shell can provide theme snapshots, theme-change subscriptions, host APIs, notifications, and lifecycle hooks.

CORS, proxy, and auth:

- Depends on how remote assets and remote data are loaded.
- Best controlled if both assets and remote fetches are host-mediated.

Security boundary:

- Better than full remote navigation because the shell controls capabilities and lifecycle.
- Still executes remote JavaScript in the IDE page, so allowlists and integrity checks are required.

Development ergonomics:

- Good for microfrontend teams.
- Requires the remote UI to be packaged as a mountable module rather than a complete app page.

Offline and cache behavior:

- Good if the shell can cache the remote manifest and bundles.
- Offline fallback can be local shell-only or last-known-good remote bundle.

Verdict:

- Recommended for a future remote UI module system.
- Not the most pragmatic first integration for Junie if the existing server already works as a complete app.

## Approach 6: Trusted Loopback Junie App With Injected IntelliJ Runtime

The WebView navigates to a Junie server running on loopback, but the IDE injects the WebView bridge, theme tokens, runtime config, and capability-scoped host APIs.

Request routing:

- Main page, JS chunks, CSS, images, and Junie app APIs load normally from `http://127.0.0.1:<port>/`, `http://[::1]:<port>/`, or optionally `http://localhost:<port>/`.
- Existing Junie calls such as `fetch("/api/...")` continue to reach the Junie local server.
- IDE calls use `window.__WVI__` or a TypeScript SDK wrapper and go through native transport to `WebViewMessageBus`.
- Theme updates and IDE events are delivered as JSON-RPC notifications.

Bridge availability:

- The IDE must inject `wvi-bridge.js` as a document-start preload script.
- The remote page should not be responsible for loading `/__webview/wvi-bridge.js`.
- A small bootstrap can install `window.__WVI_CONFIG__`, initial theme tokens, runtime metadata, and then the bridge.

Theme and IJ controls:

- Good if the IDE injects initial CSS variables before Junie app startup.
- Junie can consume IntelliJ-provided npm packages or `.d.ts` files for SDK contracts, theme tokens, and optional controls.
- Theme changes should be pushed through `WebViewMessageBus` notifications.

CORS, proxy, and auth:

- Junie app-to-Junie server requests remain same-origin browser requests.
- IDE host APIs remain out-of-band JSON-RPC, not HTTP.
- No need to rewrite Junie data layer into data-only IDE endpoints.

Security boundary:

- Treat as trusted only when the server is started by Junie or the IDE and bound to loopback.
- Require origin allowlist, exact port policy, a per-session token, navigation blocking, new-window blocking, and scoped host API namespaces.
- Do not inject the bridge into arbitrary remote origins.

Development ergonomics:

- Best fit for Junie's existing workflow.
- Preserves dev server, routes, chunks, HMR, and local APIs.
- Requires Junie to adopt SDK wrappers and theme/control packages, but not to rewrite the app as data-only endpoints.

Offline and cache behavior:

- Depends on Junie server lifecycle.
- If the local server is not running, the WebView should show an IDE-owned error/retry surface.
- Asset caching remains Junie's responsibility unless the IDE later adds a local proxy/cache.

Verdict:

- Recommended first production-oriented approach for Junie.
- It matches Junie's existing architecture while keeping IDE capabilities under the WebView runtime's control.

## Approach 7: Iframe-Based Integration

The IDE-owned shell embeds remote or Junie UI inside an `<iframe>`.

Request routing:

- The parent shell loads from the IDE virtual origin.
- The iframe loads from the remote or loopback origin and owns its own browser requests.
- Parent-to-frame communication uses `postMessage`.
- IDE calls must be proxied by the parent; the iframe should not receive direct bridge access unless it is same-origin and trusted.

Bridge availability:

- The parent has `window.__WVI__`.
- Cross-origin iframes cannot directly access parent globals.
- A message protocol is needed to forward allowed IDE operations.

Theme and IJ controls:

- More complex because theme state must be mirrored into the iframe.
- Shared controls can work only if the iframe app consumes the same SDK and message protocol.

CORS, proxy, and auth:

- The iframe uses normal browser origin rules for its own requests.
- Cross-origin messaging must validate `origin` on every message.

Security boundary:

- Useful for isolating untrusted content.
- Adds a second protocol surface between iframe and parent.
- Focus, keyboard, drag-and-drop, context menu, and lifecycle behavior are harder to make IDE-native.

Development ergonomics:

- Simple to prototype.
- Often painful for production IDE UI because many interactions need forwarding or duplication.

Offline and cache behavior:

- Same as the iframe origin.
- Parent shell can provide fallback but cannot easily cache iframe internals without a proxy.

Verdict:

- Not recommended as the primary Junie integration mechanism.
- Consider only for intentionally sandboxed or untrusted content where direct IDE integration is not required.

## Recommended Junie Direction

Use a trusted loopback web app mode with injected IntelliJ runtime.

Conceptual API:

```kotlin
suspend fun WebView.loadTrustedLoopbackApp(
  baseUrl: URI,
  policy: TrustedLoopbackWebViewPolicy,
)
```

The policy should include:

- allowed hosts: `127.0.0.1`, `[::1]`, and optionally `localhost`;
- allowed port or port range;
- per-session token required in the initial URL and handshake;
- allowed navigation scope;
- new-window behavior;
- list of exposed host API namespaces;
- optional CSP and mixed-content policy;
- debug/HMR allowance for development builds.

Startup flow:

1. Junie or the IDE starts the local UI server on loopback.
2. IDE creates a WebView and registers the native transport.
3. IDE installs a document-start bootstrap script.
4. IDE navigates to the Junie URL with a session token.
5. Junie app boots normally and reads `window.__WVI__` or SDK wrappers.
6. Junie keeps using `fetch("/api/...")` for its own local server.
7. IDE calls use JSON-RPC through `WebViewMessageBus`.
8. Theme changes and lifecycle notifications are pushed from Kotlin to the page.

Bootstrap responsibilities:

- install initial runtime config such as `window.__WVI_CONFIG__`;
- apply initial theme CSS variables to `document.documentElement` before app code runs;
- install the contents of `wvi-bridge.js`;
- expose bridge-ready signaling for engines that cannot guarantee true document-start injection;
- avoid exposing broad IDE APIs by default.

The Junie page should consume a small SDK contract rather than raw transport details:

```typescript
export interface IjWebUiContext {
  hostApi<T extends object>(namespace: string): T
  onThemeChanged(handler: (theme: ThemeSnapshot) => void): Disposable
  readonly theme: ThemeSnapshot
}
```

For the loopback app mode, this SDK can wrap `window.__WVI__` and expose typed APIs to Junie code. The `.d.ts` package is enough for compile-time API discovery, but the runtime bridge must still be injected by the IDE.

## Engine Implementation Notes

The main implementation gap is engine-level preload support.

WebView2:

- Call `CoreWebView2.AddScriptToExecuteOnDocumentCreated(...)` after `CoreWebView2` creation and before navigation.
- Keep using `window.chrome.webview.postMessage` as the transport.
- Add navigation-start and new-window handlers to enforce the loopback policy.

WKWebView:

- Add a `WKUserScript` to `WKUserContentController` with injection time `WKUserScriptInjectionTimeAtDocumentStart` and `forMainFrameOnly = true`.
- Keep using `window.webkit.messageHandlers.webviewIpc.postMessage(...)` as the transport.
- Enforce navigation policy through the native navigation delegate.

JCEF:

- Keep `CefMessageRouter` for the `__wviJcefQuery` transport.
- Prefer a document-start hook if the JCEF API available in the IDE runtime supports it.
- If no reliable document-start hook is available, use a bridge-ready handshake and require Junie app startup to await the SDK before calling IDE APIs.
- Enforce navigation policy through request/life-span handlers.

WebKitGTK:

- Use `webkit_user_content_manager_add_script` with document-start injection.
- Keep the registered `script-message-received::<handler>` transport.
- Enforce navigation policy in WebKitGTK load/navigation callbacks.

## Routing Rules for Trusted Loopback Mode

Do not overload browser `fetch` for IDE calls.

- `fetch("/api/...")` belongs to Junie and should go to the Junie local server.
- `import(...)`, JS chunks, CSS, and images belong to Junie and should go to the Junie local server.
- typed `webView.callable(...)` calls belong to the IDE and should go through native WebView transport.
- Kotlin-to-page notifications belong to `WebViewMessageBus` and should be delivered through `window.__WVI__.__deliver(...)`.

This keeps the two systems understandable:

```text
Browser HTTP origin:  Junie local web server
Native RPC channel:   IntelliJ host APIs and notifications
Theme/runtime state:  IDE-injected bootstrap plus WebViewMessageBus updates
```

## Required Tests

Policy unit tests:

- allow `127.0.0.1`, `[::1]`, and optionally `localhost`;
- reject non-loopback hosts;
- reject unexpected ports;
- reject missing or invalid session tokens;
- preserve exact origin comparison rules across redirects and navigation attempts.

Engine smoke tests:

- preload bridge exists before app script runs;
- `window.__WVI__.transport()` reports the expected transport;
- JS-to-Kotlin RPC works after loopback navigation;
- Kotlin-to-JS notification works after reload;
- bridge state is reset correctly after navigation.

Navigation tests:

- same-origin Junie navigation is allowed;
- external main-frame navigation is blocked or opened externally;
- new-window attempts are blocked or opened externally;
- subresource requests are allowed only according to policy.

Theme tests:

- initial CSS variables are present before app boot;
- initial theme snapshot is available through the SDK;
- theme-change notification updates page state;
- controls using theme tokens repaint without reload.

Junie integration smoke tests:

- `fetch("/api/...")` reaches the Junie local server;
- IDE host API calls reach Kotlin handlers;
- Junie app reload keeps the same routing model;
- server-not-running state shows an IDE-owned retry/error surface.

## Assumptions

- Junie UI is trusted only when served from a loopback server started by Junie or the IDE.
- Junie wants to keep its existing web server, routes, assets, and API model.
- Junie can consume IntelliJ-provided npm packages, `.d.ts` files, CSS variables, and optional control packages.
- The first production-worthy target is trusted local loopback, not arbitrary internet-hosted UI.
- Arbitrary remote pages must not receive IDE bridge capabilities without a separate security review and explicit capability model.

## Summary Verdict

For Junie, the recommended path is trusted loopback navigation with IDE-injected runtime. It preserves Junie's existing app architecture while giving IntelliJ control over bridge installation, theme propagation, navigation policy, and capability scoping.

Remote asset resolver and local-shell plus remote-bundle models remain useful future directions for packaged remote UI modules. Full remote navigation to arbitrary origins and iframe-based integration should not be the default for IDE-integrated UI.
