package com.intellij.searchEverywhereMl.semantics.contributors

import com.intellij.concurrency.SensitiveProgressWrapper
import com.intellij.ide.actions.SearchEverywherePsiRenderer
import com.intellij.ide.actions.searcheverywhere.ClassSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.ide.actions.searcheverywhere.PossibleSlowContributor
import com.intellij.ide.actions.searcheverywhere.PsiItemWithSimilarity
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.searchEverywhereMl.SemanticSearchEverywhereContributor
import com.intellij.searchEverywhereMl.semantics.providers.SemanticClassesProvider
import com.intellij.util.Processor
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
class SemanticClassSearchEverywhereContributor(initEvent: AnActionEvent)
  : ClassSearchEverywhereContributor(initEvent), SemanticSearchEverywhereContributor,
    SearchEverywhereConcurrentPsiElementsFetcher, PossibleSlowContributor {
  private val project = initEvent.project ?: ProjectManager.getInstance().openProjects[0]

  override val itemsProvider = SemanticClassesProvider(project, createModel(project))

  override val psiElementsRenderer = elementsRenderer as SearchEverywherePsiRenderer

  override fun getSearchProviderId(): String = ClassSearchEverywhereContributor::class.java.simpleName

  override fun fetchWeightedElements(pattern: String, progressIndicator: ProgressIndicator,
                                     consumer: Processor<in FoundItemDescriptor<Any>>) {
    // We wrap the progressIndicator here to make sure we don't run standard search under the same indicator
    ProgressManager.getInstance().executeProcessUnderProgress(
      { fetchElementsConcurrently(pattern, SensitiveProgressWrapper(progressIndicator), consumer) }, progressIndicator)
  }

  override fun isElementSemantic(element: Any) = element is PsiItemWithSimilarity<*> && element.isPureSemantic

  override fun defaultFetchElements(pattern: String, progressIndicator: ProgressIndicator,
                                    consumer: Processor<in FoundItemDescriptor<Any>>) {
    super.fetchWeightedElements(pattern, progressIndicator, consumer)
  }

  override fun syncSearchSettings() {
    itemsProvider.model = createModel(project)
  }
}