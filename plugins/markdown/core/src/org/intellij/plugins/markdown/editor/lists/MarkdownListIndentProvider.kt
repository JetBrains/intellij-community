package org.intellij.plugins.markdown.editor.lists

import com.intellij.codeInsight.editorActions.AutoHardWrapHandler
import com.intellij.lang.Language
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.impl.source.codeStyle.lineIndent.FormatterBasedLineIndentProvider
import com.intellij.psi.util.PsiEditorUtil
import com.intellij.psi.util.isAncestor
import com.intellij.psi.util.parentOfType
import org.intellij.plugins.markdown.editor.lists.ListUtils.getListItemAtLineSafely
import org.intellij.plugins.markdown.lang.MarkdownLanguage
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownBlockQuote
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile
import org.intellij.plugins.markdown.settings.MarkdownSettings
import org.intellij.plugins.markdown.util.MarkdownPsiUtil

/**
 * This is a helper class for the [MarkdownListEnterHandlerDelegate] to remove extra indentation from new list items, since
 * all the needed indentation for them is added in the MarkdownListEnterHandlerDelegate.
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
    if (editor.getUserData(AutoHardWrapHandler.AUTO_WRAP_LINE_IN_PROGRESS_KEY) == true) return null

    val document = editor.document
    val prevLine = document.getLineNumber(offset) - 1
    val listItem = file.getListItemAtLineSafely(prevLine, document)
                   ?: file.getListItemAtLineSafely(prevLine - 1, document)
                   ?: return null

    val blockQuote = MarkdownPsiUtil.findNonWhiteSpacePrevSibling(file, offset)?.parentOfType<MarkdownBlockQuote>()
    if (blockQuote != null && listItem.isAncestor(blockQuote)) {
      return null
    }

    return ""
  }

  override fun isSuitableFor(language: Language?) = language is MarkdownLanguage
}
