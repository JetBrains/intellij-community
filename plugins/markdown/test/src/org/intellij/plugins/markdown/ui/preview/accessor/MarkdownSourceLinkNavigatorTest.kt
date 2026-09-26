// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.ui.preview.accessor

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.testFramework.dispatchAllEventsInIdeEventQueue
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class MarkdownSourceLinkNavigatorTest : BasePlatformTestCase() {
  @Test
  fun `source link opens target file at offset`() {
    val content = """
      openapi: 3.0.0
      paths:
        /cat: {}
    """.trimIndent()
    val targetFile = myFixture.addFileToProject("after/swagger.yaml", content).virtualFile
    val targetOffset = content.indexOf("/cat")

    val handled = MarkdownSourceLinkNavigator.navigate(project, "source://after/swagger.yaml:$targetOffset")

    assertThat(handled).isTrue()
    dispatchAllEventsInIdeEventQueue()
    val editorManager = FileEditorManager.getInstance(project)
    assertThat(editorManager.selectedFiles).containsExactly(targetFile)
    assertThat(editorManager.selectedTextEditor?.caretModel?.offset).isEqualTo(targetOffset)
  }

  @Test
  fun `well formed source link is consumed when target file is unavailable`() {
    val handled = MarkdownSourceLinkNavigator.navigate(project, "source://missing.yaml:42")

    assertThat(handled).isTrue()
    dispatchAllEventsInIdeEventQueue()
    assertThat(FileEditorManager.getInstance(project).selectedFiles).isEmpty()
  }

  @Test
  fun `regular link is not consumed`() {
    val handled = MarkdownSourceLinkNavigator.navigate(project, "https://www.jetbrains.com")

    assertThat(handled).isFalse()
  }
}
