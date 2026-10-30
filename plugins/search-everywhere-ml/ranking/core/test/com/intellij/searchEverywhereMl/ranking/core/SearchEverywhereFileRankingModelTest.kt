package com.intellij.searchEverywhereMl.ranking.core

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.searchEverywhereMl.SearchEverywhereTab

internal class SearchEverywhereFileRankingModelTest : SearchEverywhereRankingModelTest() {
  override val tab = SearchEverywhereTab.Files

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
      .findElementAndAssert { (it as PsiFileSystemItem).virtualFile == expectedFile }
      .isAtIndex(0)
  }
}
