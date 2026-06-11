# WebView — Historical Decisions Digest

One-line summaries of approaches that were tried and changed in `community/platform/ui.webview/`. Use this when reviewing why current code looks the way it does, or before proposing to revisit a direction that has already been considered.

Source POC documents have been removed; this digest is the only persistent record of the why. For the live design, see [`directory.md`](../directory.md).

## 1. Asset serving · 2026-04 → 2026-05 · approach changed

- **Tried:** macOS-only direct `WKURLSchemeHandler` with URL shape `ij-webview://local/...` and a two-branch `file:` / `jar:` extracting asset loader (`WebUiAssetLoader`, `SystemWebViewDemoAssetLoader`) that wrote to `PathManager.getSystemPath()/webview-cache`.
- **Changed because:** disk extraction caused stale-cache bugs, the `file:`/`jar:` split was redundant once dev-run was confirmed jar-packaged, and a per-OS scheme handler did not generalize to Windows/Linux.
- **Now:** engine-neutral `WebViewAssetRoot` + `WebViewAssetResolver` + `WebViewEngine.loadAsset`. Internal schemes are backend details: `ij-webview-asset:/` on macOS, `https://ij-webview-assets.local/` on Windows/JCEF. No on-disk cache. Common assets live under `/__webview/...`.
- **See:** `architecture/WebView-Runtime-Architecture.md` (Asset Loading section).

## 2. EDT vs macOS main-thread identity · 2026-04 · principle still active

- **Tried:** Treating EDT and macOS main thread as separate dispatchers in all cases.
- **Found:** On JBR with `-XstartOnFirstThread` (or equivalent JBR native integration) EDT and the AppKit main thread can be the *same* thread. The dual-dispatcher model collapses to a single thread; deadlock characteristics change.
- **Decided:** Keep dual dispatcher (`MacMainThreadDispatcher` on top of `Foundation.executeOnMainThread()`) as the contract — public WebView facade methods may be called from any thread and are normalized internally; UI/Swing remains EDT-only; no synchronous EDT wait for native completion.
- **Diagnostic recommendation:** runtime check at startup that detects whether `Thread.currentThread()` on EDT equals the macOS main thread; document the asymmetry on `SwingWebViewHost` (the `component` is EDT-only, the WebView facade is not).
- **See:** `guides/Coding-Guides.md` (Threading section).

## 3. JS ↔ Kotlin bridge envelope · 2026-04 → 2026-05 · approach changed

- **Tried:** Ad-hoc notification-only frames `{"method": ..., "params": ...}` (POC-0) and a hand-rolled symmetric envelope (POC-1 Kotlin JSON-RPC Spec).
- **Changed because:** non-standard envelopes were ambiguous, hard to test, and unable to express request/response cleanly.
- **Now:** strict JSON-RPC 2.0 wire protocol with `jsonrpc: "2.0"` required. Old notification-only frames are *invalid* and dropped by the dispatcher. `window.__WVI__` is the JS bridge global; `transferToJs` / `transferFromJs` are the engine plumbing.
- **Design split that survived:** two-layer public API — level-1 `WebViewMessageBus` (raw JSON-RPC primitives) and level-2 `WebViewInterop` (typed Kotlin interface binding via `WebViewCallable` / `WebViewImplementable`). `WebViewInterop.messageBus` is a deliberate escape hatch, not a leak.
- **See:** `architecture/WebView-JsonRpc-Design.md`, `architecture/WebView-TS-RPC-API-Design.md`.

## 4. Frontend stack · 2026-04 → 2026-05 · approach broadened

- **Tried:** React-first rendering POC (`Lightweight-System-WebView-React-Integration`) with CDN-backed React runtime in the sample bundle.
- **Changed because:** the renderer choice should be a feature-author decision, not a platform mandate; bundled CDN-loaded JS contradicts offline / supply-chain requirements.
- **Now:** framework-agnostic platform with explicit policy. Custom Elements + Lit recommended as default; Preact/React/Svelte/Ring UI tradeoffs documented. CDN dependencies forbidden in production bundles.
- **See:** `frontend/WebView-Frontend-Framework-Policy.md`.

## 5. Common runtime side-effect import · 2026-05 · approach changed

- **Tried:** Each view bundled platform runtime by doing a side-effect import: `import "@jetbrains/intellij-webview/runtime"`.
- **Changed because:** stale-bundle hazard — feature bundles could ship an old copy of focus/theme interop while host code expected the new one. The focus regression around `pointerdown → WebViewFocusHostApi.activated()` exposed this directly.
- **Now:** common runtime is platform-injected. `defineWebViewViewConfig` (Vite helper) writes `/__webview/wvi-bridge.js` and `/__webview/wvi-platform-features.js` into every view's `index.html`; feature bundles import only typed APIs. The host serves these common assets out of `WebViewAssetResolver` independently of view roots.
- **Reference commits:** `2fcd9390c595c` (load common assets for all views), `3d16bc555a4d8` (clarify injection contract).
- **See:** `architecture/WebView-Common-Runtime-Injection-Proposal.md`.

## 6. OSR vs native embedding · 2026-04 · principle still active

- **Decided:** Native embedding (heavyweight child window) is the default path. JCEF OSR is fallback only.
- **Why:** native embedding gives native compositing, GPU-friendly rendering, and renderer-process isolation. OSR introduces buffer-copy/input complexity that masks root-cause signals during validation.
- **OSR trigger conditions (kept on record):** sustained non-fixable embedding limitations in IDE surfaces (z-order/visibility), persistent critical focus/input issues remaining after focused native-host iterations, or measured rendering consistently below acceptable baseline with no clear native-embed remediation. Trigger requires design review with recorded evidence — not silent scope creep.
- **Now:** `JCEF_OSR` provider exists in the engine registry and is used on Wayland Linux as a snapshot/fallback path; native embedding ships on macOS (WKWebView), Windows (WebView2), and Linux X11 (WebKitGTK, partial).

## 7. OS / runtime update risk vs JCEF · 2026-04 · trade-off accepted

- **Assessed:** System WebView is **not** a free JCEF replacement. Swing/native interop risks (focus, IME, shortcuts, resize, HiDPI, z-order, lifecycle) are broadly the same class as JCEF windowed mode; integration maturity is lower because the normalization layer has to be built per backend.
- **What's better with system WebView:** renderer crash isolation, native compositing, smaller in-process surface.
- **What's worse:** engine parity across macOS/Windows/Linux drifts (WebKit vs Chromium vs WebKitGTK); engines update independently of IDE releases (macOS WKWebView with the OS, Windows WebView2 Evergreen, WebKitGTK with the distribution); Linux WebKitGTK has the highest update-volatility profile.
- **Trade-off accepted:** ship per-OS system WebView with a JCEF fallback / kill switch retained until telemetry proves the system backend is stable. Use system WebView for self-contained web-like UI islands, not as a universal Swing replacement.
- **Frontend compatibility guardrails preserved:** target widely available platform features (MDN Baseline as starting point, validated in real embedded WebViews); no production CDN; strict CSP; no `eval`; bundled assets only; semantic HTML and native browser primitives preferred.

## 8. Strategic positioning · 2026-04 → 2026-05 · scope reframed

- **Original framing:** seamless Swing-interop WebView replacement for Compose for Desktop (which was cancelled).
- **Reframed:** WebView is positioned as a **process-isolated rich UI surface per OS**, not a seamless Swing-interop layer. Explicit reject: trying to recreate full Swing-style interop (e.g., transparent overlays, native-popup parity) inside a WebView. Explicit accept: pay for an interop boundary in exchange for renderer isolation and web-frontend velocity.
- **Full Russian-language analysis** (process isolation, focus, tab order, action handling, i18n, devex tradeoffs) is preserved as [`WebView-Strategic-Analysis.md`](WebView-Strategic-Analysis.md) in this directory.

## 9. Heavyweight Swing overlays above native WebView · 2026-06 · scope narrowed

- **Tried:** a broad platform-first plan with a common heavyweight component registry plus popup, hint, and balloon consumers, and an earlier prototype that leaned on JCEF `HwFacadeHelper` behavior.
- **Changed because:** the confirmed broken path was notification balloons, and balloons already use the existing platform `HwFacadeProvider` route through `HwFacadeJPanel`. A platform-wide registry would have touched unrelated popup/hint infrastructure before there was evidence those paths were broken for WebView.
- **Now:** `ui.webview` registers a WebView-local `HwFacadeProvider` decorator and a WebView-local native host registry. Native `SwingWebViewHostPanel` registers only after successful native peer attach. The helper creates a transparent non-focusable `JWindow` facade, redispatches mouse events back to Swing action components, and keeps mutable Swing state annotated `@RequiresEdt`. `BalloonImpl`, `AbstractPopup`, `LightweightHint`, and `ui.jcef` remain untouched.
- **See:** `interop/WebView-Heavyweight-Component-Registry-Plan.md`.
