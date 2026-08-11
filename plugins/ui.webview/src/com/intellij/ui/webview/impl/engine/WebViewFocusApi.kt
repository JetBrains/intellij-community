// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl.engine

import com.intellij.ui.webview.api.WebViewApiId
import com.intellij.ui.webview.api.WebViewCallable
import com.intellij.ui.webview.api.WebViewImplementable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
enum class WebViewFocusDirection {
  @SerialName("forward")
  FORWARD,

  @SerialName("backward")
  BACKWARD,
}

@ApiStatus.Internal
@Serializable
data class WebViewFocusEntry(val direction: WebViewFocusDirection)

@ApiStatus.Internal
@Serializable
data class WebViewFocusExit(val direction: WebViewFocusDirection)

@ApiStatus.Internal
interface WebViewFocusPageApi : WebViewCallable {
  fun enter(params: WebViewFocusEntry)

  fun leave()

  companion object {
    val ID: WebViewApiId<WebViewFocusPageApi> = WebViewApiId.of("webview.focus")
  }
}

@ApiStatus.Internal
interface WebViewFocusHostApi : WebViewImplementable {
  fun activated()

  fun exit(params: WebViewFocusExit)

  companion object {
    val ID: WebViewApiId<WebViewFocusHostApi> = WebViewApiId.of("webview.focus")
  }
}
