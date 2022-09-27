package com.github.firsttimeinforever.mermaid.lang.formatter

import com.intellij.formatting.FormattingContext
import com.intellij.formatting.FormattingModel
import com.intellij.formatting.FormattingModelBuilder
import com.intellij.psi.formatter.DocumentBasedFormattingModel

internal class MermaidFormattingModelBuilder : FormattingModelBuilder {
  override fun createModel(formattingContext: FormattingContext): FormattingModel {
    val settings = formattingContext.codeStyleSettings
    val block = MermaidFormattingBlock(formattingContext.node, settings, MermaidSpacingBuilder.get(settings))

    return DocumentBasedFormattingModel(block, settings, formattingContext.containingFile)
  }
}
