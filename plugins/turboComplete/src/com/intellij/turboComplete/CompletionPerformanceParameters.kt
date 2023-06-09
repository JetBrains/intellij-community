package com.intellij.turboComplete

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.completion.ml.common.CurrentProjectInfo
import com.intellij.completion.ml.experiment.ExperimentStatus
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ml.impl.turboComplete.KindCollector

data class CompletionPerformanceParameters(
  val enabled: Boolean,
  val showLookupEarly: Boolean,
  val fixedGeneratorsOrder: Boolean,
) {
  companion object {
    private fun parametrizeNoPerformance() = CompletionPerformanceParameters(false, false, false)

    private fun parametrizeForUnitTesting() = CompletionPerformanceParameters(true, false, false)

    private val isTeamCity = System.getenv("TEAMCITY_VERSION") != null

    private enum class Experiment(val number: Int) {
      PERFORMANCE_LOOKUP_RANKING(17),
      PERFORMANCE_LOOKUP(18),
      ONLY_PERFORMANCE(19);

      companion object {
        fun numbers() = Experiment.values().map { it.number }

        fun fromVersion(version: Int) = Experiment.values().find { it.number == version }
      }
    }

    private fun registryParameters(): CompletionPerformanceParameters? {
      if (Registry.`is`("ml.completion.performance.experiment")) return null

      val enablePerformance = Registry.`is`("ml.completion.performance.enable")
      val showLookupEarly = Registry.`is`("ml.completion.performance.showLookupEarly")
      val executeImmediately = Registry.`is`("ml.completion.performance.executeImmediately")

      return CompletionPerformanceParameters(
        enablePerformance,
        enablePerformance && showLookupEarly,
        executeImmediately,
      )
    }

    private fun experimentParameters(parameters: CompletionParameters): CompletionPerformanceParameters {
      if (!CurrentProjectInfo.getInstance(parameters.position.project).isIdeaProject) {
        return parametrizeNoPerformance()
      }

      val status = ExperimentStatus.getInstance().forLanguage(parameters.position.language)
      if (!status.inExperiment || status.version !in Experiment.numbers()) {
        return parametrizeNoPerformance()
      }

      return when (Experiment.fromVersion(status.version)) {
        Experiment.PERFORMANCE_LOOKUP_RANKING, Experiment.PERFORMANCE_LOOKUP -> CompletionPerformanceParameters(true, true, false)
        Experiment.ONLY_PERFORMANCE -> CompletionPerformanceParameters(true, false, false)
        else -> parametrizeNoPerformance()
      }
    }

    private fun canEnablePerformance(parameters: CompletionParameters): Boolean {
      val hasKindExecutorProvider = { SuggestionGeneratorExecutorProvider.hasAnyToCall(parameters) }
      val hasAtLeastOneGenerator = { KindCollector.forParameters(parameters).any() }
      return hasKindExecutorProvider() && hasAtLeastOneGenerator()
    }

    fun fromCompletionPreferences(parameters: CompletionParameters): CompletionPerformanceParameters {
      if (isTeamCity || !canEnablePerformance(parameters)) {
        return parametrizeNoPerformance()
      }

      if (parameters.isTestingMode) {
        return parametrizeForUnitTesting()
      }

      return registryParameters() ?: experimentParameters(parameters)
    }
  }
}