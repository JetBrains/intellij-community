// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.ui.preview

import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.ide.ui.UISettingsUtils
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.actions.AbstractToggleUseSoftWrapsAction
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.FileEditor
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
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.editor.livepreview.isLivePreviewEnabled
import org.intellij.plugins.markdown.settings.MarkdownPreviewMode
import org.intellij.plugins.markdown.settings.MarkdownPreviewSettings
import org.intellij.plugins.markdown.settings.MarkdownSettings
import java.awt.Insets
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.math.roundToInt

class MarkdownEditorWithPreviewLivePreviewTest : BasePlatformTestCase() {
  private val panelProvider = StubHtmlPanelProvider()

  override fun setUp() {
    super.setUp()
    val properties = PropertiesComponent.getInstance()
    Disposer.register(testRootDisposable) { properties.unsetValue(MarkdownEditorWithPreview.LIVE_PREVIEW_PROPERTY) }
    val settings = MarkdownSettings.getInstance()
    val previousRenderer = settings.previewMode
    Disposer.register(testRootDisposable) { settings.update { it.previewMode = previousRenderer } }
    settings.update { it.previewMode = MarkdownPreviewMode.JCEF }
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
    MarkdownSettings.getInstance().update { it.previewMode = MarkdownPreviewMode.EDITOR }
    createEditor().setLivePreviewLayout()
    assertTrue(createEditor().editor.isLivePreviewEnabled())
  }

  fun testEmptyMarkdownOpensInEditorAndPreviewAfterPreviewOnlyWasChosen() {
    restoreLastUsedLayoutAfterTest()
    createEditor().setLayout(Layout.SHOW_PREVIEW)

    myFixture.configureByText("empty.md", "")
    assertEquals(Layout.SHOW_EDITOR_AND_PREVIEW, createEditor().getLayout())

    myFixture.configureByText("filled.md", "# Heading")
    assertEquals(Layout.SHOW_PREVIEW, createEditor().getLayout())
  }

  fun testEmptyMarkdownOpensInDefaultLayout() {
    restoreLastUsedLayoutAfterTest()
    PropertiesComponent.getInstance().unsetValue(LAST_USED_LAYOUT_PROPERTY)

    myFixture.configureByText("empty.md", "")
    assertEquals(Layout.SHOW_EDITOR_AND_PREVIEW, createEditor().getLayout())
  }

  fun testRestoredLayoutWithPreviewTurnsLivePreviewOff() {
    val editorWithPreview = createEditor()
    editorWithPreview.setLivePreviewLayout()

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
    val editorWithPreview = createEditor()
    editorWithPreview.setLivePreviewLayout()
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

  fun testLivePreviewLayoutTurnsTheCustomSoftWrapIndentOnAndEditorOnlyRestoresIt() {
    val editorWithPreview = createEditor()
    editorWithPreview.editor.settings.isUseCustomSoftWrapIndent = false

    editorWithPreview.setLivePreviewLayout()
    editorWithPreview.waitForCustomSoftWrapIndent(true)

    editorWithPreview.setLayout(Layout.SHOW_EDITOR)
    editorWithPreview.waitForCustomSoftWrapIndent(false)
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

  fun testLivePreviewLayoutAddsTextMargins() {
    val editorWithPreview = createEditor()
    val contentComponent = editorWithPreview.editor.contentComponent
    assertEquals(Insets(0, 0, 0, 0), contentComponent.insets)

    editorWithPreview.setLivePreviewLayout()
    editorWithPreview.waitForFont(StartupUiUtil.labelFont.fontName)
    assertEquals(editorWithPreview.expectedTextMargins(), contentComponent.insets)

    editorWithPreview.setLayout(Layout.SHOW_EDITOR)
    editorWithPreview.waitForFont(EditorColorsManager.getInstance().globalScheme.editorFontName)
    assertEquals("Editor Only must remove the text margins", Insets(0, 0, 0, 0), contentComponent.insets)
  }

  fun testEditorZoomUpdatesTheTextMargins() {
    val editorWithPreview = createEditor()
    editorWithPreview.setLivePreviewLayout()
    editorWithPreview.waitForFont(StartupUiUtil.labelFont.fontName)
    val marginBeforeZoom = editorWithPreview.editor.contentComponent.insets.left

    // The editor zoom calls this.
    (editorWithPreview.editor as EditorEx).setFontSize(editorWithPreview.editor.colorsScheme.editorFontSize2D * 2)

    assertEquals(editorWithPreview.expectedTextMargins(), editorWithPreview.editor.contentComponent.insets)
    assertTrue("The zoom must widen the text margins", editorWithPreview.editor.contentComponent.insets.left > marginBeforeZoom)
  }

  fun testLivePreviewLayoutHidesAndRestoresGuides() {
    val editorWithPreview = createEditor()
    val editor = editorWithPreview.editor
    val editorFont = editor.colorsScheme.editorFontName
    val otherEditor = createEditor().editor
    otherEditor.settings.isLineNumbersShown = true
    otherEditor.settings.isIndentGuidesShown = true
    otherEditor.settings.isRightMarginShown = true

    for (lineNumbersShown in listOf(true, false)) {
      editor.settings.isLineNumbersShown = lineNumbersShown
      editor.settings.isIndentGuidesShown = !lineNumbersShown
      editor.settings.isRightMarginShown = lineNumbersShown
      repeat(2) {
        editorWithPreview.setLivePreviewLayout()
        editorWithPreview.waitForFont(StartupUiUtil.labelFont.fontName)

        assertFalse(editor.settings.isLineNumbersShown)
        assertFalse(editor.settings.isIndentGuidesShown)
        assertFalse(editor.settings.isRightMarginShown)
        assertTrue(otherEditor.settings.isLineNumbersShown)
        assertTrue(otherEditor.settings.isIndentGuidesShown)
        assertTrue(otherEditor.settings.isRightMarginShown)

        editorWithPreview.setLayout(Layout.SHOW_EDITOR)
        editorWithPreview.waitForFont(editorFont)

        assertEquals(lineNumbersShown, editor.settings.isLineNumbersShown)
        assertEquals(!lineNumbersShown, editor.settings.isIndentGuidesShown)
        assertEquals(lineNumbersShown, editor.settings.isRightMarginShown)
      }
    }
  }

  fun testJcefRendererHidesLivePreview() {
    val editorWithPreview = createEditor()

    assertEquals(listOf(EDITOR_ONLY_ACTION_ID, EDITOR_AND_PREVIEW_ACTION_ID, PREVIEW_ONLY_ACTION_ID), editorWithPreview.viewActionIds())
  }

  fun testWithoutPreviewOnlyEditorAndLivePreviewAreOffered() {
    panelProvider.available = false
    val editorWithPreview = createEditor()

    assertEquals(listOf(EDITOR_ONLY_ACTION_ID, LIVE_PREVIEW_ACTION_ID), editorWithPreview.viewActionIds())
    assertEquals(listOf(LIVE_PREVIEW_ACTION_ID), editorWithPreview.selectedViewActionIds())

    editorWithPreview.setState(MyFileEditorState(Layout.SHOW_EDITOR_AND_PREVIEW, null, null, false))
    assertEquals(Layout.SHOW_EDITOR, editorWithPreview.getLayout())

    editorWithPreview.setLayout(Layout.SHOW_PREVIEW)
    assertEquals(Layout.SHOW_EDITOR, editorWithPreview.getLayout())

    editorWithPreview.setLivePreviewLayout()
    assertEquals(listOf(LIVE_PREVIEW_ACTION_ID), editorWithPreview.selectedViewActionIds())
  }

  fun testThemeChangeReloadsTheStylesOfAPreviewThatIsNotShowing() {
    restoreLastUsedLayoutAfterTest()
    val editorWithPreview = createEditor()
    editorWithPreview.setLayout(Layout.SHOW_EDITOR_AND_PREVIEW)
    val preview = editorWithPreview.previewEditor
    waitFor({ "The preview must attach its HTML panel" }) { preview.htmlPanel() != null }
    val panel = preview.htmlPanel()!!
    assertFalse("The preview must not be showing, as in a background tab", preview.component.isShowing)

    ApplicationManager.getApplication().messageBus.syncPublisher(LafManagerListener.TOPIC).lookAndFeelChanged(LafManager.getInstance())

    waitFor({ "The preview must reload its styles" }) { panel.stylesReloaded }
  }

  private fun FileEditor.htmlPanel(): StubHtmlPanel? = getUserData(MarkdownPreviewFileEditor.PREVIEW_BROWSER)?.get() as StubHtmlPanel?

  fun testEditorRendererOffersOnlyEditorAndLivePreview() {
    MarkdownSettings.getInstance().update { it.previewMode = MarkdownPreviewMode.EDITOR }
    val editorWithPreview = createEditor()

    assertEquals(listOf(EDITOR_ONLY_ACTION_ID, LIVE_PREVIEW_ACTION_ID), editorWithPreview.viewActionIds())
    assertEquals(Layout.SHOW_EDITOR, editorWithPreview.getLayout())
  }

  fun testFirstEditorOpensInLivePreviewOnce() {
    MarkdownSettings.getInstance().update { it.previewMode = MarkdownPreviewMode.EDITOR }
    val properties = PropertiesComponent.getInstance()
    properties.unsetValue(MarkdownEditorWithPreview.LIVE_PREVIEW_PROPERTY)

    val firstEditor = createEditor()
    assertTrue("The first editor must open in the live preview", firstEditor.isLivePreviewLayout)

    firstEditor.setLayout(Layout.SHOW_EDITOR)
    assertFalse("The next editor must open in the chosen layout", createEditor().isLivePreviewLayout)
  }

  fun testEditorRendererKeepsTheChosenEditorOnlyLayout() {
    MarkdownSettings.getInstance().update { it.previewMode = MarkdownPreviewMode.EDITOR }
    createEditor().setLayout(Layout.SHOW_EDITOR)

    assertFalse(createEditor().isLivePreviewLayout)
  }

  fun testEditorRendererSwitchesAnOpenEditor() {
    val editorWithPreview = createEditor()
    editorWithPreview.setLayout(Layout.SHOW_EDITOR_AND_PREVIEW)

    MarkdownSettings.getInstance().update { it.previewMode = MarkdownPreviewMode.EDITOR }
    assertTrue(editorWithPreview.isLivePreviewLayout)
    assertEquals(listOf(EDITOR_ONLY_ACTION_ID, LIVE_PREVIEW_ACTION_ID), editorWithPreview.viewActionIds())

    MarkdownSettings.getInstance().update { it.previewMode = MarkdownPreviewMode.JCEF }
    assertFalse(editorWithPreview.isLivePreviewLayout)
    assertEquals(Layout.SHOW_EDITOR_AND_PREVIEW, editorWithPreview.getLayout())
    assertEquals(listOf(EDITOR_ONLY_ACTION_ID, EDITOR_AND_PREVIEW_ACTION_ID, PREVIEW_ONLY_ACTION_ID), editorWithPreview.viewActionIds())
  }

  /** The test root disposable restores the last used layout of the Markdown editors. */
  private fun restoreLastUsedLayoutAfterTest() {
    val properties = PropertiesComponent.getInstance()
    val previousLayout = properties.getValue(LAST_USED_LAYOUT_PROPERTY)
    Disposer.register(testRootDisposable) {
      if (previousLayout == null) properties.unsetValue(LAST_USED_LAYOUT_PROPERTY)
      else properties.setValue(LAST_USED_LAYOUT_PROPERTY, previousLayout)
    }
  }

  private fun createEditor(): MarkdownEditorWithPreview {
    val file = myFixture.file.virtualFile
    val textEditor = TextEditorProvider.getInstance().createEditor(project, file) as TextEditor
    val preview = MarkdownPreviewFileEditor(project, file, textEditor.editor.document)
    val editorWithPreview = MarkdownEditorWithPreview(textEditor, preview, project, MarkdownSettings.getInstance())
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

  /** Two font sizes at both sides, and the vertical scroll bar width in addition at the right side. */
  private fun MarkdownEditorWithPreview.expectedTextMargins(): Insets {
    val margin = (2 * editor.colorsScheme.editorFontSize2D).roundToInt()
    val scrollBar = (editor as EditorEx).scrollPane.verticalScrollBar
    val scrollBarWidth = scrollBar.width.takeIf { it > 0 } ?: scrollBar.preferredSize.width
    return Insets(0, margin, 0, margin + scrollBarWidth)
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

  private fun MarkdownEditorWithPreview.waitForCustomSoftWrapIndent(enabled: Boolean) {
    waitFor({ "The custom soft-wrap indent is ${editor.settings.isUseCustomSoftWrapIndent} instead of $enabled" }) {
      editor.settings.isUseCustomSoftWrapIndent == enabled
    }
  }

  private fun waitFor(message: () -> String, condition: () -> Boolean) {
    PlatformTestUtil.waitWithEventsDispatching(message, condition, TIMEOUT_SECONDS)
  }

  private fun MarkdownEditorWithPreview.viewActions(): List<AnAction> {
    val context = SimpleDataContext.getSimpleContext(PlatformCoreDataKeys.FILE_EDITOR, this)
    val event = TestActionEvent.createTestEvent(context)
    val actions = tabActions.getChildren(event)
      .flatMap { (it as? ActionGroup)?.getChildren(event)?.asList() ?: listOf(it) }
      .filter { isVisible(it, context) }
    assertFalse("The tab header must show the view actions", actions.isEmpty())
    return actions
  }

  private fun MarkdownEditorWithPreview.viewActionIds(): List<String> {
    return viewActions().map { ActionManager.getInstance().getId(it) ?: it.javaClass.name }
  }

  private fun MarkdownEditorWithPreview.selectedViewActionIds(): List<String> {
    val context = SimpleDataContext.getSimpleContext(PlatformCoreDataKeys.FILE_EDITOR, this)
    return viewActions()
      .filter { it is ToggleAction && it.isSelected(TestActionEvent.createTestEvent(it, context)) }
      .map { ActionManager.getInstance().getId(it) ?: it.javaClass.name }
  }

  private fun isVisible(action: AnAction, context: DataContext): Boolean {
    val event = TestActionEvent.createTestEvent(action, context)
    ActionUtil.updateAction(action, event)
    return event.presentation.isVisible
  }

  private class StubHtmlPanelProvider : MarkdownHtmlPanelProvider() {
    var available = true

    override fun createHtmlPanel(): MarkdownHtmlPanel = StubHtmlPanel()

    override fun isAvailable(): AvailabilityInfo = if (available) AvailabilityInfo.AVAILABLE else AvailabilityInfo.UNAVAILABLE

    override fun getProviderInfo(): ProviderInfo = ProviderInfo("Stub", StubHtmlPanelProvider::class.java.name)
  }

  private class StubHtmlPanel : MarkdownHtmlPanel {
    private val component = JPanel()
    var stylesReloaded = false

    override fun getComponent(): JComponent = component
    override fun reloadStyles() {
      stylesReloaded = true
    }
    override fun setHtml(html: String, initialScrollOffset: Int, document: VirtualFile?) = Unit
    override fun reloadWithOffset(offset: Int) = Unit
    override fun addScrollListener(listener: MarkdownHtmlPanel.ScrollListener) = Unit
    override fun removeScrollListener(listener: MarkdownHtmlPanel.ScrollListener) = Unit
    override fun dispose() = Unit
  }

  private companion object {
    const val EDITOR_ONLY_ACTION_ID = "Markdown.Layout.EditorOnly"
    const val EDITOR_AND_PREVIEW_ACTION_ID = "TextEditorWithPreview.Layout.EditorAndPreview"
    const val LIVE_PREVIEW_ACTION_ID = "org.intellij.plugins.markdown.ui.actions.MarkdownLivePreviewLayoutAction"
    const val PREVIEW_ONLY_ACTION_ID = "TextEditorWithPreview.Layout.PreviewOnly"
    const val TIMEOUT_SECONDS = 10

    val LAST_USED_LAYOUT_PROPERTY: String
      get() = MarkdownBundle.message("markdown.editor.name") + "Layout"
  }
}
