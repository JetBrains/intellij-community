package com.intellij.searchEverywhereMl.ranking

import com.intellij.ide.actions.searcheverywhere.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.components.JBList


internal class SearchEverywhereReorderingListener(private val listModel: SearchListModel,
                                                  private val resultList: JBList<Any>,
                                                  private val selectionTracker: SEListSelectionTracker) : SearchListener {
  override fun elementsAdded(list: List<SearchEverywhereFoundElementInfo?>) {}
  override fun elementsRemoved(list: List<SearchEverywhereFoundElementInfo?>) {}
  override fun contributorWaits(contributor: SearchEverywhereContributor<*>) {}
  override fun contributorFinished(contributor: SearchEverywhereContributor<*>, hasMore: Boolean) {}
  override fun searchStarted(pattern: String, contributors: Collection<SearchEverywhereContributor<*>?>) {}

  override fun searchFinished(hasMoreContributors: Map<SearchEverywhereContributor<*>?, Boolean?>) {
    val updatedElements = SearchEverywhereRankingDiffCalculator.calculateDiffIfApplicable(listModel.foundElementsInfo) ?: return

    val contributorsHasMore = listModel.foundElementsMap.keys.associateWith {
      listModel.hasMoreElements(it)
    }

    listModel.clear()

    selectionTracker.performAndKeepSelection {
      listModel.addElements(updatedElements)
    }

    contributorsHasMore.forEach { (contributor, hasMore) ->
      listModel.setHasMore(contributor, hasMore)
    }

    ApplicationManager.getApplication().invokeLater { resultList.repaint() }
  }
}