// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.fileActions.importFrom.docx

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.PathChooserDialog
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.vfs.VirtualFile
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.fileActions.DocxFileType
import org.intellij.plugins.markdown.util.ImpExpUtils

class ImportFromDocxAction : AnAction(MarkdownBundle.message("markdown.import.from.docx")) {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project!!
    val descriptor = createSelectDocxDescriptor()

    FileChooser.chooseFiles(descriptor, project, null) { files: List<VirtualFile> ->
      for (vFileToImport in files) {
        if (descriptor.isFileSelectable(vFileToImport)) {
          val suggestedFilePath = ImpExpUtils.suggestFileNameToCreate(project, vFileToImport, e.dataContext)
          ImportDocxDialog(project, vFileToImport, suggestedFilePath).show()
        }
      }
    }
  }

  private fun createSelectDocxDescriptor(): FileChooserDescriptor =
    object : FileChooserDescriptor(FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()) {
      override fun isFileSelectable(file: VirtualFile) = FileTypeRegistry.getInstance().isFileOfType(file, DocxFileType.INSTANCE)
    }.apply {
      putUserData(PathChooserDialog.PREFER_LAST_OVER_EXPLICIT, null)
    }
}
