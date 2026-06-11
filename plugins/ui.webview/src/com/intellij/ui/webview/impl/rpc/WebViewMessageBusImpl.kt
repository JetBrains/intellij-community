// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl.rpc

import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.webview.api.WebViewMessageBus
import com.intellij.ui.webview.api.WebViewMessageContext
import com.intellij.ui.webview.api.WebViewMessageRegistration
import com.intellij.ui.webview.api.WebViewNotification
import com.intellij.ui.webview.api.WebViewNotificationHandler
import com.intellij.ui.webview.api.WebViewRpcException
import com.intellij.ui.webview.api.WebViewInterop
import com.intellij.ui.webview.impl.WebViewEngineBridge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap

/**
 * Asynchronous JSON-RPC runtime between a host backend and one WebView page.
 *
 * Native callbacks must call [transferFromJs]. That method only enqueues a raw frame and returns; parsing,
 * dispatch, handler execution, responses, and JS transfers are performed by coroutine workers owned by this bus.
 */
@ApiStatus.Internal
internal class WebViewMessageBusImpl internal constructor(
  private val scope: CoroutineScope,
  private val engine: WebViewEngineBridge,
  private val json: Json = DEFAULT_JSON,
) : WebViewMessageBus {
  val interop: WebViewInterop = WebViewMessageBusInterop(this)

  private data class RegisteredCallHandler<Params : Any, Result : Any>(
    val paramsSerializer: KSerializer<Params>,
    val resultSerializer: KSerializer<Result>,
    val handler: suspend (Params, WebViewMessageContext) -> Result,
  )

  private data class RegisteredNotificationHandler<Params : Any>(
    val serializer: KSerializer<Params>,
    val handler: WebViewNotificationHandler<Params>,
  )

  private data class OutgoingFrame(
    val method: String,
    val rawJson: String,
  )

  private sealed interface IncomingFrame {
    data class Call(val id: JsonElement, val method: String, val params: JsonElement?) : IncomingFrame
    data class Notification(val method: String, val params: JsonElement?) : IncomingFrame
    data class Response(val id: JsonElement, val result: JsonElement?, val error: JsonElement?) : IncomingFrame
    data class Invalid(val id: JsonElement?, val message: String) : IncomingFrame
  }

  private val callHandlers = ConcurrentHashMap<String, RegisteredCallHandler<*, *>>()
  private val notificationHandlers = ConcurrentHashMap<String, RegisteredNotificationHandler<*>>()
  private val apiMethods = ConcurrentHashMap<String, WebViewApiMethodRegistration>()
  private val pendingIncomingCalls = ConcurrentHashMap<String, Job>()
  private val remotelyCancelledIncomingCalls = ConcurrentHashMap.newKeySet<String>()
  private val handlerJobs = ConcurrentHashMap.newKeySet<Job>()
  private val loggedOutgoingMethods = ConcurrentHashMap.newKeySet<String>()
  private val incomingFrames = Channel<String>(MAX_QUEUED_FRAMES)
  private val outgoingFrames = Channel<OutgoingFrame>(MAX_QUEUED_FRAMES)
  @Volatile private var closed = false

  private val incomingJob = scope.launch(CoroutineName("WebView RPC incoming")) {
    for (raw in incomingFrames) {
      processIncoming(raw)
    }
  }

  private val outgoingJob = scope.launch(CoroutineName("WebView RPC outgoing")) {
    for (frame in outgoingFrames) {
      transferToJs(frame)
    }
  }

  internal fun <Params : Any, Result : Any> registerApiCallHandler(
    method: String,
    paramsSerializer: KSerializer<Params>,
    resultSerializer: KSerializer<Result>,
    handler: suspend (Params, WebViewMessageContext) -> Result,
  ): WebViewMessageRegistration {
    checkOpen()
    val registration = RegisteredCallHandler(paramsSerializer, resultSerializer, handler)
    check(callHandlers.putIfAbsent(method, registration) == null) {
      "WebView API call handler is already registered: $method"
    }
    return registration(method, callHandlers, registration)
  }

  override suspend fun <Params : Any> notify(notification: WebViewNotification<Params>, params: Params) {
    checkOpen()
    enqueueOutgoing(
      method = notification.method,
      rawJson = encodeNotification(notification.method, json.encodeToJsonElement(notification.paramsSerializer, params)),
    )
  }

  internal fun <Params : Any> notifyNow(method: String, paramsSerializer: KSerializer<Params>, params: Params) {
    checkOpen()
    val rawJson = encodeNotification(method, json.encodeToJsonElement(paramsSerializer, params))
    logOutgoing(method, rawJson)
    val result = outgoingFrames.trySend(OutgoingFrame(method, rawJson))
    check(result.isSuccess) { "WebViewMessageBus outgoing queue is closed or full: $method" }
  }

  internal fun reserveApiMethods(methods: List<WebViewApiMethodRegistration>): WebViewMessageRegistration {
    checkOpen()
    val reservedMethods = ArrayList<WebViewApiMethodRegistration>(methods.size)
    try {
      for (method in methods) {
        val previous = apiMethods.putIfAbsent(method.method, method)
        check(previous == null) {
          "WebView typed API method is already registered: ${method.method}. Existing: ${previous!!.source}. New: ${method.source}"
        }
        reservedMethods += method
      }
    }
    catch (t: Throwable) {
      reservedMethods.forEach { method ->
        apiMethods.remove(method.method, method)
      }
      throw t
    }
    return registration(methods, apiMethods)
  }

  override fun <Params : Any> registerNotificationHandler(
    notification: WebViewNotification<Params>,
    handler: WebViewNotificationHandler<Params>,
  ): WebViewMessageRegistration {
    checkOpen()
    val registration = RegisteredNotificationHandler(notification.paramsSerializer, handler)
    check(notificationHandlers.putIfAbsent(notification.method, registration) == null) {
      "WebView notification handler is already registered: ${notification.method}"
    }
    return registration(notification.method, notificationHandlers, registration)
  }

  /**
   * Entrypoint for raw JSON-RPC frames arriving from JS (`postMessage` body).
   */
  internal fun transferFromJs(rawJson: String) {
    if (closed) return
    val result = incomingFrames.trySend(rawJson)
    if (result.isFailure) {
      LOG.warn("Dropping WebView message from JS: incoming queue is closed or full, chars=${rawJson.length}")
    }
  }

  private suspend fun processIncoming(raw: String) {
    when (val frame = decodeIncoming(raw)) {
      is IncomingFrame.Call -> handleIncomingCall(frame)
      is IncomingFrame.Notification -> handleIncomingNotification(frame)
      is IncomingFrame.Response -> handleIncomingResponse(frame)
      is IncomingFrame.Invalid -> handleInvalidIncomingFrame(frame)
    }
  }

  private suspend fun handleIncomingCall(frame: IncomingFrame.Call) {
    val registration = callHandlers[frame.method]
    if (registration == null) {
      enqueueErrorResponse(frame.id, frame.method, WebViewRpcErrorCodes.METHOD_NOT_FOUND, "Method not found: ${frame.method}")
      return
    }

    val idKey = callIdKey(frame.id)
    val job = scope.launch(CoroutineName("WebView RPC call ${frame.method}"), start = CoroutineStart.LAZY) {
      runIncomingCall(frame, registration)
    }
    if (pendingIncomingCalls.putIfAbsent(idKey, job) != null) {
      job.cancel(CancellationException("Duplicate WebView RPC call id"))
      enqueueErrorResponse(frame.id, frame.method, WebViewRpcErrorCodes.INVALID_FRAME, "Duplicate call id: $idKey")
      return
    }
    handlerJobs += job
    job.invokeOnCompletion {
      pendingIncomingCalls.remove(idKey, job)
      remotelyCancelledIncomingCalls.remove(idKey)
      handlerJobs.remove(job)
    }
    job.start()
  }

  @Suppress("UNCHECKED_CAST")
  private suspend fun runIncomingCall(frame: IncomingFrame.Call, registration: RegisteredCallHandler<*, *>) {
    val typedRegistration = registration as RegisteredCallHandler<Any, Any>
    val idKey = callIdKey(frame.id)
    try {
      val params = decodeParams(frame.method, frame.params, typedRegistration.paramsSerializer)
      val context = WebViewMessageContext(frame.method)
      val result = typedRegistration.handler(params, context)
      enqueueOutgoing(
        method = frame.method,
        rawJson = encodeSuccessResponse(frame.id, json.encodeToJsonElement(typedRegistration.resultSerializer, result)),
      )
    }
    catch (e: CancellationException) {
      if (!remotelyCancelledIncomingCalls.contains(idKey) && !closed) {
        enqueueErrorResponse(frame.id, frame.method, WebViewRpcErrorCodes.CANCELLED, e.message ?: "Call cancelled")
      }
      throw e
    }
    catch (e: WebViewRpcException) {
      enqueueErrorResponse(frame.id, frame.method, e.code, e.message, e.data)
    }
    catch (t: Throwable) {
      LOG.warn("WebView RPC call handler failed: method=${frame.method}", t)
      enqueueErrorResponse(frame.id, frame.method, WebViewRpcErrorCodes.INTERNAL_ERROR, t.message ?: t.javaClass.name)
    }
  }

  private fun handleIncomingNotification(frame: IncomingFrame.Notification) {
    if (frame.method == CANCEL_CALL_METHOD) {
      handleCancelCall(frame.params)
      return
    }

    val registration = notificationHandlers[frame.method]
    if (registration == null) {
      LOG.info("Dropping WebView notification from JS: method=${frame.method}, handlers=0")
      return
    }
    dispatchNotification(frame, registration)
  }

  @Suppress("UNCHECKED_CAST")
  private fun dispatchNotification(frame: IncomingFrame.Notification, registration: RegisteredNotificationHandler<*>) {
    val typedRegistration = registration as RegisteredNotificationHandler<Any>
    val params = try {
      decodeParams(frame.method, frame.params, typedRegistration.serializer)
    }
    catch (e: WebViewRpcException) {
      LOG.warn("Dropping malformed WebView notification params: method=${frame.method}, code=${e.code}, message=${e.message}")
      return
    }
    val context = WebViewMessageContext(frame.method)
    launchHandler(frame.method) {
      typedRegistration.handler.handle(params, context)
    }
  }

  private fun handleIncomingResponse(frame: IncomingFrame.Response) {
    LOG.info("Dropping WebView RPC response from JS: host-to-JS calls are not supported, id=${callIdKey(frame.id)}")
  }

  private suspend fun handleInvalidIncomingFrame(frame: IncomingFrame.Invalid) {
    LOG.warn("Dropping invalid WebView RPC frame from JS: ${frame.message}")
    val id = frame.id ?: return
    enqueueErrorResponse(id, "<invalid>", WebViewRpcErrorCodes.INVALID_FRAME, frame.message)
  }

  private fun handleCancelCall(params: JsonElement?) {
    val cancelParams = try {
      json.decodeFromJsonElement(WebViewCancelCallParams.serializer(), params ?: JsonObject(emptyMap()))
    }
    catch (t: Throwable) {
      LOG.warn("Dropping malformed WebView RPC cancellation", t)
      return
    }
    val idKey = callIdKey(cancelParams.id)
    val job = pendingIncomingCalls.remove(idKey)
    if (job == null) {
      LOG.debug("Dropping WebView RPC cancellation for unknown call id=$idKey")
      return
    }
    remotelyCancelledIncomingCalls += idKey
    job.cancel(CancellationException(cancelParams.message ?: "Remote WebView RPC cancellation"))
  }

  private fun <T : Any> registration(
    method: String,
    handlers: ConcurrentHashMap<String, T>,
    registration: T,
  ): WebViewMessageRegistration {
    return object : WebViewMessageRegistration {
      @Volatile private var registrationClosed = false

      override fun close() {
        if (registrationClosed) return
        registrationClosed = true
        handlers.remove(method, registration)
      }
    }
  }

  private fun registration(
    methods: List<WebViewApiMethodRegistration>,
    handlers: ConcurrentHashMap<String, WebViewApiMethodRegistration>,
  ): WebViewMessageRegistration {
    return object : WebViewMessageRegistration {
      @Volatile private var registrationClosed = false

      override fun close() {
        if (registrationClosed) return
        registrationClosed = true
        methods.forEach { method ->
          handlers.remove(method.method, method)
        }
      }
    }
  }

  private fun launchHandler(method: String, action: suspend () -> Unit) {
    if (closed) return
    val job = scope.launch(CoroutineName("WebView notification $method")) {
      try {
        action()
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (t: Throwable) {
        LOG.warn("WebView notification handler failed: method=$method", t)
      }
    }
    handlerJobs += job
    job.invokeOnCompletion { handlerJobs.remove(job) }
  }

  private fun <Params : Any> decodeParams(method: String, paramsElement: JsonElement?, serializer: KSerializer<Params>): Params {
    return try {
      json.decodeFromJsonElement(serializer, paramsElement ?: JsonObject(emptyMap()))
    }
    catch (_: SerializationException) {
      throw WebViewRpcException(WebViewRpcErrorCodes.INVALID_PARAMS, "Invalid params for $method", data = paramsElement)
    }
    catch (_: IllegalArgumentException) {
      throw WebViewRpcException(WebViewRpcErrorCodes.INVALID_PARAMS, "Invalid params for $method", data = paramsElement)
    }
  }

  private suspend fun enqueueErrorResponse(
    id: JsonElement,
    method: String,
    code: Int,
    message: String,
    data: JsonElement? = null,
  ) {
    if (closed) return
    enqueueOutgoing(method, encodeErrorResponse(id, WebViewRpcError(code, message, data)))
  }

  private suspend fun enqueueOutgoing(method: String, rawJson: String) {
    checkOpen()
    logOutgoing(method, rawJson)
    outgoingFrames.send(OutgoingFrame(method, rawJson))
  }

  private suspend fun transferToJs(frame: OutgoingFrame) {
    try {
      engine.transferToJs(frame.rawJson)
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (t: Throwable) {
      LOG.warn("WebView transfer to JS failed: method=${frame.method}", t)
    }
  }

  private fun decodeIncoming(raw: String): IncomingFrame {
    val jsonObject = try {
      json.parseToJsonElement(raw).jsonObject
    }
    catch (_: Throwable) {
      return IncomingFrame.Invalid(null, "Malformed JSON (${raw.length} chars)")
    }

    val id = jsonObject[ID]
    val hasId = jsonObject.containsKey(ID)
    if ((jsonObject[JSON_RPC] as? JsonPrimitive)?.contentOrNull != JSON_RPC_VERSION) {
      return IncomingFrame.Invalid(id.takeIf { hasId }, "Missing or invalid JSON-RPC version")
    }

    val method = (jsonObject[METHOD] as? JsonPrimitive)?.contentOrNull
    val hasResult = jsonObject.containsKey(RESULT)
    val hasError = jsonObject.containsKey(ERROR)

    return when {
      hasId && method != null && !hasResult && !hasError -> IncomingFrame.Call(id!!, method, jsonObject[PARAMS])
      hasId && method == null && (hasResult xor hasError) -> IncomingFrame.Response(id!!, jsonObject[RESULT], jsonObject[ERROR])
      !hasId && method != null && !hasResult && !hasError -> IncomingFrame.Notification(method, jsonObject[PARAMS])
      else -> IncomingFrame.Invalid(id.takeIf { hasId }, "Invalid JSON-RPC frame shape")
    }
  }

  private fun encodeNotification(method: String, params: JsonElement): String {
    return encodeFrame(buildJsonObject {
      put(JSON_RPC, JsonPrimitive(JSON_RPC_VERSION))
      put(METHOD, JsonPrimitive(method))
      put(PARAMS, params)
    })
  }

  private fun encodeSuccessResponse(id: JsonElement, result: JsonElement): String {
    return encodeFrame(buildJsonObject {
      put(JSON_RPC, JsonPrimitive(JSON_RPC_VERSION))
      put(ID, id)
      put(RESULT, result)
    })
  }

  private fun encodeErrorResponse(id: JsonElement, error: WebViewRpcError): String {
    return encodeFrame(buildJsonObject {
      put(JSON_RPC, JsonPrimitive(JSON_RPC_VERSION))
      put(ID, id)
      put(ERROR, json.encodeToJsonElement(WebViewRpcError.serializer(), error))
    })
  }

  private fun encodeFrame(frame: JsonObject): String {
    return json.encodeToString(JsonElement.serializer(), frame)
  }

  private fun logOutgoing(method: String, raw: String) {
    if (loggedOutgoingMethods.add(method)) {
      LOG.info("Sending first WebView RPC frame to JS: method=$method, chars=${raw.length}")
    }
    else {
      LOG.debug("Sending WebView RPC frame to JS: method=$method, chars=${raw.length}")
    }
  }

  private fun checkOpen() {
    check(!closed) { "WebViewMessageBus is closed" }
  }

  fun close() {
    if (closed) return
    closed = true
    incomingFrames.close()
    outgoingFrames.close()
    incomingJob.cancel(CancellationException("WebViewMessageBus is closed"))
    outgoingJob.cancel(CancellationException("WebViewMessageBus is closed"))
    handlerJobs.forEach { it.cancel(CancellationException("WebViewMessageBus is closed")) }
    pendingIncomingCalls.values.forEach { it.cancel(CancellationException("WebViewMessageBus is closed")) }
    handlerJobs.clear()
    pendingIncomingCalls.clear()
    callHandlers.clear()
    notificationHandlers.clear()
    apiMethods.clear()
  }

  private fun callIdKey(id: JsonElement): String = json.encodeToString(JsonElement.serializer(), id)

  companion object {
    private val LOG = Logger.getInstance("#com.intellij.ui.webview")
    private const val JSON_RPC_VERSION = "2.0"
    private const val JSON_RPC = "jsonrpc"
    private const val ID = "id"
    private const val METHOD = "method"
    private const val PARAMS = "params"
    private const val RESULT = "result"
    private const val ERROR = "error"
    private const val CANCEL_CALL_METHOD = "$/cancelRequest"
    private const val MAX_QUEUED_FRAMES = 128

    internal val DEFAULT_JSON: Json = Json {
      encodeDefaults = true
      ignoreUnknownKeys = true
    }
  }
}

internal data class WebViewApiMethodRegistration(
  val method: String,
  val source: WebViewApiMethodSource,
)

internal data class WebViewApiMethodSource(
  val apiName: String,
  val functionName: String,
) {
  override fun toString(): String = "$apiName#$functionName"
}
