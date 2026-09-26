// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.ui.preview

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview.Layout
import com.intellij.openapi.fileEditor.TextEditorWithPreview.MyFileEditorState
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.intellij.plugins.markdown.editor.livepreview.isLivePreviewEnabled
import org.intellij.plugins.markdown.settings.MarkdownSettings
import javax.swing.JComponent
import javax.swing.JPanel

class MarkdownEditorWithPreviewLivePreviewTest : BasePlatformTestCase() {
  private val panelProvider = StubHtmlPanelProvider()

  override fun setUp() {
    super.setUp()
    val properties = PropertiesComponent.getInstance()
    Disposer.register(testRootDisposable) { properties.unsetValue(MarkdownEditorWithPreview.LIVE_PREVIEW_PROPERTY) }
    ExtensionTestUtil.maskExtensions(MarkdownHtmlPanelProvider.EP_NAME, listOf(panelProvider), testRootDisposable)
    myFixture.configureByText("test.md", "# Heading\n\ntext\n")
  }

  fun testLivePreviewLayoutMarksOnlyTheTextEditor() {
    val editorWithPreview = createEditor()
    assertFalse(editorWithPreview.editor.isLivePreviewEnabled())

    editorWithPreview.setLivePreviewLayout()
    assertEquals(Layout.SHOW_EDITOR, editorWithPreview.getLayout())
    assertTrue(editorWithPreview.isLivePreviewLayout)
    assertTrue(editorWithPreview.editor.isLivePreviewEnabled())

    editorWithPreview.setLayout(Layout.SHOW_EDITOR_AND_PREVIEW)
    assertFalse(editorWithPreview.isLivePreviewLayout)
    assertFalse(editorWithPreview.editor.isLivePreviewEnabled())

    editorWithPreview.setLivePreviewLayout()
    editorWithPreview.setLayout(Layout.SHOW_EDITOR)
    assertFalse("Editor Only must turn live preview off", editorWithPreview.editor.isLivePreviewEnabled())
  }

  fun testNewEditorStartsInTheLastChosenLivePreviewLayout() {
    createEditor().setLivePreviewLayout()
    assertTrue(createEditor().editor.isLivePreviewEnabled())
  }

  fun testRestoredLayoutWithPreviewTurnsLivePreviewOff() {
    createEditor().setLivePreviewLayout()
    val editorWithPreview = createEditor()

    editorWithPreview.setState(MyFileEditorState(Layout.SHOW_PREVIEW, null, null, false))

    assertEquals(Layout.SHOW_PREVIEW, editorWithPreview.getLayout())
    assertFalse(editorWithPreview.editor.isLivePreviewEnabled())
  }

  fun testViewActionsPutLivePreviewBeforePreviewOnly() {
    val editorWithPreview = createEditor()
    editorWithPreview.setLivePreviewLayout()

    assertEquals(listOf(EDITOR_ONLY_ACTION_ID, EDITOR_AND_PREVIEW_ACTION_ID, LIVE_PREVIEW_ACTION_ID, PREVIEW_ONLY_ACTION_ID),
                 editorWithPreview.viewActionIds())
    assertEquals(listOf(LIVE_PREVIEW_ACTION_ID), editorWithPreview.selectedViewActionIds())

    editorWithPreview.setLayout(Layout.SHOW_EDITOR)
    assertEquals(listOf(EDITOR_ONLY_ACTION_ID), editorWithPreview.selectedViewActionIds())
  }

  fun testWithoutPreviewOnlyEditorAndLivePreviewAreOffered() {
    panelProvider.available = false
    val editorWithPreview = createEditor()

    assertEquals(listOf(EDITOR_ONLY_ACTION_ID, LIVE_PREVIEW_ACTION_ID), editorWithPreview.viewActionIds())
    assertEquals(listOf(EDITOR_ONLY_ACTION_ID), editorWithPreview.selectedViewActionIds())

    editorWithPreview.setState(MyFileEditorState(Layout.SHOW_EDITOR_AND_PREVIEW, null, null, false))
    assertEquals(Layout.SHOW_EDITOR, editorWithPreview.getLayout())

    editorWithPreview.setLayout(Layout.SHOW_PREVIEW)
    assertEquals(Layout.SHOW_EDITOR, editorWithPreview.getLayout())

    editorWithPreview.setLivePreviewLayout()
    assertEquals(listOf(LIVE_PREVIEW_ACTION_ID), editorWithPreview.selectedViewActionIds())
  }

  private fun createEditor(): MarkdownEditorWithPreview {
    val file = myFixture.file.virtualFile
    val textEditor = TextEditorProvider.getInstance().createEditor(project, file) as TextEditor
    val preview = MarkdownPreviewFileEditor(project, file, textEditor.editor.document)
    val editorWithPreview = MarkdownEditorWithPreview(textEditor, preview, project, MarkdownSettings.getInstance(project))
    Disposer.register(testRootDisposable, editorWithPreview)
    // The UI resolves the initial layout.
    editorWithPreview.component
    return editorWithPreview
  }

  private fun MarkdownEditorWithPreview.viewActions(): List<AnAction> {
    val event = TestActionEvent.createTestEvent(SimpleDataContext.getSimpleContext(PlatformCoreDataKeys.FILE_EDITOR, this))
    val actions = tabActions.getChildren(event).toList()
    assertFalse("The tab header must show the view actions", actions.isEmpty())
    return actions
  }

  private fun MarkdownEditorWithPreview.viewActionIds(): List<String?> {
    return viewActions().map { ActionManager.getInstance().getId(it) }
  }

  private fun MarkdownEditorWithPreview.selectedViewActionIds(): List<String?> {
    val context = SimpleDataContext.getSimpleContext(PlatformCoreDataKeys.FILE_EDITOR, this)
    return viewActions()
      .filter { it is ToggleAction && it.isSelected(TestActionEvent.createTestEvent(it, context)) }
      .map { ActionManager.getInstance().getId(it) }
  }

  private class StubHtmlPanelProvider : MarkdownHtmlPanelProvider() {
    var available = true

    override fun createHtmlPanel(): MarkdownHtmlPanel = StubHtmlPanel()

    override fun isAvailable(): AvailabilityInfo = if (available) AvailabilityInfo.AVAILABLE else AvailabilityInfo.UNAVAILABLE

    override fun getProviderInfo(): ProviderInfo = ProviderInfo("Stub", StubHtmlPanelProvider::class.java.name)
  }

  private class StubHtmlPanel : MarkdownHtmlPanel {
    private val component = JPanel()

    override fun getComponent(): JComponent = component
    override fun setHtml(html: String, initialScrollOffset: Int, document: VirtualFile?) = Unit
    override fun reloadWithOffset(offset: Int) = Unit
    override fun addScrollListener(listener: MarkdownHtmlPanel.ScrollListener) = Unit
    override fun removeScrollListener(listener: MarkdownHtmlPanel.ScrollListener) = Unit
    override fun dispose() = Unit
  }

  private companion object {
    const val EDITOR_ONLY_ACTION_ID = "Markdown.Layout.EditorOnly"
    const val EDITOR_AND_PREVIEW_ACTION_ID = "TextEditorWithPreview.Layout.EditorAndPreview"
    const val LIVE_PREVIEW_ACTION_ID = "Markdown.Layout.LivePreview"
    const val PREVIEW_ONLY_ACTION_ID = "TextEditorWithPreview.Layout.PreviewOnly"
  }
}
