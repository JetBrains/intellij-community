package com.intellij.turboComplete.platform.contributor

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.completion.ml.sorting.ContextFactorCalculator
import com.intellij.completion.ml.storage.MutableLookupStorage
import com.intellij.openapi.project.DumbAware
import com.intellij.turboComplete.CompletionPerformanceParameters

class KindOrderingFeaturesContributor : CompletionContributor(), DumbAware {
  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    val performanceParameters = CompletionPerformanceParameters.fromCompletionPreferences(parameters)
    if (!performanceParameters.enabled) return

    val lookup = LookupManager.getActiveLookup(parameters.editor) as? LookupImpl
    if (lookup != null) {
      val storage = MutableLookupStorage.get(lookup)
      if (storage != null) {
        MutableLookupStorage.saveAsUserData(parameters, storage)
        if (!storage.isContextFactorsInitialized()) {
          ContextFactorCalculator.calculateContextFactors(lookup, parameters, storage)
        }
      }
    }
    super.fillCompletionVariants(parameters, result)
  }
}