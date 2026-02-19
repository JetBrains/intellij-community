@file:OptIn(IntellijInternalApi::class)

package com.intellij.searchEverywhereMl.ranking.core

import com.intellij.ide.actions.GotoFileItemProvider
import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.ide.util.gotoByName.GotoFileModel
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.searchEverywhereMl.SearchEverywhereTab

internal class SearchEverywhereFileRankingModelTest : SearchEverywhereRankingModelTest() {
  override val tab = SearchEverywhereTab.Files
  private val gotoFileModel by lazy { GotoFileModel(project) }
  private val gotoFileModelProvider: GotoFileItemProvider by lazy { gotoFileModel.getItemProvider(null) as GotoFileItemProvider }
  private val viewModel by lazy { StubChooseByNameViewModel(gotoFileModel) }

  fun `test exact match appears at the top`() {
    var expectedFile: VirtualFile? = null

    module {
      source {
        createPackage("psi.codeStyle") {
          file("MinusculeMatcher.java") { expectedFile = it }
          file("MinusculeMatcherImpl.java")
          file("OtherMinusculeMatcher.java")
        }
      }
    }

    performSearchFor("codeStyle/MinusculeMatcher.java")
      .findElementAndAssert { (it.item as PsiFileSystemItem).virtualFile == expectedFile }
      .isAtIndex(0)
  }

  override fun filterElements(searchQuery: String): List<FoundItemDescriptor<*>> {
    val elements = mutableListOf<FoundItemDescriptor<*>>()
    gotoFileModelProvider.filterElementsWithWeights(viewModel, searchQuery, false, mockProgressIndicator) {
      elements.add(it)
      true
    }
    return elements
  }
}