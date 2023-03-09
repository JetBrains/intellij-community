package org.intellij.plugins.markdown.lang

import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.vfs.VirtualFile

object MarkdownLanguageUtils {
  fun Language.isMarkdownLanguage(): Boolean {
    return this == MarkdownLanguage.INSTANCE
  }

  fun FileType.isMarkdownType(): Boolean {
    return this == MarkdownFileType.INSTANCE
  }

  fun VirtualFile.hasMarkdownType(): Boolean {
    return FileTypeRegistry.getInstance().isFileOfType(this, MarkdownFileType.INSTANCE)
  }
}
