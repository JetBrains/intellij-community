package com.intellij.searchEverywhereMl

import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.ClassSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.FileSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManagerImpl.ALL_CONTRIBUTORS_GROUP_ID
import com.intellij.ide.actions.searcheverywhere.SymbolSearchEverywhereContributor
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.searchEverywhereMl.SearchEverywhereMlExperiment.ExperimentType
import com.intellij.util.PlatformUtils
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.VisibleForTesting
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract


sealed interface SearchEverywhereTab {
  /**
   * Indicates a tab for which MLSE logs will be collected.
   * Tabs that do not implement this interface will not have any data recorded
   */
  interface TabWithLogging : SearchEverywhereTab

  /**
   * Tabs that perform experiments during the EAP cycles need to implement this interface
   * and define the mapping of experiment groups to the experiment that should be performed.
   */
  sealed interface TabWithExperiments : TabWithLogging {
    @get:VisibleForTesting
    val experiments: Map<Int, ExperimentType>

    /**
     * Indicates whether experiments are enabled for the current tab.
     *
     * This property evaluates a combination of conditions to determine the experiment's state:
     * - Logging must be enabled for the tab.
     * - Experiments must be allowed globally.
     * - The current tab must not have experiments explicitly disabled via registry flags.
     *
     * When all these conditions are met, the experiments for the tab are considered enabled.
     */
    val isExperimentEnabled: Boolean
      get() {
        return isLoggingEnabled()
               && SearchEverywhereMlExperiment.isAllowed
               && !SearchEverywhereMlRegistry.isExperimentDisabled(this)
      }

    /**
     * Retrieves the type of experiment currently active for the corresponding tab.
     *
     * If experiments are enabled for the tab, this property determines the experiment group
     * defined for it and returns the associated experiment type. If no specific experiment is
     * specified for the group's configuration, it defaults to [ExperimentType.NoExperiment].
     *
     * When experiments are disabled for the tab, it always returns [ExperimentType.NoExperiment].
     */
    val currentExperimentType: ExperimentType
      get() {
        return if (isExperimentEnabled) {
          val experimentGroup = SearchEverywhereMlExperiment.experimentGroup
          experiments.getOrDefault(experimentGroup, ExperimentType.NoExperiment)
        }
        else {
          ExperimentType.NoExperiment
        }
      }
  }

  /**
   * Tabs where ML ranking is supported (whether by experiment or default) are required
   * to implement this interface and define an advanced setting in plugin.xml for controlling the behavior
   */
  sealed interface TabWithMlRanking : TabWithExperiments {
    @get:VisibleForTesting
    @get:NonNls
    val advancedSettingKey: String

    @get:NonNls
    val localModelPathRegistryKey: String

    /**
     * Indicates whether Machine Learning (ML) ranking is enabled for the current tab.
     *
     * This property checks both the default and current user-defined states of the advanced
     * setting associated with ML ranking for the tab. If the tab is part of an active experiment
     * (see [ExperimentType.ActiveExperiment]), additional logic is applied:
     * - If ML ranking was enabled by default but then explicitly disabled by the user, the functionality
     *   remains disabled as a user preference.
     * - Otherwise, the behavior depends on the specific experiment type and its [ExperimentType.ActiveExperiment.shouldSortByMl] property.
     *
     * If the current tab does not support experiments, the result only considers the user-defined and
     * default states of the advanced setting.
     */
    val isMlRankingEnabled: Boolean
      get() {
        val isMlRankingEnabledByDefault = AdvancedSettings.getDefaultBoolean(advancedSettingKey)
        val isMlRankingEnabled = AdvancedSettings.getBoolean(advancedSettingKey)

        val experiment = currentExperimentType
        if (experiment !is ExperimentType.ActiveExperiment) {
          return isMlRankingEnabled
        }

        if (isMlRankingEnabledByDefault && !isMlRankingEnabled) {
          // In this case, we assume that someone was not satisfied with the ML ranking,
          // and they have decided to disable it when it was enabled by default.
          // We will not turn the ML ranking back on for the experiment
          // but leave it disabled as per the user's choice.
          return false
        }

        return experiment.shouldSortByMl
      }

    /**
     * Indicates whether the experimental model is being used for the current tab.
     *
     * This property evaluates the type of experiment configured for the tab and returns `true`
     * if the current tab belongs to the [ExperimentType.ExperimentalModel] experiment type.
     * Otherwise, the return value is `false`
     *
     * The `[ExperimentType.ExperimentalModel]` experiment type represents an A/B testing scenario where a new,
     * experimental machine learning model is tested against the default configuration.
     *
     * @return `true` if the experimental model is used for the current tab; otherwise, `false`.
     */
    val useExperimentalModel: Boolean
      get() {
        return currentExperimentType == ExperimentType.ExperimentalModel
      }
  }

  val tabId: String

  object All :TabWithMlRanking {
    override val tabId: String = ALL_CONTRIBUTORS_GROUP_ID
    override val advancedSettingKey: String = "searcheverywhere.ml.sort.all"
    override val localModelPathRegistryKey: String = "search.everywhere.ml.all.model.path"

    override val experiments: Map<Int, ExperimentType> = mapOf(
      1 to ExperimentType.EssentialContributorPrediction,
      2 to ExperimentType.CombinedExperiment,
    )


    override val currentExperimentType: ExperimentType
      get() {
        val experimentType = super.currentExperimentType
        if (experimentType == ExperimentType.EssentialContributorPrediction ||
            experimentType == ExperimentType.CombinedExperiment) {
          if (SearchEverywhereMlRegistry.disableEssentialContributorsExperiment) {
            return ExperimentType.NoExperiment
          }
        }

        return experimentType
      }
  }

  object Actions : TabWithMlRanking {
    override val tabId: String = ActionSearchEverywhereContributor::class.java.simpleName
    override val advancedSettingKey: String = "searcheverywhere.ml.sort.action"
    override val localModelPathRegistryKey: String = "search.everywhere.ml.action.model.path"

    override val experiments: Map<Int, ExperimentType> = mapOf(
      1 to ExperimentType.ExactMatchManualFix,
      2 to ExperimentType.NoMl,
    )

    override val useExperimentalModel: Boolean
      get() = super.useExperimentalModel || isSemanticSearchExperiment
  }

  object Classes : TabWithMlRanking {
    override val tabId: String = ClassSearchEverywhereContributor::class.java.simpleName
    override val advancedSettingKey: String = "searcheverywhere.ml.sort.classes"
    override val localModelPathRegistryKey: String = "search.everywhere.ml.classes.model.path"

    override val experiments: Map<Int, ExperimentType> = mapOf(
      1 to ExperimentType.SemanticSearch,
      2 to ExperimentType.NoMl,
    )

    override val useExperimentalModel: Boolean
      get() = super.useExperimentalModel
              || (isSemanticSearchExperiment && (PlatformUtils.isPyCharm() || PlatformUtils.isIntelliJ()))
  }


  object Files : TabWithMlRanking {
    override val tabId: String = FileSearchEverywhereContributor::class.java.simpleName
    override val advancedSettingKey: String = "searcheverywhere.ml.sort.files"
    override val localModelPathRegistryKey: String = "search.everywhere.ml.files.model.path"

    override val experiments: Map<Int, ExperimentType> = mapOf(
      1 to ExperimentType.SemanticSearch,
      3 to ExperimentType.NoMl
    )

    override val useExperimentalModel: Boolean
      get() = super.useExperimentalModel || isSemanticSearchExperiment
  }

  object Symbols : TabWithExperiments {
    override val tabId: String = SymbolSearchEverywhereContributor::class.java.simpleName

    override val experiments: Map<Int, ExperimentType> = mapOf(
      1 to ExperimentType.SemanticSearch
    )
  }

  object Git : SearchEverywhereTab {
    override val tabId: String
      get() = "Git"
  }

  companion object {
    val allTabs: List<SearchEverywhereTab> = listOf(
      All, Actions, Classes, Files, Symbols
    )

    fun findById(id: String): SearchEverywhereTab? = allTabs.find { it.tabId == id }
  }
}

/**
 * Determines whether logging is enabled for the current tab in the Search Everywhere feature.
 *
 * This function evaluates the logging state by checking the following conditions:
 * - Logging must not be globally disabled via the SearchEverywhere registry flag.
 * - The current tab must implement the [SearchEverywhereTab.TabWithLogging] interface, which indicates eligibility for
 *   collecting MLSE (Machine Learning in Search Everywhere) logs.
 *
 * When these conditions are met, the property returns `true`, allowing data logging for the tab.
 * Otherwise, it returns `false`, disabling data logging.
 *
 * Use this function to selectively enable or disable logging based on registry settings and tab capabilities.
 */
@OptIn(ExperimentalContracts::class)
fun SearchEverywhereTab.isLoggingEnabled(): Boolean {
  contract {
    returns(true) implies (this@isLoggingEnabled is SearchEverywhereTab.TabWithLogging)
  }
  return !SearchEverywhereMlRegistry.disableLogging && this is SearchEverywhereTab.TabWithLogging
}

/**
 * Determines if the current [SearchEverywhereTab] supports machine learning (ML) ranking.
 *
 * Tabs that implement the [SearchEverywhereTab.TabWithMlRanking] interface
 * are considered to support ML ranking, either by default or through experiments.
 *
 * @return `true` if the current tab supports ML ranking; otherwise, `false`.
 */
@OptIn(ExperimentalContracts::class)
fun SearchEverywhereTab.isTabWithMlRanking(): Boolean {
  contract {
    returns(true) implies (this@isTabWithMlRanking is SearchEverywhereTab.TabWithMlRanking)
  }

  return this is SearchEverywhereTab.TabWithMlRanking
}

/**
 * Retrieves the currently active experiment type for the [SearchEverywhereTab] or returns [ExperimentType.NoExperiment]
 * if no experiments are enabled or the tab does not support experimentation.
 *
 * This property determines the active experiment type based on the following:
 * - If the current tab implements [SearchEverywhereTab.TabWithExperiments], it retrieves the tab's experiment type
 *   using the [com.intellij.searchEverywhereMl.SearchEverywhereTab.TabWithExperiments.currentExperimentType] property.
 * - If the tab does not support experiments, it defaults to [ExperimentType.NoExperiment].
 *
 * This functionality ensures that any tab, whether experimental or not, can seamlessly provide information
 * about its experiment state while maintaining a default fallback for non-experimenting tabs.
 */
val SearchEverywhereTab.currentExperimentOrNone: ExperimentType
  get() {
    if (this is SearchEverywhereTab.TabWithExperiments) {
      return this.currentExperimentType
    }
    else {
      return ExperimentType.NoExperiment
    }
  }

/**
 * Indicates whether the current experiment for the associated [SearchEverywhereTab.TabWithExperiments]
 * is of the type [ExperimentType.SemanticSearch].
 *
 * This property evaluates the [[com.intellij.searchEverywhereMl.SearchEverywhereTab.TabWithExperiments.currentExperimentType]
 * of the tab and returns `true` if it is equal to [ExperimentType.SemanticSearch]. Otherwise, it returns `false`.
 *
 * The [ExperimentType.SemanticSearch] experiment type typically introduces advanced semantic-based search
 * capabilities within the corresponding tab. Tabs without any active experiments or with
 * a different experiment type will not activate this feature.
 *
 * This is a read-only property and relies on the underlying experiment configurations and
 * state for the tab.
 */
val SearchEverywhereTab.TabWithExperiments.isSemanticSearchExperiment: Boolean
  get() = this.currentExperimentType == ExperimentType.SemanticSearch

/**
 * Indicates whether the "Essential Contributor Prediction" experiment is currently active
 * for the "All" tab in the Search Everywhere feature.
 *
 * This property checks the current experimental type associated with the tab. If the experiment
 * type corresponds to [ExperimentType.EssentialContributorPrediction], it returns `true`.
 * It also returns `true` for [ExperimentType.CombinedExperiment], which combines both
 * EssentialContributorPrediction and ExperimentalModel functionality.
 * Otherwise, it returns `false`.
 *
 * The "Essential Contributor Prediction" experiment is designed to improve search result rankings
 * by prioritizing essential contributors during retrieval.
 */
val SearchEverywhereTab.All.isEssentialContributorPredictionExperiment: Boolean
  get() = this.currentExperimentType == ExperimentType.EssentialContributorPrediction || 
          this.currentExperimentType == ExperimentType.CombinedExperiment
