package com.intellij.searchEverywhereMl.semantics.tests

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.PluginPathManager
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

abstract class SemanticSearchBaseTestCase : LightJavaCodeInsightFixtureTestCase() {
  override fun getTestDataPath() = PluginPathManager
    .getPluginHome("search-everywhere-ml").resolve("semantics/tests/testData").toString()
    // .getPluginHome("llm").resolve("embeddings/searchEverywhere/tests/testData").toString()

  protected fun createEvent(): AnActionEvent {
    return AnActionEvent.createEvent(SimpleDataContext.getProjectContext(project), null, ActionPlaces.UNKNOWN, ActionUiKind.NONE, null)
  }

  override fun runInDispatchThread(): Boolean = false
}