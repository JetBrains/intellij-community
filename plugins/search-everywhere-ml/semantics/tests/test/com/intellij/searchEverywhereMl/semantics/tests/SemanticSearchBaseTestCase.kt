package com.intellij.searchEverywhereMl.semantics.tests

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.PluginPathManager
import com.intellij.platform.ml.embeddings.search.utils.ScoredText
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

abstract class SemanticSearchBaseTestCase : LightJavaCodeInsightFixtureTestCase() {
  override fun getTestDataPath() = PluginPathManager
    .getPluginHome("search-everywhere-ml").resolve("semantics/tests/testData").toString()

  protected fun createEvent(): AnActionEvent {
    return AnActionEvent.createFromDataContext(ActionPlaces.UNKNOWN, null, SimpleDataContext.getProjectContext(project))
  }

  protected fun Iterable<ScoredText>.toIdsSet(): Set<String> {
    return this.map { it.text }.toSet()
  }
}