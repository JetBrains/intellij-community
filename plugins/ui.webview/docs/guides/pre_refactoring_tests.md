# Pre-Refactoring Tests

Status: implemented pre-refactoring guard.

Audience: coding agents implementing the `intellij.platform.ui.webview` refactoring.

Purpose: document and preserve the in-IDE smoke test suite that must run before and during runtime architecture refactoring. The test proves that a WebView hosted in an IDE tool window loads a resource-backed page and executes JavaScript. This gives the refactor a fast guard that covers the actual IDE embedding path, not only standalone Swing/JFrame smoke tests.

Current guard:

```text
com.intellij.ui.webview.WebViewInIdeSmokeTest.toolWindow_loadsResourcePage_andExecutesJavaScript
```

Use this guard whenever refactoring changes WebView runtime creation, engine selection, asset loading, Swing/tool-window embedding, or message bridge wiring. A migration step is not complete until this test has either passed or skipped for a clearly unsupported local environment.

## 1. Why This Exists

Existing `MacWebViewSmokeTest` covers direct native WebView lifecycle in a plain `JFrame`:

- native bridge creation;
- host attach/detach;
- `loadHtml`;
- `evaluateJavaScript`;
- close behavior.

That is useful but insufficient for the refactor. The risky path is IDE integration:

- tool window registration and activation;
- content creation through IDE services;
- WebView host embedded into tool window content;
- classpath/test-resource asset loading;
- JavaScript execution after the real IDE layout/attach cycle;
- JS -> Kotlin notification delivery once the v1 notification bridge exists.

Step 0 created this guard before code movement starts. Later refactoring steps must keep it green and update its implementation, not replace its scenario, when the public API moves from `WebViewFacade`/`SwingWebViewHostPanel` to `createWebViewPanel`.

## 2. Test Scope

Create an in-process IDE/JUnit smoke test first. Do not start with UI Driver unless the in-process path cannot observe the required state.

Required test name:

```text
com.intellij.ui.webview.WebViewInIdeSmokeTest
```

Required test method:

```kotlin
@Test
fun toolWindow_loadsResourcePage_andExecutesJavaScript()
```

The test should be skipped, not failed, when the current machine cannot run any WebView engine. It must fail when an engine is available but the tool-window page does not load or JS does not execute.

## 3. Minimal Page

Add test resources under:

```text
community/plugins/ui.webview/tests/testResources/webview/views/smoke/index.html
community/plugins/ui.webview/tests/testResources/webview/views/smoke/smoke.js
```

The page should be intentionally tiny. It must not depend on React, CDN resources, the demo board, product UI state, network, or timing-sensitive animations.

`index.html` should load `smoke.js` and contain one stable marker element:

```html
<!doctype html>
<html>
<head>
  <meta charset="utf-8">
  <title>WebView Smoke</title>
  <script src="smoke.js"></script>
</head>
<body>
  <main id="smoke-root">loading</main>
</body>
</html>
```

`smoke.js` should set observable state synchronously after load:

`window.__WVI__` is the WebView Interop (WVI) JS runtime bridge global.

```javascript
window.__WEBVIEW_SMOKE_EXECUTED__ = true;
document.addEventListener("DOMContentLoaded", () => {
  document.getElementById("smoke-root").textContent = "webview smoke ready";
  if (window.__WVI__ && typeof window.__WVI__.notify === "function") {
    window.__WVI__.notify("webviewSmoke/ready", {
      executed: true,
      text: document.getElementById("smoke-root").textContent,
      userAgent: navigator.userAgent,
    });
  }
});
```

The notification is a v1 bridge assertion. If the bridge is not available in the current baseline, the test may initially assert only `evaluateJavaScript`; after notification v1 lands, the notification assertion becomes mandatory.

## 4. Tool Window Setup

Do not use the demo tool window as the only smoke target. The demo is allowed to change for human-facing experiments and has fallback behavior. The smoke test needs a controlled test-only tool window/content path.

Implementation shape:

1. Create a disposable test tool window id, for example `WebView Smoke Test`.
2. Register it with `ToolWindowManager` in the test project.
3. Add a single WebView host component as the tool window content.
4. Activate/show the tool window.
5. Load `WebViewAssetRoot.fromClasspath(WebViewInIdeSmokeTest::class.java, WebViewAssetPath.of("webview/views/smoke"))` with `index.html`.
6. Wait until the host component is showing and the page has had one native attach/layout cycle.
7. Assert JS execution from Kotlin.

For the current pre-refactor API, the first implementation can use:

- `WebViewFacadeFactory.createFacade(...)`;
- `SwingWebViewHostPanel(...)`;
- `WebViewMessageBus` for the optional `webviewSmoke/ready` notification;
- `facade.loadAsset(...)`;
- `facade.evaluateJavaScript(...)`.

After `createWebViewPanel` exists, keep the same test intent and move the implementation to the new API instead of replacing the test with a different scenario.

## 5. Required Assertions

The test must assert all of these when supported by the baseline API:

- the tool window is registered;
- the tool window has content;
- the host component becomes showing or otherwise reaches the IDE attach path;
- evaluating `window.__WEBVIEW_SMOKE_EXECUTED__ === true` returns success;
- evaluating `document.getElementById("smoke-root").textContent` returns `webview smoke ready`;
- JS -> Kotlin notification `webviewSmoke/ready` is delivered after v1 notification bridge is available.

The first two assertions catch IDE/tool-window regressions. The JS assertions catch asset load and script execution regressions. The notification assertion catches bridge regressions.

## 6. Waiting And Flakiness Rules

Avoid fixed sleeps as the primary synchronization mechanism.

Preferred waiting strategy:

- wait for tool window content/host component to be showing;
- poll `evaluateJavaScript("window.__WEBVIEW_SMOKE_EXECUTED__ === true ? 'ok' : 'pending'")` until it returns `ok` or a short timeout expires;
- wait for the `webviewSmoke/ready` notification with `CompletableDeferred` and `withTimeout` once notification bridge is enabled.

Acceptable timeout range: 10-30 seconds. Keep the failure message explicit: include selected engine id, OS, tool window id, and the last JS evaluation result if available.

## 7. Platform Gating

The test should run on platforms where a WebView engine is available and the environment is not headless.

Rules:

- skip in `java.awt.headless=true`;
- skip when no engine provider/facade can be created;
- on macOS, run against WKWebView when available;
- on Linux, prefer the same engine path intended for the refactor smoke environment;
- on Windows, run when WebView2 runtime is available;
- do not make network availability a precondition.

If a platform is intentionally unsupported at the time of implementation, encode that as an assumption/skip with a clear reason, not as a failing assertion.

## 8. Implementation Steps

1. Add the test resource page under `tests/testResources/webview/views/smoke/`.
2. Add `WebViewInIdeSmokeTest` under `tests/testSrc/com/intellij/ui/webview/`.
3. Add a small test helper if needed, for example `createSmokeToolWindow(project, component)`.
4. Start with the current API so the test is useful before the refactor.
5. Run the test once before refactoring and keep it green through each migration stage.
6. When `createWebViewPanel` is introduced, migrate the test to create the tool-window content through that API.
7. When v1 notification bridge is introduced, make `webviewSmoke/ready` notification delivery mandatory.

If module dependencies need to change for IDE test infrastructure, update `.iml` first and regenerate Bazel metadata with `./build/jpsModelToBazel.cmd`.

## 9. Verification Command

Run the smoke test with the module-qualified test command:

```bash
./tests.cmd --module intellij.platform.ui.webview.tests --test 'com.intellij.ui.webview.WebViewInIdeSmokeTest'
```

If the implementation adds or changes `*.iml`, `BUILD.bazel`, or `.idea/` files, run:

```bash
./build/jpsModelToBazel.cmd
```

## 10. Acceptance Criteria

Step 0 is complete when:

- `WebViewInIdeSmokeTest.toolWindow_loadsResourcePage_andExecutesJavaScript` exists;
- test resources are local and deterministic;
- the test opens an IDE tool window, not a plain `JFrame`;
- the test proves JavaScript executed inside the loaded page;
- unsupported environments skip with a clear reason;
- the test command is documented and can be used as a refactoring guard;
- the main WebView refactoring plan treats this test as a prerequisite for implementation work.
