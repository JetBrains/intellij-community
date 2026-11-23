// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.ui.preview.accessor

import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.awaitPendingEditorOpen
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.dispatchAllEventsInIdeEventQueue
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class MarkdownSourceLinkNavigatorTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  @Test
  fun `source link opens target file at offset`(): Unit = timeoutRunBlocking {
    val content = """
      openapi: 3.0.0
      paths:
        /cat: {}
    """.trimIndent()
    val targetFile = myFixture.addFileToProject("after/swagger.yaml", content).virtualFile
    val targetOffset = content.indexOf("/cat")

    withContext(Dispatchers.EDT) {
      val handled = MarkdownSourceLinkNavigator.navigate(myFixture.project, "source://after/swagger.yaml:$targetOffset")

      assertThat(handled).isTrue()
      dispatchAllEventsInIdeEventQueue()
      awaitPendingEditorOpen(myFixture.project)
      val editorManager = FileEditorManager.getInstance(myFixture.project)
      assertThat(editorManager.selectedFiles).containsExactly(targetFile)
      assertThat(editorManager.selectedTextEditor?.caretModel?.offset).isEqualTo(targetOffset)
    }
  }

  @Test
  fun `well formed source link is consumed when target file is unavailable`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val handled = MarkdownSourceLinkNavigator.navigate(myFixture.project, "source://missing.yaml:42")

      assertThat(handled).isTrue()
      dispatchAllEventsInIdeEventQueue()
      assertThat(FileEditorManager.getInstance(myFixture.project).selectedFiles).isEmpty()
    }
  }

  @Test
  fun `regular link is not consumed`() {
    val handled = MarkdownSourceLinkNavigator.navigate(myFixture.project, "https://www.jetbrains.com")

    assertThat(handled).isFalse()
  }
}
