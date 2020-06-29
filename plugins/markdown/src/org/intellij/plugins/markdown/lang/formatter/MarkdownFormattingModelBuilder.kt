// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang.formatter

import com.intellij.formatting.FormattingModel
import com.intellij.formatting.FormattingModelBuilder
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.formatter.DocumentBasedFormattingModel
import org.intellij.plugins.markdown.lang.formatter.blocks.MarkdownFormattingBlock

internal class MarkdownFormattingModelBuilder : FormattingModelBuilder {
  override fun createModel(element: PsiElement, settings: CodeStyleSettings): FormattingModel {
    val block = MarkdownFormattingBlock(settings, MarkdownSpacingBuilder.get(settings), element.node)

    return DocumentBasedFormattingModel(block, settings, element.containingFile)
  }
}