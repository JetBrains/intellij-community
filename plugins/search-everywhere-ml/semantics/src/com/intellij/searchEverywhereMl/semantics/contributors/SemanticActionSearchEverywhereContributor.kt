package com.intellij.searchEverywhereMl.semantics.contributors

import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.ide.actions.searcheverywhere.PossibleSlowContributor
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.ide.util.gotoByName.GotoActionModel.MatchedValue
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.registry.Registry
import com.intellij.searchEverywhereMl.SemanticSearchEverywhereContributor
import com.intellij.searchEverywhereMl.semantics.contributors.SearchEverywhereConcurrentElementsFetcher.Companion.ORDERED_PRIORITIES
import com.intellij.searchEverywhereMl.semantics.contributors.SearchEverywhereConcurrentElementsFetcher.DescriptorPriority
import com.intellij.searchEverywhereMl.semantics.providers.LocalSemanticActionsProvider
import com.intellij.searchEverywhereMl.semantics.providers.SemanticActionsProvider
import com.intellij.searchEverywhereMl.semantics.providers.ServerSemanticActionsProvider
import com.intellij.searchEverywhereMl.semantics.settings.SemanticSearchSettings
import com.intellij.ui.JBColor
import com.intellij.util.Processor
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.locks.ReentrantLock
import javax.swing.ListCellRenderer
import kotlin.concurrent.withLock


/**
 * Contributor that adds semantic search functionality when searching for actions in Search Everywhere.
 * For search logic refer to [SemanticActionsProvider].
 * For indexing logic refer to [com.intellij.searchEverywhereMl.semantics.services.ActionEmbeddingsStorage].
 * Delegates rendering and data retrieval functionality to [ActionSearchEverywhereContributor].
 * Can work with two types of action providers: server-based and local
 */
@ApiStatus.Experimental
class SemanticActionSearchEverywhereContributor(defaultContributor: ActionSearchEverywhereContributor)
  : ActionSearchEverywhereContributor(defaultContributor), SemanticSearchEverywhereContributor,
    SearchEverywhereConcurrentElementsFetcher<MatchedValue, MatchedValue>, PossibleSlowContributor {
  override val itemsProvider = SemanticSearchSettings.getInstance().run {
    if (getUseRemoteActionsServer()) ServerSemanticActionsProvider(model) else LocalSemanticActionsProvider(model)
  }

  override fun getDesiredResultsCount() = DESIRED_RESULTS_COUNT

  override fun getPriorityThresholds() = PRIORITY_THRESHOLDS

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
    fetchElementsConcurrently(pattern, progressIndicator, consumer)
  }

  override fun prepareSemanticDescriptor(descriptor: FoundItemDescriptor<MatchedValue>,
                                         knownItems: MutableList<FoundItemDescriptor<MatchedValue>>,
                                         mutex: ReentrantLock,
                                         durationMs: Long): FoundItemDescriptor<MatchedValue>? = mutex.withLock {
    val knownEqualAction = knownItems.firstOrNull { checkActionsEqual(it.item, descriptor.item) }
    if (knownEqualAction != null) {
      mergeOrSkipAction(descriptor, knownEqualAction, durationMs)
    }
    else {
      knownItems.add(descriptor)
      descriptor
    }
  }

  private fun mergeOrSkipAction(newItem: FoundItemDescriptor<MatchedValue>,
                                existingItem: FoundItemDescriptor<MatchedValue>,
                                durationMs: Long): FoundItemDescriptor<MatchedValue>? {
    if (durationMs > 70) {
      // elements are frozen after 100ms delay and shouldn't be re-ordered
      logger.debug("Skip merge for '${newItem.item ?: "unknown"}', because duration is $durationMs")
      return null
    }
    logger.debug("Merge semantic action '${newItem.item ?: "unknown"}', because duration: $durationMs")
    val mergedElement = existingItem.item.mergeWith(newItem.item) as MatchedValue
    return FoundItemDescriptor(mergedElement, existingItem.weight + 1)
  }

  override fun prepareStandardDescriptor(descriptor: FoundItemDescriptor<MatchedValue>,
                                         knownItems: MutableList<FoundItemDescriptor<MatchedValue>>,
                                         mutex: ReentrantLock): FoundItemDescriptor<MatchedValue> = mutex.withLock {
    val equal = knownItems.firstOrNull { checkActionsEqual(it.item, descriptor.item) }
    return if (equal != null) {
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

  override fun FoundItemDescriptor<MatchedValue>.findPriority(): DescriptorPriority {
    return ORDERED_PRIORITIES.first { item.similarityScore!! > getPriorityThresholds()[it]!! }
  }

  override fun syncSearchSettings() {
    itemsProvider.includeDisabledActions = myDisabledActions
  }

  companion object {
    private val logger by lazy { Logger.getInstance(SemanticActionSearchEverywhereContributor::class.java) }

    val PRIORITY_THRESHOLDS = (ORDERED_PRIORITIES zip listOf(0.35, 0.25, 0.2)).toMap()
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