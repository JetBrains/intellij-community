// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.lang.psi.symbol

import com.intellij.find.usages.api.PsiUsage
import com.intellij.model.Pointer
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager

@Suppress("UnstableApiUsage")
class MermaidPsiUsage(
  override val file: PsiFile,
  override val range: TextRange,
  override val declaration: Boolean
) : PsiUsage {
  override fun createPointer(): Pointer<out PsiUsage> {
    return MermaidPsiUsagePointer(file, range, declaration)
  }

  private class MermaidPsiUsagePointer(
    file: PsiFile,
    range: TextRange,
    private val declaration: Boolean
  ) : Pointer<MermaidPsiUsage> {
    private val rangePointer = SmartPointerManager.getInstance(file.project).createSmartPsiFileRangePointer(file, range)

    override fun dereference(): MermaidPsiUsage? {
      val file = rangePointer.element ?: return null
      val range = rangePointer.range?.let(TextRange::create) ?: return null
      return MermaidPsiUsage(file, range, declaration)
    }
  }

  companion object {
    fun create(element: PsiElement, rangeInElement: TextRange, declaration: Boolean = false): MermaidPsiUsage {
      if (element is PsiFile) {
        return MermaidPsiUsage(element, rangeInElement, declaration)
      }
      return MermaidPsiUsage(
        element.containingFile,
        rangeInElement.shiftRight(element.textRange.startOffset),
        declaration
      )
    }

    fun create(reference: PsiSymbolReference): MermaidPsiUsage {
      return create(reference.element, reference.rangeInElement, declaration = false)
    }
  }
}
