package com.intellij.searchEverywhereMl.semantics.contributors

import com.intellij.concurrency.SensitiveProgressWrapper
import com.intellij.ide.actions.SearchEverywherePsiRenderer
import com.intellij.ide.actions.searcheverywhere.*
import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper.PsiItemWithPresentation
import com.intellij.ide.actions.searcheverywhere.footer.createPsiExtendedInfo
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.searchEverywhereMl.semantics.providers.SemanticFilesProvider
import com.intellij.util.Processor
import org.jetbrains.annotations.ApiStatus
import java.util.function.Consumer

// equivalent to f1(inp) ?: ... ?: fn(inp)
@ApiStatus.Internal
public fun <T, U> makeFirstYieldingNotNullOrNull(vararg fs: (T) -> U?): (T) -> U? =
  { inp -> fs.firstNotNullOfOrNull { f -> f(inp) } }

@ApiStatus.Internal
public fun tryPsiElementFromPossiblySematicEntry(entry: Any): PsiElement? =
  when (entry) {
    is PSIPresentationBgRendererWrapper.PsiItemWithPresentation -> entry.item
    is PSIPresentationBgRendererWrapper.ItemWithPresentation<*> -> when (val presUnwprapped = entry.item) {
      is PsiItemWithSimilarity<*> -> when (val semUnwrapped = presUnwprapped.value) {
        is PsiItemWithPresentation -> semUnwrapped.item
        is PsiElement -> semUnwrapped
        else -> null
      }
      else -> null
    }
    else -> null
  }

/**
 * Contributor that adds semantic search functionality when searching for files in Search Everywhere.
 * For search logic refer to [SemanticFilesProvider].
 * For indexing logic refer to [com.intellij.searchEverywhereMl.semantics.services.FileEmbeddingsStorage].
 * Delegates some of the rendering and data retrieval functionality to [FileSearchEverywhereContributor].
 */
@ApiStatus.Experimental
open class SemanticFileSearchEverywhereContributor(initEvent: AnActionEvent)
  : FileSearchEverywhereContributor(initEvent), SemanticSearchEverywhereContributor,
    SearchEverywhereConcurrentPsiElementsFetcher, PossibleSlowContributor {

  override val itemsProvider = SemanticFilesProvider(project)

  override var notifyCallback: Consumer<String>? = null

  override val psiElementsRenderer = elementsRenderer as SearchEverywherePsiRenderer

  override fun getSearchProviderId(): String = FileSearchEverywhereContributor::class.java.simpleName

  override fun fetchWeightedElements(
    pattern: String, progressIndicator: ProgressIndicator,
    consumer: Processor<in FoundItemDescriptor<Any>>,
  ) {
    // We wrap the progressIndicator here to make sure we don't run standard search under the same indicator
    ProgressManager.getInstance().executeProcessUnderProgress(
      { fetchElementsConcurrently(pattern, SensitiveProgressWrapper(progressIndicator), consumer) }, progressIndicator)
  }

  override fun isElementSemantic(element: Any) = element is PsiItemWithSimilarity<*> && element.isPureSemantic

  override fun defaultFetchElements(
    pattern: String, progressIndicator: ProgressIndicator,
    consumer: Processor<in FoundItemDescriptor<Any>>,
  ) {
    super.fetchWeightedElements(pattern, progressIndicator, consumer)
  }

  override fun checkScopeIsDefaultAndAutoSet(): Boolean = isScopeDefaultAndAutoSet

  override fun syncSearchSettings() {
    itemsProvider.model = createModel(project)
    itemsProvider.searchScope = myScopeDescriptor.scope as GlobalSearchScope
  }

  override fun createExtendedInfo(): ExtendedInfo? = createPsiExtendedInfo(
    project = null, file = null, psiElement = ::tryPsiElementFromPossiblySematicEntry
  )
}