package com.intellij.turboComplete

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.completion.ml.experiments.ExperimentStatus
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ml.impl.turboComplete.SuggestionGeneratorExecutorProvider

data class CompletionPerformanceParameters(
  val showLookupEarly: Boolean,
  val fixedGeneratorsOrder: Boolean,
) {
  companion object {
    const val PROPERTY_SHOW_LOOKUP_EARLY = "ml.completion.performance.showLookupEarly"
    const val PROPERTY_EXECUTE_IMMEDIATELY = "ml.completion.performance.executeImmediately"

    private fun parametrizeNoPerformance() = CompletionPerformanceParameters(false, true)

    private fun parametrizeWithPerformance() = CompletionPerformanceParameters(true, false)

    private enum class Experiment(val number: Int) {
      PERFORMANCE_LOOKUP_RANKING(17),
      PERFORMANCE_LOOKUP(18),
      ONLY_PERFORMANCE(19);

      companion object {
        fun numbers() = Experiment.values().map { it.number }

        fun fromVersion(version: Int) = Experiment.values().find { it.number == version }
      }
    }

    private fun systemParameters(): CompletionPerformanceParameters? {
      fun property(name: String): Boolean? = System.getProperty(name)?.toBoolean()

      val nullableShowLookupEarly = property(PROPERTY_SHOW_LOOKUP_EARLY)
      val nullableExecuteImmediately = property(PROPERTY_EXECUTE_IMMEDIATELY)

      if (nullableShowLookupEarly == null && nullableExecuteImmediately == null) {
        return null
      }

      return CompletionPerformanceParameters(nullableShowLookupEarly ?: false, nullableExecuteImmediately ?: false)
    }

    private fun registryParameters(): CompletionPerformanceParameters? {
      if (Registry.`is`("ml.completion.performance.experiment")) return null

      val showLookupEarly = Registry.`is`("ml.completion.performance.showLookupEarly")
      val executeImmediately = Registry.`is`("ml.completion.performance.executeImmediately")

      return CompletionPerformanceParameters(
        showLookupEarly,
        executeImmediately,
      )
    }

    private fun experimentParameters(parameters: CompletionParameters): CompletionPerformanceParameters? {
      val status = ExperimentStatus.getInstance().forLanguage(parameters.position.language)
      if (!status.inExperiment || status.version !in Experiment.numbers()) {
        return null
      }

      return when (Experiment.fromVersion(status.version)) {
        Experiment.PERFORMANCE_LOOKUP_RANKING, Experiment.PERFORMANCE_LOOKUP -> CompletionPerformanceParameters(true, false)
        Experiment.ONLY_PERFORMANCE -> CompletionPerformanceParameters(false, false)
        else -> parametrizeNoPerformance()
      }
    }

    private fun unitTestingParameters(parameters: CompletionParameters): CompletionPerformanceParameters? = if (parameters.isTestingMode) {
      CompletionPerformanceParameters(false, true)
    } else {
      null
    }

    private fun canEnablePerformance(parameters: CompletionParameters): Boolean {
      val project = parameters.editor.project ?: return false
      if (DumbService.isDumb(project)) {
        return false
      }
      val hasKindExecutorProvider = SuggestionGeneratorExecutorProvider.hasAnyToCall(parameters)
      return hasKindExecutorProvider
    }

    private var lastLoggedPerformanceStatus: CompletionPerformanceParameters? = null

    private fun updateLoggedPerformanceStatus(currentPerformanceParameters: CompletionPerformanceParameters) {
      if (currentPerformanceParameters != lastLoggedPerformanceStatus) {
        thisLogger().info("Turbo Completion will is using the following parameters: ${currentPerformanceParameters}")
        lastLoggedPerformanceStatus = currentPerformanceParameters
      }
    }

    fun fromCompletionPreferences(parameters: CompletionParameters): CompletionPerformanceParameters {
      if (!canEnablePerformance(parameters)) {
        return parametrizeNoPerformance()
      }

      val currentPerformanceStatus = systemParameters()
             ?: unitTestingParameters(parameters)
             ?: registryParameters()
             ?: experimentParameters(parameters)
             ?: parametrizeWithPerformance()

      updateLoggedPerformanceStatus(currentPerformanceStatus)

      return currentPerformanceStatus
    }
  }
}