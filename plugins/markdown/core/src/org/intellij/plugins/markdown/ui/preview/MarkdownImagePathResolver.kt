// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.ui.preview

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.ide.vfs.virtualFile
import org.intellij.plugins.markdown.service.VirtualFileAccessor
import org.jetbrains.annotations.ApiStatus
import java.net.URLDecoder
import java.nio.charset.Charset

@ApiStatus.Internal
object MarkdownImagePathResolver {
  suspend fun resolve(baseFile: VirtualFile, projectRoot: VirtualFile, rawPath: String, allowOutsideProjectRoot: Boolean = false): Resolution {
    if (rawPath.startsWith("file:/")) {
      val file = VirtualFileAccessor.tryGetInstance()?.tryToFindFileByUrl(rawPath)?.virtualFile() ?: return Resolution.NotFound
      return validate(file, projectRoot, allowOutsideProjectRoot)
    }
    var path = runCatching { URLDecoder.decode(rawPath, Charset.defaultCharset()) }.getOrNull() ?: return Resolution.NotFound
    if (SystemInfo.isWindows) path = StringUtil.replace(path, "\\", "/")
    val file = if (path.startsWith('/')) {
      if (SystemInfo.isWindows) path = path.trimStart('/')
      projectRoot.findFileByRelativePath(path) ?: return Resolution.NotFound
    }
    else {
      baseFile.findFileByRelativePath(path) ?: return Resolution.NotFound
    }
    return validate(file, projectRoot, allowOutsideProjectRoot)
  }

  private fun validate(file: VirtualFile, projectRoot: VirtualFile, allowOutsideProjectRoot: Boolean): Resolution {
    if (!allowOutsideProjectRoot && !VfsUtilCore.isAncestor(projectRoot, file, false)) {
      return Resolution.Forbidden
    }
    return Resolution.Found(file)
  }

  sealed interface Resolution {
    data class Found(val file: VirtualFile, val url: String = file.path) : Resolution
    data object NotFound : Resolution
    data object Forbidden : Resolution
  }
}
