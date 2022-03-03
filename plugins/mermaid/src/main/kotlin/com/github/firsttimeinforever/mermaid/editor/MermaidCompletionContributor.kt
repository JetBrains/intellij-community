package com.github.firsttimeinforever.mermaid.editor

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder

class MermaidCompletionContributor: CompletionContributor() {
  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    super.fillCompletionVariants(parameters, result)
    result.addAllElements(listOf(
      LookupElementBuilder.create("pie"),
      LookupElementBuilder.create("showData"),
      LookupElementBuilder.create("title")
    ))
  }
}
