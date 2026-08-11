// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.webview.preview

import com.intellij.openapi.project.BaseProjectDirectories
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.ui.webview.api.WebViewAssetPath
import com.intellij.ui.webview.api.WebViewAssetProvider
import com.intellij.ui.webview.api.WebViewAssetProviderResult
import java.net.URI
import java.net.URLConnection
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64

internal class MarkdownPreviewResourceProvider(
  project: Project,
  private val sourceFile: VirtualFile?,
) : WebViewAssetProvider {
  private val projectRoot = sourceFile?.let { BaseProjectDirectories.getInstance(project).getBaseDirectoryFor(it) } ?: project.guessProjectDir()
  private val allowedRoot = projectRoot ?: sourceFile?.parent

  override fun resolve(path: WebViewAssetPath): WebViewAssetProviderResult {
    val rawSource = decodeSource(path.path)
                    ?: return WebViewAssetProviderResult.Forbidden("Invalid Markdown resource path: $path")
    return when (val resource = resolveRawSource(rawSource)) {
      ResolvedResource.Forbidden -> WebViewAssetProviderResult.Forbidden("Markdown resource escapes the project root: $rawSource")
      ResolvedResource.NotFound -> WebViewAssetProviderResult.NotFound("Markdown resource not found: $rawSource")
      is ResolvedResource.LocalFile -> resource.toContent(rawSource)
      is ResolvedResource.VirtualFileResource -> resource.toContent(rawSource)
    }
  }

  private fun ResolvedResource.LocalFile.toContent(rawSource: String): WebViewAssetProviderResult {
    if (!Files.isRegularFile(path)) {
      return WebViewAssetProviderResult.NotFound("Markdown resource is not a file: $rawSource")
    }

    return WebViewAssetProviderResult.Content.of(
      contentType = URLConnection.guessContentTypeFromName(path.fileName?.toString()) ?: "application/octet-stream",
      bytes = Files.newInputStream(path).use { it.readBytes() },
      headers = mapOf("Cache-Control" to "no-cache"),
    )
  }

  private fun ResolvedResource.VirtualFileResource.toContent(rawSource: String): WebViewAssetProviderResult {
    if (file.isDirectory || !file.exists()) {
      return WebViewAssetProviderResult.NotFound("Markdown resource is not a file: $rawSource")
    }
    if (!isInsideAllowedRoot(file)) {
      return WebViewAssetProviderResult.Forbidden("Markdown resource escapes the project root: $rawSource")
    }

    return WebViewAssetProviderResult.Content.of(
      contentType = URLConnection.guessContentTypeFromName(file.name) ?: "application/octet-stream",
      bytes = file.inputStream.use { it.readBytes() },
      headers = mapOf("Cache-Control" to "no-cache"),
    )
  }

  private fun resolveRawSource(rawSource: String): ResolvedResource {
    if (sourceFile == null) return ResolvedResource.NotFound
    if (rawSource.startsWith("//")) return ResolvedResource.NotFound
    if (rawSource.startsWith("file:", ignoreCase = true)) return resolveFileUri(rawSource)
    if (rawSource.hasScheme()) return ResolvedResource.NotFound

    val resourcePath = decodePath(rawSource.trimQueryAndFragment()) ?: return ResolvedResource.NotFound
    if (resourcePath.isBlank()) return ResolvedResource.NotFound
    return if (resourcePath.startsWith('/')) {
      resolveProjectPath(projectRoot, resourcePath.trimStart('/'))
    }
    else {
      resolveProjectPath(sourceFile.parent, resourcePath)
    }
  }

  private fun resolveProjectPath(base: VirtualFile?, relativePath: String): ResolvedResource {
    if (base == null) return ResolvedResource.NotFound
    val basePath = base.toNioPathOrNull()?.toAbsolutePath()?.normalize()
    if (basePath != null) {
      return resolveLocalPath(basePath.resolve(relativePath).normalize())
    }
    val file = base.findFileByRelativePath(relativePath) ?: return ResolvedResource.NotFound
    return ResolvedResource.VirtualFileResource(file)
  }

  private fun resolveFileUri(rawSource: String): ResolvedResource {
    val uri = runCatching { URI(rawSource) }.getOrNull() ?: return ResolvedResource.NotFound
    if (!uri.scheme.equals("file", ignoreCase = true)) return ResolvedResource.NotFound
    val file = runCatching { Path.of(uri) }.getOrNull() ?: return ResolvedResource.NotFound
    return resolveLocalPath(file.toAbsolutePath().normalize())
  }

  private fun resolveLocalPath(path: Path): ResolvedResource {
    if (!isInsideAllowedRoot(path)) return ResolvedResource.Forbidden
    if (!Files.isRegularFile(path)) return ResolvedResource.NotFound
    return ResolvedResource.LocalFile(path)
  }

  private fun isInsideAllowedRoot(file: VirtualFile): Boolean {
    val root = allowedRoot ?: return false
    val filePath = file.toNioPathOrNull()?.toAbsolutePath()?.normalize()
    val rootPath = root.toNioPathOrNull()?.toAbsolutePath()?.normalize()
    if (filePath != null && rootPath != null) {
      return filePath.startsWith(rootPath)
    }
    return VfsUtilCore.isAncestor(root, file, false)
  }

  private fun isInsideAllowedRoot(path: Path): Boolean {
    val rootPath = allowedRoot?.toNioPathOrNull()?.toAbsolutePath()?.normalize() ?: return false
    return path.toRealOrNormalizedPath().startsWith(rootPath.toRealOrNormalizedPath())
  }

  private fun decodeSource(path: String): String? {
    if ('/' in path) return null
    val padded = path + "=".repeat((4 - path.length % 4) % 4)
    return runCatching { String(Base64.getUrlDecoder().decode(padded), StandardCharsets.UTF_8) }.getOrNull()
  }

  private fun decodePath(path: String): String? {
    return runCatching { URLDecoder.decode(path.replace("+", "%2B"), StandardCharsets.UTF_8) }.getOrNull()
  }

  private fun String.trimQueryAndFragment(): String {
    val queryIndex = indexOf('?').takeIf { it >= 0 } ?: length
    val fragmentIndex = indexOf('#').takeIf { it >= 0 } ?: length
    return substring(0, minOf(queryIndex, fragmentIndex))
  }

  private fun String.hasScheme(): Boolean {
    val colonIndex = indexOf(':')
    return colonIndex > 0 && take(colonIndex).withIndex().all { (index, char) ->
      if (index == 0) char.isLetter() else char.isLetterOrDigit() || char == '+' || char == '.' || char == '-'
    }
  }

  private fun Path.toRealOrNormalizedPath(): Path {
    return runCatching { toRealPath() }.getOrElse { toAbsolutePath().normalize() }
  }

  private sealed interface ResolvedResource {
    class LocalFile(val path: Path) : ResolvedResource
    class VirtualFileResource(val file: VirtualFile) : ResolvedResource
    object Forbidden : ResolvedResource
    object NotFound : ResolvedResource
  }
}

internal const val MARKDOWN_RESOURCE_PREFIX: String = "__markdown-preview-resource"
