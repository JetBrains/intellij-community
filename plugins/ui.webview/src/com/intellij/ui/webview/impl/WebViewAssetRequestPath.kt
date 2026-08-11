// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl

import com.intellij.ui.webview.api.WebViewAssetPath

@JvmInline
internal value class WebViewAssetRequestPath private constructor(
  val path: String,
) {
  fun toAssetPath(): WebViewAssetPath = WebViewAssetPath.fromRequestPath(path)

  companion object {
    fun of(path: String): WebViewAssetRequestPath {
      require('?' !in path && '#' !in path) { "WebView asset request path must not contain query or fragment: $path" }
      return WebViewAssetRequestPath(path)
    }
  }
}
