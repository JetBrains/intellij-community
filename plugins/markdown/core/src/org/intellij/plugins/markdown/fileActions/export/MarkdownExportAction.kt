// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.fileActions.export

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.vfs.VirtualFile
import org.intellij.plugins.markdown.lang.MarkdownFileType

internal class MarkdownExportAction: AnAction() {
  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project ?: return
    val virtualFile = getFileToConvert(event) ?: return
    MarkdownExportDialog(virtualFile, virtualFile.path, project).show()
  }

  override fun update(event: AnActionEvent) {
    event.presentation.isEnabled = getFileToConvert(event) != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  private fun getFileToConvert(event: AnActionEvent): VirtualFile? {
    val file = event.getData(PlatformDataKeys.FILE_EDITOR)?.file ?: event.getData(CommonDataKeys.VIRTUAL_FILE)

    return file?.takeIf { it.fileType is MarkdownFileType }
  }
}
