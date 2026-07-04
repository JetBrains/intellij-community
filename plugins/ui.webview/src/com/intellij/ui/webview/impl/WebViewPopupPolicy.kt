// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl

import com.intellij.ide.BrowserUtil
import java.net.URI

internal fun openWebViewPopupUrlExternally(url: String, opener: (String) -> Unit = { BrowserUtil.browse(it) }): Boolean {
  val trimmedUrl = url.trim()
  if (trimmedUrl.isEmpty()) return false

  val uri = runCatching { URI(trimmedUrl) }.getOrNull() ?: return blockWebViewPopup(trimmedUrl, "malformed URL")
  val scheme = uri.scheme?.lowercase() ?: return blockWebViewPopup(trimmedUrl, "missing URL scheme")
  if (scheme != "http" && scheme != "https") return blockWebViewPopup(trimmedUrl, "unsupported URL scheme")
  if (uri.host.isNullOrBlank()) return blockWebViewPopup(trimmedUrl, "missing URL host")
  if (uri.host.equals(WEBVIEW_ASSET_HTTPS_HOST, ignoreCase = true)) {
    return blockWebViewPopup(trimmedUrl, "internal WebView asset URL")
  }

  opener(trimmedUrl)
  return true
}

private fun blockWebViewPopup(url: String, reason: String): Boolean {
  WebViewLogger.LOG.debug("Blocked WebView popup URL: $reason; url=$url")
  return false
}
