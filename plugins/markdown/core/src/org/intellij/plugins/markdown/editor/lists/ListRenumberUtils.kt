// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.lists

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiFile
import com.intellij.psi.util.elementType
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import org.intellij.plugins.markdown.editor.lists.ListUtils.getLineIndentSpaces
import org.intellij.plugins.markdown.editor.lists.ListUtils.items
import org.intellij.plugins.markdown.editor.lists.ListUtils.normalizedMarker
import org.intellij.plugins.markdown.editor.lists.ListUtils.sublists
import org.intellij.plugins.markdown.editor.lists.Replacement.Companion.replaceAllInBulk
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownList

internal object ListRenumberUtils {
  fun MarkdownList.renumberInBulk(document: Document, recursive: Boolean, restart: Boolean) {
    val replacementList = renumber(document, recursive, restart)
    runWriteAction {
      replacementList.replaceAllInBulk(document)
    }
  }

  fun MarkdownList.renumber(document: Document, recursive: Boolean, restart: Boolean): List<Replacement> {
    val line = document.getLineNumber(this.startOffset)
    val firstIndent = document.getLineIndentSpaces(line, containingFile)!!.length
    return renumberingReplacements(this, ListItemIndentInfo(firstIndent, 0), document, containingFile, recursive, restart).toList()
  }


  private fun renumberingReplacements(list: MarkdownList,
                                      listIndentInfo: ListItemIndentInfo,
                                      document: Document,
                                      file: PsiFile,
                                      recursive: Boolean,
                                      restart: Boolean): Sequence<Replacement> {
    val firstMarker = list.items.first().normalizedMarker

    val start = when {
      list.elementType != MarkdownElementTypes.ORDERED_LIST -> null
      restart -> 1
      else -> firstMarker.trim().dropLast(1).toInt()
    }

    val markerFlavor = firstMarker.trim().last() // . and ) for ordered, + - * for unordered

    var sequence = sequenceOf<Replacement>()
    for ((i, item) in list.items.withIndex()) {
      val oldInfo = ListItemInfo(item, document)

      val marker = when (start) {
        null -> "$markerFlavor "
        else -> "${start + i}$markerFlavor "
      }

      val newIndentInfo = listIndentInfo.subItem(marker.length)

      val indentReplacement = oldInfo.indentInfo.changeLineIndent(oldInfo.lines.first, newIndentInfo.indent, document, file)!!
      val indentWithMarkerReplacement = Replacement(indentReplacement.range.union(item.markerElement!!.textRange),
                                                    indentReplacement.str + marker)
      sequence += indentWithMarkerReplacement

      var prevLine = oldInfo.lines.first

      if (recursive) for (sublist in item.sublists) {
        val sublistFirstLine = document.getLineNumber(sublist.startOffset)
        val sublistLastLine = document.getLineNumber(sublist.endOffset)

        // indents of non-list content should also be adjusted
        val preContentLines = prevLine + 1 until sublistFirstLine
        sequence += oldInfo.indentInfo
          .changeContentLinesIndent(preContentLines, newIndentInfo.subItemIndent(), document, file)

        sequence += renumberingReplacements(sublist, newIndentInfo, document, file, true, true)
        prevLine = sublistLastLine
      }

      val postContentLines = (prevLine + 1)..oldInfo.lines.last
      sequence += oldInfo.indentInfo.changeContentLinesIndent(postContentLines, newIndentInfo.subItemIndent(), document, file)
    }
    return sequence
  }

  private fun ListItemIndentInfo.changeContentLinesIndent(lines: IntRange,
                                                          newIndent: Int,
                                                          document: Document,
                                                          file: PsiFile): List<Replacement> =
    lines.mapNotNull { line ->
      subItem(0).changeLineIndent(line, newIndent, document, file)
    }

  private operator fun CharSequence.plus(other: CharSequence) =
    StringBuilder(this.length + other.length).append(this).append(other)
}
