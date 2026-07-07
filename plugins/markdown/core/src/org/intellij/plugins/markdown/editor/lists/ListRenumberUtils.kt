// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.lists

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiFile
import com.intellij.psi.util.elementType
import com.intellij.psi.util.endOffset
import com.intellij.psi.util.startOffset
import org.intellij.plugins.markdown.editor.lists.ListUtils.getLineIndentInnerSpacesLength
import org.intellij.plugins.markdown.editor.lists.ListUtils.items
import org.intellij.plugins.markdown.editor.lists.ListUtils.normalizedMarker
import org.intellij.plugins.markdown.editor.lists.ListUtils.sublists
import org.intellij.plugins.markdown.editor.lists.Replacement.Companion.replaceAllInBulk
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownList
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownListItem
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownListNumber
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object ListRenumberUtils {
  fun MarkdownList.renumberInBulk(document: Document, recursive: Boolean, restart: Boolean, inWriteAction: Boolean = true, sequentially: Boolean = true) {
    val replacementList = collectReplacements(document, recursive, restart, sequentially).toList()
    if (inWriteAction) {
      runWriteAction {
        replacementList.replaceAllInBulk(document)
      }
    } else {
      replacementList.replaceAllInBulk(document)
    }
  }


  private fun MarkdownList.collectReplacements(document: Document, recursive: Boolean, restart: Boolean, sequentially: Boolean = true): Sequence<Replacement> {
    val line = document.getLineNumber(this.startOffset)
    val firstIndent = document.getLineIndentInnerSpacesLength(line, containingFile)!!
    return renumberingReplacements(
      this,
      ListItemIndentInfo(firstIndent, 0),
      document,
      containingFile,
      recursive,
      restart,
      sequentially
    )
  }

  fun MarkdownListItem.obtainMarkerNumber(): Int? {
    return (markerElement as? MarkdownListNumber)?.number
  }

  private fun renumberingReplacements(list: MarkdownList, listIndentInfo: ListItemIndentInfo, document: Document, file: PsiFile, recursive: Boolean, restart: Boolean, sequentially: Boolean = true): Sequence<Replacement> {
    val firstItem = list.items.first()

    val start = when {
      list.elementType != MarkdownElementTypes.ORDERED_LIST -> null
      restart -> 1
      else -> firstItem.obtainMarkerNumber() ?: error("Failed to obtain first item number")
    }

    val markerFlavor = firstItem.normalizedMarker.trim().last() // . and ) for ordered, + - * for unordered

    var sequence = sequenceOf<Replacement>()
    for ((i, item) in list.items.withIndex()) {
      val oldInfo = ListItemInfo(item, document)

      val marker = when (start) {
        null -> "$markerFlavor "
        else -> "${if (sequentially) start + i else 1}$markerFlavor "
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
