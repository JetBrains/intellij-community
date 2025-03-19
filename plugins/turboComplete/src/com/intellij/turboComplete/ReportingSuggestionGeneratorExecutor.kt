package com.intellij.turboComplete

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.addingPolicy.PolicyController
import com.intellij.platform.ml.impl.turboComplete.*
import com.intellij.turboComplete.analysis.PipelineListener

private class GenerationReportingSuggestionGenerator(
  private val suggestionGenerator: SuggestionGenerator,
  private val listener: KindExecutionListener,
) : SuggestionGenerator by suggestionGenerator {
  override fun generateCompletionVariants() {
    listener.onGenerationStarted(suggestionGenerator)
    try {
      suggestionGenerator.generateCompletionVariants()
    }
    finally {
      listener.onGenerationFinished(suggestionGenerator)
    }
  }
}

class ReportingSuggestionGeneratorExecutor(
  private val baseExecutor: SuggestionGeneratorExecutor,
  private val listener: KindExecutionListener,
) : SuggestionGeneratorExecutor by baseExecutor {
  fun reportGenerateCompletionKinds(collector: KindCollector,
                                    parameters: CompletionParameters,
                                    completionKindsExecutor: SuggestionGeneratorExecutor,
                                    result: CompletionResultSet) {
    listener.onCollectionStarted()
    try {
      collector.collectKinds(parameters, completionKindsExecutor, result)
    }
    finally {
      listener.onCollectionFinished()
    }
  }

  override fun pass(suggestionGenerator: SuggestionGenerator) {
    val reportingCompletionKind = GenerationReportingSuggestionGenerator(suggestionGenerator, listener)
    listener.onGeneratorCollected(suggestionGenerator)
    baseExecutor.pass(reportingCompletionKind)
  }

  companion object {
    fun initialize(
      parameters: CompletionParameters,
      policyController: PolicyController,
    ): ReportingSuggestionGeneratorExecutor {
      val listener = object : DelegatingKindExecutionListener<KindExecutionListener> {
        override val delegatedListeners: MutableList<KindExecutionListener> = PipelineListener.EP_NAME.extensionList.toMutableList()
      }
      listener.onInitialize(parameters)

      val performanceParameters = CompletionPerformanceParameters.fromCompletionPreferences(parameters)

      val generatorExecutor = if (performanceParameters.fixedGeneratorsOrder) {
        ImmediateExecutor(parameters, policyController)
      }
      else {
        val executorProvider = SuggestionGeneratorExecutorProvider.findOneMatching(parameters)
        executorProvider.createExecutor(parameters, policyController)
      }

      return ReportingSuggestionGeneratorExecutor(generatorExecutor, listener)
    }
  }
}