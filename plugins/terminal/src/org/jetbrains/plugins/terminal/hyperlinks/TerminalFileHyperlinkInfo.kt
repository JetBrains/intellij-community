// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.hyperlinks

import com.intellij.execution.filters.FileHyperlinkInfoBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.asNioPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * A hyperlink to the file at [path] in the environment of the terminal.
 *
 * The link is created from the path alone, so the file does not have to be in the VFS
 * when the link is found. [TerminalHyperlinkNavigator] calls [refreshFile] before
 * navigating, so a file created after the last VFS refresh can be opened too.
 *
 * [lineNumber] and [columnNumber] are zero-based and exposed for tests.
 */
@ApiStatus.Internal
class TerminalFileHyperlinkInfo(
  project: Project,
  val path: EelPath,
  val lineNumber: Int,
  val columnNumber: Int,
) : FileHyperlinkInfoBase(project, lineNumber, columnNumber) {

  override val virtualFile: VirtualFile?
    get() = nioPath()?.let { VirtualFileManager.getInstance().findFileByNioPath(it) }

  /**
   * Loads the file into the VFS from the disk, so that [descriptor] finds it
   * even if it appeared after the last VFS refresh.
   */
  suspend fun refreshFile() {
    val nioPath = nioPath() ?: return
    withContext(Dispatchers.IO) {
      VirtualFileManager.getInstance().refreshAndFindFileByNioPath(nioPath)
    }
  }

  private fun nioPath(): Path? {
    return try {
      path.asNioPath()
    }
    catch (_: IllegalArgumentException) {
      null // no file system is registered for the environment
    }
  }
}
