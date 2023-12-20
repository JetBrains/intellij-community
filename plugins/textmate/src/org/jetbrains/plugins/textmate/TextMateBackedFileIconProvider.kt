package org.jetbrains.plugins.textmate

import com.intellij.ide.FileIconProvider
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon

internal class TextMateBackedFileIconProvider : FileIconProvider {
  override fun getIcon(file: VirtualFile, flags: Int, project: Project?): Icon? {
    val extension = file.extension ?: return null
    val fileType = FileTypeManager.getInstance().getFileTypeByExtension(extension)
    return if (fileType is TextMateBackedFileType) fileType.icon else null
  }
}