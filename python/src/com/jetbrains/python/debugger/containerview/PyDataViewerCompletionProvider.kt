// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.containerview

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.TextFieldCompletionProvider
import com.jetbrains.python.debugger.PyDebugValue
import com.jetbrains.python.debugger.PyFrameAccessor

class PyDataViewerCompletionProvider(private val frameAccessor: PyFrameAccessor) : TextFieldCompletionProvider() {
  override fun addCompletionVariants(text: String, offset: Int, prefix: String, result: CompletionResultSet) {
    val values = availableValues.sortedBy { obj: PyDebugValue -> obj.name }
    for (i in values.indices) {
      val value = values[i]
      val element = LookupElementBuilder.create(value.name).withTypeText(value.type, true)
      result.addElement(PrioritizedLookupElement.withPriority(element, -i.toDouble()))
    }
  }

  private val availableValues: List<PyDebugValue>
    get() {
      val values: MutableList<PyDebugValue> = ArrayList()
      try {
        val list = frameAccessor.loadFrame(null) ?: return values
        for (i in 0 until list.size()) {
          val value = list.getValue(i) as PyDebugValue
          val type = value.type
          if (DataViewStrategy.getStrategy(type) != null) {
            values.add(value)
          }
        }
      }
      catch (e: Exception) {
        thisLogger().error(e)
      }
      return values
    }
}