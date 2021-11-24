// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.fileActions.importFrom.docx

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.PathChooserDialog
import com.intellij.openapi.vfs.VirtualFile
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.fileActions.export.MarkdownDocxExportProvider
import org.intellij.plugins.markdown.fileActions.utils.MarkdownImportExportUtils

internal class MarkdownImportFromDocxAction : AnAction() {
  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project ?: return
    val descriptor = DocxFileChooserDescriptor().apply { putUserData(PathChooserDialog.PREFER_LAST_OVER_EXPLICIT, null) }

    FileChooser.chooseFiles(descriptor, project, null) { files: List<VirtualFile> ->
      for (vFileToImport in files) {
        if (descriptor.isFileSelectable(vFileToImport)) {
          val suggestedFilePath = MarkdownImportExportUtils.suggestFileNameToCreate(project, vFileToImport, event.dataContext)
          val importTaskTitle = MarkdownBundle.message("markdown.import.docx.convert.task.title")
          val importDialogTitle = MarkdownBundle.message("markdown.import.from.docx.dialog.title")

          MarkdownImportDocxDialog(vFileToImport, importTaskTitle, importDialogTitle, project, suggestedFilePath).show()
        }
      }
    }
  }

  private class DocxFileChooserDescriptor : FileChooserDescriptor(FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()) {
    override fun isFileSelectable(file: VirtualFile?): Boolean {
      return file != null && file.extension == MarkdownDocxExportProvider.format.extension
    }
  }
}
