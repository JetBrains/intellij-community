// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.containerview

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.TextFieldCompletionProvider
import com.jetbrains.python.debugger.PyDebugValue
import com.jetbrains.python.debugger.PyDebuggerException
import com.jetbrains.python.debugger.PyFrameAccessor
import com.jetbrains.python.debugger.values.DataFrameDebugValue

class PyDataViewerCompletionProvider(private val frameAccessor: PyFrameAccessor) : TextFieldCompletionProvider() {
  override fun addCompletionVariants(text: String, offset: Int, prefix: String, result: CompletionResultSet) {
    val values = dataFrameValues.sortedBy { obj: PyDebugValue -> obj.name }
    for (i in values.indices) {
      val value = values[i]
      val element = LookupElementBuilder.create(value.name).withTypeText(value.type, true)
      result.addElement(PrioritizedLookupElement.withPriority(element, -i.toDouble()))
    }
  }

  private val dataFrameValues: List<PyDebugValue>
    get() = try {
      val pythonVariables = frameAccessor.loadFrame(null) ?: return emptyList()
      buildList {
        for (i in 0 until pythonVariables.size()) {
          val dataFrameValue = pythonVariables.getValue(i) as? DataFrameDebugValue ?: continue
          add(dataFrameValue)
        }
      }
    } catch (e: PyDebuggerException) {
      thisLogger().error("Error loading frame variables for completion provider: ${e.message}")
      emptyList()
    }
}