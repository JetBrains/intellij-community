// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.api

import org.jetbrains.annotations.ApiStatus

/**
 * Typed protocol facade for one WebView instance.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface WebViewInterop {
  /**
   * Low-level JSON-RPC message bus used by this interop facade.
   */
  val messageBus: WebViewMessageBus

  /**
   * Registers [implementation] as the handler for incoming calls and notifications in [id]'s namespace.
   */
  fun <T : WebViewImplementable> implement(
    id: WebViewApiId<T>,
    implementation: T,
  ): WebViewMessageRegistration

  /**
   * Creates a proxy that sends calls or notifications to the implementation identified by [id].
   */
  fun <T : WebViewCallable> callable(id: WebViewApiId<T>): T
}
