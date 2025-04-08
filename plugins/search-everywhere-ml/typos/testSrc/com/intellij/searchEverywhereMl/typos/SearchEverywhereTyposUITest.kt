package com.intellij.searchEverywhereMl.typos

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI
import com.intellij.ide.ui.IdeUiService
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.Processor
import org.junit.Assert
import javax.swing.JLabel
import javax.swing.ListCellRenderer

class SearchEverywhereTyposUITest : LightPlatformTestCase() {
  fun `test first actual search result gets preselected`() {
    val searchEverywhereUI = SearchEverywhereUI(project,
                                                listOf(MockSearchEverywhereContributor("Show Color Picker")),
                                                { _ -> null})
    // normally SearchEverywhereUI is registered against the baloon as parent disposable in SearchEverywhereManager
    Disposer.register(testRootDisposable, searchEverywhereUI)
    val seContext = IdeUiService.getInstance().createUiDataContext(searchEverywhereUI)
    val selected = seContext.getData(PlatformDataKeys.SELECTED_ITEM)
    Assert.assertNull(selected)
  }
}


private class MockSearchEverywhereContributor(private val elements: Collection<String>) : SearchEverywhereContributor<String> {
  constructor(vararg elements: String) : this(listOf(*elements))

  override fun getSearchProviderId(): String = this::class.java.simpleName
  override fun getGroupName(): String = this::class.java.simpleName
  override fun getSortWeight(): Int = 0
  override fun showInFindResults(): Boolean = true
  override fun processSelectedItem(selected: String, modifiers: Int, searchText: String): Boolean = true
  override fun getElementsRenderer(): ListCellRenderer<in String> = ListCellRenderer<String> { _, _, _, _, _ ->
    JLabel()
  }
  override fun isShownInSeparateTab(): Boolean = true

  override fun fetchElements(pattern: String, progressIndicator: ProgressIndicator, consumer: Processor<in String>) {
    elements.forEach { consumer.process(it) }
  }
}