// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.ui.preview

import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.io.URLUtil
import com.intellij.util.system.LowLevelLocalMachineAccess
import com.intellij.util.system.OS
import org.intellij.plugins.markdown.service.VirtualFileAccessor
import org.jetbrains.annotations.ApiStatus
import java.net.URI

/**
 * Resolves a path written in a Markdown document to a file: the `src` of an image, or the destination
 * of a link.
 *
 * The resolver uses the VFS only, so it also runs on a Remote Development backend. Run it where
 * the files are. A Remote Development frontend has no directory tree. See IJPL-254292.
 */
@ApiStatus.Internal
object MarkdownPreviewPathResolver {
  // A scheme needs two or more characters. One character before a colon is a Windows drive letter.
  private val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.\\-]+:")

  // The resolver runs on the machine that holds the files, so the local OS is the right one.
  @OptIn(LowLevelLocalMachineAccess::class)
  private val isWindows = OS.CURRENT == OS.Windows

  sealed interface Resolution {
    data class Found(val file: VirtualFile) : Resolution
    data object NotFound : Resolution

    /** The file exists outside the project root, and the project is not trusted. */
    data object Forbidden : Resolution
  }

  /**
   * Tells if the browser loads [rawSource] itself, as it does a `data:` URI or an `http:` URL.
   *
   * A `file:` URL is not one of them, because the file can live on another machine.
   */
  fun isBrowserOwned(rawSource: String): Boolean {
    if (rawSource.startsWith("//")) {
      // A URL without a scheme. A Windows UNC path must use a backslash instead.
      return true
    }
    val scheme = SCHEME.find(rawSource)?.value ?: return false
    return !scheme.equals("file:", ignoreCase = true)
  }

  /**
   * Finds the file that [rawSource] names for [document].
   *
   * @param allowOutsideProjectRoot pass the trust state of the project
   */
  suspend fun resolve(
    document: VirtualFile?,
    projectRoot: VirtualFile?,
    rawSource: String,
    allowOutsideProjectRoot: Boolean = false,
  ): Resolution {
    val file = find(document, projectRoot, rawSource) ?: return Resolution.NotFound
    if (!file.isValid || file.isDirectory) {
      return Resolution.NotFound
    }
    if (allowOutsideProjectRoot) {
      return Resolution.Found(file)
    }
    if (projectRoot == null || !VfsUtilCore.isAncestor(projectRoot, file, false)) {
      return Resolution.Forbidden
    }
    return Resolution.Found(file)
  }

  /** The names on the path that [rawSource] names, or none if the browser loads [rawSource] itself. */
  fun pathNames(rawSource: String): Set<String> {
    if (rawSource.isEmpty() || isBrowserOwned(rawSource)) {
      return emptySet()
    }
    val trimmed = trimQueryAndFragment(rawSource)
    val path = if (trimmed.startsWith("file:", ignoreCase = true)) runCatching { URI(trimmed).path }.getOrNull() else decode(trimmed)
    return path.orEmpty().split('/').filterTo(LinkedHashSet()) { it.isNotEmpty() && it != "." && it != ".." }
  }

  private suspend fun find(document: VirtualFile?, projectRoot: VirtualFile?, rawSource: String): VirtualFile? {
    if (rawSource.isEmpty() || isBrowserOwned(rawSource)) {
      return null
    }
    if (rawSource.startsWith("file:", ignoreCase = true)) {
      return findByFileUrl(trimQueryAndFragment(rawSource))
    }
    val path = decode(trimQueryAndFragment(rawSource))
    if (path.isEmpty()) {
      return null
    }
    if (hasDriveLetter(path)) {
      return findByAbsolutePath(path)
    }
    if (path.startsWith('/')) {
      return projectRoot?.findFileByRelativePath(path) ?: findByAbsolutePath(path)
    }
    // The project root is a fallback for a path that names the project, not the document.
    return document?.parent?.findFileByRelativePath(path)
           ?: projectRoot?.findFileByRelativePath(path)
  }

  /** A Remote Development frontend holds no local file, so it asks the backend. See IJPL-253826. */
  private suspend fun findByFileUrl(url: String): VirtualFile? {
    val localFile = findByAbsolutePath(runCatching { URI(url).path }.getOrNull())
    if (localFile != null) {
      return localFile
    }
    return VirtualFileAccessor.tryGetInstance()?.tryToFindFileByUrl(url)?.virtualFile()
  }

  private fun hasDriveLetter(path: String): Boolean {
    return isWindows
           && path.length >= 3
           && path[0].isLetter()
           && path[1] == ':'
           && path[2] == '/'
  }

  private fun findByAbsolutePath(rawPath: String?): VirtualFile? {
    if (rawPath.isNullOrEmpty()) {
      return null
    }
    // A backslash separates a Windows path. Elsewhere it is a legal character of a file name.
    var path = if (isWindows) rawPath.replace('\\', '/') else rawPath
    if (isWindows && path.length > 2 && path[0] == '/' && path[2] == ':') {
      // `file:///C:/image.png` keeps a slash before the drive letter.
      path = path.substring(1)
    }
    return StandardFileSystems.local().findFileByPath(path)
  }

  private fun trimQueryAndFragment(rawSource: String): String {
    val end = rawSource.indexOfFirst { it == '?' || it == '#' }
    return if (end < 0) rawSource else rawSource.substring(0, end)
  }

  private fun decode(rawPath: String): String {
    // Unlike URLDecoder, this keeps a plus sign and a percent sign without two hex digits literal, as a browser does.
    val decoded = URLUtil.unescapePercentSequences(rawPath)
    return if (isWindows) decoded.replace('\\', '/') else decoded
  }
}
