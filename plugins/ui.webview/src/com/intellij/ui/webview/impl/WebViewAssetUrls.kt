// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl

import com.intellij.ui.webview.api.WebViewAssetPath
import java.net.URI
import org.jetbrains.annotations.ApiStatus

internal const val WEBVIEW_ASSET_CUSTOM_SCHEME: String = "ij-webview-asset"

internal const val WEBVIEW_ASSET_HTTPS_HOST: String = "ij-webview-assets.local"

internal fun webViewAssetCustomSchemeUrl(entry: WebViewAssetPath, query: String? = null): String {
  return "$WEBVIEW_ASSET_CUSTOM_SCHEME:/${entry.path}${querySuffix(query)}"
}

@ApiStatus.Internal
fun webViewAssetHttpsUrl(entry: WebViewAssetPath, query: String? = null): String {
  return "https://$WEBVIEW_ASSET_HTTPS_HOST/${entry.path}${querySuffix(query)}"
}

@ApiStatus.Internal
fun resolveWebViewAssetUrl(url: String, resolver: WebViewAssetResolver?): WebViewAssetResponse? {
  val requestPath = try {
    parseWebViewAssetRequestPath(url)
  }
  catch (e: IllegalArgumentException) {
    return WebViewAssetResponse.forbidden(e.message ?: "Invalid WebView asset URL")
  } ?: return null

  return resolver?.resolve(requestPath) ?: WebViewAssetResponse.notFound("No active WebView asset root")
}

private fun querySuffix(query: String?): String {
  if (query == null) return ""
  require(!query.startsWith("?") && '#' !in query) { "WebView asset query must be a raw query without '?' or '#': $query" }
  return "?$query"
}

private fun parseWebViewAssetRequestPath(url: String): WebViewAssetRequestPath? {
  val uri = runCatching { URI(url) }.getOrNull() ?: return null
  return when (uri.scheme) {
    WEBVIEW_ASSET_CUSTOM_SCHEME -> {
      require(uri.rawAuthority == null) { "WebView asset URL must not contain authority: $url" }
      WebViewAssetRequestPath.of(uri.rawPath.orEmpty())
    }
    "https" -> {
      if (uri.host != WEBVIEW_ASSET_HTTPS_HOST) return null
      require(uri.port < 0) { "WebView asset URL must not contain port: $url" }
      WebViewAssetRequestPath.of(uri.rawPath?.trimStart('/') ?: "")
    }
    "http" -> {
      if (uri.host == WEBVIEW_ASSET_HTTPS_HOST) throw IllegalArgumentException("WebView asset URL must use https: $url")
      null
    }
    else -> null
  }
}
