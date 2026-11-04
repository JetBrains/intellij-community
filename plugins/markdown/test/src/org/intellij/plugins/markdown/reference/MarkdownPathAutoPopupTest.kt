// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.reference

import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.fixtures.CompletionAutoPopupTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.application
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
class MarkdownPathAutoPopupTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  private val lookup: LookupImpl? get() = CompletionAutoPopupTestUtil.getLookup(myFixture)

  private fun type(s: String) = CompletionAutoPopupTestUtil.type(myFixture, s)

  private fun runTest(block: () -> Unit) = CompletionAutoPopupTestUtil.runWithAutoPopupEnabled<Throwable>(myFixture, block)

  @Test
  fun `test completion finds nested file by name`(): Unit = runTest {
    myFixture.addFileToProject("src/bazillion/trillion/nesting/directories/UniqueNameForClass.kt", "")

    myFixture.configureByText("README.md", "See @UniqueName<caret>")
    IndexingTestUtil.waitUntilIndexesAreReady(myFixture.project)
    type("F")

    assertNotNull(lookup)
    assertTrue(lookup!!.items.any { "src/bazillion/trillion/nesting/directories/UniqueNameForClass.kt" in it.allLookupStrings })
  }

  @Test
  fun `test at completion opens automatically and suggests recent files`(): Unit = runTest {
    EditorHistoryManager.getInstance(myFixture.project).removeAllFiles()
    val recentFile = myFixture.addFileToProject("src/RecentClass.kt", "")
    FileEditorManager.getInstance(myFixture.project).requestOpenFile(recentFile.virtualFile).get()

    myFixture.configureByText("README.md", "See <caret>")
    type("@")

    assertNotNull(lookup)
    assertTrue(lookup!!.items.any { "src/RecentClass.kt" in it.allLookupStrings })
  }

  @Test
  fun `test slash completion opens automatically and suggests recent files`(): Unit = runTest {
    EditorHistoryManager.getInstance(myFixture.project).removeAllFiles()
    val recentFile = myFixture.addFileToProject("src/RecentClass.kt", "")
    FileEditorManager.getInstance(myFixture.project).requestOpenFile(recentFile.virtualFile).get()

    myFixture.configureByText("README.md", "See <caret>")
    type("/")

    assertNotNull(lookup)
    assertTrue(lookup!!.items.any { "src/RecentClass.kt" in it.allLookupStrings })
  }

  @Test
  fun `test slash completion finds nested file by name`(): Unit = runTest {
    val path = "src/bazillion/trillion/nesting/directories/UniqueNameForClass.kt"
    myFixture.addFileToProject(path, "")

    myFixture.configureByText("README.md", "See /UniqueName<caret>")
    IndexingTestUtil.waitUntilIndexesAreReady(myFixture.project)
    type("F")

    assertNotNull(lookup)
    assertTrue(lookup!!.items.any { path in it.allLookupStrings })
  }

  @Test
  fun `test relative slash completion keeps the relative prefix`(): Unit = runTest {
    val path = "src/bazillion/trillion/nesting/directories/UniqueNameForClass.kt"
    myFixture.addFileToProject(path, "")

    myFixture.configureByText("README.md", "See ./UniqueName<caret>")
    IndexingTestUtil.waitUntilIndexesAreReady(myFixture.project)
    type("F")

    val item = lookup!!.items.first { path in it.allLookupStrings }
    application.invokeAndWait {
      lookup!!.finishLookup('\n', item)
      myFixture.checkResult("See ./$path<caret>")
    }
  }
}
