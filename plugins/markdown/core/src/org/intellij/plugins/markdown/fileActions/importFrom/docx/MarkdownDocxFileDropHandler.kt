// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.fileActions.importFrom.docx

import com.intellij.ide.impl.DataManagerImpl
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.FileDropEvent
import com.intellij.openapi.editor.FileDropHandler
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.fileActions.export.MarkdownDocxExportProvider
import org.intellij.plugins.markdown.fileActions.utils.MarkdownImportExportUtils
import java.io.File

internal class MarkdownDocxFileDropHandler : FileDropHandler {
  override suspend fun handleDrop(e: FileDropEvent): Boolean {
    if (e.files.size != 1) return false
    if (!isDocxFile(e.files.first())) return false

    return handleDrop(e.files.first(), e.project)
  }

  private suspend fun handleDrop(file: File, project: Project): Boolean {
    val vFileToImport = VfsUtil.findFileByIoFile(file, true) ?: return false
    val dataContext = DataManagerImpl().dataContext

    val suggestedFilePath = readAction {
      MarkdownImportExportUtils.suggestFileNameToCreate(project, vFileToImport, dataContext)
    }

    withContext(Dispatchers.EDT) {
      MarkdownImportDocxDialog(
        vFileToImport,
        MarkdownBundle.message("markdown.import.docx.convert.task.title"),
        MarkdownBundle.message("markdown.import.from.docx.dialog.title"),
        project,
        suggestedFilePath
      ).show()
    }

    return true
  }

  private fun isDocxFile(file: File): Boolean {
    return file.extension == MarkdownDocxExportProvider.format.extension
  }
}
