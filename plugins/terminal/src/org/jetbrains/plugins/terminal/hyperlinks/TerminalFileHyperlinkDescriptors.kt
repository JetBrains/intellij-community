// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.hyperlinks

import com.intellij.execution.filters.FileHyperlinkInfo
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.plugins.terminal.hyperlinks.menu.BackendHyperlinkInfoService
import org.jetbrains.plugins.terminal.hyperlinks.session.TerminalHyperlinksSessionId

/**
 * Reads the file that a file hyperlink of a terminal names, on the backend.
 *
 * A hyperlinks session belongs to the project of the terminal editor that created it, and each project counts its
 * session ids from zero. So the caller passes that owner project, not the project of the window that shows the menu.
 * The terminal navigates nothing here. The caller decides the project in which the file opens.
 *
 * [hyperlinkInfo] reads the hyperlink of a session. It is [BackendHyperlinkInfoService] by default.
 */
@Internal
class TerminalFileHyperlinkDescriptors(
  private val hyperlinkInfo: suspend (Project, TerminalHyperlinksSessionId, TerminalHyperlinkId) -> HyperlinkInfo? = { project, sessionId, id ->
    BackendHyperlinkInfoService.getInstance().getHyperlinkInfo(project, sessionId, id)?.hyperlinkInfo
  },
) {
  /**
   * The descriptor of the file hyperlink [hyperlinkId] of the session [sessionId] in [ownerProject], or `null` for a
   * hyperlink that names no file, a gone hyperlink, or a file that is not in the VFS.
   *
   * A [TerminalFileHyperlinkInfo] loads its file into the VFS first, as a click does.
   */
  suspend fun descriptorOf(ownerProject: Project, sessionId: TerminalHyperlinksSessionId, hyperlinkId: TerminalHyperlinkId): OpenFileDescriptor? {
    if (ownerProject.isDisposed) {
      return null
    }
    val fileHyperlinkInfo = hyperlinkInfo(ownerProject, sessionId, hyperlinkId) as? FileHyperlinkInfo ?: return null
    if (fileHyperlinkInfo is TerminalFileHyperlinkInfo) {
      fileHyperlinkInfo.refreshFile()
    }
    return readAction { fileHyperlinkInfo.descriptor }?.takeIf { it.file.isValid }
  }
}
