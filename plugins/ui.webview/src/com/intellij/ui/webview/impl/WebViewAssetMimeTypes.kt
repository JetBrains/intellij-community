// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl

import java.nio.file.Files
import java.nio.file.Path

internal fun webViewAssetContentType(path: String, file: Path? = null): String {
  webViewAssetContentTypeByExtension(path)?.let { return it }
  if (file != null) {
    Files.probeContentType(file)?.let { return withCharsetIfText(it) }
  }
  return "application/octet-stream"
}

private fun webViewAssetContentTypeByExtension(path: String): String? {
  return when (path.substringAfterLast('.', missingDelimiterValue = "").lowercase()) {
    "html", "htm" -> "text/html; charset=utf-8"
    "css" -> "text/css; charset=utf-8"
    "js", "mjs" -> "text/javascript; charset=utf-8"
    "json", "map" -> "application/json; charset=utf-8"
    "svg" -> "image/svg+xml"
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "ico" -> "image/x-icon"
    "otf" -> "font/otf"
    "woff" -> "font/woff"
    "woff2" -> "font/woff2"
    "ttf" -> "font/ttf"
    "wasm" -> "application/wasm"
    else -> null
  }
}

private fun withCharsetIfText(contentType: String): String {
  return if (contentType.startsWith("text/") && "charset=" !in contentType) "$contentType; charset=utf-8" else contentType
}
