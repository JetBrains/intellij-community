// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.ui.preview

import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.ide.ui.UISettingsUtils
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.actions.AbstractToggleUseSoftWrapsAction
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview.Layout
import com.intellij.openapi.fileEditor.TextEditorWithPreview.MyFileEditorState
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.PersistentFSConstants
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.limits.FileSizeLimit
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.ui.StartupUiUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.editor.livepreview.isLivePreviewEnabled
import org.intellij.plugins.markdown.settings.MarkdownPreviewMode
import org.intellij.plugins.markdown.settings.MarkdownPreviewSettings
import org.intellij.plugins.markdown.settings.MarkdownSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.Insets
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.math.roundToInt

@TestApplication
class MarkdownEditorWithPreviewLivePreviewTest {
  private val myFixture by codeInsightModuleFixture(projectFixture)

  @TestDisposable
  lateinit var disposable: Disposable

  private val panelProvider = StubHtmlPanelProvider()

  /** The parent of the settings restores. The editors close before it is disposed. */
  private lateinit var settingsRestore: Disposable

  @BeforeEach
  fun setUp() {
    val properties = PropertiesComponent.getInstance()
    Disposer.register(disposable) { properties.unsetValue(MarkdownEditorWithPreview.LIVE_PREVIEW_PROPERTY) }
    ExtensionTestUtil.maskExtensions(MarkdownHtmlPanelProvider.EP_NAME, listOf(panelProvider), myFixture.testRootDisposable)
    settingsRestore = Disposer.newDisposable(myFixture.testRootDisposable, "settings restore")
    val settings = MarkdownSettings.getInstance()
    val previousRenderer = settings.previewMode
    Disposer.register(settingsRestore) { settings.update { it.previewMode = previousRenderer } }
    settings.update { it.previewMode = MarkdownPreviewMode.JCEF }
    myFixture.configureByText("test.md", "# Heading\n\ntext\n")
  }

  @Test
  fun testLivePreviewLayoutMarksOnlyTheTextEditor(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
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
      assertFalse(editorWithPreview.editor.isLivePreviewEnabled(), "Editor Only must turn live preview off")
    }
  }

  @Test
  fun testNewEditorStartsInTheLastChosenLivePreviewLayout(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      MarkdownSettings.getInstance().update { it.previewMode = MarkdownPreviewMode.EDITOR }
      createEditor().setLivePreviewLayout()
      assertTrue(createEditor().editor.isLivePreviewEnabled())
    }
  }

  @Test
  fun testEmptyMarkdownOpensInEditorAndPreviewAfterPreviewOnlyWasChosen(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      restoreLastUsedLayoutAfterTest()
      createEditor().setLayout(Layout.SHOW_PREVIEW)

      myFixture.configureByText("empty.md", "")
      assertEquals(Layout.SHOW_EDITOR_AND_PREVIEW, createEditor().getLayout())

      myFixture.configureByText("filled.md", "# Heading")
      assertEquals(Layout.SHOW_PREVIEW, createEditor().getLayout())
    }
  }

  @Test
  fun testEmptyMarkdownOpensInDefaultLayout(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      restoreLastUsedLayoutAfterTest()
      PropertiesComponent.getInstance().unsetValue(LAST_USED_LAYOUT_PROPERTY)

      myFixture.configureByText("empty.md", "")
      assertEquals(Layout.SHOW_EDITOR_AND_PREVIEW, createEditor().getLayout())
    }
  }

  @Test
  fun testRestoredLayoutWithPreviewTurnsLivePreviewOff(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val editorWithPreview = createEditor()
      editorWithPreview.setLivePreviewLayout()

      editorWithPreview.setState(MyFileEditorState(Layout.SHOW_PREVIEW, null, null, false))

      assertEquals(Layout.SHOW_PREVIEW, editorWithPreview.getLayout())
      assertFalse(editorWithPreview.editor.isLivePreviewEnabled())
    }
  }

  @Test
  fun testLivePreviewLayoutShowsTheTextInTheUiFont(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val editorWithPreview = createEditor()
      val editorFont = editorWithPreview.editor.colorsScheme.editorFontName
      val uiFont = StartupUiUtil.labelFont.fontName
      assertFalse(uiFont == editorFont, "The UI font must differ from the editor font")

      editorWithPreview.setLivePreviewLayout()
      editorWithPreview.waitForFont(uiFont)

      editorWithPreview.setLayout(Layout.SHOW_EDITOR)
      editorWithPreview.waitForFont(editorFont)
    }
  }

  @Test
  fun testRestoredLayoutWithPreviewShowsTheEditorFont(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val editorWithPreview = createEditor()
      editorWithPreview.setLivePreviewLayout()
      editorWithPreview.waitForFont(StartupUiUtil.labelFont.fontName)

      editorWithPreview.setState(MyFileEditorState(Layout.SHOW_PREVIEW, null, null, false))

      editorWithPreview.waitForFont(EditorColorsManager.getInstance().globalScheme.editorFontName)
    }
  }

  @Test
  fun testLivePreviewLayoutShowsTheTextInThePreviewFontSize(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val previewFontSize = changePreviewFontSize()
      val editorWithPreview = createEditor()

      editorWithPreview.setLivePreviewLayout()
      editorWithPreview.waitForFontSize(UISettingsUtils.getInstance().scaleFontSize(previewFontSize.toFloat()))

      editorWithPreview.setLayout(Layout.SHOW_EDITOR)
      editorWithPreview.waitForFontSize(UISettingsUtils.getInstance().scaledEditorFontSize)
    }
  }

  @Test
  fun testPreviewFontSizeChangeUpdatesTheLivePreviewFont(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val editorWithPreview = createEditor()
      editorWithPreview.setLivePreviewLayout()
      editorWithPreview.waitForFont(StartupUiUtil.labelFont.fontName)

      val previewFontSize = changePreviewFontSize()

      editorWithPreview.waitForFontSize(UISettingsUtils.getInstance().scaleFontSize(previewFontSize.toFloat()))
    }
  }

  @Test
  fun testLivePreviewLayoutTurnsSoftWrapsOn(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val editorWithPreview = createEditor()
      assertFalse(editorWithPreview.editor.settings.isUseSoftWraps, "The unit-test mode turns soft wraps off")

      editorWithPreview.setLivePreviewLayout()
      editorWithPreview.waitForSoftWraps(true)

      editorWithPreview.setLayout(Layout.SHOW_EDITOR)
      editorWithPreview.waitForSoftWraps(false)
    }
  }

  @Test
  fun testLivePreviewLayoutTurnsTheCustomSoftWrapIndentOnAndEditorOnlyRestoresIt(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val editorWithPreview = createEditor()
      editorWithPreview.editor.settings.isUseCustomSoftWrapIndent = false

      editorWithPreview.setLivePreviewLayout()
      editorWithPreview.waitForCustomSoftWrapIndent(true)

      editorWithPreview.setLayout(Layout.SHOW_EDITOR)
      editorWithPreview.waitForCustomSoftWrapIndent(false)
    }
  }

  @Test
  fun testSoftWrapToggleDoesNotTurnSoftWrapsOffInLivePreview(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val editorWithPreview = createEditor()
      editorWithPreview.setLivePreviewLayout()
      editorWithPreview.waitForSoftWraps(true)

      // View | Active Editor | Soft-Wrap calls this for the active editor.
      AbstractToggleUseSoftWrapsAction.toggleSoftWraps(editorWithPreview.editor, null, false)
      editorWithPreview.waitForSoftWraps(true)

      editorWithPreview.setLayout(Layout.SHOW_EDITOR)
      editorWithPreview.waitForSoftWraps(false)
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
      assertFalse(editorWithPreview.editor.settings.isUseSoftWraps, "Editor Only must not force soft wraps")
    }
  }

  @Test
  fun testEditorOnlyLayoutKeepsSoftWrapsThatWereOn(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val editorWithPreview = createEditor()
      editorWithPreview.editor.settings.isUseSoftWraps = true

      editorWithPreview.setLivePreviewLayout()
      editorWithPreview.waitForFont(StartupUiUtil.labelFont.fontName)
      editorWithPreview.setLayout(Layout.SHOW_EDITOR)
      editorWithPreview.waitForFont(EditorColorsManager.getInstance().globalScheme.editorFontName)

      assertTrue(editorWithPreview.editor.settings.isUseSoftWraps, "Editor Only must keep the soft wraps from before live preview")
    }
  }

  @Test
  fun testLivePreviewLayoutKeepsSoftWrapsOffAboveTheIntellisenseLimit(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val editorWithPreview = createEditor()
      val originalLimit = FileSizeLimit.getDefaultIntellisenseLimit()
      Disposer.register(disposable) { PersistentFSConstants.setMaxIntellisenseFileSize(originalLimit) }
      PersistentFSConstants.setMaxIntellisenseFileSize(editorWithPreview.editor.document.textLength - 1)

      editorWithPreview.setLivePreviewLayout()
      editorWithPreview.waitForFont(StartupUiUtil.labelFont.fontName)

      assertFalse(editorWithPreview.editor.settings.isUseSoftWraps, "A document above the IntelliSense limit must stay unwrapped")

      AbstractToggleUseSoftWrapsAction.toggleSoftWraps(editorWithPreview.editor, null, true)
      AbstractToggleUseSoftWrapsAction.toggleSoftWraps(editorWithPreview.editor, null, false)
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
      assertFalse(editorWithPreview.editor.settings.isUseSoftWraps, "The soft-wrap toggle must work above the IntelliSense limit")
    }
  }

  @Test
  fun testLivePreviewLayoutAddsTextMargins(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val editorWithPreview = createEditor()
      val contentComponent = editorWithPreview.editor.contentComponent
      assertEquals(Insets(0, 0, 0, 0), contentComponent.insets)

      editorWithPreview.setLivePreviewLayout()
      editorWithPreview.waitForFont(StartupUiUtil.labelFont.fontName)
      assertEquals(editorWithPreview.expectedTextMargins(), contentComponent.insets)

      editorWithPreview.setLayout(Layout.SHOW_EDITOR)
      editorWithPreview.waitForFont(EditorColorsManager.getInstance().globalScheme.editorFontName)
      assertEquals(Insets(0, 0, 0, 0), contentComponent.insets, "Editor Only must remove the text margins")
    }
  }

  @Test
  fun testEditorZoomUpdatesTheTextMargins(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val editorWithPreview = createEditor()
      editorWithPreview.setLivePreviewLayout()
      editorWithPreview.waitForFont(StartupUiUtil.labelFont.fontName)
      val marginBeforeZoom = editorWithPreview.editor.contentComponent.insets.left

      // The editor zoom calls this.
      (editorWithPreview.editor as EditorEx).setFontSize(editorWithPreview.editor.colorsScheme.editorFontSize2D * 2)

      assertEquals(editorWithPreview.expectedTextMargins(), editorWithPreview.editor.contentComponent.insets)
      assertTrue(editorWithPreview.editor.contentComponent.insets.left > marginBeforeZoom, "The zoom must widen the text margins")
    }
  }

  @Test
  fun testLivePreviewLayoutHidesAndRestoresGuides(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val editorWithPreview = createEditor()
      val editor = editorWithPreview.editor
      val editorFont = editor.colorsScheme.editorFontName
      val otherEditor = createEditor().editor
      otherEditor.settings.isLineNumbersShown = true
      otherEditor.settings.isIndentGuidesShown = true
      otherEditor.settings.isRightMarginShown = true
      otherEditor.settings.isVirtualSpace = true

      for (lineNumbersShown in listOf(true, false)) {
        editor.settings.isLineNumbersShown = lineNumbersShown
        editor.settings.isIndentGuidesShown = !lineNumbersShown
        editor.settings.isRightMarginShown = lineNumbersShown
        editor.settings.isVirtualSpace = lineNumbersShown
        repeat(2) {
          editorWithPreview.setLivePreviewLayout()
          editorWithPreview.waitForFont(StartupUiUtil.labelFont.fontName)

          assertFalse(editor.settings.isLineNumbersShown)
          assertFalse(editor.settings.isIndentGuidesShown)
          assertFalse(editor.settings.isRightMarginShown)
          assertFalse(editor.settings.isVirtualSpace)
          assertTrue(otherEditor.settings.isLineNumbersShown)
          assertTrue(otherEditor.settings.isIndentGuidesShown)
          assertTrue(otherEditor.settings.isRightMarginShown)
          assertTrue(otherEditor.settings.isVirtualSpace)

          editorWithPreview.setLayout(Layout.SHOW_EDITOR)
          editorWithPreview.waitForFont(editorFont)

          assertEquals(lineNumbersShown, editor.settings.isLineNumbersShown)
          assertEquals(!lineNumbersShown, editor.settings.isIndentGuidesShown)
          assertEquals(lineNumbersShown, editor.settings.isRightMarginShown)
          assertEquals(lineNumbersShown, editor.settings.isVirtualSpace)
        }
      }
    }
  }

  @Test
  fun testSettingsWithoutOverrideFollowTheGlobalSettingsAfterLivePreview(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val globalSettings = EditorSettingsExternalizable.getInstance()
      val lineNumbersShown = globalSettings.isLineNumbersShown
      val virtualSpace = globalSettings.isVirtualSpace
      Disposer.register(settingsRestore) {
        globalSettings.isLineNumbersShown = lineNumbersShown
        globalSettings.isVirtualSpace = virtualSpace
      }
      globalSettings.isLineNumbersShown = true
      globalSettings.isVirtualSpace = true
      val editorWithPreview = createEditor()
      val editor = editorWithPreview.editor
      val editorFont = editor.colorsScheme.editorFontName

      editorWithPreview.setLivePreviewLayout()
      editorWithPreview.waitForFont(StartupUiUtil.labelFont.fontName)
      assertFalse(editor.settings.isLineNumbersShown)
      assertFalse(editor.settings.isVirtualSpace)

      editorWithPreview.setLayout(Layout.SHOW_EDITOR)
      editorWithPreview.waitForFont(editorFont)
      assertTrue(editor.settings.isLineNumbersShown)
      assertTrue(editor.settings.isVirtualSpace)

      globalSettings.isLineNumbersShown = false
      globalSettings.isVirtualSpace = false
      assertFalse(editor.settings.isLineNumbersShown, "The editor must follow the global settings after live preview")
      assertFalse(editor.settings.isVirtualSpace, "The editor must follow the global settings after live preview")
    }
  }

  @Test
  fun testJcefRendererHidesLivePreview(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val editorWithPreview = createEditor()

      assertEquals(listOf(EDITOR_ONLY_ACTION_ID, EDITOR_AND_PREVIEW_ACTION_ID, PREVIEW_ONLY_ACTION_ID), editorWithPreview.viewActionIds())
    }
  }

  @Test
  fun testWithoutPreviewOnlyEditorAndLivePreviewAreOffered(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
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
  }

  @Test
  fun testThemeChangeReloadsTheStylesOfAPreviewThatIsNotShowing(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      restoreLastUsedLayoutAfterTest()
      val editorWithPreview = createEditor()
      editorWithPreview.setLayout(Layout.SHOW_EDITOR_AND_PREVIEW)
      val preview = editorWithPreview.previewEditor
      waitFor({ "The preview must attach its HTML panel" }) { preview.htmlPanel() != null }
      val panel = preview.htmlPanel()!!
      assertFalse(preview.component.isShowing, "The preview must not be showing, as in a background tab")

      ApplicationManager.getApplication().messageBus.syncPublisher(LafManagerListener.TOPIC).lookAndFeelChanged(LafManager.getInstance())

      waitFor({ "The preview must reload its styles" }) { panel.stylesReloaded }
    }
  }

  private fun FileEditor.htmlPanel(): StubHtmlPanel? = getUserData(MarkdownPreviewFileEditor.PREVIEW_BROWSER)?.get() as StubHtmlPanel?

  @Test
  fun testEditorRendererOffersOnlyEditorAndLivePreview(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      MarkdownSettings.getInstance().update { it.previewMode = MarkdownPreviewMode.EDITOR }
      val editorWithPreview = createEditor()

      assertEquals(listOf(EDITOR_ONLY_ACTION_ID, LIVE_PREVIEW_ACTION_ID), editorWithPreview.viewActionIds())
      assertEquals(Layout.SHOW_EDITOR, editorWithPreview.getLayout())
    }
  }

  @Test
  fun testFirstEditorOpensInLivePreviewOnce(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      MarkdownSettings.getInstance().update { it.previewMode = MarkdownPreviewMode.EDITOR }
      val properties = PropertiesComponent.getInstance()
      properties.unsetValue(MarkdownEditorWithPreview.LIVE_PREVIEW_PROPERTY)

      val firstEditor = createEditor()
      assertTrue(firstEditor.isLivePreviewLayout, "The first editor must open in the live preview")

      firstEditor.setLayout(Layout.SHOW_EDITOR)
      assertFalse(createEditor().isLivePreviewLayout, "The next editor must open in the chosen layout")
    }
  }

  @Test
  fun testEditorRendererKeepsTheChosenEditorOnlyLayout(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      MarkdownSettings.getInstance().update { it.previewMode = MarkdownPreviewMode.EDITOR }
      createEditor().setLayout(Layout.SHOW_EDITOR)

      assertFalse(createEditor().isLivePreviewLayout)
    }
  }

  @Test
  fun testEditorRendererSwitchesAnOpenEditor(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
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
  }

  /** The test root disposable restores the last used layout of the Markdown editors. */
  private fun restoreLastUsedLayoutAfterTest() {
    val properties = PropertiesComponent.getInstance()
    val previousLayout = properties.getValue(LAST_USED_LAYOUT_PROPERTY)
    Disposer.register(disposable) {
      if (previousLayout == null) properties.unsetValue(LAST_USED_LAYOUT_PROPERTY)
      else properties.setValue(LAST_USED_LAYOUT_PROPERTY, previousLayout)
    }
  }

  private fun createEditor(): MarkdownEditorWithPreview {
    val file = myFixture.file.virtualFile
    val textEditor = TextEditorProvider.getInstance().createEditor(myFixture.project, file) as TextEditor
    val preview = MarkdownPreviewFileEditor(myFixture.project, file, textEditor.editor.document)
    val editorWithPreview = MarkdownEditorWithPreview(textEditor, preview, myFixture.project, MarkdownSettings.getInstance())
    Disposer.register(myFixture.testRootDisposable, editorWithPreview)
    // The UI resolves the initial layout.
    editorWithPreview.component
    return editorWithPreview
  }

  /** Sets a `Preview font size` that differs from the editor font size. [settingsRestore] restores the old value. */
  private fun changePreviewFontSize(): Int {
    val settings = service<MarkdownPreviewSettings>()
    val originalFontSize = settings.state.fontSize
    Disposer.register(settingsRestore) { settings.update { it.state.fontSize = originalFontSize } }
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
    assertFalse(actions.isEmpty(), "The tab header must show the view actions")
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
    private val projectFixture = projectFixture(openAfterCreation = true)

    const val EDITOR_ONLY_ACTION_ID = "Markdown.Layout.EditorOnly"
    const val EDITOR_AND_PREVIEW_ACTION_ID = "TextEditorWithPreview.Layout.EditorAndPreview"
    const val LIVE_PREVIEW_ACTION_ID = "org.intellij.plugins.markdown.ui.actions.MarkdownLivePreviewLayoutAction"
    const val PREVIEW_ONLY_ACTION_ID = "TextEditorWithPreview.Layout.PreviewOnly"
    const val TIMEOUT_SECONDS = 10

    val LAST_USED_LAYOUT_PROPERTY: String
      get() = MarkdownBundle.message("markdown.editor.name") + "Layout"
  }
}
