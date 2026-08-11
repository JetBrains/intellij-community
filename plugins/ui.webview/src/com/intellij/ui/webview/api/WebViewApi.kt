// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.api

import org.jetbrains.annotations.ApiStatus
import kotlin.reflect.KClass

/**
 * Common marker for typed WebView protocol interfaces.
 *
 * A WebView protocol type must be a Kotlin interface and must extend either [WebViewCallable] or [WebViewImplementable].
 * Protocol methods may have no value parameters or one serializable params object. Multiple value parameters are rejected
 * because they would create a positional wire contract. Use a data class when an operation needs several fields:
 *
 * ```kotlin
 * @Serializable
 * data class OpenFileParams(val path: String, val line: Int? = null)
 *
 * suspend fun openFile(params: OpenFileParams): OpenFileResult
 * ```
 */
@ApiStatus.Experimental
interface WebViewApi

/**
 * Marker for a protocol interface implemented by this side of the WebView boundary.
 *
 * Public methods declared directly in the marked interface become incoming JSON-RPC call or notification handlers.
 */
@ApiStatus.Experimental
interface WebViewImplementable : WebViewApi

/**
 * Marker for a protocol interface implemented by the other side of the WebView boundary.
 *
 * The runtime creates a proxy whose method calls send JSON-RPC frames to the WebView bridge.
 */
@ApiStatus.Experimental
interface WebViewCallable : WebViewApi

/**
 * Typed WebView protocol id.
 *
 * [apiClass] must be a Kotlin interface. Runtime binding reflects only public methods declared directly in that interface;
 * implementation-only methods and methods inherited from parent interfaces are not protocol methods.
 *
 * @property apiClass protocol interface used for runtime reflection.
 * @property namespace wire namespace prepended to method names as `namespace/methodName`.
 */
@ApiStatus.Experimental
class WebViewApiId<T : WebViewApi> private constructor(
  val apiClass: KClass<T>,
  val namespace: String,
) {
  companion object {
    /**
     * Creates a typed protocol id for [T] and [namespace].
     *
     * [T] must be an interface extending [WebViewCallable] or [WebViewImplementable].
     */
    inline fun <reified T : WebViewApi> of(namespace: String): WebViewApiId<T> = of(T::class, namespace)

    /**
     * Creates a typed protocol id for [apiClass] and [namespace].
     *
     * [apiClass] must be an interface extending [WebViewCallable] or [WebViewImplementable].
     */
    fun <T : WebViewApi> of(apiClass: KClass<T>, namespace: String): WebViewApiId<T> {
      require(apiClass.java.isInterface) { "WebView API type must be an interface: ${apiClass.qualifiedName ?: apiClass.simpleName}" }
      return WebViewApiId(apiClass, validateWebViewApiNamespace(namespace))
    }
  }
}

internal fun validateWebViewApiNamespace(namespace: String): String {
  require(namespace.isNotBlank()) { "WebView API namespace must not be blank" }
  require(!namespace.startsWith('.') && !namespace.endsWith('.')) {
    "WebView API namespace must not start or end with '.': $namespace"
  }
  require(!namespace.startsWith('/') && !namespace.endsWith('/')) {
    "WebView API namespace must not start or end with '/': $namespace"
  }
  require(namespace.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '_' || it == '-' || it == '.' }) {
    "WebView API namespace contains unsupported characters: $namespace"
  }
  return namespace
}
