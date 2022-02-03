// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.images

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.LookupElementRenderer
import com.intellij.openapi.util.TextRange
import com.intellij.util.ProcessingContext
import org.intellij.plugins.markdown.MarkdownIcons

class MarkdownImageTagCompletionProvider: CompletionProvider<CompletionParameters>() {
  private fun shouldComplete(parameters: CompletionParameters): Boolean {
    val offset = parameters.offset - 1
    val startOffset = when {
      offset < 0 -> 0
      else -> offset
    }
    val text = parameters.editor.document.getText(TextRange.create(startOffset, parameters.offset))
    return text == "<"
  }

  override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
    if (!shouldComplete(parameters)) {
      return
    }
    val element = LookupElementBuilder.create("img src=\"\">").withInsertHandler { context, _ ->
      context.editor.caretModel.moveCaretRelatively(-2, 0, false, false, false)
    }.withRenderer(object: LookupElementRenderer<LookupElement>() {
      override fun renderElement(element: LookupElement, presentation: LookupElementPresentation) {
        presentation.itemText = "<img src=\"...\">"
        presentation.icon = MarkdownIcons.ImageGutter
      }
    })
    result.addElement(element)
  }
}
