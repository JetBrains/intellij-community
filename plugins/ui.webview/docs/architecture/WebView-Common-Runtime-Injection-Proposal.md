# WebView Common Runtime Injection Proposal

Status: ✅ **IMPLEMENTED** (commit `2fcd9390c595c` "load common runtime assets for all views", follow-up `3d16bc555a4d8` "clarify WebView runtime injection contract"). The build-helper auto-injects `/__webview/wvi-bridge.js` and `/__webview/wvi-platform-features.js` into every view's `index.html`; feature bundles no longer import `@jetbrains/intellij-webview/runtime`. This doc is kept as design rationale + an outstanding-tests checklist.

Stage-level legend: ✅ done · ⏳ partial · ⬜ todo · 🚫 blocked · 🗑️ dropped.

| Proposed model item | Status | Note |
|---|---|---|
| 1. Build-time auto injection via `defineWebViewViewConfig` / `defineWebViewViewConfigs` | ✅ | Vite helper writes both common scripts before view entry |
| 2. Typed API package `@jetbrains/intellij-webview` carries no runtime side effects | ✅ | |
| 3. Removed `@jetbrains/intellij-webview/runtime` side-effect imports from consumers | ⏳ | verify per consumer: `community/platform/ui.webview/demo`, `community/platform/ui.webview/markdown-preview` |
| 4. `WebViewAssetResolver` serves `/__webview/*` independently from view roots | ✅ | |
| 5. Future base mechanisms join the same injection list | ✅ (convention, not code) | |
| Tests: source-level (`focusInterop.test.mts`, theme tests) | ✅ | |
| Tests: build-helper contract (generated HTML has both common assets before the view entry) | ⬜ | |
| Tests: resolver-level contract (`WebViewAssetResolverTest` asserting `/__webview/wvi-bridge.js`, `/__webview/wvi-platform-features.js`, sample view HTML, sample view `view.js` does not contain platform-feature symbols) | ⏳ | |
| Repository guard: forbid `@jetbrains/intellij-webview/runtime` imports in feature views | ⬜ | |
| Consumer artifact guard (each `resources/webview/views/<id>/` contract) | ⬜ | |

## Problem

WebView pages currently depend on shared platform behavior that is easy to desynchronize from client bundles. The failure mode is simple: a view imports or bundles an old copy of `@jetbrains/intellij-webview/runtime`, the platform code changes focus or theme behavior, and the generated `view.js` keeps the stale implementation. The host-side code and tests may be correct while the actual page never installs the new base mechanism.

The focus regression exposed this directly. `focusInterop.ts` contained `pointerdown -> WebViewFocusHostApi.activated()`, but a consumer bundle still contained the older runtime without that listener. The result was a page that rendered, exposed typed APIs, and passed isolated focus tests, but did not notify Swing when the user clicked inside the WebView.

This is not specific to focus. The same class of bug applies to any platform-owned page mechanism:

- bridge bootstrap and transport selection;
- theme delivery and theme-change notifications;
- Swing/WebView focus activation and focus-boundary traversal;
- future base behavior such as keyboard/IME workarounds, accessibility hooks, diagnostics, or standard event routing.

## Goal

Make every WebView page receive all required platform mechanisms automatically, before any feature code runs, without requiring client code to import or bundle those mechanisms.

The invariant should be:

> A feature bundle may use typed WebView APIs, but must not carry platform-owned runtime implementations.

Client code should import only typed API surfaces such as `apiId`, `webView`, `webViewTheme`, `WebViewCallable`, and `WebViewImplementable`. Platform-owned runtime code should be loaded as shared WebView assets served by `WebViewAssetResolver`.

## Non-Goals

- This does not redesign JSON-RPC or typed API declarations.
- This does not require every WebView page to use React, Vite, or any specific UI framework.
- This does not move feature protocol implementations into the platform runtime.
- This does not introduce backend-specific focus fallbacks. Native fallback work should still go through the same host focus path if a concrete uncovered scenario appears.

## Current Base Runtime Split

There are two categories of runtime code:

- **Common platform assets** served under `/__webview/`:
  - `/__webview/wvi-bridge.js` installs `window.__WVI__` and the low-level transport bridge.
  - `/__webview/wvi-platform-features.js` installs platform features on top of that bridge, currently theme and focus interop.
- **Feature app bundles** served from `resources/webview/views/<view-id>/`:
  - `view.js` contains product UI code and may use typed proxies from `@jetbrains/intellij-webview`.
  - `index.html` must load common platform assets before `view.js`.

The broken pattern is a feature entry point with:

```ts
import "@jetbrains/intellij-webview/runtime"
```

That side-effect import copies platform feature implementations into the feature bundle. It creates one platform runtime per generated app, which makes stale dependency links and partial rebuilds dangerous.

## Proposed Model

The supported platform WebView build path is `@jetbrains/intellij-webview/vite` through `defineWebViewViewConfig` or `defineWebViewViewConfigs`. In that pipeline, common runtime injection is automatic and non-optional: a view author provides feature HTML and application code, but does not choose or remember the platform runtime prelude. Bypassing this helper is an advanced path and should be treated as unsupported unless it uses an equivalent helper with the same injection contract.

### 1. Platform-owned auto injection

The shared Vite helper should inject common WebView runtime assets into every generated view HTML:

```html
<script src="/__webview/wvi-bridge.js"></script>
<script src="/__webview/wvi-platform-features.js"></script>
<script type="module" src="./view.js"></script>
```

Feature authors should not add these tags manually. When they use `defineWebViewViewConfig` or `defineWebViewViewConfigs`, there should be no per-view step where the common runtime assets can be forgotten.

The helper should enforce this in two places:

- `transformIndexHtml` for dev-server and normal Vite HTML processing;
- `generateBundle` for the final build artifact, so generated resources stay correct even if another plugin or build mode bypasses the first hook.

The scripts must be injected before application scripts. `wvi-platform-features.js` depends on `wvi-bridge.js`, and feature code can depend on both `webView` and `webViewTheme` being usable from the first module evaluation.

### 2. Typed API package stays importable

The package `@jetbrains/intellij-webview` remains the public TypeScript surface for feature code:

```ts
import { apiId, webView, webViewTheme, type WebViewCallable } from "@jetbrains/intellij-webview"
```

Those imports must not install platform mechanisms. They should provide lazy proxies and types only. If the page is loaded outside the host without common assets, the failure should be explicit and early, not silently downgraded to a duplicate runtime.

### 3. Remove feature-side runtime imports

All current consumers should remove `@jetbrains/intellij-webview/runtime` side-effect imports:

- `community/platform/ui.webview/demo/webview-src/views/sample-panel`
- `community/platform/ui.webview/demo/webview-src/views/controls-showcase`
- `community/platform/ui.webview/demo/webview-src/views/markdown-link-graph`
- `community/platform/ui.webview/markdown-preview/webview-src/views/markdown-preview`

Their generated `index.html` files should load `/__webview/wvi-bridge.js` and `/__webview/wvi-platform-features.js`. Their generated `view.js` files should not contain `installWebViewFocusInterop`, `installIJTheming`, or other platform feature implementations.

### 4. Common assets remain resolver-owned

`WebViewAssetResolver` should continue serving `/__webview/*` independently from each view root. This keeps platform runtime assets stable for classpath roots, directory roots, and dev-source roots.

The resolver should not be the primary injection mechanism. It should not rewrite arbitrary view HTML or force-run common JavaScript before application code. Kotlin owns common asset serving and diagnostics; the frontend build helper owns the HTML prelude.

The view author should not add raw `/__webview/*.js` tags manually. Manual tags make ordering and future asset additions easy to miss. The build helper should own the list.

### 5. Future base mechanisms join one list

When a new base mechanism is needed, it should be added to the platform features entry point or to a new common asset listed by the same injection helper. Examples:

- focus activation or traversal changes;
- theme additions;
- keyboard or IME normalization;
- standard context-menu policy;
- accessibility or diagnostics bootstrap;
- platform capability announcements.

The rule is that a mechanism needed by all or most WebView pages goes into common runtime assets. It should not be introduced as a feature-side import that every consumer must remember to add.

## Test Strategy

The tests should catch the exact stale-bundle failure mode, not only isolated source behavior.

### Source-level tests

Keep focused tests for the feature implementations themselves, for example:

- `focusInterop.test.mts` verifies `pointerdown` calls `hostApi.activated()` and focus-boundary traversal behaves correctly.
- Theme tests verify initial theme and theme-change delivery.

These tests are necessary but insufficient because they do not prove that generated pages load the implementation.

### Build-helper contract tests

The shared Vite helper should have focused tests for the generated HTML contract:

- HTML without common runtime assets gets both `/__webview/wvi-bridge.js` and `/__webview/wvi-platform-features.js` before application scripts;
- partial injection does not pass silently: HTML containing only one common asset either gets the missing asset injected in the correct order or fails explicitly;
- duplicate manual tags are not required for supported views.

### Resolver-level contract tests

`WebViewAssetResolverTest` should assert that:

- `/__webview/wvi-bridge.js` resolves for any root;
- `/__webview/wvi-platform-features.js` resolves for any root;
- platform features contain expected base mechanisms such as focus activation and theme handling;
- representative view HTML loads common assets before `view.js`;
- representative view `view.js` does not contain platform feature implementations.

This catches stale test fixtures and generated resources that still bundle old runtime code.

### Repository guard

Add a lightweight guard that searches production WebView frontend sources for forbidden side-effect imports:

```text
@jetbrains/intellij-webview/runtime
```

Allowed matches should be limited to platform runtime implementation files and tests. Feature views should fail the guard if they import this entry point.

### Consumer artifact guard

For each generated production view, verify the artifact contract:

- `resources/webview/views/<view-id>/index.html` contains `/__webview/wvi-bridge.js`;
- `resources/webview/views/<view-id>/index.html` contains `/__webview/wvi-platform-features.js` before `view.js`;
- `resources/webview/views/<view-id>/view.js` does not contain platform feature implementation symbols.

This guard can start with the known view list and later be generalized by scanning `resources/webview/views/*/index.html`.

## Migration Plan

1. Update `@jetbrains/intellij-webview/vite` helpers to inject common runtime assets in dev and production output.
2. Remove `@jetbrains/intellij-webview/runtime` imports from all current feature entry points.
3. Rebuild all current WebView resources.
4. Add resolver and artifact guards so stale bundles fail tests.
5. Update authoring docs to tell feature authors to import typed APIs only.
6. Keep `/__webview/wvi-platform-features.js` as the single place where common page-side mechanisms are installed.

## Open Questions

- For non-Vite consumers that cannot use `@jetbrains/intellij-webview/vite` or a minimal equivalent helper, should there be a Kotlin-side HTML rewrite fallback in `WebViewAssetResolver`? This would be a fallback only, not the default architecture.
- Should the artifact guard be a Kotlin test, a Bun test, or both? Kotlin sees classpath resources; Bun sees frontend source and generated files more directly.
- Do we want a manifest for common runtime assets, or is the helper-owned ordered list enough until there are more than two assets?

## Recommendation

Use build-time HTML injection through the supported platform WebView build pipeline as the primary mechanism and `WebViewAssetResolver` as the common asset server. Treat feature-side runtime side-effect imports as forbidden for production views.

This keeps focus, theme, and future platform behavior in one runtime path while still letting feature code use typed WebView APIs without knowing how platform bootstrap is assembled.
