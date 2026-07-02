# WebView Focus and Tab Order Interop Plan

Status: **IMPLEMENTED**. Stages 1-6 and 8 landed (`api/WebViewFocusApi.kt`, `impl/SwingWebViewHostPanel.kt`, `impl/engine/WebViewFocusInterop.kt`, `webview-src/.../focusInterop.ts`); robot test coverage in `WebViewFocusInteropRobotTest.kt`. Stage 7 has focused diagnostics for the WebView focus path: host-side `[wvi-focus]` `LOG.debug` entries in `SwingWebViewHostPanel`/`WebViewFocusInterop` and page-side `[wvi-focus]` `console.debug` entries captured by `WebViewConsoleCapture`. The plan is kept as design rationale + verification checklist for v2 work (accessibility traversal parity, advanced opt-out markers).

Stage-level legend: ✅ done · ⏳ partial · ⬜ todo · 🚫 blocked · 🗑️ dropped.

| Stage | Status | Reference |
|---|---|---|
| 1. Internal focus protocol | ✅ | `api/WebViewFocusApi.kt` |
| 2. Host-side wiring at WebView creation | ✅ | `impl/engine/WebViewFocusInterop.kt`, `impl/WebViewFocusEntrySink.kt` |
| 3. `SwingWebViewHostPanel` as Swing focus proxy | ✅ | `impl/SwingWebViewHostPanel.kt` |
| 4. One Swing tab stop for component-backed engines | ✅ | `SwingWebViewHostPanel` focus traversal policy |
| 5. Page-side focus boundary management | ✅ | `webview-src/.../focusInterop.ts` (delivered via common runtime injection) |
| 6. Backend changes kept minimal | ✅ | existing `requestFocus`/`clearFocus` paths in mac/win/linux/JCEF bridges |
| 7. Diagnostics behind debug logging | ✅ | host `LOG.debug` taps plus page-side focus event logging through console capture |
| 8. Reviewable slices | ✅ | landed across `N500-262` slices including the focus robot test |

## Goal

Add deterministic keyboard focus interop between Swing-hosted IDE UI and WebView content while preserving native browser input ownership for typing, selection, IME, mouse, and scrolling.

The target model is:

- Swing sees each WebView host as one focus traversal stop.
- The browser owns DOM focus traversal while focus is inside the WebView.
- The WebView crosses back into Swing only at the DOM focus boundary on ordinary `Tab` / `Shift+Tab`.
- Programmatic focus, mouse focus, popup focus, and restore-focus flows do not force DOM first/last focus unless the direction is known.

## Current State

`SwingWebViewHost` already exposes explicit host focus hooks:

- `requestWebViewFocus()` transfers focus into the native browser.
- `clearWebViewFocus()` clears browser focus and lets Swing become the focus owner again.

`SwingWebViewHostPanel` already delegates these calls to either a `ComponentBackedWebViewEngine` or a `NativeWebViewHostPeer`. It also clears browser focus when the user clicks another Swing component in the same window.

Backend-specific focus pieces already exist:

- macOS WKWebView uses `makeFirstResponder` / `nil` through `WKWebViewBridge`.
- Windows WebView2 uses `SetFocus`, `MoveFocus(COREWEBVIEW2_MOVE_FOCUS_REASON_PROGRAMMATIC)`, and a native accelerator bridge for selected IDE shortcuts.
- Linux WebKitGTK uses `gtk_widget_grab_focus` plus X11 focus calls where applicable.
- JCEF OSR in `ui.webview` uses a focusable Swing component with disabled focus traversal keys and direct event forwarding to CEF.

The missing part is the boundary protocol: entering the first/last tabbable DOM element from Swing traversal and exiting to the next/previous Swing component at the DOM boundary.

## JBCef Reference Behavior to Reuse

JBCef has several patterns worth reusing conceptually:

- The outer `JBCefBrowser` panel is the Swing component inserted into UI, while the browser UI component is the real native event target.
- The wrapper panel is marked as focus-cycle root and focus traversal policy provider, and its policy returns the browser UI component. This keeps the browser as a single Swing traversal unit instead of exposing nested focus stops.
- `CefFocusHandler.onSetFocus` suppresses browser focus on navigation unless explicit `FOCUS_ON_SHOW` / `FOCUS_ON_NAVIGATION` or existing component focus allows it.
- Windowed JCEF reposts selected CEF keyboard events into the AWT event queue so IDE shortcuts can still run while native browser focus owns input.
- JCEF OSR disables Swing focus traversal keys and forwards key events directly to CEF, which avoids Swing consuming `Tab` before the browser can handle DOM focus.

The WebView implementation should reuse the wrapper-as-focus-proxy and native-input-ownership ideas. It should not copy JCEF-specific CEF handlers or turn ordinary browser input into Swing key synthesis.

## Proposed Design

### Swing host behavior

`SwingWebViewHostPanel` should become the stable Swing focus proxy for all backends.

Required behavior:

- Make the host panel focusable and request-focus enabled.
- Treat component-backed browser engines as one focus traversal unit, similar to `JBCefBrowser.MyFTP`, so Swing does not expose both host panel and browser component as separate stops.
- On focus gained with a traversal cause, transfer native focus into the browser and send a page-side focus entry event with direction `forward` or `backward`.
- On focus gained without a traversal direction, transfer native focus only. Do not force first/last DOM focus for mouse clicks, popup restore, or programmatic focus.
- When the page reports user activation from a pointer event, request Swing focus for the host even if no DOM input becomes focused. This makes a click on an empty WebView area move typing ownership away from the previous Swing component.
- On page-side boundary exit, clear native browser focus and call `KeyboardFocusManager.focusNextComponent(host)` or `focusPreviousComponent(host)` on EDT.
- When Swing focus moves to another component in the same window, clear native browser focus so AppKit/HWND/GTK focus does not keep consuming key events after the editor or another Swing component is focused.
- Ignore stale exit events when the host is not showing, disposed, or no longer owns the focus transition.

### Typed focus protocol

Add an internal typed WebView protocol under namespace `webview.focus`.

Kotlin side:

- `WebViewFocusPageApi : WebViewCallable` with `fun enter(params: WebViewFocusEntry)`.
- `WebViewFocusHostApi : WebViewImplementable` with `fun activated()` and `fun exit(params: WebViewFocusExit)`.
- DTO direction values: `forward`, `backward`.

TypeScript side:

- Matching `WebViewCallable` / `WebViewImplementable` declarations in the common WebView package.
- The implementation is installed from platform features, not copied into product-specific WebView UIs.

This protocol should remain internal for v1. `createWebViewPanel(...)` should wire it automatically so feature code gets consistent focus behavior by default.

### Page-side boundary handling

Install a platform feature in `wvi-platform-features.js`:

- On `enter(forward)`, focus the first tabbable DOM element.
- On `enter(backward)`, focus the last tabbable DOM element.
- On `keydown` for ordinary `Tab`, inspect current DOM focus and tabbable boundaries.
- If focus is not at a boundary, let the browser handle DOM traversal normally.
- If focus is at the first element and the user presses `Shift+Tab`, prevent default and notify host `exit(backward)`.
- If focus is at the last element and the user presses `Tab`, prevent default and notify host `exit(forward)`.
- If there are no tabbable elements, do not create a trap. Notify host exit in the requested direction.

The tabbable scanner must be conservative and browser-like enough for IDE UI controls. It should include standard form controls, anchors with `href`, buttons, selects, textareas, `[tabindex]`, `[contenteditable]`, and open shadow roots where possible. It must exclude disabled, hidden, inert, non-rendered, and negative-tabindex elements from sequential traversal.

## Hard Cases and Risks

### Browser tab order is hard to mirror

The browser's sequential focus algorithm handles details that are easy to miss: positive `tabindex`, disabled controls, `hidden`, CSS visibility/display, `inert`, `contenteditable`, radio groups, custom focus traps, `aria-activedescendant`, shadow DOM, and iframes. Any JavaScript scanner is an approximation.

Mitigation: keep the scanner narrow and boundary-only. Let the browser perform normal intra-page traversal and only decide whether the active element is the first or last reachable tab stop.

### Shadow DOM and Web Components

Open shadow roots can be scanned recursively. Closed shadow roots and iframe content cannot be reliably inspected from the common runtime.

Mitigation: support open shadow roots in v1 and document that closed shadow-root widgets must provide their own escape behavior or expose focusable host elements with correct `tabindex`.

### `Tab` can be text input, not traversal

Editors, textareas, rich text fields, and IME candidate selection can legitimately use `Tab` as input. Intercepting every `Tab` would break browser-native editing.

Mitigation: do not intercept during composition (`event.isComposing`). Add an explicit page-side opt-out marker for editor-like regions, for example `data-webview-focus-boundary="native"`, and avoid boundary interception when the active element is inside that region.

### Focus direction can be unknown

`FocusEvent.Cause.TRAVERSAL_FORWARD` and `TRAVERSAL_BACKWARD` are useful but not universal. Mouse clicks, restore after popup, `IdeFocusManager.requestFocus`, tool-window activation, and OS focus restore can enter the host without a reliable direction.

Mitigation: introduce an `auto` host-side path that only requests native browser focus and does not call `enter(first/last)`.
Do not add timeout-based repair here: page-side `enter(forward/backward)` is only valid when Swing reports a real traversal direction or the page explicitly asks to cross a boundary.

### Native focus and Swing focus can diverge

JBCef already treats native browser focus and Java focus as distinct states. System WebViews have the same issue: WKWebView can be first responder, WebView2 can own HWND focus, or WebKitGTK can own GTK/X11 focus while Swing still reports a different focus owner.

Two user-visible failure modes are especially important:

- Native WebView focus remains active while Swing focus returns to an editor. On macOS this can surface as an unsupported-key beep on every editor keystroke because the WKWebView first responder still sees keys it does not handle.
- Swing focus remains on the previous editor/component after the user clicks an empty WebView area. The editor caret can keep blinking and typing can continue to go to the old Swing focus owner, even though the user visibly interacted with the WebView.

Mitigation: make `SwingWebViewHostPanel` the single source for Swing traversal and keep backend-specific native focus calls idempotent. Page pointer activation should request Swing focus for the host without forcing DOM focus, and host-side Swing focus-owner changes should clear native browser focus when focus moves outside the WebView in the same window. Do not require Swing focus owner equality as the only proof that the browser owns input.

### macOS Robot coverage caveats

AWT `Robot` tests against macOS WKWebView are useful, but they do not behave like ordinary unit tests:

- A test process can create a visible `JFrame` and still not be the active foreground application. In that state `Robot` mouse/key events may not reach Swing even though the window is on screen. Guard this with runtime assumptions/preflight checks (`assumeTrue`) around the initial Swing focus/key injection instead of hard-failing the WebView behavior under test.
- Do not add a custom opt-in gate for local IDE runs. A headless guard plus runtime Robot/focus assumptions is the right shape: IDE foreground runs execute normally, while non-foreground/headless-like launches skip.
- Treat `Robot` click and `Robot` drag separately. A click can activate WKWebView and transfer focus correctly on macOS, while a synthetic drag still may not create a DOM text selection in WKWebView. Do not use macOS Robot text-selection as the only signal for focus correctness.
- For the macOS unsupported-key beep regression, assert the native AppKit first responder state directly: after returning focus from WKWebView to Swing, the first responder must no longer be the WKWebView or one of its descendants. This is stronger than only checking Swing `KeyboardFocusManager` state.

Selection-by-drag coverage can still be valuable on backends where the Robot primitive is reliable (for example WebView2 on Windows), but keep that test platform-specific instead of making macOS focus interop depend on WKWebView text-selection synthesis.

### Load and autofocus can steal focus

HTML `autofocus` or navigation-created focus can move browser focus even when the user is currently interacting with Swing. JBCef suppresses focus-on-navigation unless explicitly enabled.

Mitigation: only send focus entry when Swing focus actually enters the WebView host. Do not let load completion or bridge-ready events call `requestWebViewFocus()` by themselves.

### Message ordering races

Focus exit can race with navigation, dispose, bridge initialization, popup close, or another focus transfer. A stale `exit` notification could move Swing focus unexpectedly.

Mitigation: process exit on EDT, check that the host is showing and still relevant, and make duplicate exits harmless. Do not assume message delivery order across load boundaries.

### Double tab stops

Component-backed engines can expose both the wrapper panel and the nested browser component to Swing traversal. This would make users press `Tab` twice at the WebView boundary.

Mitigation: use a JBCef-style focus traversal policy provider around component-backed browser components so the WebView contributes exactly one Swing stop.

### Popups, modality, and native menus

`JBPopup`, `DialogWrapper`, context menus, native select dropdowns, file pickers, and IME candidate windows can temporarily move focus out of the browser without meaning `Tab` traversal.

Mitigation: handle only explicit page-side boundary `exit` as traversal. Treat blur/focus-lost as state only, not as a request to move to the next Swing component.

### Windows WebView2 focus threading

WebView2 focus crosses Win32 HWND focus, the WebView2 controller thread, and Swing EDT. Existing off-EDT work already calls out `AttachThreadInput` as the risky stabilization stage. The accelerator callback is synchronous, while Swing focus traversal is EDT-bound.

Mitigation: keep `Tab` boundary exit in the typed JS protocol instead of the WebView2 accelerator bridge. Use `WinWebViewShortcutInterop` only for IDE shortcuts, and keep focus operations coalesced/idempotent on the WebView2 dispatcher.

Mouse activation has one extra Windows-specific invariant. The container HWND can receive `WM_MOUSEACTIVATE` or a button-down notification before Swing observes a normal AWT focus event. The native bridge must notify `SwingWebViewHostPanel` before WebView2 dispatches the page pointer event, so the host panel becomes the Swing focus owner while the click is still being processed. That callback must only synchronize Swing focus to the host panel; it must not call WebView2 `MoveFocus(COREWEBVIEW2_MOVE_FOCUS_REASON_PROGRAMMATIC)`. The original mouse click is already activating the native WebView2 child, and an additional programmatic native focus move in the same pointer pipeline is observable by page code as a blur/focus bounce. Pointer-opened controls that call `preventDefault()` on `pointerdown` and close on `window.blur` (for example Radix-style comboboxes) will close immediately if this invariant is violated.

The same invariant applies to native focus clearing. Several WebView hosts can be installed in one Swing window, and each host observes the global `permanentFocusOwner` change. When focus moves into one host, sibling hosts see that focus owner as outside their subtree. Only a host that previously owned WebView focus should run the native clear path; an already-outside Windows host calling `clearFocusForSwingFocusTransfer()` can `SetFocus(parent)` and blur the newly activated WebView2 page while a pointer-opened popup is still settling.

The page-side pointer activation path has the matching rule: always notify the host that the WebView was activated, but do not force focus onto the pointer target after the event if the event's default action was prevented. Custom controls that intentionally prevent default must keep ownership of their open/focus behavior.

### Linux backend differences

X11 native focus and Wayland snapshot rendering have different guarantees. Wayland snapshot mode is not a normal embedded native focus owner.

Mitigation: ship and test the common protocol with best-effort backend focus calls, but keep Linux Wayland listed as limited until interactive input and focus ownership are proven with smoke tests.

### Accessibility traversal

Screen readers can move focus without JavaScript `keydown` events. Browser accessibility traversal and Swing traversal may not line up if the integration only watches `Tab`.

Mitigation: v1 covers keyboard `Tab` traversal only. Accessibility parity needs a separate validation pass with screen readers and platform accessibility APIs.

## Implementation Plan

### Stage 1: Add the internal focus protocol

Add the protocol in the public API package, but keep the concrete installation internal.

Kotlin files:

- Add `WebViewFocusDirection` as a serializable enum or sealed string-backed DTO with values `forward` and `backward`.
- Add `WebViewFocusEntry` and `WebViewFocusExit` serializable data classes.
- Add `WebViewFocusPageApi : WebViewCallable` with `suspend fun enter(params: WebViewFocusEntry)`.
- Add `WebViewFocusHostApi : WebViewImplementable` with `fun activated()` and `fun exit(params: WebViewFocusExit)`.
- Put the API id in one place: `WebViewApiId.of<WebViewFocusPageApi>("webview.focus")` and `WebViewApiId.of<WebViewFocusHostApi>("webview.focus")`.

TypeScript files:

- Add matching types under `webview-src/packages/api/src/` and export them from the package entry point.
- Use the same namespace literal, `webview.focus`, and the same method names: `enter`, `activated`, and `exit`.

Do not add public options to `WebViewPanelOptions` in this stage. The v1 behavior is always installed for panels created by `createWebViewPanel(...)`.

### Stage 2: Install host-side wiring at WebView creation time

Wire the protocol where the message bus, runtime info handler, and host component are already assembled: `WebViewEngineProvider.createWebView(...)`.

Implementation shape:

- Create the `SwingWebViewHostPanel` once inside `createHostComponent()` and pass the `WebViewInterop`/focus controller into it, or create a small `WebViewFocusInteropController` next to the host panel that owns both the host and `WebViewInterop` registration.
- Register `WebViewFocusHostApi` on the Kotlin side before returning the host component.
- Create a callable proxy for `WebViewFocusPageApi` and use it when Swing focus enters the host with known traversal direction.
- Make registration lifetime match the `WebView` lifetime. Close the registration from `WebView.close()` together with the existing theme registration and message bus.

The controller must be idempotent:

- Ignore `exit` when the host is not showing.
- Ignore `exit` when the host has already been detached or the `WebView` is closing.
- Coalesce repeated same-direction exits until Swing focus actually leaves or re-enters the host.
- Run all Swing focus traversal calls on EDT.

### Stage 3: Make `SwingWebViewHostPanel` the Swing focus proxy

Extend `SwingWebViewHostPanel` rather than each backend.

Required host changes:

- Set `isFocusable = true` and `isRequestFocusEnabled = true` in `init`.
- Add a `FocusListener` to detect focus gained/lost on the host.
- On focus gained, call `requestWebViewFocus()`.
- If the gained focus cause is `TRAVERSAL_FORWARD`, notify the page `enter(forward)` after native focus request.
- If the cause is `TRAVERSAL_BACKWARD`, notify the page `enter(backward)` after native focus request.
- For mouse/programmatic/activation causes, call only `requestWebViewFocus()` and do not send `enter`.
- Handle page-side `activated()` by calling `requestFocusInWindow()` for the host on EDT when Swing focus is not already inside the host.
- Keep the existing mouse click-out listener, but make sure it does not trigger Swing traversal; it should only clear native WebView focus.
- Track `KeyboardFocusManager.permanentFocusOwner` and clear native WebView focus when Swing focus moves to another component in the same IDE window.

Host-side `exit` handling:

- `exit(forward)` calls `clearWebViewFocus()` and then `KeyboardFocusManager.getCurrentKeyboardFocusManager().focusNextComponent(this)`.
- `exit(backward)` calls `clearWebViewFocus()` and then `focusPreviousComponent(this)`.
- If traversal fails and Swing focus remains on the host, do not resend `enter` recursively.

### Stage 4: Enforce one Swing tab stop for component-backed engines

Component-backed engines currently add their native/browser component inside `SwingWebViewHostPanel`. Align this with JBCef's wrapper policy.

Implementation shape:

- When `componentBackedEngine != null`, set the host panel as a focus cycle root and focus traversal policy provider.
- Install a small `FocusTraversalPolicy` that returns `componentBackedEngine.component` as first/last/default/before/after component, matching the JBCef `MyFTP` idea.
- Ensure the nested component does not become an additional independent Swing stop from the parent container's perspective.
- For native-peer engines, keep host panel as the single Swing stop because no nested Swing browser component exists.

This stage should be verified with a pure Swing test using `JTextField -> SwingWebViewHostPanel -> JTextField` and a fake `ComponentBackedWebViewEngine`.

### Stage 5: Add page-side focus boundary management

Install the JavaScript feature from `webview-src/packages/impl/src/platformFeatures.ts` so it is bundled into `wvi-platform-features.js`.

Implementation shape:

- Add a focused module, for example `focusInterop.ts`, under `webview-src/packages/impl/src/`.
- It registers the page implementation for `WebViewFocusPageApi.enter`.
- It creates a callable host proxy for `WebViewFocusHostApi.exit`.
- It notifies `WebViewFocusHostApi.activated` from a capture-phase pointer event so clicks on empty page regions still transfer Swing focus to the WebView host.
- It listens for `keydown` in capture phase so application code cannot accidentally create a boundary trap before the common runtime sees the event.
- It handles only `event.key === "Tab"`, no Ctrl/Alt/Meta modifiers, and no `event.isComposing`.
- It skips handling when the active element is inside an explicit opt-out subtree, for example `[data-webview-focus-boundary="native"]`.
- It prevents default only when it actually sends `exit(...)`.

Tabbable scanner policy:

- Include `button`, `input`, `select`, `textarea`, `a[href]`, `area[href]`, `iframe`, `object`, `embed`, `[contenteditable]`, `audio[controls]`, `video[controls]`, `summary`, and `[tabindex]`.
- Exclude disabled controls, `[hidden]`, hidden ancestors, `inert` ancestors, zero-size/non-rendered elements, and `tabindex < 0`.
- Support open shadow roots recursively.
- Sort positive `tabindex` elements before zero/default tabindex elements using browser-compatible order as far as practical.
- Treat closed shadow roots and cross-origin frames as opaque focusable hosts only.

### Stage 6: Keep backend changes minimal

Do not start by modifying native bridges. Native bridge work should follow only from a concrete backend failure with a smoke/robot test that proves the common contract is not enough.

Backend expectations:

- macOS: entering WKWebView uses `makeFirstResponder(webView)`. Returning focus to Swing must not blindly clear to `nil`; transfer first responder to the containing AppKit view when Swing owns typing again, otherwise WKWebView can keep seeing unhandled keys and macOS may play the unsupported-key sound.
- Windows: keep `WinWebViewShortcutInterop` for shortcuts only. Do not route ordinary `Tab` through WebView2 `AcceleratorKeyPressed` unless the JS boundary protocol proves impossible for a specific WebView2 case.
- Linux X11: rely on existing WebKitGTK focus calls first.
- Linux Wayland snapshot: mark as limited if focus ownership cannot be proven; do not block the common API on full Wayland parity.
- JCEF OSR/windowed: use existing component-backed focus path; only adjust wrapper traversal if tests reveal double stops.

Keep native bridge changes narrow and backend-owned; do not turn them into generic Swing key synthesis or cross-backend focus emulation.

### Stage 7: Add diagnostics behind debug logging

Add low-noise diagnostics only for boundary transitions.

Suggested events:

- Swing focus entered host: direction/cause/backend/debugName.
- Page `enter` delivered or skipped because direction is unknown.
- Page boundary `exit` requested: direction and active element summary.
- Host ignored stale `exit`: reason.
- Host applied Swing traversal: next/previous and resulting focus owner if available.

Use existing WebView logging facilities. Focus trace entries use the `[wvi-focus] host|page` marker. Page-side events are formatted as one `console.debug` string so `WebViewConsoleCapture` preserves the target, active element, and default-action details in `idea.log`. Do not log every `keydown`.

### Stage 8: Land in reviewable slices

Recommended commit/MR slices:

1. Kotlin/TypeScript protocol types and no-op wiring tests.
2. Host-side controller and `SwingWebViewHostPanel` focus proxy behavior.
3. Page-side `focusInterop.ts` and TypeScript tests for tabbable scanning.
4. Component-backed one-tab-stop adjustment and Swing tests.
5. Backend smoke fixes only if a real backend fails the common contract.

Mouse/native activation must not synthesize page entry later via a timer. That path only synchronizes Swing host ownership and native browser ownership; the browser click keeps its own DOM focus/default-action semantics.

Each slice should leave existing WebView panels working even if the page-side platform features script is not loaded; in that case the host can request native focus but cannot perform DOM boundary exit.

## Test Plan

Automated tests:

- Add a pure Swing/Kotlin test for host focus delegation and one-tab-stop behavior.
- Add protocol tests with a fake WebView bridge: Swing focus entry sends page `enter`, page `exit` causes next/previous Swing traversal.
- Add TypeScript tests for tabbable boundary detection, including empty pages, disabled controls, hidden elements, negative tabindex, positive tabindex, contenteditable, and open shadow roots.
- Keep Windows shortcut tests separate from focus boundary tests so shortcut routing does not become the generic `Tab` path.
- Keep a Windows WebView2 Robot regression for pointer-opened comboboxes: focus an external Swing control, click a Radix-like combobox in the embedded WebView once, assert the popup remains open after animation frames, assert Swing focus moved to `SwingWebViewHostPanel`, assert mouse activation did not synthesize page `enter(forward)`, assert an already-outside sibling WebView host did not clear native focus during activation, then click the Swing control again and assert it takes focus back from the WebView.

Smoke tests/manual checks:

- Swing field -> WebView -> Swing field with `Tab`.
- Reverse traversal with `Shift+Tab`.
- Multiple DOM controls inside the page.
- Empty/static page.
- Click on an empty/static WebView area, then type: typing must not continue in the previously focused editor/component.
- Focus editor while a macOS WKWebView preview is visible, then type: there should be no unsupported-key beep from the WebView.
- `textarea` or editor-like region where `Tab` must remain page-owned.
- Page load with `<input autofocus>` while focus is in a Swing field.
- Popup open/close around a focused WebView.
- EN typing and at least one IME path after repeated focus entry/exit.
- Real backends: Windows WebView2, macOS WKWebView, JCEF OSR/windowed, and Linux WebKitGTK where available.

Suggested commands after implementation:

```shell
./tests.cmd --module intellij.platform.ui.webview.tests --test com.intellij.ui.webview.WebViewFocusInteropTest
./tests.cmd --module intellij.platform.ui.webview.tests --test com.intellij.ui.webview.SwingWebViewHostPanelGeometryTest
```

For frontend package changes:

```shell
cd community/plugins/ui.webview/webview-src
bun run typecheck
bun run build
```

## Non-goals for v1

- Do not expose public plugin-facing focus APIs unless a concrete client needs custom policy.
- Do not attempt to fully reimplement the browser focus algorithm in JavaScript.
- Do not route plain typing or IME through Swing.
- Do not use WebView2 accelerator callbacks as the normal `Tab` boundary mechanism.
- Do not claim accessibility traversal parity before dedicated accessibility testing.
