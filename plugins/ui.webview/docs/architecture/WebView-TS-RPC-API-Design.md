# WebView TypeScript RPC API Design

Status: design note with initial Kotlin and TypeScript runtime implementation.

Audience: platform and plugin authors defining WebView protocol contracts.

Current implementation state:

- Kotlin exposes `WebViewApi`, `WebViewImplementable`, `WebViewCallable`, `WebViewApiId`, `WebViewInterop`, and `WebViewMessageBus` from `com.intellij.ui.webview.api`.
- `WebView.interop` provides the typed facade over the per-WebView `WebViewMessageBus`; the reflection binding and proxy implementation live in `com.intellij.ui.webview.impl.rpc`.
- Kotlin `WebViewApiId` stores the protocol `KClass<T>` and namespace, validates that the API type is an interface, and rejects invalid namespace strings.
- Kotlin runtime reflection registers only public methods declared directly in the protocol interface. Implementation-only methods and inherited interface methods are not protocol methods.
- Kotlin protocol methods support zero value parameters or one serializable params object. Multiple value parameters are rejected; use a serializable data class for multi-field params.
- Reflected Kotlin bindings use slash-form wire names: `namespace/methodName`.
- `WebViewImplementable` supports TypeScript -> Kotlin request/response calls for `suspend` methods and notifications for non-suspend `Unit` methods.
- Kotlin `WebViewCallable` currently sends notification methods only. Public Kotlin -> TypeScript request/response calls remain out of scope.
- TypeScript public API is exported from `webview-src/packages/api/src` through `@jetbrains/intellij-webview` and includes `apiId`, `WebViewApi`, `WebViewCallable`, `WebViewImplementable`, `WebViewApiId`, `Callable`, `WebViewImplementation`, `webview`, and typed `webview.callable(...)` / `webview.implement(...)` entrypoints.
- TypeScript bridge implementation is private under `webview-src/packages/impl/src`; it owns the generated `wvi-bridge.js` bundle, JSON-RPC frame model, native transport, and dispatch state.
- The JavaScript bridge keeps the legacy descriptor notification API, while the typed callable facade uses slash-form wire names.

Still pending / intentionally limited:

- No code generation; Kotlin and TypeScript protocol files are still manually paired.
- TypeScript runtime cannot inspect erased interfaces. Contract checks are compile-time-only at typed entrypoints plus runtime validation of namespace and implementation objects.
- TypeScript overload rejection is a type-system goal, not a runtime feature.

Related docs:

- [WebView JSON-RPC Design](WebView-JsonRpc-Design.md) - wire protocol and Kotlin message bus behavior.
- [Frontend View Model Patterns](../frontend/WebView-Frontend-View-Model-Patterns.md) - DTO, store, and view-model boundaries.
- [Frontend Testability Without IDE](../frontend/WebView-Frontend-Testability.md) - mock bridge and scenario model goals.

## Goal

WebView protocol code should require as little manual binding as possible. Feature code should define typed interfaces and a namespace. Runtime code should derive wire method names, serializers, dispatch tables, and proxy behavior from those interfaces.

The public model has two explicit roles:

| Role | Meaning |
| --- | --- |
| `WebViewImplementable` | This side implements the interface and receives calls or notifications. |
| `WebViewCallable` | This side calls the interface implemented by the other side. |

The operation kind is derived from the method signature:

| Kotlin signature | TypeScript signature | Wire kind |
| --- | --- | --- |
| `suspend fun ...: Result` | `(...): Promise<Result>` | JSON-RPC request/response call |
| `fun ...: Unit` | `(...): void` | JSON-RPC notification |

TypeScript interop operations must never look blocking. Any request/response method in a WebView protocol interface must return `Promise`.

## API Layers

The typed protocol API should sit above `WebViewMessageBus`.

| Layer | Audience | Responsibility |
| --- | --- | --- |
| `WebViewInterop` | Feature and plugin code | Typed contract binding: `implement(...)`, `callable(...)`, typed API id validation, reflection/type validation, serializer lookup, and proxy creation. |
| `WebViewMessageBus` | WebView runtime, engines, tests, low-level adapters | Physical JSON-RPC traffic: parsing frames, request ids, cancellation, queues, lifecycle, dispatching incoming frames, and sending raw request/notification frames to the bridge. |
| `window.__WVI__` bridge | JavaScript bridge runtime and tests | Native transport adapter and JSON-RPC frame delivery. |

Application code should not bind protocol interfaces directly on the message bus. Kotlin code should use `webView.interop` or another injected `WebViewInterop` instance. TypeScript feature code should use the typed API id/interface facade exported by `@jetbrains/intellij-webview`, not the raw `window.__WVI__` bridge.

Expected Kotlin shape:

```kotlin
interface WebViewInterop {
  fun <T : WebViewImplementable> implement(
    id: WebViewApiId<T>,
    implementation: T,
  ): Disposable

  fun <T : WebViewCallable> callable(
    id: WebViewApiId<T>,
  ): T
}
```

`WebViewInterop` owns the high-level protocol model and delegates physical traffic to `WebViewMessageBus`. The bus can still keep lower-level operations needed internally, but those operations are not the API that app code should reach for.

## Wire Naming

Calls and notifications use the same namespace separator:

| Kind | Default wire method |
| --- | --- |
| Call | `namespace/methodName` |
| Notification | `namespace/methodName` |

Examples:

```text
editor/openFile
editor/refresh
editor/ready
editor/snapshot
editor/selectedFileChanged
```

The slash form keeps compatibility with the existing method style such as `acp/ready` and `demo/board/snapshot`, and avoids exposing a second naming convention to protocol authors.

## Typed API IDs

Namespaces should not be passed around as untyped string literals in feature code. A namespace is part of the protocol contract and should be declared once as a typed API id.

An API id is both:

- the wire namespace string;
- a reference to the WebView protocol interface type.

Raw namespace strings remain supported, but only at API id declaration sites:

- Kotlin: `WebViewApiId.of<EditorHostApi>("editor.host")`;
- TypeScript: `apiId<EditorHostApi>()("editor.host")`.

The namespace itself may be a short product name (`editor.host`) or a Java/Kotlin-like FQN (`com.intellij.ui.webview.demo.EditorHostApi`). TypeScript does not have an equivalent runtime package namespace for interfaces, so the protocol namespace must be declared explicitly as a typed token. The wire separator between namespace and method is always `/`.

New app-facing APIs should accept typed API ids, not raw `String` / `string` namespace overloads at each call site.

TypeScript API id shape:

```ts
declare const WEBVIEW_API_ID: unique symbol

export interface WebViewApiId<Api, Namespace extends string = string> {
  readonly namespace: Namespace
  readonly [WEBVIEW_API_ID]: Api
}

export function apiId<Api extends WebViewApi>(): <const Namespace extends string>(
  namespace: Namespace,
) => WebViewApiId<Api, Namespace>
```

TypeScript usage:

```ts
export const editorHostApiId = apiId<EditorHostApi>()("editor.host")

const hostApi = webview.callable(editorHostApiId)
```

The TypeScript object is still just a tiny runtime value containing the wire namespace, but TypeScript preserves both the literal namespace type `"editor.host"` and the API interface type `EditorHostApi`. This lets helper types infer the API from the id and derive method names when needed:

```ts
type ApiOf<Id> = Id extends WebViewApiId<infer Api, string> ? Api : never

type WebViewMethodName<Id extends WebViewApiId<unknown>, Method extends string> =
  `${Id["namespace"]}/${Method}`
```

The marker prevents arbitrary `{ namespace: string }` objects from being accepted. Runtime validation should still check the string format:

- non-empty;
- no leading or trailing `.` or `/`;
- no whitespace;
- contains only protocol-safe characters, for example `[A-Za-z0-9_.-]+`.

Kotlin should keep the id as a companion property of the protocol interface:

```kotlin
interface EditorHostApi : WebViewImplementable {
  companion object {
    val id: WebViewApiId<EditorHostApi> = WebViewApiId.of("editor.host")
  }
}
```

Kotlin API id shape:

```kotlin
class WebViewApiId<T : WebViewApi> private constructor(
  val apiClass: KClass<T>,
  val namespace: String,
) {
  companion object {
    inline fun <reified T : WebViewApi> of(namespace: String): WebViewApiId<T> = of(T::class, namespace)

    fun <T : WebViewApi> of(apiClass: KClass<T>, namespace: String): WebViewApiId<T> {
      require(namespace.isNotBlank())
      require(!namespace.startsWith('.') && !namespace.endsWith('.'))
      require(!namespace.startsWith('/') && !namespace.endsWith('/'))
      require(namespace.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '_' || it == '-' || it == '.' })
      return WebViewApiId(apiClass, namespace)
    }
  }
}
```

Kotlin stores `KClass<T>` in the id because runtime reflection cannot recover a phantom generic type from a value class. Kotlin multiple upper bounds are intersection bounds. `where T : WebViewCallable, T : WebViewImplementable` would require the same protocol interface to have both roles, so the single `of` factory is bounded by the common `WebViewApi` marker instead. The role-specific constraints stay on `WebViewInterop.callable(...)` and `WebViewInterop.implement(...)`.

String-taking overloads may stay temporarily for migration, but examples and new code should use typed API ids.

## Kotlin API Shape

Kotlin marker interfaces:

```kotlin
interface WebViewApi
interface WebViewImplementable : WebViewApi
interface WebViewCallable : WebViewApi
```

Kotlin implements host calls from the page:

```kotlin
@Serializable
data class OpenFileRequest(val path: String, val line: Int? = null)

@Serializable
data class OpenFileResult(val opened: Boolean)

@Serializable
data class EditorSnapshot(val files: List<EditorFileDto>)

interface EditorHostApi : WebViewImplementable {
  companion object {
    val id: WebViewApiId<EditorHostApi> = WebViewApiId.of("editor.host")
  }

  suspend fun openFile(params: OpenFileRequest): OpenFileResult
  suspend fun refresh(): EditorSnapshot
}

interop.implement(
  id = EditorHostApi.id,
  implementation = editorHostApi,
)
```

Kotlin implements notifications sent by the page:

```kotlin
@Serializable
data class SelectedFileDto(val fileId: String)

interface EditorPageEvents : WebViewImplementable {
  companion object {
    val id: WebViewApiId<EditorPageEvents> = WebViewApiId.of("editor.page")
  }

  fun ready()
  fun selectedFileChanged(params: SelectedFileDto)
}

interop.implement(
  id = EditorPageEvents.id,
  implementation = object : EditorPageEvents {
    override fun ready() {
      startStreaming()
    }

    override fun selectedFileChanged(params: SelectedFileDto) {
      selectFile(params.fileId)
    }
  },
)
```

Kotlin sends notifications to the page through a callable proxy:

```kotlin
@Serializable
data class ThemeDto(val themeId: String)

interface EditorHostEvents : WebViewCallable {
  companion object {
    val id: WebViewApiId<EditorHostEvents> = WebViewApiId.of("editor.host.events")
  }

  fun snapshot(params: EditorSnapshot)
  fun themeChanged(params: ThemeDto)
}

val hostEvents = interop.callable(EditorHostEvents.id)

hostEvents.snapshot(snapshot)
hostEvents.themeChanged(theme)
```

For the current runtime, Kotlin `WebViewCallable` should allow only notification methods (`fun ...: Unit`). Kotlin -> TypeScript request/response calls remain out of the public API until explicitly approved.

### Kotlin Reflection

`WebViewInterop` should minimize manual binding, but protocol validation must happen eagerly when an interface is bound:

1. `implement(id, implementation)` and `callable(id)` receive a `WebViewApiId<T>` that carries both the namespace and the protocol interface type.
2. At binding time, the runtime reflects public protocol methods declared directly in the API interface and builds a method table keyed by `methodName`.
3. Method names must be unique in the bound interface. Overloads are rejected because they would map to the same `namespace/methodName` wire key.
4. The runtime validates the method contract:
   - `WebViewImplementable` call methods must be `suspend`, have zero or one serializable params object, and return `Unit` or a serializable result.
   - `WebViewImplementable` notification methods must be non-suspend and return `Unit`.
5. The runtime resolves serializers and caches the reflected method binding in a dispatch table.
6. Incoming calls and notifications resolve `methodName` against the cached table. Methods inherited from parent interfaces and methods only present on the implementation object are ignored.

`callable(id)` infers `T` from `WebViewApiId<T>` and returns a dynamic proxy backed by `WebViewMessageBus`. Call and notification methods both use slash-form method names. They differ only by frame shape: calls have a JSON-RPC `id`, notifications do not.

## TypeScript API Shape

TypeScript uses marker interfaces for compile-time role separation:

```ts
declare const WEBVIEW_API: unique symbol
declare const WEBVIEW_CALLABLE: unique symbol
declare const WEBVIEW_IMPLEMENTABLE: unique symbol

export interface WebViewApi {
  readonly [WEBVIEW_API]: true
}

export interface WebViewCallable extends WebViewApi {
  readonly [WEBVIEW_CALLABLE]: true
}

export interface WebViewImplementable extends WebViewApi {
  readonly [WEBVIEW_IMPLEMENTABLE]: true
}
```

`declare const` and `unique symbol` create type-only marker keys. They do not emit JavaScript. They make WebView protocol interfaces nominal enough that a callable contract cannot be accidentally used as an implementable contract.

The TypeScript runtime API should infer the protocol interface type from `WebViewApiId<Api>`:

```ts
interface WebViewBridge {
  callable<Api extends WebViewCallable>(
    id: WebViewApiId<Api>,
    options?: WebViewRuntimeOptions,
  ): Callable<Api>

  implement<Api extends WebViewImplementable>(
    id: WebViewApiId<Api>,
    implementation: WebViewImplementation<Api>,
    options?: WebViewRuntimeOptions,
  ): Disposable
}
```

This mirrors the Kotlin split: `apiId<Api extends WebViewApi>()` accepts only protocol interfaces, while `webview.callable(...)` and `webview.implement(...)` enforce the concrete role. Do not model this as `Api extends WebViewCallable & WebViewImplementable`; that is also an intersection and would require one TypeScript interface to have both roles.

Feature contracts:

```ts
import { apiId, type WebViewCallable, type WebViewImplementable, webview } from "@jetbrains/intellij-webview"

type OpenFileRequest = { path: string; line?: number }
type OpenFileResult = { opened: boolean }
type SelectedFileDto = { fileId: string }
type ThemeDto = { themeId: string }
type EditorSnapshot = { files: Array<{ id: string; path: string }> }

interface EditorHostApi extends WebViewCallable {
  openFile(params: OpenFileRequest): Promise<OpenFileResult>
  refresh(): Promise<EditorSnapshot>
}

interface EditorPageEvents extends WebViewCallable {
  ready(): void
  selectedFileChanged(params: SelectedFileDto): void
}

interface EditorHostEvents extends WebViewImplementable {
  snapshot(params: EditorSnapshot): void
  themeChanged(params: ThemeDto): void
}

const editorHostApiId = apiId<EditorHostApi>()("editor.host")
const editorPageEventsId = apiId<EditorPageEvents>()("editor.page")
const editorHostEventsId = apiId<EditorHostEvents>()("editor.host.events")
```

Calling host calls and sending page notifications:

```ts
const hostApi = webview.callable(editorHostApiId)
const pageEvents = webview.callable(editorPageEventsId)

const result = await hostApi.openFile({ path: "/tmp/a.txt", line: 12 })
const snapshot = await hostApi.refresh()

pageEvents.ready()
pageEvents.selectedFileChanged({ fileId: "main.kt" })
```

Implementing host notifications in the page:

```ts
webview.implement(editorHostEventsId, {
  snapshot(snapshot) {
    store.applySnapshot(snapshot)
  },

  themeChanged(theme) {
    applyTheme(theme.themeId)
  },
})
```

`webview.callable(id)` returns a `Proxy`. Property access builds a function. Calling that function sends either:

- a JSON-RPC request when the TypeScript contract method returns `Promise`;
- a JSON-RPC notification when the TypeScript contract method returns `void`.

The runtime cannot inspect TypeScript interfaces at runtime. Compile-time validation happens at the `webview.callable(id)` and `webview.implement(id, implementation)` API boundary.

## TypeScript Contract Validation

Any WebView protocol interface member except marker symbols must be a method returning either `void` or `Promise`.

Valid:

```ts
interface GoodApi extends WebViewCallable {
  openFile(params: OpenFileRequest): Promise<OpenFileResult>
  ready(): void
}
```

Invalid:

```ts
interface BadApi extends WebViewCallable {
  state: string
  openFile(params: OpenFileRequest): OpenFileResult
  isReady(): boolean
}
```

The invalid interface may be declared, but it must fail when used with a WebView runtime entrypoint:

```ts
const badApiId = apiId<BadApi>()("editor.bad")
webview.callable(badApiId)
// Type error should name: "state" | "openFile" | "isReady"
```

The public types should implement this through mapped-type checks:

```ts
type AnyWebViewMethod = (...args: any[]) => void | Promise<unknown>

type InvalidWebViewMethodKeys<T> = {
  [K in WebViewMethodKeys<T>]: T[K] extends AnyWebViewMethod ? never : K
}[WebViewMethodKeys<T>]

type EnforceWebViewMethods<T> =
  InvalidWebViewMethodKeys<T> extends never
    ? T
    : { ERROR_invalid_webview_methods: InvalidWebViewMethodKeys<T> }
```

The exact implementation can vary, but error messages should make the invalid method keys visible.

Method names must also be unique in the TypeScript contract used at a binding point. TypeScript object keys are unique, but interface overload declarations can still create multiple call signatures for one method name. WebView protocol interfaces must not use that syntax:

```ts
interface BadOverloadedApi extends WebViewCallable {
  openFile(params: OpenFileRequest): Promise<OpenFileResult>
  openFile(path: string): Promise<OpenFileResult>
}

const badOverloadedApiId = apiId<BadOverloadedApi>()("editor.badOverloaded")
webview.callable(badOverloadedApiId)
// Type error should name: "openFile"
```

The binding type check should reject overloaded function members by reducing every method property to exactly one call signature. If that cannot be proven for a member, the API should fail at `webview.callable(id)` or `webview.implement(id, implementation)`, not later in application code.

## Manual Authoring Guide

No code generation is assumed in this iteration. Protocol files are maintained manually by agents or developers, and the rule is that every cross-boundary contract has a matching pair of interfaces: one side implements it, the other side calls it.

Direction mapping:

| Kotlin side | TypeScript side | Meaning |
| --- | --- | --- |
| `WebViewImplementable` | `WebViewCallable` | TypeScript calls methods implemented by Kotlin. |
| `WebViewCallable` | `WebViewImplementable` | Kotlin sends notifications to methods implemented by TypeScript. |

For the current runtime, the second row is notification-only. Public Kotlin -> TypeScript request/response calls are not part of the API.

Authoring steps:

1. Choose one namespace string for one protocol interface.
2. Put `val id: WebViewApiId<Api>` in the Kotlin interface companion object.
3. Put `const editorApiId = apiId<Api>()("...")` in the TypeScript protocol module.
4. Use the same namespace string in both ids.
5. Define the Kotlin interface with either `WebViewImplementable` or `WebViewCallable`.
6. Define the matching TypeScript interface with the opposite role.
7. Keep method names identical on both sides. The wire method name is always `namespace/methodName`.
8. Do not overload methods. A method name may appear only once in one protocol interface.
9. Keep DTO JSON shape identical: property names, casing, optionality, nullability, array shapes, enum/string values, and ID types must match.
10. Expose only the protocol module to feature code. Components, stores, and projections do not call `window.__WVI__` or raw bridge APIs.

Call method shape:

| Kotlin implementable method | TypeScript callable method | Wire method |
| --- | --- | --- |
| `suspend fun openFile(params: OpenFileRequest): OpenFileResult` | `openFile(params: OpenFileRequest): Promise<OpenFileResult>` | `editor.host/openFile` |
| `suspend fun refresh(): EditorSnapshot` | `refresh(): Promise<EditorSnapshot>` | `editor.host/refresh` |
| `suspend fun save(params: SaveRequest): Unit` | `save(params: SaveRequest): Promise<void>` | `editor.host/save` |

Notification method shape:

| Kotlin notification method | TypeScript notification method | Wire method |
| --- | --- | --- |
| `fun ready(): Unit` | `ready(): void` | `editor.page/ready` |
| `fun selectedFileChanged(params: SelectedFileDto): Unit` | `selectedFileChanged(params: SelectedFileDto): void` | `editor.page/selectedFileChanged` |
| `fun snapshot(params: EditorSnapshot): Unit` | `snapshot(params: EditorSnapshot): void` | `editor.host.events/snapshot` |

The side that calls a request/response method must see a `Promise`. A TypeScript WebView protocol method returning a plain result is invalid even if the underlying bridge can technically deliver a response.

Manual matching example:

```kotlin
interface EditorHostApi : WebViewImplementable {
  companion object {
    val id: WebViewApiId<EditorHostApi> = WebViewApiId.of("editor.host")
  }

  suspend fun openFile(params: OpenFileRequest): OpenFileResult
  suspend fun refresh(): EditorSnapshot
}
```

```ts
export interface EditorHostApi extends WebViewCallable {
  openFile(params: OpenFileRequest): Promise<OpenFileResult>
  refresh(): Promise<EditorSnapshot>
}

export const editorHostApiId = apiId<EditorHostApi>()("editor.host")
```

```kotlin
interface EditorPageEvents : WebViewImplementable {
  companion object {
    val id: WebViewApiId<EditorPageEvents> = WebViewApiId.of("editor.page")
  }

  fun ready(): Unit
  fun selectedFileChanged(params: SelectedFileDto): Unit
}
```

```ts
export interface EditorPageEvents extends WebViewCallable {
  ready(): void
  selectedFileChanged(params: SelectedFileDto): void
}

export const editorPageEventsId = apiId<EditorPageEvents>()("editor.page")
```

```kotlin
interface EditorHostEvents : WebViewCallable {
  companion object {
    val id: WebViewApiId<EditorHostEvents> = WebViewApiId.of("editor.host.events")
  }

  fun snapshot(params: EditorSnapshot): Unit
}
```

```ts
export interface EditorHostEvents extends WebViewImplementable {
  snapshot(params: EditorSnapshot): void
}

export const editorHostEventsId = apiId<EditorHostEvents>()("editor.host.events")
```

Manual review checklist:

- Every Kotlin `WebViewApiId.of<Api>("x")` has exactly one matching TypeScript `apiId<Api>()("x")`.
- Every protocol interface has opposite roles on the two sides: implementable vs callable.
- Every method name matches exactly and produces slash-form wire name `namespace/methodName`.
- No protocol interface has overloaded methods or duplicate wire method names.
- Every call method is `suspend` on Kotlin and returns `Promise` on TypeScript.
- Every notification method returns `Unit` on Kotlin and `void` on TypeScript.
- DTO fields match by JSON shape, not by local model names.
- Raw namespace strings appear only in `WebViewApiId.of(...)` or `apiId(...)`.
- Feature code imports the protocol module, not raw bridge globals or raw method strings.

## Protocol Module Pattern

Each feature should keep protocol contracts in a small module. The module is not meant to invent a separate "runtime object". Its job is narrower:

- keep DTOs, WebView protocol interfaces, and namespace declarations together;
- centralize the mapping from interface to namespace;
- keep `webview.callable(...)` and `webview.implement(...)` out of UI components;
- give tests and scenario harnesses one place to inject `options.bridge`.

Prefer flat named exports. A grouped object can be added only when it makes a specific caller simpler.

```ts
// webViewProtocol.ts
export interface EditorHostApi extends WebViewCallable {
  openFile(params: OpenFileRequest): Promise<OpenFileResult>
}

export interface EditorPageEvents extends WebViewCallable {
  ready(): void
}

export interface EditorHostEvents extends WebViewImplementable {
  snapshot(params: EditorSnapshot): void
}

export const editorHostApiId = apiId<EditorHostApi>()("editor.host")
export const editorPageEventsId = apiId<EditorPageEvents>()("editor.page")
export const editorHostEventsId = apiId<EditorHostEvents>()("editor.host.events")

export function editorHostApi(options?: WebViewRuntimeOptions): Callable<EditorHostApi> {
  return webview.callable(editorHostApiId, options)
}

export function editorPageEvents(options?: WebViewRuntimeOptions): Callable<EditorPageEvents> {
  return webview.callable(editorPageEventsId, options)
}

export function implementEditorHostEvents(
  implementation: WebViewImplementation<EditorHostEvents>,
  options?: WebViewRuntimeOptions,
): Disposable {
  return webview.implement(editorHostEventsId, implementation, options)
}
```

Feature code uses the protocol module:

```ts
const hostApi = editorHostApi()
const pageEvents = editorPageEvents()

implementEditorHostEvents({
  snapshot(snapshot) {
    store.dispatch({ type: "snapshot", snapshot })
  },
})

await hostApi.openFile({ path: "/tmp/a.txt" })
pageEvents.ready()
```

For browser tests and future scenario harnesses, `options.bridge` can inject a mock `WebViewBridge` with the same public low-level shape as production `window.__WVI__`.

## Boundary Rules

- Protocol interfaces describe bridge DTOs and commands, not UI view models.
- DTOs crossing the boundary must be serializable values: primitives, arrays, maps, IDs, and nested DTOs.
- Frontend stores own browser-side state; pure projection functions derive UI-ready view models.
- RPC calls must not be hidden inside projection functions or view-model getters.
- Raw method strings are generated from namespace and interface method names by default. Explicit wire-name overrides can be added later only for compatibility cases.
- Namespace strings should be wrapped in `WebViewApiId.of(...)` or `apiId(...)` once per protocol module and reused from there.
- Kotlin application code should use `WebViewInterop`, not `WebViewMessageBus`, for typed protocol contracts.
- TypeScript application code should not use `window.__WVI__`, raw `hostApi(namespace)`, or raw method strings directly.
