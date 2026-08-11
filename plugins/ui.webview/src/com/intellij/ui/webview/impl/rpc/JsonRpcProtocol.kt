// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl.rpc

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
internal data class WebViewRpcError(
  val code: Int,
  val message: String,
  val data: JsonElement? = null,
)

@ApiStatus.Internal
@Serializable
internal data class WebViewCancelCallParams(
  val id: JsonElement,
  val message: String? = null,
)

@ApiStatus.Internal
internal object WebViewRpcErrorCodes {
  const val INVALID_FRAME: Int = -32600
  const val METHOD_NOT_FOUND: Int = -32601
  const val INVALID_PARAMS: Int = -32602
  const val INTERNAL_ERROR: Int = -32603
  const val CANCELLED: Int = -32800
}
