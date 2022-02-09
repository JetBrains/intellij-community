package org.intellij.plugins.markdown.editor.lists

import com.intellij.codeInsight.editorActions.AutoHardWrapHandler
import com.intellij.lang.Language
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.impl.source.codeStyle.lineIndent.FormatterBasedLineIndentProvider
import com.intellij.psi.util.PsiEditorUtil
import com.intellij.psi.util.parentOfTypes
import com.intellij.refactoring.suggested.startOffset
import org.intellij.plugins.markdown.editor.lists.ListUtils.getLineIndentRange
import org.intellij.plugins.markdown.editor.lists.ListUtils.getListItemAtLineSafely
import com.intellij.util.text.CharArrayUtil
import org.intellij.plugins.markdown.lang.MarkdownLanguage
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownBlockQuote
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFence
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile
import org.intellij.plugins.markdown.settings.MarkdownSettings

/**
 * This is a helper class for the [MarkdownListEnterHandlerDelegate] to provide correct indentation for new lines, created on Enter.
 *
 * [FormatterBasedLineIndentProvider] is extended to be able to fallback to the default behaviour
 */
internal class MarkdownListIndentProvider : FormatterBasedLineIndentProvider() {

  override fun getLineIndent(project: Project, editor: Editor, language: Language?, offset: Int): String? {
    if (!MarkdownSettings.getInstance(project).isEnhancedEditingEnabled) {
      return null
    }
    val file = PsiEditorUtil.getPsiFile(editor) as? MarkdownFile ?: return null
    return doGetLineIndent(editor, file, offset) ?: super.getLineIndent(project, editor, language, offset)
  }

  private fun doGetLineIndent(editor: Editor, file: MarkdownFile, offset: Int): String? {
    if (isInBlockQuoteOrCodeFence(editor, file)) return null
    if (editor.getUserData(AutoHardWrapHandler.AUTO_WRAP_LINE_IN_PROGRESS_KEY) == true) return null

    val document = editor.document

    val prevLine = document.getLineNumber(offset) - 1

    val listItem = file.getListItemAtLineSafely(prevLine, document)
                   ?: file.getListItemAtLineSafely(prevLine - 1, document)
                   ?: return null
    val firstLine = document.getLineNumber(listItem.startOffset)

    val indentRange = document.getLineIndentRange(firstLine)
    return document.getText(indentRange)
  }

  private fun isInBlockQuoteOrCodeFence(editor: Editor, file: MarkdownFile): Boolean {
    val document = editor.document
    val prevLine = document.getLineNumber(editor.caretModel.offset) - 1
    if (prevLine == -1) return false

    val prevLineEnd = document.getLineEndOffset(prevLine)
    val beforeWhitespaceOffset = CharArrayUtil.shiftBackward(document.text, prevLineEnd - 1, " \t")
    if (beforeWhitespaceOffset == -1) return false

    return file.findElementAt(beforeWhitespaceOffset)
      ?.parentOfTypes(MarkdownBlockQuote::class, MarkdownCodeFence::class) != null
  }

  override fun isSuitableFor(language: Language?) = language is MarkdownLanguage
}
