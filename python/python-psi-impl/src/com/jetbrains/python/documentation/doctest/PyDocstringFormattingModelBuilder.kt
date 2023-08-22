package com.jetbrains.python.documentation.doctest

import com.intellij.formatting.*
import com.intellij.psi.PsiElement
import com.intellij.psi.formatter.DocumentBasedFormattingModel
import com.intellij.psi.formatter.common.AbstractBlock

class PyDocstringFormattingModelBuilder : CustomFormattingModelBuilder {
  override fun isEngagedToFormat(context: PsiElement): Boolean =
    context.containingFile?.language == PyDocstringLanguageDialect.getInstance()

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