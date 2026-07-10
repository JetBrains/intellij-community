# Coding Guides

## Purpose

This document contains only Kotlin coding conventions for `intellij.platform.ui.webview`.
Project architecture, rendering strategy, and runtime constraints are documented in the proposal.

## Kotlin API Design

- Prefer explicit interfaces for public contracts and keep implementation details internal.
- Prefer immutable `data class` models (`val` fields) for protocol and state transfer.
- Use sealed hierarchies for closed result/error families.
- Avoid `Any` in public APIs; use strongly typed models and serializers.
- Keep nullable types explicit and minimal; prefer non-null defaults when possible.

## Composition and UI Decoupling

- Prefer composition/aggregation over inheritance for feature assembly.
- Use inheritance only for clear and stable `is-a` relationships or required framework extension points.
- Keep rendering/business logic independent from Swing types; isolate Swing-specific code in host/adapters.
- Depend on small rendering abstractions (for example, `WebView` / `WebViewEngine`), not directly on `JPanel` or other Swing classes.
- Do not treat this as dogma: choose the simpler design when inheritance is objectively clearer and does not increase coupling.

## Coroutines and Suspend APIs

- Use structured concurrency only; avoid `GlobalScope`.
- Keep suspend APIs cancellation-friendly and side-effect boundaries explicit.
- Never block coroutine threads in production code (`runBlocking` is test/bootstrap-only).
- Prefer `withContext` for dispatcher hops instead of ad-hoc thread APIs.
- Keep long-running handlers cooperative (`isActive`, cancellable suspensions).
- Take into attention [Ownership Guideline](kotlin-reactive-stream-ownership-guideline.md)

## Threading Model

WebView code crosses three threading worlds and uses two dedicated dispatchers to keep them apart:

- **EDT (AWT event dispatch thread)** — owns Swing components and the host panel. UI mutations only.
- **macOS main / AppKit main thread** — owns `WKWebView` and Foundation calls. All native WebView work on macOS funnels through `MacMainThreadDispatcher`, which is a `CoroutineDispatcher` built on top of `Foundation.executeOnMainThread()` (already used widely by the platform). Do NOT use Toolbox's `PrimordialMainDispatcher` shape here; it requires a custom native library that we don't need because `Foundation` is already on the classpath.
- **WebView2 dedicated thread (Windows)** — `WebView2-Thread`, owns the WebView2 controller and child HWND. All STA operations are dispatched there via `WebView2Dispatcher` (see [windows-webview2-off-edt-plan](../backends/windows-webview2-off-edt-plan.md)).

Contract for public WebView API:

- public facade methods (`WebView`, `WebViewMessageBus`, `WebViewInterop`, `createWebViewPanel`, asset providers) may be called from any thread — the implementation normalizes to the required native thread internally;
- `SwingWebViewHost.component` and any Swing-typed property are EDT-only — document on KDoc as in the [Threading KDoc Convention](#threading-kdoc-convention) below;
- never block EDT waiting for native completion (`waitUntilDone=true` style is forbidden in production code);
- bridge native callbacks resume coroutines via `suspendCancellableCoroutine`; cancel pending work on `WebView.close()` so close cannot hang.

### EDT vs macOS main-thread identity

On JBR builds with `-XstartOnFirstThread` (or equivalent JBR native integration) AWT can start its event loop **on** the AppKit main thread. EDT and the macOS main thread become the same thread, the dual-dispatcher model collapses to one thread, and deadlock characteristics change. The dispatchers stay correct (a single-thread version is still serialised), but assumptions about cross-thread hops do not hold.

- Treat this as an environment fact, not a bug — production paths must work whether the threads are distinct or merged.
- If you are writing a tricky cross-thread sequence, add a one-time diagnostic at startup that records whether `EDT == macOS main thread`. This lets you tell apart "deadlock because of duplicated dispatch" from "ordering bug" if a regression surfaces.
- Document such expectations on the calling site, not silently in the dispatcher.

History: this constraint was identified during POC-0 (see `historical/historical-decisions.md` § "EDT vs macOS main-thread identity").

## Error Handling

- Preserve typed error context; do not collapse everything into generic exceptions.
- Avoid swallowing exceptions; either handle with context or rethrow.
- Keep logging structured and include actionable metadata.
- Convert low-level exceptions to domain-level errors at module boundaries.

## Readability and Maintainability

- Prefer small focused functions over long multi-purpose methods.
- Use expression bodies and local extension functions when they improve clarity.
- Keep extension functions pure unless side effects are explicit in naming.
- Avoid hidden global state and static mutable singletons.
- Add concise comments only for non-obvious intent.

## Java Interop

- Keep Java-facing APIs explicit (`@JvmStatic`, `@JvmOverloads`) only when needed.
- Avoid Kotlin-only API shapes in cross-language entrypoints when Java callers are expected.
- Keep nullability contracts stable for Java consumers.

## Tests

- Test contract behavior, not implementation details.
- Cover cancellation, timeout, and error propagation paths for suspend APIs.
- Prefer deterministic tests with controlled dispatchers and explicit time control.

## Review Notes

### `@ApiStatus.Experimental` Rule

All public types in this module must be annotated `@ApiStatus.Experimental` (or `@ApiStatus.Internal`) since this is POC code. This prevents accidental external adoption by plugin developers or other platform modules before the API surface is stabilized. Apply this to interfaces (`WebView`, `WebViewEngine`, `SwingWebViewHost`), factory methods, and any public data classes.

### Threading KDoc Convention

Host panel classes that hold both EDT-bound properties (`component`) and thread-safe properties (`engine`) should document thread affinity explicitly in KDoc. For example:

```kotlin
/**
 * Swing host adapter for a WebView engine.
 *
 * @property component The Swing component. EDT-only — must be accessed and added to hierarchy on EDT.
 * @property engine The WebView engine. Thread-safe — may be called from any thread.
 */
```

This convention should be applied consistently to avoid threading bugs as the module grows.

### Documentation Co-location Note

`Coding-Guides.md` and `kotlin-reactive-stream-ownership-guideline.md` contain general Kotlin conventions (coroutine ownership, composition patterns, error handling) that are useful beyond `ui.webview`. If other modules adopt similar patterns, consider extracting shared conventions to a common location (e.g., `community/docs/kotlin-conventions/`) to avoid duplication and drift.
