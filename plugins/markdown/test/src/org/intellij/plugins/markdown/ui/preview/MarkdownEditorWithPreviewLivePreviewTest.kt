// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.ui.preview

import com.intellij.ide.ui.UISettingsUtils
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.actions.AbstractToggleUseSoftWrapsAction
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview.Layout
import com.intellij.openapi.fileEditor.TextEditorWithPreview.MyFileEditorState
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.PersistentFSConstants
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.limits.FileSizeLimit
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.StartupUiUtil
import org.intellij.plugins.markdown.editor.livepreview.isLivePreviewEnabled
import org.intellij.plugins.markdown.settings.MarkdownPreviewSettings
import org.intellij.plugins.markdown.settings.MarkdownSettings
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.math.roundToInt

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

  fun testLivePreviewLayoutShowsTheTextInTheUiFont() {
    val editorWithPreview = createEditor()
    val editorFont = editorWithPreview.editor.colorsScheme.editorFontName
    val uiFont = StartupUiUtil.labelFont.fontName
    assertFalse("The UI font must differ from the editor font", uiFont == editorFont)

    editorWithPreview.setLivePreviewLayout()
    editorWithPreview.waitForFont(uiFont)

    editorWithPreview.setLayout(Layout.SHOW_EDITOR)
    editorWithPreview.waitForFont(editorFont)
  }

  fun testRestoredLayoutWithPreviewShowsTheEditorFont() {
    createEditor().setLivePreviewLayout()
    val editorWithPreview = createEditor()
    editorWithPreview.waitForFont(StartupUiUtil.labelFont.fontName)

    editorWithPreview.setState(MyFileEditorState(Layout.SHOW_PREVIEW, null, null, false))

    editorWithPreview.waitForFont(EditorColorsManager.getInstance().globalScheme.editorFontName)
  }

  fun testLivePreviewLayoutShowsTheTextInThePreviewFontSize() {
    val previewFontSize = changePreviewFontSize()
    val editorWithPreview = createEditor()

    editorWithPreview.setLivePreviewLayout()
    editorWithPreview.waitForFontSize(UISettingsUtils.getInstance().scaleFontSize(previewFontSize.toFloat()))

    editorWithPreview.setLayout(Layout.SHOW_EDITOR)
    editorWithPreview.waitForFontSize(UISettingsUtils.getInstance().scaledEditorFontSize)
  }

  fun testPreviewFontSizeChangeUpdatesTheLivePreviewFont() {
    val editorWithPreview = createEditor()
    editorWithPreview.setLivePreviewLayout()
    editorWithPreview.waitForFont(StartupUiUtil.labelFont.fontName)

    val previewFontSize = changePreviewFontSize()

    editorWithPreview.waitForFontSize(UISettingsUtils.getInstance().scaleFontSize(previewFontSize.toFloat()))
  }

  fun testLivePreviewLayoutTurnsSoftWrapsOn() {
    val editorWithPreview = createEditor()
    assertFalse("The unit-test mode turns soft wraps off", editorWithPreview.editor.settings.isUseSoftWraps)

    editorWithPreview.setLivePreviewLayout()
    editorWithPreview.waitForSoftWraps(true)

    editorWithPreview.setLayout(Layout.SHOW_EDITOR)
    editorWithPreview.waitForSoftWraps(false)
  }

  fun testSoftWrapToggleDoesNotTurnSoftWrapsOffInLivePreview() {
    val editorWithPreview = createEditor()
    editorWithPreview.setLivePreviewLayout()
    editorWithPreview.waitForSoftWraps(true)

    // View | Active Editor | Soft-Wrap calls this for the active editor.
    AbstractToggleUseSoftWrapsAction.toggleSoftWraps(editorWithPreview.editor, null, false)
    editorWithPreview.waitForSoftWraps(true)

    editorWithPreview.setLayout(Layout.SHOW_EDITOR)
    editorWithPreview.waitForSoftWraps(false)
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    assertFalse("Editor Only must not force soft wraps", editorWithPreview.editor.settings.isUseSoftWraps)
  }

  fun testEditorOnlyLayoutKeepsSoftWrapsThatWereOn() {
    val editorWithPreview = createEditor()
    editorWithPreview.editor.settings.isUseSoftWraps = true

    editorWithPreview.setLivePreviewLayout()
    editorWithPreview.waitForFont(StartupUiUtil.labelFont.fontName)
    editorWithPreview.setLayout(Layout.SHOW_EDITOR)
    editorWithPreview.waitForFont(EditorColorsManager.getInstance().globalScheme.editorFontName)

    assertTrue("Editor Only must keep the soft wraps from before live preview", editorWithPreview.editor.settings.isUseSoftWraps)
  }

  fun testLivePreviewLayoutKeepsSoftWrapsOffAboveTheIntellisenseLimit() {
    val editorWithPreview = createEditor()
    val originalLimit = FileSizeLimit.getDefaultIntellisenseLimit()
    Disposer.register(testRootDisposable) { PersistentFSConstants.setMaxIntellisenseFileSize(originalLimit) }
    PersistentFSConstants.setMaxIntellisenseFileSize(editorWithPreview.editor.document.textLength - 1)

    editorWithPreview.setLivePreviewLayout()
    editorWithPreview.waitForFont(StartupUiUtil.labelFont.fontName)

    assertFalse("A document above the IntelliSense limit must stay unwrapped", editorWithPreview.editor.settings.isUseSoftWraps)

    AbstractToggleUseSoftWrapsAction.toggleSoftWraps(editorWithPreview.editor, null, true)
    AbstractToggleUseSoftWrapsAction.toggleSoftWraps(editorWithPreview.editor, null, false)
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    assertFalse("The soft-wrap toggle must work above the IntelliSense limit", editorWithPreview.editor.settings.isUseSoftWraps)
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

  /** Sets a `Preview font size` that differs from the editor font size. The test root disposable restores the old value. */
  private fun changePreviewFontSize(): Int {
    val settings = service<MarkdownPreviewSettings>()
    val originalFontSize = settings.state.fontSize
    Disposer.register(testRootDisposable) { settings.update { it.state.fontSize = originalFontSize } }
    val fontSize = EditorColorsManager.getInstance().globalScheme.editorFontSize2D.roundToInt() + 6
    settings.update { it.state.fontSize = fontSize }
    return fontSize
  }

  private fun MarkdownEditorWithPreview.waitForFont(fontName: String) {
    waitFor({ "The editor shows ${editor.colorsScheme.editorFontName} instead of $fontName" }) {
      editor.colorsScheme.editorFontName == fontName
    }
  }

  private fun MarkdownEditorWithPreview.waitForFontSize(fontSize: Float) {
    waitFor({ "The editor font size is ${editor.colorsScheme.editorFontSize2D} instead of $fontSize" }) {
      editor.colorsScheme.editorFontSize2D == fontSize
    }
  }

  private fun MarkdownEditorWithPreview.waitForSoftWraps(enabled: Boolean) {
    waitFor({ "The editor soft wraps are ${editor.settings.isUseSoftWraps} instead of $enabled" }) {
      editor.settings.isUseSoftWraps == enabled
    }
  }

  private fun waitFor(message: () -> String, condition: () -> Boolean) {
    PlatformTestUtil.waitWithEventsDispatching(message, condition, TIMEOUT_SECONDS)
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
    const val TIMEOUT_SECONDS = 10
  }
}
