// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.application.ex.ApplicationUtil
import com.intellij.openapi.progress.ProgressManager
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl
import com.jetbrains.python.debugger.state.PyRuntime
import com.jetbrains.python.debugger.values.DataFrameDebugValue
import com.jetbrains.python.debugger.values.completePandasDataFrameColumns
import java.util.concurrent.Callable

enum class PyRuntimeCompletionType {
  DYNAMIC_CLASS, DATA_FRAME_COLUMNS
}

/**
 * @param completionType - type of completion items to choose post-processing function in the future
 */
data class CompletionResultData(val setOfCompletionItems: Set<String>, val completionType: PyRuntimeCompletionType)

private fun postProcessingChildren(completionResultData: CompletionResultData,
                                   candidate: PyObjectCandidate,
                                   parameters: CompletionParameters): List<LookupElement> {
  return when (completionResultData.completionType) {
    PyRuntimeCompletionType.DATA_FRAME_COLUMNS -> {
      val project = parameters.editor.project ?: return emptyList()
      processDataFrameColumns(candidate.psiName,
                              completionResultData.setOfCompletionItems,
                              candidate.needValidatorCheck,
                              parameters.position,
                              project,
                              true)
    }
    PyRuntimeCompletionType.DYNAMIC_CLASS -> proceedPyValueChildrenNames(completionResultData.setOfCompletionItems, true)
  }
}

interface PyRuntimeCompletionRetrievalService {
  /**
   * This function checks additional conditions before calling completion
   * @return true - if all checks pass / false - if not
   */
  fun canComplete(parameters: CompletionParameters): Boolean

  fun extractItemsForCompletion(result: Pair<XValueNodeImpl, List<String>>?,
                                candidate: PyObjectCandidate): CompletionResultData? {
    val (node, listOfCalls) = result ?: return null
    val debugValue = node.valueContainer
    if (debugValue is DataFrameDebugValue) {
      val dfColumns = completePandasDataFrameColumns(debugValue.treeColumns, listOfCalls) ?: return null
      return CompletionResultData(dfColumns, PyRuntimeCompletionType.DATA_FRAME_COLUMNS)
    }
    return CompletionResultData(node.loadedChildren.mapNotNull { (it as? XValueNodeImpl)?.name }.toSet(),
                                PyRuntimeCompletionType.DYNAMIC_CLASS)
  }
}

fun createCompletionResultSet(retrievalService: PyRuntimeCompletionRetrievalService,
                              runtimeService: PyRuntime,
                              parameters: CompletionParameters): List<LookupElement> {
  if (!retrievalService.canComplete(parameters)) return emptyList()
  val project = parameters.editor.project ?: return emptyList()
  val treeNodeList = runtimeService.getGlobalPythonVariables(parameters.originalFile.virtualFile, project, parameters.editor)
                     ?: return emptyList()
  val pyObjectCandidates = getCompleteAttribute(parameters)

  return ApplicationUtil.runWithCheckCanceled(Callable {
    return@Callable pyObjectCandidates.flatMap { candidate ->
      getSetOfChildrenByListOfCall(getParentNodeByName(treeNodeList, candidate.psiName), candidate.pyQualifiedExpressionList)
        .let { retrievalService.extractItemsForCompletion(it, candidate) }
        ?.let { postProcessingChildren(it, candidate, parameters) }
      ?: emptyList()
    }
  }, ProgressManager.getInstance().progressIndicator)
}