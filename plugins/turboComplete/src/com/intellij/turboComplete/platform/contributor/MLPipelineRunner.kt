package com.intellij.turboComplete.platform.contributor

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PolicyObeyingResultSet
import com.intellij.codeInsight.completion.addingPolicy.PolicyController
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.platform.ml.impl.turboComplete.KindCollector
import com.intellij.platform.ml.impl.turboComplete.SmartPipelineRunner
import com.intellij.turboComplete.ReportingSuggestionGeneratorExecutor
import com.intellij.util.indexing.DumbModeAccessType

class MLPipelineRunner : SmartPipelineRunner {
  override fun runPipeline(kindCollector: KindCollector, parameters: CompletionParameters, result: CompletionResultSet) {
    if (!kindCollector.shouldBeCalled(parameters)) {
      return
    }
    val policyController = PolicyController(result)
    val obeyingResult = PolicyObeyingResultSet(result, policyController)

    val executor = ReportingSuggestionGeneratorExecutor.initialize(
      parameters, policyController
    )

    val policyWhileGenerating = executor.createNoneKindPolicy()
    policyController.invokeWithPolicy(policyWhileGenerating) {
      executor.reportGenerateCompletionKinds(kindCollector, parameters, executor, obeyingResult)
    }

    DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(ThrowableComputable {
      executor.executeAll()
    })
  }
}