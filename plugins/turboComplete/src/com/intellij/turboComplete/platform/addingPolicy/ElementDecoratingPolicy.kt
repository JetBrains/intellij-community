package com.intellij.turboComplete.platform.addingPolicy

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.addingPolicy.ElementsAddingPolicy
import com.intellij.codeInsight.lookup.LookupElement

abstract class ElementDecoratingPolicy(protected val base: ElementsAddingPolicy) : ElementsAddingPolicy {
  override fun addElement(result: CompletionResultSet, element: LookupElement) {
    decorate(element)
    base.addElement(result, element)
  }

  override fun addAllElements(result: CompletionResultSet, elements: MutableIterable<LookupElement>) {
    elements.forEach { decorate(it) }
    base.addAllElements(result, elements)
  }

  override fun onActivate(result: CompletionResultSet) = base.onActivate(result)

  override fun onDeactivate(result: CompletionResultSet) = base.onDeactivate(result)

  abstract fun decorate(element: LookupElement)

  override fun onResultStop(result: CompletionResultSet) = base.onResultStop(result)
}