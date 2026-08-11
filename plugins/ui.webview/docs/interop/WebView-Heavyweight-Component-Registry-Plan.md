# WebView Heavyweight Overlay Interop Plan

Status: WebView-local implementation is done for notification balloons and other `HwFacadeJPanel`-based Swing overlays. Broader platform popup, menu, and hint integration remains future work and should be handled only after a concrete broken path is confirmed.

## Implemented Branch Scope

This branch uses the existing platform extension point `com.intellij.ui.HwFacadeProvider` instead of adding new popup or balloon platform code.

The WebView plugin registers:

```xml
<registryKey key="ide.webview.heavyweight.hwfacade.enabled"
             defaultValue="true"
             restartRequired="false"
             description="Enable heavyweight facade for Swing overlays shown above native WebView hosts"/>
<hwFacadeProvider implementation="com.intellij.ui.webview.impl.WebViewHwFacadeProvider" order="first"/>
```

The flag is WebView-plugin local and defaults to `true`. Turning it off makes `WebViewHwFacadeProvider.isAvailable()` return `false`, so the platform falls back to the next available `HwFacadeProvider` exactly as it did before this WebView provider participated.

The provider is a decorator, not a replacement. `WebViewHwFacadeProvider.create(target)` creates a WebView helper and passes it the next available non-WebView `HwFacadeProvider` helper. When there is no overlapping native WebView host, or when the WebView flag is disabled, painting and lifecycle calls are delegated to the existing helper path.

## Problem

Native-backed WebView implementations render outside the Swing lightweight hierarchy. Swing may place a popup, hint, or balloon above a WebView in logical z-order, but the operating system can still paint the native child window above the lightweight Swing component.

The first user-visible broken path was notification balloons. `BalloonImpl.MyComponent` already extends `HwFacadeJPanel`, and `HwFacadeJPanel` already goes through `HwFacadeHelper.create(...)`, so balloons can be fixed through the existing `HwFacadeProvider` contract without touching platform balloon classes.

## Implemented Design

### WebView host registry

`WebViewHeavyweightHostRegistry` is an internal WebView registry for native WebView host panels only. It is not a platform-wide heavyweight component registry.

Responsibilities:

- keep native host components weakly;
- notify listeners on registration, unregistration, and registered component changes;
- ignore empty target bounds;
- ignore hidden, non-showing, zero-size, or location-unavailable hosts;
- ignore the queried target and registered descendants of the target;
- when the target has a window ancestor, consider only hosts from the same window;
- expose overlap checks only inside `ui.webview` implementation code.

All registry operations are annotated `@RequiresEdt` because they read Swing component state and fire listeners that may activate Swing facade windows. The storage uses weak/concurrent containers, but that is not a license to call the API off EDT.

### Host registration

`SwingWebViewHostPanel` registers itself only when all of these are true:

- the engine is marked heavyweight through internal implementation metadata;
- the native peer exists;
- `nativePeer.attach(this)` succeeds;
- the host has not already registered.

Component-backed engines and fake/test engines do not register by default. `removeNotify()` unregisters before native detach. Resize, move, show, hide, and native frame sync paths notify the registry only while the host is registered.

The relevant mutable Swing lifecycle state (`nativePeerAttached`, `heavyweightRegistration`, host bounds/visibility sync) is EDT-owned and the new registration methods are annotated `@RequiresEdt`.

### HwFacade provider and helper

`WebViewHwFacadeProvider` is registered with `order="first"` so it can decide whether a WebView-native host overlaps the target. It still decorates the next available provider, so existing JCEF or platform behavior is preserved when WebView does not need a facade.

`WebViewHwFacadeHelper` activates only when:

- `ide.webview.heavyweight.hwfacade.enabled` is enabled;
- the target is showing;
- target screen bounds overlap a registered native WebView host.

When active, the helper paints the lightweight target into a translucent `VolatileImage` back buffer and mirrors that buffer in a non-focusable `JWindow` facade owned by the target window. The delegate helper is hidden while the WebView facade is active, then resumes when the WebView facade is not active.

The helper's mutable state (`facadeWindow`, `backBuffer`, owner/listener fields, mouse redispatch state) is EDT-owned. Entry points and state-mutating helpers are annotated `@RequiresEdt`.

### Transparent facade details

The facade keeps the details that were needed to avoid rectangular shadow/border artifacts around balloons:

- facade panel is `isOpaque = false`;
- facade panel is `isDoubleBuffered = false`;
- facade panel does not call `super.paintComponent(g)`;
- both the facade panel and back buffer are cleared with `AlphaComposite.Clear` and then painted with `AlphaComposite.SrcOver`;
- `window.type = Window.Type.POPUP` before showing;
- `window.rootPane.putClientProperty("Window.shadow", false)`;
- `window.isAutoRequestFocus = false`;
- `window.isFocusable = false`;
- `window.focusableWindowState = false`;
- `JdkEx.setTransparent(window)`.

### Mouse redispatch

The WebView facade window is not mouse-transparent. The previous mouse-transparent behavior let click events fall through to the native WebView child window, so notification action clicks were lost even though hover/move behavior could still appear to work.

The facade panel now receives mouse events and redispatches them to the deepest enabled Swing component inside the original target. Release and drag events are sent to the component that received the press when that component is still a valid dispatch target. Disabled deepest components fall back to the nearest enabled parent.

The implementation uses `MouseEventAdapter` for event conversion/redispatch and keeps the mouse state in `WebViewMouseEventRedispatcher`.

## Current Coverage

Implemented and tested:

- provider is available by default and unavailable when `ide.webview.heavyweight.hwfacade.enabled=false`;
- helper delegates when no WebView facade is active;
- mouse press/release/click are redispatched to nested Swing components;
- release after press goes to the pressed component;
- disabled deepest component falls back to enabled parent;
- registry detects overlapping hosts;
- hidden, non-showing, zero-size, non-overlapping, self, and descendant hosts are ignored;
- registry listener notifications fire for register, registered component change, and unregister;
- host-panel geometry/focus tests continue to pass.

Manual validation confirmed notification balloons render without the extra rectangular frame and notification action clicks work above a native WebView host.

## Scope Boundaries

Done in this branch:

- WebView plugin registry key and `HwFacadeProvider` registration;
- WebView-local heavyweight host registry;
- native WebView host registration from `SwingWebViewHostPanel`;
- WebView-specific `HwFacadeHelper` decorator;
- transparent facade painting;
- mouse event redispatch for notification/balloon actions;
- EDT threading annotations for the new Swing mutable state.

Intentionally not changed:

- `BalloonImpl`;
- `AbstractPopup`;
- menu popup infrastructure;
- `LightweightHint`;
- `com.intellij.ui.HwFacadeHelper`, `HwFacadeJPanel`, `HwFacadeNonOpaquePanel`;
- `com.intellij.ui.jcef.*`.

This means the current implementation targets balloons/notifications and other existing `HwFacadeJPanel` users. It does not claim full coverage for all lightweight menus, popups, and hints above native WebView hosts.

## Future Platform Work

A platform-wide heavyweight component registry may still be useful, but it is no longer the first patch for this branch.

Only start that work when a concrete platform-owned consumer needs it. Likely candidates:

- `AbstractPopup` choosing a heavyweight popup when popup bounds overlap a registered native component;
- `LightweightHint` choosing a real popup instead of layered-pane rendering when overlap is detected;
- a generic platform facade controller if non-WebView native components need the same balloon path.

Future platform work should remain WebView-agnostic:

- no WebView classes or engine checks in platform popup, hint, or balloon code;
- no `nativePeer` heuristics in platform UI;
- no dependency from low-level platform UI modules back to `ui.webview`;
- no broad z-order model unless there is evidence the simpler overlap model is insufficient.

If such a platform registry is introduced, WebView can replace `WebViewHeavyweightHostRegistry` registration with the common registry while keeping the `HwFacadeProvider` decorator approach if it still gives the narrowest integration point.

## Verification Commands

Current affected test set:

```shell
./tests.cmd --module intellij.platform.ui.webview.tests --test 'com.intellij.ui.webview.impl.WebViewHeavyweightHostRegistryTest;com.intellij.ui.webview.impl.WebViewHwFacadeProviderTest;com.intellij.ui.webview.SwingWebViewHostPanelGeometryTest'
```

Expected result at the time this document was updated: 25 tests passed.
