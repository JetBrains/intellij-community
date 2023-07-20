package com.intellij.searchEverywhereMl.ranking

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.openapi.progress.ProgressIndicator
import javax.swing.JLabel
import javax.swing.ListCellRenderer
import com.intellij.util.Processor

class MockSearchEverywhereContributor(val name: String? = null) : SearchEverywhereContributor<Any> {
  override fun getSearchProviderId(): String = name ?: javaClass.name
  override fun getGroupName() = "Mock"
  override fun getSortWeight() = 0
  override fun showInFindResults() = false

  override fun getElementsRenderer(): ListCellRenderer<in Any> {
    return ListCellRenderer<Any> { _, _, _, _, _ ->
      JLabel()
    }
  }

  override fun getDataForItem(element: Any, dataId: String) = null
  override fun processSelectedItem(selected: Any, modifiers: Int, searchText: String) = false
  override fun fetchElements(pattern: String, progressIndicator: ProgressIndicator, consumer: Processor<in Any>) {}
}