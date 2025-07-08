package com.intellij.searchEverywhereMl

import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.VisibleForTesting

object SearchEverywhereMlRegistry {
  private const val EXPERIMENT_GROUP_KEY = "search.everywhere.ml.experiment.group"
  val experimentGroupNumber: Int
    get() = Registry.intValue(EXPERIMENT_GROUP_KEY, -1, -1, SearchEverywhereMlExperiment.NUMBER_OF_GROUPS - 1)

  private const val DISABLE_LOGGING_KEY = "search.everywhere.force.disable.logging.ml"
  val disableLogging: Boolean
    get() = Registry.`is`(DISABLE_LOGGING_KEY,  false)


  fun isExperimentDisabled(tabWithExperiments: SearchEverywhereTab.TabWithExperiments): Boolean {
    return when (tabWithExperiments) {
      SearchEverywhereTab.All -> disableAllTabExperiment
      SearchEverywhereTab.Actions -> disableActionExperiment
      SearchEverywhereTab.Classes -> disableClassesExperiment
      SearchEverywhereTab.Files -> disableFilesExperiment
      SearchEverywhereTab.Symbols -> disableSymbolsExperiment
    }
  }

  private const val DISABLE_ALL_EXPERIMENT_KEY = "search.everywhere.force.disable.experiment.all.ml"
  val disableAllTabExperiment: Boolean
    get() = Registry.`is`(DISABLE_ALL_EXPERIMENT_KEY, false)

  private const val DISABLE_ACTION_EXPERIMENT_KEY = "search.everywhere.force.disable.experiment.action.ml"
  val disableActionExperiment: Boolean
    get() = Registry.`is`(DISABLE_ACTION_EXPERIMENT_KEY, false)

  private const val DISABLE_FILES_EXPERIMENT_KEY = "search.everywhere.force.disable.experiment.files.ml"
  val disableFilesExperiment: Boolean
    get() = Registry.`is`(DISABLE_FILES_EXPERIMENT_KEY, false)

  private const val DISABLE_CLASSES_EXPERIMENT_KEY = "search.everywhere.force.disable.experiment.classes.ml"
  val disableClassesExperiment: Boolean
    get() = Registry.`is`(DISABLE_CLASSES_EXPERIMENT_KEY, false)

  private const val DISABLE_SYMBOLS_EXPERIMENT_KEY = "search.everywhere.force.disable.experiment.symbols.ml"
  val disableSymbolsExperiment: Boolean
    get() = Registry.`is`(DISABLE_SYMBOLS_EXPERIMENT_KEY, false)

  private const val DISABLE_ESSENTIAL_CONTRIBUTORS_EXPERIMENT_KEY = "search.everywhere.force.disable.experiment.essential.contributors.ml"
  val disableEssentialContributorsExperiment: Boolean
    get() = Registry.`is`(DISABLE_ESSENTIAL_CONTRIBUTORS_EXPERIMENT_KEY, false)

  @VisibleForTesting
  val ALL_DISABLE_EXPERIMENT_KEYS: List<String> = listOf(
    DISABLE_ALL_EXPERIMENT_KEY,
    DISABLE_ACTION_EXPERIMENT_KEY,
    DISABLE_FILES_EXPERIMENT_KEY,
    DISABLE_CLASSES_EXPERIMENT_KEY,
    DISABLE_SYMBOLS_EXPERIMENT_KEY,
    DISABLE_ESSENTIAL_CONTRIBUTORS_EXPERIMENT_KEY
  )
}
