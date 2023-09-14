package com.intellij.searchEverywhereMl.semantics.experiments

import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.internal.statistic.eventLog.EventLogConfiguration
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.util.registry.Registry

@Service(Service.Level.APP)
class SearchEverywhereSemanticExperiments {
  private val experimentGroup: Int
    get() {
      val experimentGroup = EventLogConfiguration.getInstance().bucket % GRANULARITY
      val registryExperimentGroup = Registry.intValue("search.everywhere.ml.experiment.group", -1, -1, GRANULARITY - 1)
      return if (registryExperimentGroup >= 0) registryExperimentGroup else experimentGroup
    }

  fun getSemanticFeatureForTab(providerId: String): SemanticSearchFeature {
    return TAB_EXPERIMENTS[providerId]?.getSemanticFeatureByGroup(experimentGroup) ?: SemanticSearchFeature.NOT_ENABLED
  }

  internal class TabExperimentPlan(vararg mapping: Pair<Int, SemanticSearchFeature>) {
    private val groupFeatures = hashMapOf(*mapping)

    init {
      require(mapping.all { it.first in 0 until GRANULARITY }) {
        "Group number in tab experiment mapping should be between 0 and ${GRANULARITY - 1}"
      }
    }

    fun getSemanticFeatureByGroup(group: Int) = groupFeatures.getOrDefault(group, SemanticSearchFeature.NOT_ENABLED)
  }

  enum class SemanticSearchFeature { NOT_ENABLED, ENABLED }

  companion object {
    const val GRANULARITY = 8

    private val TAB_EXPERIMENTS = hashMapOf(
      ActionSearchEverywhereContributor::class.java.simpleName to TabExperimentPlan(
        0 to SemanticSearchFeature.ENABLED, // half of one of NO_EXPERIMENT groups
        2 to SemanticSearchFeature.ENABLED, // half of the USE_EXPERIMENTAL group
      )
    )

    fun getInstance() = service<SearchEverywhereSemanticExperiments>()
  }
}