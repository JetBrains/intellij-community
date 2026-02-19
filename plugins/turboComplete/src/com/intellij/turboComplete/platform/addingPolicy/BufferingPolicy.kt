package com.intellij.turboComplete.platform.addingPolicy

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.addingPolicy.ElementsAddingPolicy
import com.intellij.codeInsight.lookup.LookupElement

class BufferingPolicy : ElementsAddingPolicy.Default {
  private val buffer: MutableList<LookupElement> = mutableListOf()

  override fun onResultStop(result: CompletionResultSet) {
    flushBuffer(result)
  }

  override fun addElement(result: CompletionResultSet, element: LookupElement) {
    buffer.add(element)
  }

  override fun addAllElements(result: CompletionResultSet, elements: Iterable<LookupElement>) {
    buffer.addAll(elements)
  }

  override fun onDeactivate(result: CompletionResultSet) {
    flushBuffer(result)
  }

  private fun flushBuffer(result: CompletionResultSet) {
    result.addAllElements(buffer)
    buffer.clear()
  }
}