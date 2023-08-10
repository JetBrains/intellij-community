package com.intellij.searchEverywhereMl.semantics.contributors

import com.intellij.concurrency.SensitiveProgressWrapper
import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.ide.actions.searcheverywhere.PossibleSlowContributor
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.ide.util.gotoByName.GotoActionModel.MatchedValue
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.searchEverywhereMl.SemanticSearchEverywhereContributor
import com.intellij.searchEverywhereMl.semantics.providers.LocalSemanticActionsProvider
import com.intellij.searchEverywhereMl.semantics.providers.SemanticActionsProvider
import com.intellij.searchEverywhereMl.semantics.providers.ServerSemanticActionsProvider
import com.intellij.searchEverywhereMl.semantics.settings.SemanticSearchSettings
import com.intellij.ui.JBColor
import com.intellij.util.Processor
import javax.swing.ListCellRenderer


/**
 * Contributor that adds semantic search functionality when searching for actions in Search Everywhere
 * Delegates rendering and data retrieval functionality to [ActionSearchEverywhereContributor].
 * Can work with two types of action providers: server-based and local
 */
class SemanticActionSearchEverywhereContributor(defaultContributor: ActionSearchEverywhereContributor)
  : ActionSearchEverywhereContributor(defaultContributor), SemanticSearchEverywhereContributor, PossibleSlowContributor {

  private val semanticActionsProvider: SemanticActionsProvider

  init {
    val settings = SemanticSearchSettings.getInstance()
    semanticActionsProvider = if (settings.getUseRemoteActionsServer()) {
      ServerSemanticActionsProvider(model)
    }
    else {
      LocalSemanticActionsProvider(model)
    }
  }

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
    val knownItems = mutableSetOf<FoundItemDescriptor<MatchedValue>>()

    val semanticSearchIndicatorWrapper = SensitiveProgressWrapper(progressIndicator)
    ProgressManager.getInstance().executeProcessUnderProgress(
      {
        for (descriptor in semanticActionsProvider.search(pattern)) {
          if (semanticSearchIndicatorWrapper.isCanceled) break
          val descriptorToProcess: FoundItemDescriptor<MatchedValue>

          val equal = knownItems.firstOrNull { checkActionsEqual(it.item, descriptor.item) }
          if (equal != null) {
            val mergedElement = equal.item.mergeWith(descriptor.item) as MatchedValue
            descriptorToProcess = FoundItemDescriptor(mergedElement, equal.weight + 1)
          }
          else {
            knownItems.add(descriptor)
            descriptorToProcess = descriptor
          }
          consumer.process(descriptorToProcess)
        }
      },
      semanticSearchIndicatorWrapper
    )

    super.fetchWeightedElements(pattern, progressIndicator) { descriptor ->
      val descriptorToProcess: FoundItemDescriptor<MatchedValue>
      val equal = knownItems.firstOrNull { checkActionsEqual(it.item, descriptor.item) }
      if (equal != null) {
        if (equal.item.shouldBeMergedIntoAnother()) {
          val mergedElement = descriptor.item.mergeWith(equal.item) as MatchedValue
          descriptorToProcess = FoundItemDescriptor(mergedElement, descriptor.weight + 1)
        }
        else {
          descriptorToProcess = descriptor
        }
      }
      else {
        knownItems.add(descriptor)
        descriptorToProcess = descriptor
      }
      consumer.process(descriptorToProcess)
    }
  }

  companion object {
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