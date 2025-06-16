package com.intellij.searchEverywhereMl

import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert

class SearchEverywhereMlExperimentTest : BasePlatformTestCase() {
  private val EXPERIMENT_GROUP_REG_KEY = "search.everywhere.ml.experiment.group"

  fun `test experiment group registry value is clamped between -1 and the number of experiment groups`() {
    val mlExperiment = SearchEverywhereMlExperiment.apply { isExperimentalMode = true }

    // Upper boundary
    Registry.get(EXPERIMENT_GROUP_REG_KEY).setValue(SearchEverywhereMlExperiment.NUMBER_OF_GROUPS - 1)
    assertEquals(SearchEverywhereMlExperiment.NUMBER_OF_GROUPS - 1, mlExperiment.experimentGroup)

    // Clamp - NUMBER_OF_GROUPS is used as modulo, so the max allowed value is NUMBER_OF_GROUPS - 1
    Registry.get(EXPERIMENT_GROUP_REG_KEY).setValue(SearchEverywhereMlExperiment.NUMBER_OF_GROUPS)
    assertEquals(SearchEverywhereMlExperiment.NUMBER_OF_GROUPS - 1, mlExperiment.experimentGroup)
  }

  fun `test no experiment group higher than total number of groups`() {
    // For context, see IDEA-322948. Basically, we want to make sure that all experiments we defined are accessible
    SearchEverywhereTab.allTabs
      .filterIsInstance<SearchEverywhereTab.TabWithExperiments>()
      .associateWith { it.experiments.keys }
      .mapValues { it ->
        it.value.filter { experimentGroup ->
          experimentGroup < 0 || experimentGroup >= SearchEverywhereMlExperiment.NUMBER_OF_GROUPS
        }
      }
      .filterValues { it.isNotEmpty() }
      .takeIf { it.isNotEmpty() }
      ?.map { (tab, invalidGroups) -> "Tab \"${tab}\" with groups $invalidGroups" }
      ?.let { "Inaccessible experiment groups: $it" }
      ?.let {
        Assert.fail(it)
      }
  }
}