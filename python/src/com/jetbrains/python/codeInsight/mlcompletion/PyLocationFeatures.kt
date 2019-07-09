// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.mlcompletion

import com.intellij.codeInsight.completion.ml.ContextFeatureProvider
import com.intellij.codeInsight.completion.ml.MLFeatureValue
import com.intellij.codeInsight.lookup.Lookup

class PyLocationFeatures : ContextFeatureProvider {
  override fun getName(): String = "python"

  override fun calculateFeatures(lookup: Lookup): Map<String, MLFeatureValue> {
    val result = HashMap<String, MLFeatureValue>()
    val locationPsi = lookup.psiElement ?: return result

    result["is_directly_in_arguments_context"] = MLFeatureValue.binary(PyCompletionFeatures.isDirectlyInArgumentsContext(locationPsi))
    result["is_in_condition"] = MLFeatureValue.binary(PyCompletionFeatures.isInCondition(locationPsi))
    result["is_after_if_statement_without_else_branch"] = MLFeatureValue.binary(PyCompletionFeatures.isAfterIfStatementWithoutElseBranch(locationPsi))
    result["is_in_for_statement"] = MLFeatureValue.binary(PyCompletionFeatures.isInForStatement(locationPsi))

    val neighboursKws = PyCompletionFeatures.getPrevNeighboursKeywordIds(locationPsi)
    if (neighboursKws.size > 0) result["prev_neighbour_keyword_1"] = MLFeatureValue.float(neighboursKws[0])
    if (neighboursKws.size > 1) result["prev_neighbour_keyword_2"] = MLFeatureValue.float(neighboursKws[1])

    val sameLineKws = PyCompletionFeatures.getPrevKeywordsIdsInTheSameLine(locationPsi)
    if (sameLineKws.size > 0) result["prev_same_line_keyword_1"] = MLFeatureValue.float(sameLineKws[0])
    if (sameLineKws.size > 1) result["prev_same_line_keyword_2"] = MLFeatureValue.float(sameLineKws[1])

    val sameColumnKws = PyCompletionFeatures.getPrevKeywordsIdsInTheSameColumn(locationPsi)
    if (sameColumnKws.size > 0) result["prev_same_column_keyword_1"] = MLFeatureValue.float(sameColumnKws[0])
    if (sameColumnKws.size > 1) result["prev_same_column_keyword_2"] = MLFeatureValue.float(sameColumnKws[1])

    return result
  }
}