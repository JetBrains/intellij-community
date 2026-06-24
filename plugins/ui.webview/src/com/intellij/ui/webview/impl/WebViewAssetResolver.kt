// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl

import com.intellij.ui.webview.api.WebViewAssetPath
import com.intellij.ui.webview.api.WebViewAssetProviderResult
import com.intellij.ui.webview.api.WebViewAssetRoot
import com.intellij.ui.webview.api.WebViewAssetSource
import java.nio.file.Files
import java.nio.file.Path
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class WebViewAssetResolver(
  private val root: WebViewAssetRoot,
  private val commonFontRoot: Path = defaultCommonFontRoot(),
) {
  private val devSourceRoots = WebViewAssetDevSourceRoots()

  internal fun resolve(requestPath: WebViewAssetRequestPath): WebViewAssetResponse {
    val path = try {
      requestPath.toAssetPath()
    }
    catch (e: IllegalArgumentException) {
      return WebViewAssetResponse.forbidden(e.message ?: "Invalid WebView asset request path: ${requestPath.path}")
    }

    return try {
      resolveCommonFontAsset(path) ?: resolveCommonRuntimeAsset(path) ?: resolveScopedAsset(path) ?: when (val source = root.source) {
        is WebViewAssetSource.Directory -> resolveFromDirectory(source.root, path, noCache = true)
        is WebViewAssetSource.Classpath -> resolveFromClasspath(source, path)
      }
    }
    catch (t: Throwable) {
      WebViewLogger.LOG.warn("Failed to resolve WebView asset: $path", t)
      WebViewAssetResponse.internalError("Failed to resolve WebView asset: $path")
    }
  }

  private fun resolveFromClasspath(source: WebViewAssetSource.Classpath, path: WebViewAssetPath): WebViewAssetResponse {
    devSourceRoots.find(source, path)?.let { devRoot ->
      val response = resolveFromDirectory(devRoot, path, noCache = true)
      if (response.statusCode != 404) return response
    }

    val resourcePath = "${source.root.path}/${path.path}"
    val resourceUrl = source.owner.classLoader.getResource(resourcePath)
                      ?: return WebViewAssetResponse.notFound("WebView asset resource not found: $resourcePath")
    val bytes = resourceUrl.openStream().use { it.readBytes() }
    return WebViewAssetResponse(
      statusCode = 200,
      statusText = "OK",
      contentType = webViewAssetContentType(path.path),
      bytes = bytes,
      headers = mapOf("Cache-Control" to "no-cache"),
    )
  }

  private fun resolveFromDirectory(root: Path, path: WebViewAssetPath, noCache: Boolean): WebViewAssetResponse {
    val base = root.toAbsolutePath().normalize()
    val file = base.resolve(path.path).normalize()
    if (!file.startsWith(base)) {
      return WebViewAssetResponse.forbidden("WebView asset path escapes root: $path")
    }
    if (!Files.isRegularFile(file)) {
      return WebViewAssetResponse.notFound("WebView asset file not found: $path")
    }
    return WebViewAssetResponse(
      statusCode = 200,
      statusText = "OK",
      contentType = webViewAssetContentType(path.path, file),
      bytes = Files.newInputStream(file).use { it.readBytes() },
      headers = if (noCache) mapOf("Cache-Control" to "no-cache") else emptyMap(),
    )
  }

  private fun resolveCommonRuntimeAsset(path: WebViewAssetPath): WebViewAssetResponse? {
    val runtimePath = path.path.removePrefix(COMMON_RUNTIME_REQUEST_PREFIX)
    if (runtimePath == path.path) return null

    val resourcePath = "$COMMON_RUNTIME_RESOURCE_ROOT/$runtimePath"
    val resourceUrl = WebViewAssetResolver::class.java.classLoader.getResource(resourcePath)
                      ?: return WebViewAssetResponse.notFound("Common WebView runtime asset not found: $path")
    val bytes = resourceUrl.openStream().use { it.readBytes() }
    return WebViewAssetResponse(
      statusCode = 200,
      statusText = "OK",
      contentType = webViewAssetContentType(path.path),
      bytes = bytes,
      headers = mapOf("Cache-Control" to "no-cache"),
    )
  }

  private fun resolveCommonFontAsset(path: WebViewAssetPath): WebViewAssetResponse? {
    val fontPath = path.path.removePrefix(COMMON_FONT_REQUEST_PREFIX)
    if (fontPath == path.path) return null

    val fileName = COMMON_FONT_FILES[fontPath]
                   ?: return WebViewAssetResponse.notFound("Common WebView font asset not found: $path")
    val file = commonFontRoot.resolve(fileName).normalize()
    if (!file.startsWith(commonFontRoot) || !Files.isRegularFile(file)) {
      return WebViewAssetResponse.notFound("Common WebView font file not found: $path")
    }
    return WebViewAssetResponse(
      statusCode = 200,
      statusText = "OK",
      contentType = webViewAssetContentType(fontPath),
      bytes = Files.newInputStream(file).use { it.readBytes() },
      headers = mapOf("Cache-Control" to "no-cache"),
    )
  }

  private fun resolveScopedAsset(path: WebViewAssetPath): WebViewAssetResponse? {
    val scopedProvider = root.scopedAssetProviders.firstOrNull { path.matchesPrefix(it.prefix) } ?: return null
    val scopedPath = path.relativeTo(scopedProvider.prefix)
                       ?: return WebViewAssetResponse.notFound("Scoped WebView asset path is empty: $path")
    val result = scopedProvider.provider.resolve(scopedPath)
                 ?: return WebViewAssetResponse.notFound("Scoped WebView asset not found: $path")
    return result.toResponse()
  }

  private fun WebViewAssetPath.matchesPrefix(prefix: WebViewAssetPath): Boolean {
    return path == prefix.path || path.startsWith("${prefix.path}/")
  }

  private fun WebViewAssetPath.relativeTo(prefix: WebViewAssetPath): WebViewAssetPath? {
    val relative = path.removePrefix(prefix.path).trimStart('/')
    return if (relative.isEmpty()) null else WebViewAssetPath.of(relative)
  }

  private fun WebViewAssetProviderResult.toResponse(): WebViewAssetResponse {
    return when (this) {
      is WebViewAssetProviderResult.Content -> WebViewAssetResponse(
        statusCode = 200,
        statusText = "OK",
        contentType = contentType,
        bytes = readBytes(),
        headers = headers,
      )
      is WebViewAssetProviderResult.Forbidden -> WebViewAssetResponse.forbidden(message)
      is WebViewAssetProviderResult.NotFound -> WebViewAssetResponse.notFound(message)
    }
  }

  companion object {
    private const val COMMON_RUNTIME_REQUEST_PREFIX = "__webview/"
    private const val COMMON_FONT_REQUEST_PREFIX = "__webview/fonts/"
    private const val COMMON_RUNTIME_RESOURCE_ROOT = "webview"
    private val COMMON_FONT_FILES = mapOf(
      "inter/Inter-Regular.otf" to "Inter-Regular.otf",
      "inter/Inter-Italic.otf" to "Inter-Italic.otf",
      "inter/Inter-SemiBold.otf" to "Inter-SemiBold.otf",
      "inter/Inter-SemiBoldItalic.otf" to "Inter-SemiBoldItalic.otf",
      "jetbrains-mono/JetBrainsMono-Regular.ttf" to "JetBrainsMono-Regular.ttf",
      "jetbrains-mono/JetBrainsMono-Italic.ttf" to "JetBrainsMono-Italic.ttf",
      "jetbrains-mono/JetBrainsMono-Bold.ttf" to "JetBrainsMono-Bold.ttf",
      "jetbrains-mono/JetBrainsMono-BoldItalic.ttf" to "JetBrainsMono-BoldItalic.ttf",
    )

    private fun defaultCommonFontRoot(): Path {
      return Path.of(System.getProperty("java.home"), "lib", "fonts").toAbsolutePath().normalize()
    }
  }
}
