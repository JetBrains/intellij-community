// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.ui.webview.api.WebViewAssetPath
import org.jetbrains.annotations.ApiStatus
import java.net.URI
import kotlin.time.measureTimedValue

private val LOG = fileLogger()

internal const val WEBVIEW_ASSET_CUSTOM_SCHEME: String = "ij-webview-asset"

// Windows WebView2 needs a non-opaque custom-scheme origin for ES module loading, so its
// internal asset URLs use a fixed authority: `ij-webview-asset://assets/...`.
// The host is not a view router; each CoreWebView2 still resolves assets through its own handler.
internal const val WEBVIEW_ASSET_CUSTOM_SCHEME_HOST: String = "assets"

internal const val WEBVIEW_ASSET_HTTPS_HOST: String = "ij-webview-assets.local"

internal fun webViewAssetCustomSchemeUrl(entry: WebViewAssetPath, query: String? = null): String {
  return "$WEBVIEW_ASSET_CUSTOM_SCHEME://$WEBVIEW_ASSET_CUSTOM_SCHEME_HOST/${entry.path}${querySuffix(query)}"
}

@ApiStatus.Internal
fun webViewAssetHttpsUrl(entry: WebViewAssetPath, query: String? = null): String {
  return "https://$WEBVIEW_ASSET_HTTPS_HOST/${entry.path}${querySuffix(query)}"
}

@ApiStatus.Internal
fun resolveWebViewAssetUrl(url: String, resolver: WebViewAssetResolver?, diagnosticEngine: String? = null): WebViewAssetResponse? {
  val timedResponse = measureTimedValue {
    val request: WebViewAssetRequest?
    try {
      request = parseWebViewAssetRequest(url)
    }
    catch (e: IllegalArgumentException) {
      return@measureTimedValue WebViewAssetResponse.forbidden(e.message ?: "Invalid WebView asset URL")
    }
    if (request == null) return@measureTimedValue null

    (resolver?.resolve(request.path) ?: WebViewAssetResponse.notFound("No active WebView asset root"))
      .withRequestDiagnostics(request.path.path, request.scheme)
  }
  val response = timedResponse.value
  val engine = diagnosticEngine
  if (engine != null && response != null) {
    LOG.traceWebViewPerf("webview.asset.resolve", timedResponse.duration, response.diagnosticDetails(engine))
  }
  return response
}

private fun querySuffix(query: String?): String {
  if (query == null) return ""
  require(!query.startsWith("?") && '#' !in query) { "WebView asset query must be a raw query without '?' or '#': $query" }
  return "?$query"
}

private data class WebViewAssetRequest(
  val path: WebViewAssetRequestPath,
  val scheme: String,
)

private fun parseWebViewAssetRequest(url: String): WebViewAssetRequest? {
  val uri = runCatching { URI(url) }.getOrNull() ?: return null
  return when (uri.scheme) {
    WEBVIEW_ASSET_CUSTOM_SCHEME -> {
      require(uri.host == WEBVIEW_ASSET_CUSTOM_SCHEME_HOST) { "WebView asset URL must use $WEBVIEW_ASSET_CUSTOM_SCHEME_HOST authority: $url" }
      require(uri.port < 0) { "WebView asset URL must not contain port: $url" }
      WebViewAssetRequest(WebViewAssetRequestPath.of(uri.rawPath?.trimStart('/') ?: ""), WEBVIEW_ASSET_CUSTOM_SCHEME)
    }
    "https" -> {
      if (uri.host != WEBVIEW_ASSET_HTTPS_HOST) return null
      require(uri.port < 0) { "WebView asset URL must not contain port: $url" }
      WebViewAssetRequest(WebViewAssetRequestPath.of(uri.rawPath?.trimStart('/') ?: ""), "https")
    }
    "http" -> {
      if (uri.host == WEBVIEW_ASSET_HTTPS_HOST) throw IllegalArgumentException("WebView asset URL must use https: $url")
      null
    }
    else -> null
  }
}
