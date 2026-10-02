// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.documentation.doctest

import com.intellij.formatting.Block
import com.intellij.formatting.CustomFormattingModelBuilder
import com.intellij.formatting.FormattingContext
import com.intellij.formatting.FormattingModel
import com.intellij.formatting.Spacing
import com.intellij.psi.PsiElement
import com.intellij.psi.formatter.DocumentBasedFormattingModel
import com.intellij.psi.formatter.common.AbstractBlock
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PyDocstringFormattingModelBuilder : CustomFormattingModelBuilder {
  override fun isEngagedToFormat(context: PsiElement): Boolean =
    context.containingFile?.language == PyDoctestLanguageDialect.getInstance()

  override fun createModel(formattingContext: FormattingContext): FormattingModel {
    return DocumentBasedFormattingModel(PyDocstringBlock(formattingContext),
                                        formattingContext.codeStyleSettings,
                                        formattingContext.containingFile)
  }

  class PyDocstringBlock(formattingContext: FormattingContext) : AbstractBlock(formattingContext.node, null, null) {
    override fun getSpacing(child1: Block?, child2: Block): Spacing? = null

    override fun isLeaf(): Boolean = true

    override fun buildChildren(): List<Block> = emptyList()
  }
}