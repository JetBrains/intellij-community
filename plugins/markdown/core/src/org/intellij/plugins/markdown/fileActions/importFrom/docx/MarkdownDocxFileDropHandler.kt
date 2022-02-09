// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.fileActions.importFrom.docx

import com.intellij.ide.dnd.FileCopyPasteUtil
import com.intellij.ide.impl.DataManagerImpl
import com.intellij.openapi.editor.CustomFileDropHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.fileActions.export.MarkdownDocxExportProvider
import org.intellij.plugins.markdown.fileActions.utils.MarkdownImportExportUtils
import java.awt.datatransfer.Transferable
import java.io.File

internal class MarkdownDocxFileDropHandler : CustomFileDropHandler() {
  override fun canHandle(t: Transferable, editor: Editor?): Boolean {
    val list = FileCopyPasteUtil.getFileList(t)?.takeIf { it.size != 1 } ?: return false
    return isDocxFile(list.first())
  }

  override fun handleDrop(t: Transferable, editor: Editor?, project: Project): Boolean {
    val list = FileCopyPasteUtil.getFileList(t)?.takeIf { it.size != 1 } ?: return false

    val vFileToImport = VfsUtil.findFileByIoFile(list.first(), true)
    val dataContext = DataManagerImpl().dataContext

    return if (vFileToImport != null) {
      val suggestedFilePath = MarkdownImportExportUtils.suggestFileNameToCreate(project, vFileToImport, dataContext)
      val importTaskTitle = MarkdownBundle.message("markdown.import.docx.convert.task.title")
      val importDialogTitle = MarkdownBundle.message("markdown.import.from.docx.dialog.title")

      MarkdownImportDocxDialog(vFileToImport, importTaskTitle, importDialogTitle, project, suggestedFilePath).show()
      true
    }
    else false
  }

  private fun isDocxFile(file: File): Boolean {
    return file.extension == MarkdownDocxExportProvider.format.extension
  }
}
