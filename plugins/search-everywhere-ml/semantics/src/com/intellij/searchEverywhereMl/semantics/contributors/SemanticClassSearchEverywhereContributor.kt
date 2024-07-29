package com.intellij.searchEverywhereMl.semantics.contributors

import com.intellij.concurrency.SensitiveProgressWrapper
import com.intellij.ide.actions.SearchEverywherePsiRenderer
import com.intellij.ide.actions.searcheverywhere.*
import com.intellij.ide.actions.searcheverywhere.footer.createPsiExtendedInfo
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.searchEverywhereMl.semantics.providers.SemanticClassesProvider
import com.intellij.util.Processor
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.function.Consumer

@ApiStatus.Experimental
@Internal
open class SemanticClassSearchEverywhereContributor(initEvent: AnActionEvent)
  : ClassSearchEverywhereContributor(initEvent), SemanticSearchEverywhereContributor,
    SearchEverywhereConcurrentPsiElementsFetcher, PossibleSlowContributor {
  override val itemsProvider = SemanticClassesProvider(project)

  override var notifyCallback: Consumer<String>? = null

  override val psiElementsRenderer = elementsRenderer as SearchEverywherePsiRenderer

  override fun getSearchProviderId(): String = ClassSearchEverywhereContributor::class.java.simpleName

  override fun fetchWeightedElements(
    pattern: String,
    progressIndicator: ProgressIndicator,
    consumer: Processor<in FoundItemDescriptor<Any>>,
  ) {
    // We wrap the progressIndicator here to make sure we don't run standard search under the same indicator
    ProgressManager.getInstance().executeProcessUnderProgress(
      { fetchElementsConcurrently(pattern, SensitiveProgressWrapper(progressIndicator), consumer) }, progressIndicator)
  }

  override fun isElementSemantic(element: Any) = element is PsiItemWithSimilarity<*> && element.isPureSemantic

  override fun defaultFetchElements(pattern: String, progressIndicator: ProgressIndicator,
                                    consumer: Processor<in FoundItemDescriptor<Any>>) {
    super.fetchWeightedElements(pattern, progressIndicator, consumer)
  }

  override fun checkScopeIsDefaultAndAutoSet(): Boolean = isScopeDefaultAndAutoSet

  override fun syncSearchSettings() {
    itemsProvider.model = createModel(project)
    itemsProvider.searchScope = myScopeDescriptor.scope as GlobalSearchScope
  }

  override fun createExtendedInfo(): ExtendedInfo? = createPsiExtendedInfo(
    project = null, file = null, psiElement = ::tryPsiElementFromPossiblySemanticEntry
  )
}