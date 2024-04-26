package com.intellij.searchEverywhereMl.semantics.contributors

import com.intellij.concurrency.SensitiveProgressWrapper
import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.ide.actions.searcheverywhere.PossibleSlowContributor
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.ide.util.gotoByName.GotoActionModel.MatchedValue
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.impl.Utils.runUpdateSessionForActionSearch
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ml.embeddings.search.utils.ScoredText
import com.intellij.searchEverywhereMl.SemanticSearchEverywhereContributor
import com.intellij.searchEverywhereMl.semantics.contributors.SearchEverywhereConcurrentElementsFetcher.Companion.ORDERED_PRIORITIES
import com.intellij.searchEverywhereMl.semantics.contributors.SearchEverywhereConcurrentElementsFetcher.DescriptorPriority
import com.intellij.searchEverywhereMl.semantics.providers.LocalSemanticActionsProvider
import com.intellij.searchEverywhereMl.semantics.providers.ServerSemanticActionsProvider
import com.intellij.searchEverywhereMl.semantics.settings.SearchEverywhereSemanticSettings
import com.intellij.ui.JBColor
import com.intellij.util.Processor
import com.intellij.util.TimeoutUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.ApiStatus
import javax.swing.ListCellRenderer


/**
 * Contributor that adds semantic search functionality when searching for actions in Search Everywhere.
 * For search logic refer to [com.intellij.searchEverywhereMl.semantics.providers.SemanticActionsProvider].
 * For indexing logic refer to [com.intellij.platform.ml.embeddings.search.services.ActionEmbeddingsStorage].
 * Delegates rendering and data retrieval functionality to [ActionSearchEverywhereContributor].
 * Can work with two types of action providers: server-based and local
 */
@ApiStatus.Experimental
class SemanticActionSearchEverywhereContributor(defaultContributor: ActionSearchEverywhereContributor)
  : ActionSearchEverywhereContributor(defaultContributor), SemanticSearchEverywhereContributor,
    SearchEverywhereConcurrentElementsFetcher<MatchedValue, MatchedValue>, PossibleSlowContributor {
  override val itemsProvider
    get() = throw UnsupportedOperationException()

  override val desiredResultsCount
    get() = DESIRED_RESULTS_COUNT

  override val priorityThresholds
    get() = PRIORITY_THRESHOLDS

  override fun isElementSemantic(element: Any): Boolean {
    return (element is MatchedValue && element.type == GotoActionModel.MatchedValueType.SEMANTIC)
  }

  override fun getElementsRenderer(): ListCellRenderer<in MatchedValue> {
    return ListCellRenderer { list, element, index, isSelected, cellHasFocus ->
      val panel = super.getElementsRenderer().getListCellRendererComponent(list, element, index, isSelected, cellHasFocus)

      if (Registry.`is`("search.everywhere.ml.semantic.highlight.items") && isElementSemantic(element)) {
        panel.background = JBColor.GREEN.darker().darker()
      }
      panel
    }
  }

  override fun fetchWeightedElements(pattern: String,
                                     progressIndicator: ProgressIndicator,
                                     consumer: Processor<in FoundItemDescriptor<MatchedValue>>) {
    // We wrap the progressIndicator here to make sure we don't run standard search under the same indicator
    ProgressManager.getInstance().runProcess(
      { fetchElementsConcurrently(pattern, SensitiveProgressWrapper(progressIndicator), consumer) },
      progressIndicator
    )
  }

  override fun prepareSemanticDescriptor(descriptor: FoundItemDescriptor<MatchedValue>,
                                         knownItems: MutableList<FoundItemDescriptor<MatchedValue>>,
                                         durationMs: Long): () -> FoundItemDescriptor<MatchedValue>? = {
    val knownEqualAction = knownItems.firstOrNull { checkActionsEqual(it.item, descriptor.item) }
    if (knownEqualAction != null) {
      mergeOrSkipAction(descriptor, knownEqualAction, durationMs)
    }
    else {
      knownItems.add(descriptor)
      descriptor
    }
  }

  @RequiresBackgroundThread
  override fun fetchElementsConcurrently(pattern: String,
                                         progressIndicator: ProgressIndicator,
                                         consumer: Processor<in FoundItemDescriptor<MatchedValue>>) {
    runBlockingCancellable {
      model.buildGroupMappings()
      runUpdateSessionForActionSearch(model.updateSession) { presentationProvider ->
        val knownItems = mutableListOf<FoundItemDescriptor<MatchedValue>>()
        val mutex = Mutex()

        val itemsProvider = SearchEverywhereSemanticSettings.getInstance().run {
          if (getUseRemoteActionsServer()) ServerSemanticActionsProvider(model, presentationProvider)
          else LocalSemanticActionsProvider(model, presentationProvider)
        }
        itemsProvider.includeDisabledActions = myDisabledActions

        val standardSearchJob = launch {
          doFetchItems(this, presentationProvider, pattern) {
            val prepareDescriptor = prepareStandardDescriptor(it, knownItems)
            val descriptor = mutex.withLock { prepareDescriptor() }
            blockingContext {
              consumer.process(descriptor)
            }
          }
        }

        val searchStart = System.nanoTime()
        launch {
          var foundItemsCount = 0
          val cachedMatches = mutableListOf<ScoredText>()

          val semanticMatches = itemsProvider.searchIfEnabled(pattern, priorityThresholds[DescriptorPriority.LOW])
          if (semanticMatches.isEmpty()) return@launch
          standardSearchJob.join()
          for (priority in ORDERED_PRIORITIES) {
            val iterator = if (priority == DescriptorPriority.HIGH) semanticMatches.iterator()
            else cachedMatches.filter { it.findPriority() == priority }.iterator()

            while (iterator.hasNext()) {
              ensureActive()
              val match = iterator.next()
              if (priority == DescriptorPriority.HIGH && match.findPriority() != priority) {
                cachedMatches.add(match)
                continue
              }

              for (descriptor in itemsProvider.createItemDescriptors(match.text, match.similarity, pattern)) {
                val prepareDescriptor = prepareSemanticDescriptor(descriptor, knownItems, TimeoutUtil.getDurationMillis(searchStart))
                mutex.withLock { prepareDescriptor() }?.let {
                  blockingContext { consumer.process(it) }
                  foundItemsCount++
                }
                if (priority != DescriptorPriority.HIGH && foundItemsCount >= desiredResultsCount) return@launch
              }
            }
            if (progressIndicator.isCanceled || foundItemsCount >= desiredResultsCount) return@launch
          }
        }
      }
    }
  }

  private fun mergeOrSkipAction(newItem: FoundItemDescriptor<MatchedValue>,
                                existingItem: FoundItemDescriptor<MatchedValue>,
                                durationMs: Long): FoundItemDescriptor<MatchedValue>? {
    if (durationMs > 70) {
      // elements are frozen after 100ms delay and shouldn't be re-ordered
      logger.debug { "Skip merge for '${newItem.item ?: "unknown"}', because duration is $durationMs" }
      return null
    }
    logger.debug { "Merge semantic action '${newItem.item ?: "unknown"}', because duration: $durationMs" }
    val mergedElement = existingItem.item.mergeWith(newItem.item) as MatchedValue
    return FoundItemDescriptor(mergedElement, existingItem.weight + 1)
  }

  override fun prepareStandardDescriptor(descriptor: FoundItemDescriptor<MatchedValue>,
                                         knownItems: MutableList<FoundItemDescriptor<MatchedValue>>): () -> FoundItemDescriptor<MatchedValue> = {
    val equal = knownItems.firstOrNull { checkActionsEqual(it.item, descriptor.item) }
    if (equal != null) {
      if (equal.item.shouldBeMergedIntoAnother()) {
        val mergedElement = descriptor.item.mergeWith(equal.item) as MatchedValue
        FoundItemDescriptor(mergedElement, descriptor.weight + 1)
      }
      else {
        descriptor
      }
    }
    else {
      knownItems.add(descriptor)
      descriptor
    }
  }

  override fun defaultFetchElements(pattern: String,
                                    progressIndicator: ProgressIndicator,
                                    consumer: Processor<in FoundItemDescriptor<MatchedValue>>) {
    super.fetchWeightedElements(pattern, progressIndicator, consumer)
  }

  override fun ScoredText.findPriority(): DescriptorPriority {
    return ORDERED_PRIORITIES.first { similarity > priorityThresholds[it]!! }
  }

  override fun syncSearchSettings() {
    throw UnsupportedOperationException()
  }

  companion object {
    private val logger = Logger.getInstance(SemanticActionSearchEverywhereContributor::class.java)

    val PRIORITY_THRESHOLDS = (ORDERED_PRIORITIES zip listOf(0.68, 0.5, 0.4)).toMap()
    private const val DESIRED_RESULTS_COUNT = 10

    private fun extractAction(item: Any): AnAction? {
      if (item is AnAction) return item
      return ((if (item is MatchedValue) item.value else item) as? GotoActionModel.ActionWrapper)?.action
    }

    private fun checkActionsEqual(lhs: Any, rhs: Any): Boolean {
      val lhsAction = extractAction(lhs)
      val rhsAction = extractAction(rhs)
      return lhsAction != null && rhsAction != null && lhsAction == rhsAction
    }
  }
}