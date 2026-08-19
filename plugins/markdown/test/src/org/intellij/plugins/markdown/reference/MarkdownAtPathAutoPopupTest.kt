// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.reference

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.fixtures.CompletionAutoPopupTestCase
import com.intellij.util.application
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class MarkdownAtPathAutoPopupTest : CompletionAutoPopupTestCase() {
  @Test
  fun `test completion finds nested file by name`() {
    myFixture.addFileToProject("src/bazillion/trillion/nesting/directories/UniqueNameForClass.kt", "")

    myFixture.configureByText("README.md", "See @UniqueName<caret>")
    IndexingTestUtil.waitUntilIndexesAreReady(project)
    type("F")

    assertNotNull(lookup)
    assertTrue(lookup!!.items.any { "src/bazillion/trillion/nesting/directories/UniqueNameForClass.kt" in it.allLookupStrings })
  }

  @Test
  fun `test at completion opens automatically and suggests recent files`() {
    EditorHistoryManager.getInstance(project).removeAllFiles()
    val recentFile = myFixture.addFileToProject("src/RecentClass.kt", "")
    application.invokeAndWait {
      FileEditorManager.getInstance(project).openFile(recentFile.virtualFile, true)
    }

    myFixture.configureByText("README.md", "See <caret>")
    type("@")

    assertNotNull(lookup)
    assertTrue(lookup!!.items.any { "src/RecentClass.kt" in it.allLookupStrings })
  }
}
