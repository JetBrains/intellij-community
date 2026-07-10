// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.api

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
fun interface WebViewAssetProvider {
  fun resolve(path: WebViewAssetPath): WebViewAssetProviderResult?
}

@ApiStatus.Experimental
sealed interface WebViewAssetProviderResult {
  class Content private constructor(
    val contentType: String,
    bytes: ByteArray,
    headers: Map<String, String>,
  ) : WebViewAssetProviderResult {
    private val content = bytes.copyOf()
    val headers: Map<String, String> = headers.toMap()

    fun readBytes(): ByteArray = content.copyOf()

    companion object {
      @JvmStatic
      @JvmOverloads
      fun of(contentType: String, bytes: ByteArray, headers: Map<String, String> = emptyMap()): Content {
        return Content(contentType, bytes, headers)
      }
    }
  }

  class Forbidden(val message: String) : WebViewAssetProviderResult

  class NotFound(val message: String) : WebViewAssetProviderResult
}

internal data class WebViewScopedAssetProvider(
  val prefix: WebViewAssetPath,
  val provider: WebViewAssetProvider,
)
