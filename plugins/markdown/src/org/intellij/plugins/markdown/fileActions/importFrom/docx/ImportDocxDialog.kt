// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.fileActions.importFrom.docx

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.fileActions.ImportExportBaseDialog
import org.intellij.plugins.markdown.util.ImpExpUtils

class ImportDocxDialog(private val project: Project,
                       private val vFileToImport: VirtualFile,
                       suggestedFilePath: String) : ImportExportBaseDialog(project, suggestedFilePath, vFileToImport) {

  init {
    title = MarkdownBundle.message("markdown.import.from.docx.dialog.title")
  }

  override fun doAction(selectedFileUrl: String) {
    val selectedResDir = "${File(selectedFileUrl).parent}/import-resources" //todo: move to pandoc settings
    ImpExpUtils.copyAndConvertToMd(project, vFileToImport, selectedFileUrl, selectedResDir)
  }
}
