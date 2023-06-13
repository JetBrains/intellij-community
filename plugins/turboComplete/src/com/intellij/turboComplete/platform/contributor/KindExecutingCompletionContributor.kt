package com.intellij.turboComplete.platform.contributor

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PolicyObeyingResultSet
import com.intellij.codeInsight.completion.addingPolicy.PolicyController
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.platform.ml.impl.turboComplete.KindCollector
import com.intellij.turboComplete.CompletionPerformanceParameters
import com.intellij.turboComplete.ReportingSuggestionGeneratorExecutor
import com.intellij.turboComplete.SuggestionGeneratorExecutorProvider
import com.intellij.util.indexing.DumbModeAccessType

class KindExecutingCompletionContributor : CompletionContributor() {
  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    val performanceParameters = CompletionPerformanceParameters.fromCompletionPreferences(parameters)
    if (!performanceParameters.enabled) return

    val executorProvider = SuggestionGeneratorExecutorProvider.findOneMatching(parameters)

    val policyController = PolicyController(result)
    val obeyingResult = PolicyObeyingResultSet(result, policyController)

    val executor = ReportingSuggestionGeneratorExecutor.initialize(
      parameters, policyController, executorProvider
    )

    for (generator in KindCollector.forParameters(parameters)) {
      val policyWhileGenerating = executor.createNoneKindPolicy()
      policyController.invokeWithPolicy(policyWhileGenerating) {
        executor.reportGenerateCompletionKinds(generator, parameters, executor, obeyingResult, policyController)
      }
      if (obeyingResult.isStopped) break
    }

    DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(ThrowableComputable {
      executor.executeAll()
    })
  }
}