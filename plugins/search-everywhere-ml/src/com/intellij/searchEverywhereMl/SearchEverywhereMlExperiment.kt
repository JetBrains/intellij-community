package com.intellij.searchEverywhereMl

import com.intellij.internal.statistic.eventLog.EventLogConfiguration
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.openapi.application.ApplicationManager
import com.intellij.searchEverywhereMl.SearchEverywhereMlExperiment.VERSION
import com.intellij.searchEverywhereMl.log.MLSE_RECORDER_ID
import com.intellij.util.MathUtil
import org.jetbrains.annotations.TestOnly
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * Handles the machine learning experiments in Search Everywhere.
 * Determines enabled functionality for every user participating in an experiment.
 * Please update the [VERSION] number if you need to reshuffle experiment groups.
 * This might be helpful to avoid the impact of the previous experiments on the current experiments.
 */
object SearchEverywhereMlExperiment {
  const val VERSION: Int = 2
  const val NUMBER_OF_GROUPS: Int = 4

  var isExperimentalMode: Boolean = StatisticsUploadAssistant.isSendAllowed() && ApplicationManager.getApplication().isEAP
    @TestOnly set

  val isAllowed: Boolean
    get() = isExperimentalMode && !SearchEverywhereMlRegistry.disableLogging

  /**
   * Overrides the experiment group assignment for testing purposes.
   * This is an internal variable intended for use in test environments only.
   *
   * When set to a non-null value, it forces the experiment group to the specified integer.
   * A null value indicates that no override is applied, and the default group assignment logic is used.
   */
  @TestOnly
  var experimentGroupOverride: Int? = null

  /**
   * Determines the experiment group number for a feature, based on various conditions.
   * The group number is derived from the following steps:
   * 1. Checks if there's an override for the experiment group; if present, returns it.
   * 2. If experimental mode is enabled:
   *    - Retrieves the experiment group number from the registry.
   *    - If a valid registry group is found (non-negative), returns it.
   *    - Otherwise, calculates and returns the computed group number.
   * 3. If experimental mode is disabled, returns -1.
   */
  @get:JvmStatic
  val experimentGroup: Int
    get() {
      val override = experimentGroupOverride
      if (override != null) {
        return override
      }

      return if (isExperimentalMode) {
        val registryExperimentGroup = SearchEverywhereMlRegistry.experimentGroupNumber
        if (registryExperimentGroup >= 0) registryExperimentGroup else computedGroup
      }
      else -1
    }

  private val computedGroup: Int by lazy {
    val mlseLogConfiguration = EventLogConfiguration.getInstance().getOrCreate(MLSE_RECORDER_ID)
    // experiment groups get updated on the VERSION property change:
    MathUtil.nonNegativeAbs((mlseLogConfiguration.deviceId + VERSION).hashCode()) % NUMBER_OF_GROUPS
  }

  sealed interface ExperimentType {
    /**
     * Indicates that for the current experiment group,
     * there are no active experiments and the default
     * behavior should be followed
     */
    object NoExperiment : ExperimentType

    sealed class ActiveExperiment(val shouldSortByMl: Boolean) : ExperimentType

    /**
     * An experiment where no machine learning
     * should be applied to a tab and old, heuristic-based
     * ranking should be used.
     *
     * This experiment type is used when the machine learning
     * approach became the default for a specific tab,
     * and to gather data how it compares to the old baseline,
     * an experiment with disabled ML can be used.
     */
    object NoMl : ActiveExperiment(false)

    /**
     * An experiment group applicable only for the All tab,
     * where essential contributor prediction will take place.
     */
    object EssentialContributorPrediction : ActiveExperiment(false)

    /**
     * An experiment group where a new (experimental) model
     * should be used instead of the default one.
     *
     * This experiment type is used when the machine learning
     * approach became the default for a specific tab,
     * and a new model should be tested during the A/B testing cycle
     */
    object ExperimentalModel : ActiveExperiment(true)

    /**
     * An experiment group that enables semantic search
     */
    object SemanticSearch: ActiveExperiment(true)

    /**
     * An experiment group where the exact match issue is manually fixed
     * (see, for example, IJPL-171760 or IJPL-55751)
     */
    object ExactMatchManualFix : ActiveExperiment(true)

    /**
     * An experiment group that combines both EssentialContributorPrediction and ExperimentalModel
     * functionality for the All tab.
     */
    object CombinedExperiment : ActiveExperiment(true)
  }
}

@OptIn(ExperimentalContracts::class)
fun SearchEverywhereMlExperiment.ExperimentType.isActiveExperiment(): Boolean {
  contract {
    returns(true) implies(this@isActiveExperiment is SearchEverywhereMlExperiment.ExperimentType.ActiveExperiment)
    returns(false) implies(this@isActiveExperiment is SearchEverywhereMlExperiment.ExperimentType.NoExperiment)
  }

  return this is SearchEverywhereMlExperiment.ExperimentType.ActiveExperiment
}
