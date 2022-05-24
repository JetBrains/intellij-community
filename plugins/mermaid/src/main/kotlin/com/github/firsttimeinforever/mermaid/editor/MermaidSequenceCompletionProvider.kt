package com.github.firsttimeinforever.mermaid.editor

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.util.ProcessingContext

class MermaidSequenceCompletionProvider : MermaidLiveTemplateCompletionProvider() {
  private val keywords = listOf("loop", "alt", "opt", "par", "rect")
  
  override fun addCompletions(
    parameters: CompletionParameters,
    context: ProcessingContext,
    result: CompletionResultSet
  ) {
    val project = parameters.originalFile.project
    result.addAllElements(keywords.map { createKeywordLookupElement(project, it) })
  }
}

class MermaidSequenceBranchCompletionProvider(private val branch: String) : MermaidLiveTemplateCompletionProvider() {
  override fun addCompletions(
    parameters: CompletionParameters,
    context: ProcessingContext,
    result: CompletionResultSet
  ) {
    val project = parameters.originalFile.project
    result.addElement(createKeywordLookupElement(project, branch))
  }
}
