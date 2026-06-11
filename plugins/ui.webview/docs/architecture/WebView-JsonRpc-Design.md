# WebView JSON-RPC Design

Status: current implementation/design note for the asynchronous `WebViewMessageBus` JSON-RPC runtime.

Audience: coding agents maintaining the call/response, notification, cancellation, reflected API, and future remote-engine proxy layers for `intellij.platform.ui.webview`.

Purpose: describe the JSON-RPC mechanism implemented by `WebViewMessageBus` and the transport boundary that must stay compatible with a future Remote Dev engine proxy.

## 1. Scope

This design covers the WebView RPC runtime. It includes:

- JS -> Kotlin call/response;
- typed call params and result serializers;
- cancellation through `$/cancelRequest`;
- reflected Kotlin interface binding through `bindApi<T>()`;
- JavaScript dynamic proxies for reflected APIs.

The old notification-only `{ "method": ..., "params": ... }` envelope is not part of this API. Frames without `jsonrpc: "2.0"` are invalid protocol.

## 2. ACP Reference

Before implementing this runtime, inspect `/Users/Artem.Bukhonov/work/acp-kotlin-sdk` as the local Kotlin JSON-RPC reference implementation.

Use these files as design input:

- `acp-model/src/commonMain/kotlin/com/agentclientprotocol/rpc/JsonRpc.kt` for JSON-RPC id handling, message decoding by field presence, error codes, and `Json` configuration choices;
- `acp/src/commonMain/kotlin/com/agentclientprotocol/protocol/Protocol.kt` for call/response correlation, pending incoming/outgoing maps, cancellation via `$/cancelRequest`, transport separation, and conversion between JSON-RPC errors and Kotlin exceptions;
- `acp/src/commonMain/kotlin/com/agentclientprotocol/protocol/Protocol.extensions.kt` for call context and typed extension patterns;
- `acp-model/src/commonTest/kotlin/com/agentclientprotocol/rpc/RequestIdTest.kt` and `acp-ktor-test/src/commonTest/kotlin/com/agentclientprotocol/ProtocolTest.kt` for behavior-focused test scenarios.

Do not introduce an `acp-kotlin-sdk` dependency unless that is explicitly approved. Adapt ACP concepts to browser transport constraints.

Do not copy ACP's Kotlin Multiplatform implementation constraints into WebView. The WebView runtime is IntelliJ JVM code, so prefer ordinary JVM primitives:

- `ConcurrentHashMap` for pending incoming calls and handler maps;
- coroutine `Job` for async incoming call state;
- narrow `synchronized` blocks or JVM locks only when a compound update genuinely needs them.

Avoid `atomicfu`, persistent collections, and KMP compatibility work unless the WebView module later becomes multiplatform, which is not part of this plan.

## 3. Protocol

Use JSON-RPC 2.0 as the common wire format.

Supported flows:

- JS -> Kotlin call/response;
- JS -> Kotlin notification;
- Kotlin -> JS notification;
- cancellation through `$/cancelRequest`.

Protocol defaults:

- JSON-RPC version: `2.0`;
- max inbound pending calls: 256;
- outbound queue size: 128;
- cancellation error code: `-32800`.

Call:

```json
{
  "jsonrpc": "2.0",
  "id": 17,
  "method": "host.openFile",
  "params": { "path": "/tmp/a.txt" }
}
```

Successful response:

```json
{
  "jsonrpc": "2.0",
  "id": 17,
  "result": { "opened": true }
}
```

Error response:

```json
{
  "jsonrpc": "2.0",
  "id": 17,
  "error": {
    "code": -32601,
    "message": "Method not found"
  }
}
```

Notification:

```json
{
  "jsonrpc": "2.0",
  "method": "ui.selectionChanged",
  "params": { "selectedIds": ["a", "b"] }
}
```

Message decoding follows the field-presence rule: response has `id` plus `result` or `error`; call has `id` plus `method`; notification has `method` without `id`.

## 4. Runtime State

The JSON-RPC runtime should be scoped to one `WebView` instance. Runtime jobs should be launched in the caller-provided `CoroutineScope` and tracked by the `WebView`; do not create a hidden `SupervisorJob`/child scope as the default lifecycle model.

Internal state:

- `pendingIncomingCalls: ConcurrentHashMap<WebViewIncomingCallId, Job>`;
- call handlers keyed by JSON-RPC method name in a JVM concurrent map and registered only by `bindApi<T>()`;
- notification handlers keyed by JSON-RPC method name in a JVM concurrent map;
- reflected API bindings keyed by namespace and method name in a JVM concurrent map;
- one engine-owned outgoing transport for raw-message delivery;
- close state that rejects new outgoing notifications and registrations after shutdown starts.

ACP-derived runtime rules to preserve:

- keep a separate wrapper type for incoming call ids;
- put incoming call jobs into the pending map as soon as the handler job exists;
- remove incoming call jobs from `invokeOnCompletion`;
- keep call and notification handler maps safe against concurrent registration and delivery;
- keep call context in coroutine context, not in thread-local state.

The implementation uses `WebViewEngine` as the transport boundary:

```kotlin
interface WebViewEngine {
  suspend fun transferToJs(rawJson: String)
}

internal fun WebViewMessageBus.transferFromJs(rawJson: String)
```

Do not add a second `send(rawJson)` abstraction at this boundary. Kotlin -> JS is `transferToJs(rawJson)`, and JS -> Kotlin is `transferFromJs(rawJson)`.

## 5. Kotlin API

Current API:

```kotlin
suspend fun <Params : Any> WebViewMessageBus.notify(
  notification: WebViewNotification<Params>,
  params: Params,
)

fun <Params : Any> WebViewMessageBus.registerNotificationHandler(
  notification: WebViewNotification<Params>,
  handler: WebViewNotificationHandler<Params>,
): WebViewMessageRegistration

fun <T : Any> WebViewMessageBus.bindApi(
  api: KClass<T>,
  implementation: T,
  namespace: String,
): WebViewMessageRegistration

inline fun <reified T : Any> WebViewMessageBus.bindApi(
  implementation: T,
  namespace: String,
): WebViewMessageRegistration = bindApi(T::class, implementation, namespace)
```

Reflected APIs must follow these rules:

- API type must be an interface;
- exposed functions must be `suspend`;
- overloads are not allowed unless a method-name annotation is added later;
- each function should have zero parameters or one serializable params object;
- each function should return `Unit` or one serializable response object;
- method names are derived from interface function names and the binding namespace as `namespace.functionName`;
- serializers must be resolved once when the API is bound.

## 6. Call Flow

Kotlin does not initiate JavaScript API calls. Kotlin -> JS traffic is limited to responses for JS calls and resultless notifications.

Incoming call means JS -> Kotlin call/response.

Flow:

1. Decode incoming JSON into a JSON-RPC call by field presence.
2. Find handler by `method`.
3. If handler is missing, send `METHOD_NOT_FOUND` response.
4. Launch handler in the webview-owned call handler scope.
5. Store the job in `pendingIncomingCalls`, keyed by `WebViewIncomingCallId`.
6. Decode params using the registered params serializer.
7. Run the handler with `WebViewMessageContext` available in coroutine context.
8. Serialize the result and send `JsonRpcResponse(id, result)`.
9. Remove the pending incoming job on completion.

Error mapping:

- invalid params or serializer failures -> `INVALID_PARAMS` or `PARSE_ERROR`;
- explicit protocol errors -> their JSON-RPC code/data;
- missing method -> `METHOD_NOT_FOUND`;
- local cancellation not caused by remote `$/cancelRequest` -> `CANCELLED` response;
- remote `$/cancelRequest` -> cancel the job and do not send an additional success response;
- unexpected exception -> `INTERNAL_ERROR` response and diagnostic logging.

## 7. Cancellation

Use the JSON-RPC notification method `$/cancelRequest`, matching ACP.

Payload:

```kotlin
@Serializable
data class WebViewCancelRequestNotification(
  val id: JsonElement,
  val message: String? = null,
)
```

On JS caller cancellation:

- the browser bridge sends `$/cancelRequest` for an aborted host API call;
- the JavaScript promise rejects with a cancellation error;
- the Kotlin runtime cancels the matching incoming handler job if it is still active.

On receive:

- look up `pendingIncomingCalls[WebViewIncomingCallId(id)]`;
- if found, remove it and cancel its job with a dedicated cancellation exception;
- if not found, log/debug and ignore.

## 8. JS Runtime

The JS side is the common JSON-RPC bridge loaded from `/__webview/wvi-bridge.js`.

State:

- `nextCallId` for JS-created outgoing calls;
- `pendingCalls: Map<CallId, { resolve, reject, abort? }>` for JS -> Kotlin calls;
- notification handlers for Kotlin -> JS notifications, registered through descriptors;
- one engine transport adapter hidden behind the common bridge.

Conceptual shape:

```typescript
interface WebViewBridge {
  hostApi<T extends object>(namespace: string): T
  notification<TParams>(descriptor: WebViewNotificationDescriptor<TParams>): WebViewNotificationBinding<TParams>
  notifications<T extends Record<string, WebViewNotificationDescriptor<any>>>(descriptors: T): WebViewNotificationBindings<T>
  transport(): string
}
```

`hostApi<T>(namespace)` returns a JavaScript `Proxy` where property access builds a function that sends `namespace + "." + propertyName` as a JSON-RPC request. Every proxy function returns a `Promise`, matching Kotlin `suspend` functions exposed through `bindApi<T>`. Notifications are exposed through descriptor-bound `{ send, on }` helpers so application code does not call raw method strings.

## 9. Remote Engine Proxy

Remote Dev support must not change the JS <-> Kotlin JSON-RPC contract. The future proxy lives below `WebViewMessageBus` and implements the async `WebViewEngine` contract on the backend side.

Proxy command set:

- `LoadAsset(root, entry, query)`;
- `LoadHtml(html, baseFile)`;
- `EvaluateJavaScript(script)`;
- `TransferToJs(rawJson)`;
- `Close`.

Each command needs a correlation id so the backend proxy can complete the corresponding suspend call. Inbound JS frames are not command responses; they are async events from the real engine to the backend proxy, which must call `WebViewMessageBus.transferFromJs(rawJson)`.

The real engine side owns the native WebView and Swing/native host component. The backend proxy must not pretend to be a Swing component provider; it is only the async command/event boundary that lets backend models and message handlers run without a local engine.

## 10. Required Tests

Use ACP tests as the behavioral template, adapted to an in-memory WebView transport before engine-specific tests are added.

Required protocol unit tests:

- call ids support numeric and string ids and round-trip through JSON;
- JSON-RPC decoding classifies call, response, and notification by field presence;
- JS -> Kotlin call completes with result;
- missing method returns `METHOD_NOT_FOUND`;
- handler `JsonRpcException` preserves code, message, and data;
- handler `CancellationException` becomes `CANCELLED` unless it came from remote `$/cancelRequest`;
- remote `$/cancelRequest` cancels the matching incoming handler job;
- response for unknown id is logged/debugged and does not fail the runtime;
- outgoing notification creates no pending call state;
- incoming notification invokes `registerNotificationHandler` and never sends a response;
- notification handler failure is logged and does not poison subsequent messages;
- duplicate API binding/notification method registration fails fast;
- closing a `WebView` cancels active incoming calls and detaches transport handlers;
- `bindApi<T>` rejects non-interface APIs, non-suspend functions, unsupported parameter shapes, missing serializers, and overloads;
- `bindApi<T>` maps `suspend fun openFile(params: OpenFileRequest): OpenFileResponse` to JSON-RPC method `host.openFile` when `namespace = "host"`;
- JS `hostApi<WebUiHostApi>("host")` sends `host.openFile` and resolves/rejects promises from JSON-RPC responses.
