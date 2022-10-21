package org.intellij.plugins.markdown.lang.formatter

import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor
import com.intellij.psi.util.siblings
import org.intellij.plugins.markdown.editor.tables.TableFormattingUtils
import org.intellij.plugins.markdown.lang.MarkdownLanguageUtils.isMarkdownLanguage
import org.intellij.plugins.markdown.lang.formatter.settings.MarkdownCustomCodeStyleSettings
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTable

internal class TablePostFormatProcessor: PostFormatProcessor {
  override fun processElement(source: PsiElement, settings: CodeStyleSettings): PsiElement {
    if (!source.language.isMarkdownLanguage() || source !is MarkdownTable|| !shouldReformat(settings)) {
      return source
    }
    val document = obtainDocument(source) ?: return source
    PsiDocumentManager.getInstance(source.project).commitDocument(document)
    processTable(source, document)
    // Reformatting table does not invalidate the root table element,
    // so just return original element
    return source
  }

  override fun processText(source: PsiFile, rangeToReformat: TextRange, settings: CodeStyleSettings): TextRange {
    if (source !is MarkdownFile || !shouldReformat(settings)) {
      return rangeToReformat
    }
    val document = obtainDocument(source) ?: return rangeToReformat
    PsiDocumentManager.getInstance(source.project).commitDocument(document)
    val elements = source.firstChild?.lastChild?.siblings(forward = false, withSelf = true).orEmpty()
    val tables = elements.filterIsInstance<MarkdownTable>()
    for (table in tables) {
      if (rangeToReformat.intersects(table.textRange)) {
        processTable(table, document)
        PsiDocumentManager.getInstance(source.project).commitDocument(document)
      }
    }
    return source.textRange
  }

  private fun shouldReformat(settings: CodeStyleSettings): Boolean {
    val custom = settings.getCustomSettings(MarkdownCustomCodeStyleSettings::class.java)
    return custom.FORMAT_TABLES
  }

  private fun processTable(table: MarkdownTable, document: Document) {
    TableFormattingUtils.reformatAllColumns(table, document, trimToMaxContent = true)
  }

  private fun obtainDocument(element: PsiElement): Document? {
    val viewProvider = when (element) {
      is PsiFile -> element.viewProvider
      else -> element.containingFile?.viewProvider
    }
    return viewProvider?.document
  }
}
