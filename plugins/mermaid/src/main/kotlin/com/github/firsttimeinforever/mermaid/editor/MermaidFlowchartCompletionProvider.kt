package com.github.firsttimeinforever.mermaid.editor

import com.github.firsttimeinforever.mermaid.lang.psi.impl.MermaidSubgraphStatementImpl
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.util.ProcessingContext

class MermaidFlowchartCompletionProvider : MermaidLiveTemplateCompletionProvider() {
  override fun addCompletions(
    parameters: CompletionParameters,
    context: ProcessingContext,
    result: CompletionResultSet
  ) {
    val project = parameters.originalFile.project
    result.addElement(createKeywordLookupElement(project, "subgraph"))

    var psiElement = parameters.position
    while (psiElement.parent != null) {
      psiElement = psiElement.parent
      if (psiElement is MermaidSubgraphStatementImpl) {
        result.addElement(LookupElementBuilder.create("direction"))
        break
      }
    }
  }
}

class MermaidFlowchartDirectionCompletionProvider : CompletionProvider<CompletionParameters>() {
  private val directions = listOf("LR", "RL", "TB", "BT", "TD", "BR", "<", ">", "^", "v")

  override fun addCompletions(
    parameters: CompletionParameters,
    context: ProcessingContext,
    result: CompletionResultSet
  ) {
    result.addAllElements(directions.map { LookupElementBuilder.create(it) })
  }
}
