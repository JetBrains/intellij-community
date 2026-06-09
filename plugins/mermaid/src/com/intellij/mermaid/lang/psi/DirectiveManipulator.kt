// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.lang.psi

import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator

class DirectiveManipulator : AbstractElementManipulator<MermaidDirectiveValue>() {
  override fun handleContentChange(
    element: MermaidDirectiveValue,
    range: TextRange,
    newContent: String?
  ): MermaidDirectiveValue {
    val oldText = element.text
    val newText = oldText.substring(0, range.startOffset) + newContent + oldText.substring(range.endOffset)
    val newElement = checkNotNull(MermaidElementFactory.createDirectiveValue(element.project, newText))
    return element.replace(newElement) as MermaidDirectiveValue
  }
}
