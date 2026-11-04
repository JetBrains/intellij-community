// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.backend.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.psi.util.PsiTreeUtil
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownAtPath

internal class MarkdownAtPathCompletionContributor : MarkdownPathCompletionContributor() {
  override fun findPath(parameters: CompletionParameters): PathCompletionContext? {
    val element = PsiTreeUtil.getParentOfType(findElementAtCaret(parameters), MarkdownAtPath::class.java) ?: return null
    val elementStart = element.textRange.startOffset
    val pathEnd = parameters.offset - elementStart
    if (pathEnd < 1 || pathEnd > element.textLength) return null
    return PathCompletionContext(
      pathPrefix = element.text.substring(1, pathEnd),
      startOffset = elementStart,
      insertionPrefix = "@",
    )
  }
}
