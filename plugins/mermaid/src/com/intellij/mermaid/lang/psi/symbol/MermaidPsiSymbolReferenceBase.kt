// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.lang.psi.symbol

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

@Suppress("UnstableApiUsage")
abstract class MermaidPsiSymbolReferenceBase(
  private val element: PsiElement,
  private val rangeInElement: TextRange? = null
) : MermaidPsiSymbolReference {
  override fun getElement(): PsiElement {
    return element
  }

  override fun getRangeInElement(): TextRange {
    return rangeInElement ?: TextRange(0, element.textLength)
  }
}
