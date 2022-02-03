// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.lists

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.guessProjectForFile
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiUtilCore
import com.intellij.psi.util.parentOfType
import com.intellij.util.text.CharArrayUtil
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownList
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownListItem

internal object ListUtils {
  /** [offset] may be located inside the indent of the returned item, but not on a blank line */
  fun MarkdownFile.getListItemAt(offset: Int, document: Document) =
    getListItemAtLine(document.getLineNumber(offset), document)

  /** [lineNumber] should belong to a list item and not represent a blank line, otherwise returns `null` */
  fun MarkdownFile.getListItemAtLine(lineNumber: Int, document: Document): MarkdownListItem? {
    val lineStart = document.getLineStartOffset(lineNumber)
    val lineEnd = document.getLineEndOffset(lineNumber)
    val searchingOffset = CharArrayUtil.shiftBackward(document.charsSequence, lineStart, lineEnd - 1, " \t\n")

    if (searchingOffset < lineStart) {
      return null
    }

    val elementAtOffset = PsiUtilCore.getElementAtOffset(this, searchingOffset)
    return elementAtOffset.parentOfType(true)
  }

  /** If 0 <= [lineNumber] < [Document.getLineCount], equivalent to [getListItemAtLine].
   * Otherwise, returns null. */
  fun MarkdownFile.getListItemAtLineSafely(lineNumber: Int, document: Document) =
    if (lineNumber in 0 until document.lineCount)
      getListItemAtLine(lineNumber, document)
    else null


  fun Document.getLineIndentRange(lineNumber: Int): TextRange {
    val lineStart = getLineStartOffset(lineNumber)
    val nonWsStart = CharArrayUtil.shiftForward(charsSequence, lineStart, " \t")

    return TextRange.create(lineStart, nonWsStart)
  }

  /**
   * Returns the indent at line [lineNumber], with all tabs replaced with spaces.
   * Returns `null` if [file] is null and failed to guess it.
   */
  fun Document.getLineIndentSpaces(lineNumber: Int, file: PsiFile? = null): String? {
    val psiFile = file ?: run {
      val virtualFile = FileDocumentManager.getInstance().getFile(this) ?: return null
      val project = guessProjectForFile(virtualFile) ?: return null
      PsiDocumentManager.getInstance(project).getPsiFile(this) ?: return null
    }

    val tabSize = CodeStyle.getFacade(psiFile).tabSize
    val indentStr = getText(getLineIndentRange(lineNumber))
    return indentStr.replace("\t", " ".repeat(tabSize))
  }

  val MarkdownList.items get() = children.filterIsInstance<MarkdownListItem>()

  val MarkdownListItem.sublists get() = children.filterIsInstance<MarkdownList>()
  val MarkdownListItem.list get() = parent as MarkdownList

  /**
   * Returns a marker of the item with leading whitespaces trimmed, and with a single space in the end.
   */
  val MarkdownListItem.normalizedMarker: String
    get() = this.markerElement!!.text.trim().let { "$it " }
}
