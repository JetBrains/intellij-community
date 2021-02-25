// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.fileActions.importFrom.docx

import com.intellij.ide.dnd.FileCopyPasteUtil
import com.intellij.ide.impl.DataManagerImpl
import com.intellij.openapi.editor.CustomFileDropHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import org.intellij.plugins.markdown.fileActions.DocxFileType
import org.intellij.plugins.markdown.util.ImpExpUtils
import java.awt.datatransfer.Transferable

class DocxFileDropHandler: CustomFileDropHandler() {
  override fun canHandle(t: Transferable, editor: Editor?): Boolean {
    val list = FileCopyPasteUtil.getFileList(t)
    if (list == null || list.size != 1) return false

    return DocxFileType.isDocxFile(list.first())
  }

  override fun handleDrop(t: Transferable, editor: Editor?, project: Project): Boolean {
    val list = FileCopyPasteUtil.getFileList(t)
    if (list == null || list.size != 1) return false

    val vFileToImport = VfsUtil.findFileByIoFile(list.first(), true)
    val dataContext = DataManagerImpl().dataContext

    return if (vFileToImport != null) {
      val suggestedFilePath = ImpExpUtils.suggestFileNameToCreate(project, vFileToImport, dataContext)
      ImportDocxDialog(project, vFileToImport, suggestedFilePath).show()
      true
    }
    else false
  }
}
