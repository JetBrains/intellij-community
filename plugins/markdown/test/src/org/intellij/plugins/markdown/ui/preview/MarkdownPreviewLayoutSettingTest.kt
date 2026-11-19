// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.ui.preview

import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.util.Disposer
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.ui.JBSplitter
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.settings.MarkdownSettings
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * see IJPL-253568
 */
@TestApplication
class MarkdownPreviewLayoutSettingTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  @BeforeEach
  fun setUp() {
    val settings = MarkdownSettings.getInstance()
    val wasVerticalSplit = settings.isVerticalSplit
    Disposer.register(myFixture.testRootDisposable) {
      settings.update { it.isVerticalSplit = wasVerticalSplit }
    }
  }

  @Test
  fun testSplitHorizontallySurvivesRestoredPerFileOrientation(): Unit = timeoutRunBlocking {
    val settings = MarkdownSettings.getInstance()
    settings.update { it.isVerticalSplit = false }

    withContext(Dispatchers.EDT) {
      val splitter = splitterOf(settings)
      assertTrue(splitter.orientation, "setting must be applied to a freshly created editor")
    }
  }

  @Test
  fun testRestoredStateDoesNotOverrideSetting(): Unit = timeoutRunBlocking {
    val settings = MarkdownSettings.getInstance()
    settings.update { it.isVerticalSplit = false }

    withContext(Dispatchers.EDT) {
      val editorWithPreview = createEditor(settings)
      val splitter = UIUtil.findComponentOfType(editorWithPreview.component, JBSplitter::class.java)!!

      editorWithPreview.setState(
        TextEditorWithPreview.MyFileEditorState(TextEditorWithPreview.Layout.SHOW_EDITOR_AND_PREVIEW, null, null, false)
      )

      assertTrue(splitter.orientation, "global setting must win over restored per-file orientation")
    }
  }

  @Test
  fun testUndoKeepsManuallySelectedOrientation(): Unit = timeoutRunBlocking {
    val settings = MarkdownSettings.getInstance()
    settings.update { it.isVerticalSplit = false }

    withContext(Dispatchers.EDT) {
      val editorWithPreview = createEditor(settings)
      val splitter = UIUtil.findComponentOfType(editorWithPreview.component, JBSplitter::class.java)!!
      editorWithPreview.setVerticalSplit(false)

      val undoState = editorWithPreview.getState(FileEditorStateLevel.UNDO)
      editorWithPreview.setState(undoState)

      assertFalse(splitter.orientation, "undo must keep the manually selected orientation")
    }
  }

  @RequiresEdt
  private fun splitterOf(settings: MarkdownSettings): JBSplitter =
    UIUtil.findComponentOfType(createEditor(settings).component, JBSplitter::class.java)!!

  @RequiresEdt
  private fun createEditor(settings: MarkdownSettings): MarkdownEditorWithPreview {
    myFixture.configureByText("test.md", "# Heading\n\ntext\n")
    val file = myFixture.file.virtualFile
    val textEditor = TextEditorProvider.getInstance().getTextEditor(myFixture.editor)
    val preview = MarkdownPreviewFileEditor(myFixture.project, file, myFixture.editor.document)
    val editorWithPreview = MarkdownEditorWithPreview(textEditor, preview, myFixture.project, settings)
    Disposer.register(myFixture.testRootDisposable, editorWithPreview)
    return editorWithPreview
  }
}
