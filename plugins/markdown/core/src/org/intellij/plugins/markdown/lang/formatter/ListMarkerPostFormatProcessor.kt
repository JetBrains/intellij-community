// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.lang.formatter

import com.intellij.codeInsight.CodeInsightUtilCore
import com.intellij.formatting.FormatterTagHandler
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor
import com.intellij.psi.util.PsiUtilCore
import com.intellij.psi.util.elementType
import org.intellij.plugins.markdown.editor.lists.ListUtils.items
import org.intellij.plugins.markdown.lang.MarkdownLanguage
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.formatter.settings.MarkdownCustomCodeStyleSettings
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownList

/**
 * Replaces the bullet of an unordered list item and the delimiter of an ordered list item
 * with the values of [MarkdownCustomCodeStyleSettings.unorderedListMarker] and [MarkdownCustomCodeStyleSettings.orderedListMarker].
 *
 * The processor changes all items of each list that intersects the range, so a list keeps one marker.
 * Two adjacent lists that differ only in the marker become one list.
 * This changes the rendered result: a blank line between the two lists makes the merged list loose,
 * and the numbers of a merged ordered list continue, such as `1. 2. 1) 2)` that renders as 1, 2, 3, 4.
 * The processor keeps the markers of a list when a change makes a marker line a thematic break, such as `+ - -`.
 * The processor keeps the markers of a list when one of its changes is in a range that a formatter tag disables.
 */
internal class ListMarkerPostFormatProcessor : PostFormatProcessor {
  override fun processElement(source: PsiElement, settings: CodeStyleSettings): PsiElement {
    val file = source.containingFile as? MarkdownFile ?: return source
    val range = source.textRange
    processText(file, range, settings)
    if (source.isValid) {
      return source
    }
    // The marker change keeps all offsets, so the reparsed element has the same range,
    // or it is a part of an element that the merge of adjacent lists makes
    return CodeInsightUtilCore.findElementInRange(file, range.startOffset, range.endOffset, source.javaClass, MarkdownLanguage.INSTANCE)
           ?: generateSequence(PsiUtilCore.getElementAtOffset(file, range.startOffset)) { it.parent }
             .takeWhile { it !is PsiFile }
             .firstOrNull { source.javaClass.isInstance(it) && it.textRange.contains(range) }
           ?: file
  }

  override fun processText(source: PsiFile, rangeToReformat: TextRange, settings: CodeStyleSettings): TextRange {
    if (source !is MarkdownFile) {
      return rangeToReformat
    }
    val custom = settings.getCustomSettings(MarkdownCustomCodeStyleSettings::class.java)
    val bullet = custom.unorderedListMarker.bullet
    val delimiter = custom.orderedListMarker.delimiter
    if (bullet == null && delimiter == null) {
      return rangeToReformat
    }
    val document = source.viewProvider.document ?: return rangeToReformat
    val documentManager = PsiDocumentManager.getInstance(source.project)
    documentManager.commitDocument(document)

    val lists = SyntaxTraverser.psiTraverser(source)
      .filter(MarkdownList::class.java)
      .filter { it.textRange.intersectsStrict(rangeToReformat) }
      .toList()
    val replacementsByList = lists
      .associateWith { list -> collectReplacements(list, bullet, delimiter) }
      .filterValues { replacements -> replacements.isNotEmpty() }
      .toMutableMap()
    if (replacementsByList.isEmpty()) {
      return rangeToReformat
    }
    if (settings.FORMATTER_TAGS_ENABLED) {
      val enabledRanges = FormatterTagHandler(settings).getEnabledRanges(source.node, source.textRange)
      replacementsByList.values.removeIf { replacements -> !replacements.all { isEnabled(it.offset, enabledRanges) } }
    }
    val markerStarts = lists.flatMap { list -> list.items.mapNotNull { it.markerElement?.let(::markerStart) } }.toSet()
    dropListsThatCreateThematicBreaks(replacementsByList, markerStarts, document)

    val replacements = replacementsByList.values.flatten()
    if (replacements.isEmpty()) {
      return rangeToReformat
    }
    for (replacement in replacements) {
      document.replaceString(replacement.offset, replacement.offset + 1, replacement.char.toString())
    }
    documentManager.commitDocument(document)
    return rangeToReformat
  }

  private fun collectReplacements(list: MarkdownList, bullet: Char?, delimiter: Char?): List<Replacement> {
    return list.items.mapNotNull { item ->
      val marker = item.markerElement ?: return@mapNotNull null
      val text = marker.text
      val (index, expected) = when (marker.elementType) {
        MarkdownTokenTypes.LIST_BULLET -> text.indexOfFirst { !it.isWhitespace() } to bullet
        MarkdownTokenTypes.LIST_NUMBER -> text.indexOfLast { it == '.' || it == ')' } to delimiter
        else -> return@mapNotNull null
      }
      if (expected == null || index < 0 || text[index] == expected) {
        return@mapNotNull null
      }
      Replacement(marker.textRange.startOffset + index, expected)
    }
  }

  private fun isEnabled(offset: Int, enabledRanges: List<TextRange>): Boolean {
    return enabledRanges.any { it.contains(offset) }
  }

  private fun markerStart(marker: PsiElement): Int? {
    val index = marker.text.indexOfFirst { !it.isWhitespace() }
    return if (index < 0) null else marker.textRange.startOffset + index
  }

  /**
   * Drops all lists with a change on a line that becomes a thematic break, such as `+ - -` with the dash marker.
   * A dropped list restores its markers on other lines too, so the check repeats.
   */
  private fun dropListsThatCreateThematicBreaks(
    replacementsByList: MutableMap<MarkdownList, List<Replacement>>,
    markerStarts: Set<Int>,
    document: Document,
  ) {
    val text = document.charsSequence
    val markerStartsByLine = markerStarts.groupBy(document::getLineNumber)
    while (true) {
      val charsByOffset = replacementsByList.values.flatten().associate { it.offset to it.char }
      val brokenLines = charsByOffset.keys.map(document::getLineNumber).toSet().filter { line ->
        val end = document.getLineEndOffset(line)
        markerStartsByLine[line].orEmpty().any { start ->
          THEMATIC_BREAK.matches((start until end).map { charsByOffset[it] ?: text[it] }.joinToString(""))
        }
      }
      if (brokenLines.isEmpty()) {
        return
      }
      replacementsByList.values.removeIf { replacements -> replacements.any { document.getLineNumber(it.offset) in brokenLines } }
    }
  }

  private data class Replacement(val offset: Int, val char: Char)
}

private val THEMATIC_BREAK = Regex("""([-*_])(?:[ \t]*\1){2,}[ \t]*""")
