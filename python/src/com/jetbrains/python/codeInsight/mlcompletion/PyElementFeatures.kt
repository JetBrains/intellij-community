// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.mlcompletion

import com.intellij.codeInsight.completion.CompletionLocation
import com.intellij.codeInsight.completion.ml.ContextFeatures
import com.intellij.codeInsight.completion.ml.ElementFeatureProvider
import com.intellij.codeInsight.completion.ml.MLFeatureValue
import com.intellij.codeInsight.lookup.LookupElement

class PyElementFeatures : ElementFeatureProvider {
  override fun getName(): String = "python"

  override fun calculateFeatures(element: LookupElement,
                                 location: CompletionLocation,
                                 contextFeatures: ContextFeatures): Map<String, MLFeatureValue> {
    val result = HashMap<String, MLFeatureValue>()

    val lookupString = element.lookupString
    val locationPsi = location.completionParameters.position

    PyCompletionFeatures.getPyLookupElementInfo(element)?.let { info ->
      result["kind"] = MLFeatureValue.categorical(info.kind)
      result["is_builtins"] = MLFeatureValue.binary(info.isBuiltins)
      PyCompletionFeatures.getNumberOfOccurrencesInScope(info.kind, locationPsi, lookupString)?.let { occurrences ->
        result["number_of_occurrences_in_scope"] = MLFeatureValue.float(occurrences)
      }
      PyCompletionFeatures.getBuiltinPopularityFeature(lookupString, info.isBuiltins)?.let { result["builtin_popularity"] = MLFeatureValue.float(it) }
    }

    PyCompletionFeatures.getImportPopularityFeature(locationPsi, lookupString)?.let { result["import_popularity"] = MLFeatureValue.float(it) }
    PyCompletionFeatures.getKeywordId(lookupString)?.let { result["keyword_id"] = MLFeatureValue.float(it) }

    return result
  }

}