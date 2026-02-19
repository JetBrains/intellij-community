// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl
import com.jetbrains.python.debugger.PyDebugValue
import com.jetbrains.python.debugger.values.DataFrameDebugValue
import com.jetbrains.python.debugger.values.completeDataFrameColumns


interface PyRuntimeCompletionRetrievalService {
  /**
   * This function checks additional conditions before calling completion
   * @return true - if all checks pass / false - if not
   */
  fun canComplete(parameters: CompletionParameters): Boolean

  fun extractItemsForCompletion(
    result: Pair<XValueNodeImpl, List<PyQualifiedExpressionItem>>?,
    candidate: PyObjectCandidate, completionType: CompletionType,
  ): CompletionResultData? {
    val (node, listOfCalls) = result ?: return null
    val debugValue = node.valueContainer
    if (debugValue is DataFrameDebugValue) {
      val dfColumns = completeDataFrameColumns(debugValue.treeColumns, listOfCalls.map { it.pyQualifiedName }) ?: return null
      return CompletionResultData(dfColumns, PyRuntimeCompletionType.DATA_FRAME_COLUMNS, getReferenceExpression(debugValue, node.name))
    }
    if (completionType == CompletionType.BASIC) return null
    PyRuntimeCompletionUtils.computeChildrenIfNeeded(node)
    if ((debugValue as PyDebugValue).qualifiedType == "builtins.dict") {
      return CompletionResultData(node.loadedChildren.mapNotNull { (it as? XValueNodeImpl)?.name }.toSet(),
                                  PyRuntimeCompletionType.DICT_KEYS, getReferenceExpression(debugValue, node.name))
    }
    return CompletionResultData(node.loadedChildren.mapNotNull { (it as? XValueNodeImpl)?.name }.toSet(),
                                PyRuntimeCompletionType.DYNAMIC_CLASS, getReferenceExpression(debugValue, node.name))
  }

  /**
   * This function returns string presentation of reference expression.
   *
   * An example:
   * ```
   * class B:
   *   d = {
   *      "key1" : df
   *   }
   * ```
   * For completion inside `B.d['key1'].<caret>` that corresponding to `df` PyDebugValue returns "B.d['key1']"
   */
  private fun getReferenceExpression(pyDebugValue: PyDebugValue, nodeName: String?): String {
    nodeName ?: return ""
    val parent = pyDebugValue.parent ?: return pyDebugValue.name
    var referenceName: String = nodeName

    for (parentValue in generateSequence(parent, PyDebugValue::getParent)) {
      if (parentValue.qualifiedType in PyRuntimeCompletionUtils.typeToDelimiter.keys) {
        referenceName = "${parentValue.name}[$referenceName]"
      }
      referenceName = "${parentValue.name}.$referenceName"
    }
    return referenceName
  }
}