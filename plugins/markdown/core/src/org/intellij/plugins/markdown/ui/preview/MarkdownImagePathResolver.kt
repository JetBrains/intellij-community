// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.ui.preview

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.toNioPathOrNull
import org.jetbrains.annotations.ApiStatus
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.Charset
import java.nio.file.Path

@ApiStatus.Internal
object MarkdownImagePathResolver {
  fun resolve(baseFile: VirtualFile, projectRoot: VirtualFile, rawPath: String, allowOutsideProjectRoot: Boolean = false): Resolution {
    if (rawPath.startsWith("file:/")) {
      val path = runCatching { Path.of(URI(rawPath)) }.getOrNull() ?: return Resolution.NotFound
      return validate(path, projectRoot, allowOutsideProjectRoot)
    }
    var path = runCatching { URLDecoder.decode(rawPath, Charset.defaultCharset()) }.getOrNull() ?: return Resolution.NotFound
    if (SystemInfo.isWindows) path = StringUtil.replace(path, "\\", "/")
    if (path.startsWith('/')) {
      if (SystemInfo.isWindows) path = path.trimStart('/')
      path = findRelativePath(projectRoot, path) ?: path
    }
    else {
      path = findRelativePath(baseFile, path) ?: return Resolution.NotFound
    }
    return validate(Path.of(FileUtil.toSystemIndependentName(path)).normalize(), projectRoot, allowOutsideProjectRoot)
  }

  private fun validate(path: Path, projectRoot: VirtualFile, allowOutsideProjectRoot: Boolean): Resolution {
    val rootPath = projectRoot.toNioPathOrNull() ?: return Resolution.NotFound
    if (!allowOutsideProjectRoot && !path.toAbsolutePath().normalize().startsWith(rootPath.toAbsolutePath().normalize())) {
      return Resolution.Forbidden
    }
    return Resolution.Found(path, FileUtil.toSystemIndependentName(path.toString()))
  }

  private fun findRelativePath(base: VirtualFile, path: String): String? {
    val file = base.findFileByRelativePath(path) ?: return null
    val nioPath = file.toNioPathOrNull() ?: return null
    return nioPath.normalize().toString()
  }

  sealed interface Resolution {
    data class Found(val path: Path, val url: String) : Resolution
    data object NotFound : Resolution
    data object Forbidden : Resolution
  }
}
