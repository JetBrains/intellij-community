// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.highlighting.date

import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import com.intellij.refactoring.suggested.startOffset
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.psi.MarkdownRecursiveElementVisitor

internal class MarkdownTextsCollectingVisitor : MarkdownRecursiveElementVisitor() {
  val texts = mutableListOf<TextWithOffset>()

  override fun visitElement(element: PsiElement) {
    super.visitElement(element)

    if (element.elementType !== MarkdownTokenTypes.TEXT) {
      return
    }

    texts += element.text to element.startOffset
  }
}

typealias TextWithOffset = Pair<String, Int>
