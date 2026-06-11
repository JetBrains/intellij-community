// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface WebViewMessageBus {
  /**
   * Sends a JSON-RPC notification to the WebView page.
   *
   * [params] is serialized with [WebViewNotification.paramsSerializer]. Notifications have no response; sending after the
   * WebView message bus is closed fails with [IllegalStateException].
   */
  suspend fun <Params : Any> notify(notification: WebViewNotification<Params>, params: Params)

  /**
   * Registers a handler for JSON-RPC notifications received from the WebView page.
   *
   * Only one handler can be registered for the same [WebViewNotification.method] at a time. Close the returned registration
   * to unregister the handler.
   */
  fun <Params : Any> registerNotificationHandler(
    notification: WebViewNotification<Params>,
    handler: WebViewNotificationHandler<Params>,
  ): WebViewMessageRegistration
}

/**
 * Typed descriptor for one JSON-RPC notification method.
 *
 * [method] is the wire method name. [paramsSerializer] defines the single payload object shape for this notification.
 */
@ApiStatus.Experimental
interface WebViewNotification<Params : Any> {
  val method: String
  val paramsSerializer: KSerializer<Params>
}

/**
 * Handler for notifications received through [WebViewMessageBus].
 */
@ApiStatus.Experimental
fun interface WebViewNotificationHandler<Params : Any> {
  suspend fun handle(params: Params, context: WebViewMessageContext)
}

/**
 * Disposable registration returned by WebView message bus APIs.
 */
@ApiStatus.Experimental
interface WebViewMessageRegistration {
  fun close()
}

/**
 * Error returned by the remote JSON-RPC side for a failed call.
 */
@ApiStatus.Experimental
class WebViewRpcException(
  val code: Int,
  override val message: String,
  val data: JsonElement? = null,
) : RuntimeException(message)

/**
 * Metadata for a message currently being dispatched.
 */
@ApiStatus.Experimental
data class WebViewMessageContext(val method: String)
