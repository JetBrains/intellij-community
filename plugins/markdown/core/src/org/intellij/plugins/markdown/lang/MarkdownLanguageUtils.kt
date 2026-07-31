package org.intellij.plugins.markdown.lang

import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

fun Language.isMarkdownLanguage(): Boolean {
  return this == MarkdownLanguage.INSTANCE
}

fun Language.supportsMarkdown(dataContext: DataContext? = null): Boolean {
  return MarkdownCompatibilityChecker.EP_NAME.extensionList.any { it.isSupportedContext(this, dataContext) }
}

fun Editor.supportsMarkdown(): Boolean {
  val virtualFile = FileDocumentManager.getInstance().getFile(document) ?: return false
  val fileType = virtualFile.fileType as? LanguageFileType ?: return false
  return MarkdownCompatibilityChecker.EP_NAME.extensionList.any { it.isSupportedLanguage(fileType.language) }
}

fun PsiElement.supportsMarkdown(): Boolean {
  return MarkdownCompatibilityChecker.EP_NAME.extensionList.any { it.isSupportedElement(this) }
}

fun PsiFile.supportsMarkdown(dataContext: DataContext? = null): Boolean {
  return language.supportsMarkdown(dataContext)
}

fun PsiFile.supportsMarkdown(range: TextRange): Boolean {
  return MarkdownCompatibilityChecker.EP_NAME.extensionList.any { it.isSupportedRange(this, range) }
}

fun FileType.isMarkdownType(): Boolean {
  return this == MarkdownFileType.INSTANCE
}

fun VirtualFile.hasMarkdownType(): Boolean {
  return FileTypeRegistry.getInstance().isFileOfType(this, MarkdownFileType.INSTANCE)
}
