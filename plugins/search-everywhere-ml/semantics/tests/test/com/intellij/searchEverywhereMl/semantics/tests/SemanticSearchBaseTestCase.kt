package com.intellij.searchEverywhereMl.semantics.tests

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.PluginPathManager
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.searchEverywhereMl.semantics.services.IndexingLifecycleTracker
import com.intellij.platform.ml.embeddings.search.utils.ScoredText
import com.intellij.platform.ml.embeddings.search.services.SemanticSearchFileChangeListener
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

abstract class SemanticSearchBaseTestCase : LightJavaCodeInsightFixtureTestCase() {
  override fun setUp() {
    super.setUp()
    VirtualFileManager.getInstance().addAsyncFileListener(SemanticSearchFileChangeListener.getInstance(project),
                                                          IndexingLifecycleTracker.getInstance(project))
  }

  override fun getTestDataPath() = PluginPathManager
    .getPluginHome("search-everywhere-ml").resolve("semantics/tests/testData").toString()

  protected fun createEvent(): AnActionEvent {
    return AnActionEvent.createFromDataContext(ActionPlaces.UNKNOWN, null, SimpleDataContext.getProjectContext(project))
  }

  protected fun Iterable<ScoredText>.toIdsSet(): Set<String> {
    return this.map { it.text }.toSet()
  }
}