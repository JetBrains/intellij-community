// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.ui.preview

import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.JBSplitter
import com.intellij.util.ui.UIUtil
import org.intellij.plugins.markdown.settings.MarkdownSettings

/**
 * see IJPL-253568
 */
class MarkdownPreviewLayoutSettingTest : BasePlatformTestCase() {
  fun testSplitHorizontallySurvivesRestoredPerFileOrientation() {
    val settings = MarkdownSettings.getInstance(project)
    settings.update { it.isVerticalSplit = false }

    val splitter = splitterOf(settings)
    assertTrue("setting must be applied to a freshly created editor", splitter.orientation)
  }

  fun testRestoredStateDoesNotOverrideSetting() {
    val settings = MarkdownSettings.getInstance(project)
    settings.update { it.isVerticalSplit = false }

    val editorWithPreview = createEditor(settings)
    val splitter = UIUtil.findComponentOfType(editorWithPreview.component, JBSplitter::class.java)!!

    editorWithPreview.setState(
      TextEditorWithPreview.MyFileEditorState(TextEditorWithPreview.Layout.SHOW_EDITOR_AND_PREVIEW, null, null, false)
    )

    assertTrue("global setting must win over restored per-file orientation", splitter.orientation)
  }

  private fun splitterOf(settings: MarkdownSettings): JBSplitter =
    UIUtil.findComponentOfType(createEditor(settings).component, JBSplitter::class.java)!!

  private fun createEditor(settings: MarkdownSettings): MarkdownEditorWithPreview {
    myFixture.configureByText("test.md", "# Heading\n\ntext\n")
    val file = myFixture.file.virtualFile
    val textEditor = TextEditorProvider.getInstance().getTextEditor(myFixture.editor)
    val preview = MarkdownPreviewFileEditor(project, file, myFixture.editor.document)
    val editorWithPreview = MarkdownEditorWithPreview(textEditor, preview, project, settings)
    Disposer.register(testRootDisposable, editorWithPreview)
    return editorWithPreview
  }
}
