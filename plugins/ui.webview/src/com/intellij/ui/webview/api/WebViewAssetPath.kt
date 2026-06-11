// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.api

import org.jetbrains.annotations.ApiStatus
import java.net.URI

/**
 * Normalized relative browser path inside a [WebViewAssetRoot].
 *
 * This is not a filesystem [java.nio.file.Path]: it uses URI-style `/` separators and cannot contain
 * an absolute path, empty segments, `.`, `..`, query, or fragment.
 */
@ApiStatus.Experimental
@JvmInline
value class WebViewAssetPath private constructor(val path: String) {
  override fun toString(): String = path

  companion object {
    @JvmStatic
    fun of(path: String): WebViewAssetPath {
      require(!path.trim().startsWith('/')) { "WebView asset path must be relative: $path" }
      val normalized = normalize(path)
      require(normalized.isNotEmpty()) { "WebView asset path must not be empty" }
      return WebViewAssetPath(normalized)
    }

    @JvmStatic
    fun indexHtml(): WebViewAssetPath = WebViewAssetPath("index.html")

    internal fun fromRequestPath(path: String): WebViewAssetPath {
      if (path.trim('/').isBlank()) return indexHtml()
      val normalized = normalize(path.trim('/'))
      return if (normalized.isEmpty()) indexHtml() else WebViewAssetPath(normalized)
    }

    private fun normalize(path: String): String {
      val trimmed = path.trim()
      require(trimmed.isNotEmpty()) { "WebView asset path must not be empty" }
      require('\\' !in trimmed) { "WebView asset path must use '/' separators: $path" }

      val uri = runCatching { URI(trimmed) }
        .getOrElse { throw IllegalArgumentException("Invalid WebView asset path: $path", it) }
      require(!uri.isAbsolute) { "WebView asset path must be relative: $path" }
      require(uri.rawQuery == null) { "WebView asset path must not contain query: $path" }
      require(uri.rawFragment == null) { "WebView asset path must not contain fragment: $path" }
      require(uri.path.trim('/').split('/').none { it == ".." }) { "WebView asset path must not escape the root: $path" }

      val normalized = uri.normalize().path.trim('/')
      require(!normalized.startsWith("../") && normalized != "..") { "WebView asset path must not escape the root: $path" }
      require(normalized.split('/').none { it.isEmpty() || it == "." || it == ".." }) {
        "WebView asset path must contain only normalized segments: $path"
      }
      return normalized
    }
  }
}
