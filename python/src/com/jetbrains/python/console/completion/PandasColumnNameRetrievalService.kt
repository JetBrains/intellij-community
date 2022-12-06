// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.console.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.jetbrains.python.debugger.values.DataFrameDebugValue
import com.jetbrains.python.debugger.values.completePandasDataFrameColumns


interface PandasColumnNameRetrievalService {
  /**
   *
   * This function checks additional condition before completion algorithm
   * @param parameters - CompletionParameters
   * @return true - if all checks pass / false - if not
   *
   */
  fun canComplete(parameters: CompletionParameters): Boolean

  /**
   *
   * This function return map of created DataFrame objects in environment
   * @param parameters - CompletionParameters
   * @return map, where key - a name of the variable in virtual file that references to the DataFrame object, value - DataFrameDebugValue
   *
   */
  fun getInformationFromRuntime(parameters: CompletionParameters): Map<String, DataFrameDebugValue>?

  /**
   *
   * This function return map of created DataFrame objects in environment
   * @param dataFrameObjects - map, where key - a name of the variable that references to the DataFrame object, value - DataFrameDebugValue
   * @param candidate - possible DataFrame candidate
   * @return set of names for completion
   *
   */
  fun getPandasColumns(dataFrameObjects: Map<String, DataFrameDebugValue>, candidate: PandasDataFrameCandidate): Set<String> {
    return dataFrameObjects[candidate.psiName]?.let { completePandasDataFrameColumns(it.treeColumns, candidate.columnsBefore) }
           ?: emptySet()
  }
}