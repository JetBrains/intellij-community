package com.intellij.searchEverywhereMl.typos

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellCheckResult
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellingCorrector
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI
import com.intellij.ide.ui.IdeUiService
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.Processor
import org.junit.Assert
import javax.swing.JLabel
import javax.swing.ListCellRenderer

class SearchEverywhereTyposUITest : LightPlatformTestCase() {
  fun `test first actual search result gets preselected`() {
    val searchEverywhereUI = SearchEverywhereUI(project,
                                                listOf(MockSearchEverywhereContributor("Show Color Picker")),
                                                { _ -> null},
                                                MockSpellingCorrector())
    val elements = PlatformTestUtil.waitForFuture(searchEverywhereUI.findElementsForPattern ("colop"))
    assert(elements.size == 2)
    val seContext = IdeUiService.getInstance().createUiDataContext(searchEverywhereUI)
    val selected = seContext.getData(PlatformDataKeys.SELECTED_ITEM)
    Assert.assertEquals("Show Color Picker", selected)
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

private class MockSpellingCorrector : SearchEverywhereSpellingCorrector {
  override fun isAvailableInTab(tabId: String): Boolean {
    return true
  }

  override fun checkSpellingOf(query: String): SearchEverywhereSpellCheckResult {
    return SearchEverywhereSpellCheckResult.Correction("color", 1.0)
  }
}