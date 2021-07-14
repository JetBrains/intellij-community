// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.fileActions.importFrom.docx

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.fileActions.MarkdownFileActionsBaseDialog
import org.intellij.plugins.markdown.fileActions.utils.MarkdownImportExportUtils
import org.jetbrains.annotations.Nls

internal class MarkdownImportDocxDialog(
  private val fileToImport: VirtualFile,
  private val importTaskTitle: @Nls String,
  project: Project,
  suggestedFilePath: String,
) : MarkdownFileActionsBaseDialog(project, suggestedFilePath, fileToImport) {

  init {
    title = MarkdownBundle.message("markdown.import.from.docx.dialog.title")
    setOKButtonText(MarkdownBundle.message("markdown.import.dialog.ok.button"))
  }

  override fun doAction(selectedFileUrl: String) {
    MarkdownImportExportUtils.copyAndConvertToMd(project, fileToImport, selectedFileUrl, importTaskTitle)
  }

  override fun getFileNameIfExist(dir: String, fileNameWithoutExtension: String): String? {
    return when {
      FileUtil.exists(FileUtil.join(dir, "$fileNameWithoutExtension.md")) -> "$fileNameWithoutExtension.md"
      FileUtil.exists(FileUtil.join(dir, "$fileNameWithoutExtension.docx")) -> "$fileNameWithoutExtension.docx"
      else -> null
    }
  }
}
