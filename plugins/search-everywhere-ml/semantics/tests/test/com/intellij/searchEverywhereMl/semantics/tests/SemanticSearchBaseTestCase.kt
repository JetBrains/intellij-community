package com.intellij.searchEverywhereMl.semantics.tests

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.PluginPathManager
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.platform.ml.embeddings.indexer.storage.ScoredKey
import com.intellij.platform.ml.embeddings.jvm.indices.EntityId
import com.intellij.platform.ml.embeddings.jvm.models.LocalEmbeddingService
import com.intellij.platform.ml.embeddings.jvm.models.LocalEmbeddingServiceProviderImpl
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

abstract class SemanticSearchBaseTestCase : LightJavaCodeInsightFixtureTestCase() {
  protected val modelService: LocalEmbeddingService
    get() = runBlockingCancellable { LocalEmbeddingServiceProviderImpl.getInstance().getService()!! }

  override fun getTestDataPath() = PluginPathManager
    .getPluginHome("search-everywhere-ml").resolve("semantics/tests/testData").toString()

  protected fun createEvent(): AnActionEvent {
    return AnActionEvent.createFromDataContext(ActionPlaces.UNKNOWN, null, SimpleDataContext.getProjectContext(project))
  }

  protected fun Iterable<ScoredKey<EntityId>>.toIdsSet(): Set<String> {
    return this.map { it.key.id }.toSet()
  }

  override fun runInDispatchThread(): Boolean = false
}