package com.intellij.turboComplete.platform.addingPolicy

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.addingPolicy.ElementsAddingPolicy
import com.intellij.codeInsight.lookup.LookupElement

abstract class ElementDecoratingPolicy(private val base: ElementsAddingPolicy) : ElementsAddingPolicy by base {
  override fun addElement(result: CompletionResultSet, element: LookupElement) {
    decorate(element)
    base.addElement(result, element)
  }

  override fun addAllElements(result: CompletionResultSet, elements: Iterable<LookupElement>) {
    elements.forEach { decorate(it) }
    base.addAllElements(result, elements)
  }

  abstract fun decorate(element: LookupElement)
}