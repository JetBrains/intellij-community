// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl.rpc

import com.intellij.ui.webview.api.WebViewAssetPath
import com.intellij.ui.webview.api.WebViewAssetRoot
import com.intellij.ui.webview.api.WebViewNotification
import com.intellij.ui.webview.api.WebViewApiId
import com.intellij.ui.webview.api.WebViewCallable
import com.intellij.ui.webview.api.WebViewImplementable
import com.intellij.ui.webview.impl.WebViewEngineBridge
import com.intellij.ui.webview.impl.WebViewJsMessageReceiver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal class WebViewMessageBusTest {
  @Test
  fun notify_serializesAndTransfersJsonRpcNotification(): Unit = runBusTest {
    val engine = RecordingEngine()
    val bus = createBus(engine)

    bus.notify(TestNotification, TestPayload("from-kotlin"))

    val rawMessage = engine.awaitTransfer()
    val rawJson = WebViewMessageBusImpl.DEFAULT_JSON.parseToJsonElement(rawMessage).jsonObject
    assertEquals("2.0", rawJson["jsonrpc"]!!.jsonPrimitive.content)
    assertFalse("id" in rawJson)
    assertEquals(TestNotification.method, rawJson["method"]!!.jsonPrimitive.content)
    assertEquals("from-kotlin", rawJson["params"]!!.jsonObject["value"]!!.jsonPrimitive.content)
  }

  @Test
  fun incomingApiCall_invokesBoundSuspendInterfaceAndTransfersResponse(): Unit = runBusTest {
    val engine = RecordingEngine()
    val bus = createBus(engine)
    bus.bindApi<TestApi>(
      implementation = object : TestApi {
        override suspend fun echo(params: TestPayload): TestPayload = TestPayload(params.value + ":handled")
        override suspend fun ping(): TestPayload = TestPayload("pong")
      },
      namespace = "host",
    )

    bus.transferFromJs("""{"jsonrpc":"2.0","id":7,"method":"host/echo","params":{"value":"from-js"}}""")

    val response = WebViewMessageBusImpl.DEFAULT_JSON.parseToJsonElement(engine.awaitTransfer()).jsonObject
    assertEquals("7", response["id"]!!.jsonPrimitive.content)
    assertEquals("from-js:handled", response["result"]!!.jsonObject["value"]!!.jsonPrimitive.content)
  }

  @Test
  fun transferFromJsReturnsBeforeApiHandlerCompletes(): Unit = runBusTest {
    val engine = RecordingEngine()
    val bus = createBus(engine)
    val finishHandler = CompletableDeferred<Unit>()
    bus.bindApi<TestApi>(
      implementation = object : TestApi {
        override suspend fun echo(params: TestPayload): TestPayload {
          finishHandler.await()
          return params
        }

        override suspend fun ping(): TestPayload = TestPayload("pong")
      },
      namespace = "host",
    )

    bus.transferFromJs("""{"jsonrpc":"2.0","id":7,"method":"host/echo","params":{"value":"from-js"}}""")

    assertFalse(engine.hasTransferWithinTimeout())
    finishHandler.complete(Unit)
    val response = WebViewMessageBusImpl.DEFAULT_JSON.parseToJsonElement(engine.awaitTransfer()).jsonObject
    assertEquals("from-js", response["result"]!!.jsonObject["value"]!!.jsonPrimitive.content)
  }

  @Test
  fun incomingNotification_invokesRegisteredHandler(): Unit = runBusTest {
    val bus = createBus()
    val received = CompletableDeferred<TestPayload>()
    val registration = bus.registerNotificationHandler(TestNotification) { params, context ->
      assertEquals(TestNotification.method, context.method)
      received.complete(params)
    }

    bus.transferFromJs("""{"jsonrpc":"2.0","method":"${TestNotification.method}","params":{"value":"from-js"}}""")

    assertEquals(TestPayload("from-js"), withTimeout(1.seconds) { received.await() })
    registration.close()
  }

  @Test
  fun unknownCallTransfersMethodNotFoundError(): Unit = runBusTest {
    val engine = RecordingEngine()
    val bus = createBus(engine)

    bus.transferFromJs("""{"jsonrpc":"2.0","id":"missing","method":"test/missing","params":{"value":"ignored"}}""")

    val response = WebViewMessageBusImpl.DEFAULT_JSON.parseToJsonElement(engine.awaitTransfer()).jsonObject
    assertEquals("missing", response["id"]!!.jsonPrimitive.content)
    assertEquals(WebViewRpcErrorCodes.METHOD_NOT_FOUND.toString(), response["error"]!!.jsonObject["code"]!!.jsonPrimitive.content)
  }

  @Test
  fun malformedAndOldNotificationsAreDropped(): Unit = runBusTest {
    val engine = RecordingEngine()
    val bus = createBus(engine)

    bus.transferFromJs("""{"method":"${TestNotification.method}","params":{"value":"old"}}""")
    bus.transferFromJs("not-json")

    assertFalse(engine.hasTransferWithinTimeout())
  }

  @Test
  fun bindApiRegistersSuspendInterfaceCalls(): Unit = runBusTest {
    val engine = RecordingEngine()
    val bus = createBus(engine)
    val registration = bus.bindApi<TestApi>(
      implementation = object : TestApi {
        override suspend fun echo(params: TestPayload): TestPayload = TestPayload(params.value + ":bound")
        override suspend fun ping(): TestPayload = TestPayload("pong")
      },
      namespace = "host",
    )

    bus.transferFromJs("""{"jsonrpc":"2.0","id":1,"method":"host/echo","params":{"value":"from-js"}}""")
    val echoResponse = WebViewMessageBusImpl.DEFAULT_JSON.parseToJsonElement(engine.awaitTransfer()).jsonObject
    assertEquals("from-js:bound", echoResponse["result"]!!.jsonObject["value"]!!.jsonPrimitive.content)

    bus.transferFromJs("""{"jsonrpc":"2.0","id":2,"method":"host/ping"}""")
    val pingResponse = WebViewMessageBusImpl.DEFAULT_JSON.parseToJsonElement(engine.awaitTransfer()).jsonObject
    assertEquals("pong", pingResponse["result"]!!.jsonObject["value"]!!.jsonPrimitive.content)
    registration.close()
  }

  @Test
  fun bindApiRegistersOnlyMethodsDeclaredInInterface(): Unit = runBusTest {
    val engine = RecordingEngine()
    val bus = createBus(engine)
    bus.bindApi<ChildApi>(
      implementation = object : ChildApi {
        override suspend fun declared(params: TestPayload): TestPayload = TestPayload(params.value + ":declared")
        override suspend fun inherited(params: TestPayload): TestPayload = TestPayload(params.value + ":inherited")
      },
      namespace = "child",
    )

    bus.transferFromJs("""{"jsonrpc":"2.0","id":1,"method":"child/declared","params":{"value":"from-js"}}""")
    val declaredResponse = WebViewMessageBusImpl.DEFAULT_JSON.parseToJsonElement(engine.awaitTransfer()).jsonObject
    assertEquals("from-js:declared", declaredResponse["result"]!!.jsonObject["value"]!!.jsonPrimitive.content)

    bus.transferFromJs("""{"jsonrpc":"2.0","id":2,"method":"child/inherited","params":{"value":"from-js"}}""")
    val inheritedResponse = WebViewMessageBusImpl.DEFAULT_JSON.parseToJsonElement(engine.awaitTransfer()).jsonObject
    assertEquals(WebViewRpcErrorCodes.METHOD_NOT_FOUND.toString(), inheritedResponse["error"]!!.jsonObject["code"]!!.jsonPrimitive.content)
  }

  @Test
  fun interopImplementRegistersCallsAndNotificationsWithSlashNames(): Unit = runBusTest {
    val engine = RecordingEngine()
    val bus = createBus(engine)
    val readyPayload = CompletableDeferred<TestPayload>()
    bus.interop.implement(
      id = TypedHostApi.id,
      implementation = object : TypedHostApi {
        override suspend fun echo(params: TestPayload): TestPayload = TestPayload(params.value + ":typed")

        override fun ready(params: TestPayload) {
          readyPayload.complete(params)
        }
      },
    )

    bus.transferFromJs("""{"jsonrpc":"2.0","id":1,"method":"typed.host/echo","params":{"value":"from-js"}}""")
    val echoResponse = WebViewMessageBusImpl.DEFAULT_JSON.parseToJsonElement(engine.awaitTransfer()).jsonObject
    assertEquals("from-js:typed", echoResponse["result"]!!.jsonObject["value"]!!.jsonPrimitive.content)

    bus.transferFromJs("""{"jsonrpc":"2.0","method":"typed.host/ready","params":{"value":"ready"}}""")
    assertEquals(TestPayload("ready"), withTimeout(1.seconds) { readyPayload.await() })
  }

  @Test
  fun interopCallableProxyTransfersNotificationsWithSlashNames(): Unit = runBusTest {
    val engine = RecordingEngine()
    val bus = createBus(engine)
    val events = bus.interop.callable(TypedPageEvents.id)

    events.snapshot(TestPayload("from-kotlin"))

    val snapshot = WebViewMessageBusImpl.DEFAULT_JSON.parseToJsonElement(engine.awaitTransfer()).jsonObject
    assertFalse("id" in snapshot)
    assertEquals("typed.page/snapshot", snapshot["method"]!!.jsonPrimitive.content)
    assertEquals("from-kotlin", snapshot["params"]!!.jsonObject["value"]!!.jsonPrimitive.content)

    events.ready()

    val ready = WebViewMessageBusImpl.DEFAULT_JSON.parseToJsonElement(engine.awaitTransfer()).jsonObject
    assertFalse("id" in ready)
    assertEquals("typed.page/ready", ready["method"]!!.jsonPrimitive.content)
  }

  @Test
  fun bindApiRejectsNonSuspendFunctions(): Unit = runBusTest {
    val bus = createBus()

    assertThrows(IllegalStateException::class.java) {
      bus.bindApi<BadApi>(
        implementation = object : BadApi {
          override fun echo(params: TestPayload): TestPayload = params
        },
        namespace = "bad",
      )
    }
  }

  @Test
  fun apiIdRejectsClassApiTypes() {
    assertThrows(IllegalArgumentException::class.java) {
      WebViewApiId.of(BadClassApi::class, "bad.class")
    }
  }

  @Test
  fun duplicateApiBindingRegistrationFailsUntilClosed(): Unit = runBusTest {
    val bus = createBus()
    val api = object : TestApi {
      override suspend fun echo(params: TestPayload): TestPayload = params
      override suspend fun ping(): TestPayload = TestPayload("pong")
    }
    val registration = bus.bindApi<TestApi>(api, namespace = "host")

    assertThrows(IllegalStateException::class.java) {
      bus.bindApi<TestApi>(api, namespace = "host")
    }

    registration.close()
    bus.bindApi<TestApi>(api, namespace = "host").close()
  }

  @Test
  fun duplicateTypedApiMethodReportsExistingApiSourceUntilClosed(): Unit = runBusTest {
    val bus = createBus()
    val registration = bus.interop.implement(
      id = TypedHostApi.id,
      implementation = object : TypedHostApi {
        override suspend fun echo(params: TestPayload): TestPayload = params

        override fun ready(params: TestPayload) {
        }
      },
    )

    val error = assertThrows(IllegalStateException::class.java) {
      bus.interop.implement(
        id = DuplicateReadyHostApi.id,
        implementation = object : DuplicateReadyHostApi {
          override fun ready(params: TestPayload) {
          }
        },
      )
    }
    val message = error.message.orEmpty()
    assertTrue(message.contains("typed.host/ready"), message)
    assertTrue(message.contains("TypedHostApi#ready"), message)
    assertTrue(message.contains("DuplicateReadyHostApi#ready"), message)

    registration.close()
    bus.interop.implement(
      id = DuplicateReadyHostApi.id,
      implementation = object : DuplicateReadyHostApi {
        override fun ready(params: TestPayload) {
        }
      },
    ).close()
  }

  @Test
  fun duplicateNotificationRegistrationFailsUntilClosed(): Unit = runBusTest {
    val bus = createBus()
    val registration = bus.registerNotificationHandler(TestNotification) { _, _ -> }

    assertThrows(IllegalStateException::class.java) {
      bus.registerNotificationHandler(TestNotification) { _, _ -> }
    }

    registration.close()
    bus.registerNotificationHandler(TestNotification) { _, _ -> }.close()
  }

  @Test
  fun notificationHandlerFailureDoesNotPoisonSubsequentNotifications(): Unit = runBusTest {
    val bus = createBus()
    var shouldFail = true
    val secondDelivery = CompletableDeferred<TestPayload>()
    bus.registerNotificationHandler(TestNotification) { params, _ ->
      if (shouldFail) {
        shouldFail = false
        error("expected test failure")
      }
      secondDelivery.complete(params)
    }

    bus.transferFromJs("""{"jsonrpc":"2.0","method":"${TestNotification.method}","params":{"value":"first"}}""")
    bus.transferFromJs("""{"jsonrpc":"2.0","method":"${TestNotification.method}","params":{"value":"second"}}""")

    assertEquals(TestPayload("second"), withTimeout(1.seconds) { secondDelivery.await() })
  }

  @Test
  fun closeRejectsOutgoingNotifications(): Unit = runBusTest {
    val bus = createBus()

    bus.close()

    val error = runCatching { bus.notify(TestNotification, TestPayload("after-close")) }.exceptionOrNull()
    assertTrue(error is IllegalStateException)
  }

  @Test
  fun closeCancelsActiveNotificationHandlers(): Unit = runBusTest {
    val bus = createBus()
    val started = CompletableDeferred<Unit>()
    val cancelled = CompletableDeferred<Unit>()
    bus.registerNotificationHandler(TestNotification) { _, _ ->
      started.complete(Unit)
      try {
        awaitCancellation()
      }
      finally {
        cancelled.complete(Unit)
      }
    }

    bus.transferFromJs("""{"jsonrpc":"2.0","method":"${TestNotification.method}","params":{"value":"active"}}""")
    withTimeout(1.seconds) { started.await() }

    bus.close()

    withTimeout(1.seconds) { cancelled.await() }
  }

  @Test
  fun remoteCancelRequestCancelsActiveApiCallWithoutResponse(): Unit = runBusTest {
    val engine = RecordingEngine()
    val bus = createBus(engine)
    val started = CompletableDeferred<Unit>()
    val cancelled = CompletableDeferred<Unit>()
    bus.bindApi<BlockingApi>(
      implementation = object : BlockingApi {
        override suspend fun block(params: TestPayload): TestPayload {
          started.complete(Unit)
          try {
            awaitCancellation()
          }
          finally {
            cancelled.complete(Unit)
          }
        }
      },
      namespace = "host",
    )

    bus.transferFromJs("""{"jsonrpc":"2.0","id":7,"method":"host/block","params":{"value":"from-js"}}""")
    withTimeout(1.seconds) { started.await() }

    bus.transferFromJs("""{"jsonrpc":"2.0","method":"$/cancelRequest","params":{"id":7,"message":"stop"}}""")

    withTimeout(1.seconds) { cancelled.await() }
    assertFalse(engine.hasTransferWithinTimeout())
  }

  private fun runBusTest(block: suspend BusTestScope.() -> Unit): Unit = runBlocking {
    val buses = ArrayList<WebViewMessageBusImpl>()
    val testScope = BusTestScope(this) { engine ->
      WebViewMessageBusImpl(this, engine).also { buses += it }
    }
    try {
      testScope.block()
    }
    finally {
      buses.forEach { it.close() }
    }
  }

  private class BusTestScope(
    private val delegate: CoroutineScope,
    private val busFactory: (RecordingEngine) -> WebViewMessageBusImpl,
  ) : CoroutineScope by delegate {
    fun createBus(
      engine: RecordingEngine = RecordingEngine(),
    ): WebViewMessageBusImpl = busFactory(engine)
  }

  private object TestNotification : WebViewNotification<TestPayload> {
    override val method: String = "test/notification"
    override val paramsSerializer = TestPayload.serializer()
  }

  @Serializable
  private data class TestPayload(val value: String)

  private interface TestApi {
    suspend fun echo(params: TestPayload): TestPayload
    suspend fun ping(): TestPayload
  }

  private interface BadApi {
    fun echo(params: TestPayload): TestPayload
  }

  private class BadClassApi : WebViewImplementable

  private interface TypedHostApi : WebViewImplementable {
    suspend fun echo(params: TestPayload): TestPayload

    fun ready(params: TestPayload)

    companion object {
      val id: WebViewApiId<TypedHostApi> = WebViewApiId.of("typed.host")
    }
  }

  private interface TypedPageEvents : WebViewCallable {
    fun snapshot(params: TestPayload)

    fun ready()

    companion object {
      val id: WebViewApiId<TypedPageEvents> = WebViewApiId.of("typed.page")
    }
  }

  private interface DuplicateReadyHostApi : WebViewImplementable {
    fun ready(params: TestPayload)

    companion object {
      val id: WebViewApiId<DuplicateReadyHostApi> = WebViewApiId.of("typed.host")
    }
  }

  private interface BlockingApi {
    suspend fun block(params: TestPayload): TestPayload
  }

  private interface ParentApi {
    suspend fun inherited(params: TestPayload): TestPayload
  }

  private interface ChildApi : ParentApi {
    suspend fun declared(params: TestPayload): TestPayload
  }

  private class RecordingEngine : WebViewEngineBridge {
    override val isHeavyweight: Boolean = false

    val transferredMessages = ArrayList<String>()
    private val transfers = Channel<String>(Channel.UNLIMITED)

    suspend fun awaitTransfer(): String = withTimeout(1.seconds) { transfers.receive() }

    suspend fun hasTransferWithinTimeout(): Boolean = withTimeoutOrNull(200.milliseconds) { transfers.receive() } != null

    override suspend fun loadFile(file: Path) {
    }

    override suspend fun loadAsset(root: WebViewAssetRoot, entry: WebViewAssetPath, query: String?) {
    }

    override suspend fun loadHtml(html: String, baseFile: Path?) {
    }

    override suspend fun evaluateJavaScript(script: String): String? = null

    override suspend fun transferToJs(rawJson: String) {
      transferredMessages += rawJson
      transfers.send(rawJson)
    }

    override fun connectMessageBus(receiver: WebViewJsMessageReceiver) {
    }

    override suspend fun close() {
    }
  }
}
