// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.containerview

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.TextFieldCompletionProvider
import com.jetbrains.python.debugger.PyDebugValue
import com.jetbrains.python.debugger.PyFrameAccessor

class PyDataViewerModel(
  val project: Project,
  val frameAccessor: PyFrameAccessor,
) {
  /**
   * Represents a formatting string used for specifying format.
   *
   * This field is used as a source of truth during switching between community and powerful tables.
   */
  var format: String = ""

  /**
   * Represents a slicing string used for slicing data in a viewer panel.
   * For example, np_array_3d[0] or df['column_1'].
   *
   * This field is used as a source of truth during switching between community and powerful tables.
   */
  var slicing: String = ""

  /**
   * This field is used as a source of truth during switching between community and powerful tables.
   */
  var isColored: Boolean = false

  var protectedColored: Boolean = PyDataView.isColoringEnabled(project)

  var originalVarName: @NlsSafe String? = null

  var modifiedVarName: String? = null

  var debugValue: PyDebugValue? = null

  var isModified: Boolean = false

  inner class PyDataViewerCompletionProvider : TextFieldCompletionProvider() {
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
}