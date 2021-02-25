// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.fileActions

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.vfs.VfsUtil
import org.intellij.plugins.markdown.MarkdownBundle
import java.io.File
import javax.swing.Icon

class DocxFileType : FileType {
  companion object {
    val INSTANCE = DocxFileType()
    const val NAME = "Doc(x)"

    fun isDocxFile(file: File): Boolean {
      val vFile = VfsUtil.findFileByIoFile(file, true)

      return vFile != null && FileTypeRegistry.getInstance().isFileOfType(vFile, INSTANCE)
    }
  }

  override fun getName(): String = NAME

  override fun getDescription(): String = MarkdownBundle.message("markdown.import.file.type.docx")

  override fun getDefaultExtension(): String = "docx"

  override fun getIcon(): Icon = AllIcons.FileTypes.Text

  override fun isBinary(): Boolean = false
}
