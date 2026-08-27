package org.intellij.plugins.markdown.lang.formatter

import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor
import org.intellij.plugins.markdown.editor.tables.TableFormattingUtils
import org.intellij.plugins.markdown.lang.formatter.settings.MarkdownCustomCodeStyleSettings
import org.intellij.plugins.markdown.lang.formatter.settings.TableStyle
import org.intellij.plugins.markdown.lang.isMarkdownLanguage
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTable

internal class TablePostFormatProcessor : PostFormatProcessor {
  override fun processElement(source: PsiElement, settings: CodeStyleSettings): PsiElement {
    if (!source.language.isMarkdownLanguage() || !shouldReformat(settings)) {
      return source
    }
    if (source !is MarkdownTable && source !is MarkdownFile) {
      return source
    }

    if (source is MarkdownFile) {
      processText(source, source.textRange, settings)
      return source
    }

    val document = obtainDocument(source) ?: return source
    PsiDocumentManager.getInstance(source.project).commitDocument(document)
    val tableStyle = settings.getCustomSettings(MarkdownCustomCodeStyleSettings::class.java).tableStyle
    processTable(source as MarkdownTable, document, tableStyle)
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
    val tableStyle = settings.getCustomSettings(MarkdownCustomCodeStyleSettings::class.java).tableStyle
    SyntaxTraverser.revPsiTraverser()
      .withRoot(source)
      .asSequence()
      .filterIsInstance<MarkdownTable>()
      .filter { rangeToReformat.intersects(it.textRange) }
      .forEach { table ->
        processTable(table, document, tableStyle)
        PsiDocumentManager.getInstance(source.project).commitDocument(document)
      }
    return source.textRange
  }

  private fun shouldReformat(settings: CodeStyleSettings): Boolean {
    return settings.getCustomSettings(MarkdownCustomCodeStyleSettings::class.java).FORMAT_TABLES
  }

  private fun processTable(table: MarkdownTable, document: Document, tableStyle: TableStyle) {
    TableFormattingUtils.reformatAllColumns(table, document, tableStyle, trimToMaxContent = true)
  }

  private fun obtainDocument(element: PsiElement): Document? {
    val viewProvider = when (element) {
      is PsiFile -> element.viewProvider
      else -> element.containingFile?.viewProvider
    }
    return viewProvider?.document
  }
}
