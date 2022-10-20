package com.intellij.ide.actions.searcheverywhere.ml

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.util.Processor
import javax.swing.JLabel
import javax.swing.ListCellRenderer

class MockSearchEverywhereContributor : SearchEverywhereContributor<Any> {
  override fun getSearchProviderId(): String = javaClass.name
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