// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl

import java.nio.charset.StandardCharsets
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class WebViewAssetResponse(
  val statusCode: Int,
  val statusText: String,
  val contentType: String,
  val bytes: ByteArray,
  val headers: Map<String, String> = emptyMap(),
) {
  val mimeType: String get() = contentType.substringBefore(';').trim()

  companion object {
    fun notFound(message: String): WebViewAssetResponse = error(404, "Not Found", message)
    fun forbidden(message: String): WebViewAssetResponse = error(403, "Forbidden", message)
    fun internalError(message: String): WebViewAssetResponse = error(500, "Internal Server Error", message)

    private fun error(statusCode: Int, statusText: String, message: String): WebViewAssetResponse {
      return WebViewAssetResponse(
        statusCode = statusCode,
        statusText = statusText,
        contentType = "text/plain; charset=utf-8",
        bytes = message.toByteArray(StandardCharsets.UTF_8),
        headers = mapOf("Cache-Control" to "no-store"),
      )
    }
  }
}
