package com.intellij.searchEverywhereMl.semantics.contributors

import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributorFactory
import com.intellij.ide.actions.searcheverywhere.WeightedSearchEverywhereContributor
import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.searchEverywhereMl.semantics.providers.LocalSemanticActionsProvider
import com.intellij.searchEverywhereMl.semantics.providers.SemanticActionsProvider
import com.intellij.searchEverywhereMl.semantics.providers.ServerSemanticActionsProvider
import com.intellij.ui.JBColor
import com.intellij.util.Processor
import java.awt.Component
import javax.swing.ListCellRenderer


/**
 * Contributor that adds semantic search functionality when searching for actions in Search Everywhere
 * Delegates rendering and data retrieval functionality to [ActionSearchEverywhereContributor].
 * Can work with two types of action providers: server-based and local
 */
class SemanticActionSearchEverywhereContributor(
  project: Project?,
  contextComponent: Component?,
  editor: Editor?
) : WeightedSearchEverywhereContributor<GotoActionModel.MatchedValue>, LightEditCompatible, SemanticSearchEverywhereContributor {

  private val delegateContributor = ActionSearchEverywhereContributor(project, contextComponent, editor)

  private val myModel = GotoActionModel(project, contextComponent, editor)

  private val semanticActionsProvider: SemanticActionsProvider

  init {
    myModel.buildGroupMappings()

    semanticActionsProvider = if (Registry.`is`("search.everywhere.ml.semantic.actions.use.server.model")) {
      ServerSemanticActionsProvider(myModel)
    }
    else {
      LocalSemanticActionsProvider(myModel)
    }
  }

  override fun getSearchProviderId(): String = SemanticActionSearchEverywhereContributor::class.java.simpleName

  override fun getGroupName() = delegateContributor.groupName

  override fun getSortWeight() = delegateContributor.sortWeight

  override fun showInFindResults() = delegateContributor.showInFindResults()

  override fun isShownInSeparateTab() = delegateContributor.isShownInSeparateTab

  override fun processSelectedItem(selected: GotoActionModel.MatchedValue, modifiers: Int, searchText: String): Boolean {
    return delegateContributor.processSelectedItem(selected, modifiers, searchText)
  }

  override fun getElementsRenderer(): ListCellRenderer<in GotoActionModel.MatchedValue> {
    val defaultRenderer = delegateContributor.elementsRenderer
    return ListCellRenderer<GotoActionModel.MatchedValue> { list, element, index, isSelected, cellHasFocus ->
      val panel = defaultRenderer.getListCellRendererComponent(list, element, index, isSelected, cellHasFocus)

      panel.background = JBColor.GREEN.darker().darker()
      panel
    }
  }

  override fun fetchWeightedElements(pattern: String,
                                     progressIndicator: ProgressIndicator,
                                     consumer: Processor<in FoundItemDescriptor<GotoActionModel.MatchedValue>>) {
    if (pattern.isBlank()) {
      return
    }

    ProgressManager.getInstance().executeProcessUnderProgress(
      {
        for (descriptor in semanticActionsProvider.search(pattern)) {
          if (progressIndicator.isCanceled) {
            break
          }
          consumer.process(descriptor)
        }
      },
      progressIndicator
    )
  }

  override fun getDataForItem(element: GotoActionModel.MatchedValue, dataId: String) = delegateContributor.getDataForItem(element, dataId)

  companion object {
    class Factory : SearchEverywhereContributorFactory<GotoActionModel.MatchedValue> {
      override fun createContributor(initEvent: AnActionEvent): SemanticActionSearchEverywhereContributor {
        return SemanticActionSearchEverywhereContributor(
          initEvent.project,
          initEvent.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT),
          initEvent.getData(CommonDataKeys.EDITOR)
        )
      }
    }
  }
}