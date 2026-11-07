// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.psiScip

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace

/** The SCIP ranges of the offsets of one file text. The document collectors share it. */
internal class LineMap(text: CharSequence) {
  private val lineStarts = lineStarts(text)

  fun range(textRange: TextRange): ScipRange {
    val startLine = lineOf(textRange.startOffset)
    val endLine = lineOf(textRange.endOffset)
    return ScipRange(startLine, textRange.startOffset - lineStarts[startLine], endLine, textRange.endOffset - lineStarts[endLine])
  }

  /**
   * The range from the first child after the doc comment, and after the whitespace and comments that follow it, to the end of [declaration].
   * An empty child, such as an empty modifier list, does not start the range.
   */
  fun enclosingRange(declaration: PsiElement): ScipRange {
    val end = declaration.textRange.endOffset
    var child = declaration.firstChild
    while (child != null && (child is PsiComment || child is PsiWhiteSpace || child.textLength == 0)) {
      child = child.nextSibling
    }
    val start = child?.textRange?.startOffset ?: declaration.textRange.startOffset
    return range(TextRange(start, end))
  }

  private fun lineOf(offset: Int): Int {
    val index = lineStarts.binarySearch(offset)
    return if (index >= 0) index else -index - 2
  }
}

private fun lineStarts(text: CharSequence): IntArray {
  val starts = ArrayList<Int>()
  starts.add(0)
  for (i in text.indices) {
    if (text[i] == '\n') {
      starts.add(i + 1)
    }
  }
  return starts.toIntArray()
}
