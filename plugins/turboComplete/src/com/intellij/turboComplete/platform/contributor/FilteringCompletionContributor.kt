package com.intellij.turboComplete.platform.contributor

import com.intellij.codeInsight.completion.*
import com.intellij.platform.ml.impl.turboComplete.KindCollector
import com.intellij.turboComplete.CompletionPerformanceParameters

class FilteringCompletionContributor : CompletionContributor() {
  private val filter: (CompletionContributor) -> Boolean = RejectedListFilter(KindCollector.EP_NAME.extensionList.map { it.kindVariety.actualCompletionContributorClass })

  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    val performanceParameters = CompletionPerformanceParameters.fromCompletionPreferences(parameters)
    if (!performanceParameters.enabled) return

    // TODO: Verify if that works

    FilteringResultSet(result, filter).runRemainingContributors(parameters,true)
    result.stopHere()
  }
}

private class RejectedListFilter(private val rejectedTypes: List<Class<*>>) : (CompletionContributor) -> Boolean {
  override fun invoke(contributor: CompletionContributor): Boolean {
    return contributor.javaClass !in rejectedTypes
  }
}