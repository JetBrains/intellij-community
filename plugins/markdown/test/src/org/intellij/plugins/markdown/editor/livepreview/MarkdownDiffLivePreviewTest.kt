// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.livepreview

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.EditorDiffViewer
import com.intellij.diff.contents.DiffContent
import com.intellij.diff.impl.DiffRequestPanelImpl
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.diff.tools.simple.SimpleDiffViewer
import com.intellij.diff.tools.simple.SimpleOnesideDiffViewer
import com.intellij.diff.tools.util.DiffDataKeys
import com.intellij.ide.DataManager
import com.intellij.ide.impl.HeadlessDataManager
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil
import org.intellij.plugins.markdown.lang.MarkdownFileType
import org.intellij.plugins.markdown.ui.preview.MarkdownEditorWithPreview

class MarkdownDiffLivePreviewTest : BasePlatformTestCase() {
  override fun setUp() {
    super.setUp()
    val properties = PropertiesComponent.getInstance()
    Disposer.register(testRootDisposable) { properties.unsetValue(PROPERTY) }
    Registry.get(MarkdownEditorWithPreview.LIVE_PREVIEW_REGISTRY_KEY).setValue(true, testRootDisposable)
    HeadlessDataManager.fallbackToProductionDataManager(testRootDisposable)
  }

  fun testRightToolbarStartsWithTheGroupThatHoldsTheAction() {
    val diff = openDiff()
    val actionManager = ActionManager.getInstance()
    val group = actionManager.getAction(IdeActions.DIFF_VIEWER_RIGHT_TOOLBAR) as DefaultActionGroup
    val children = (diff.toolbarWithGroup.actionGroup as DefaultActionGroup).getChildren(actionManager).filter { it !is Separator }
    assertEquals(listOf(group), children)
    assertTrue("The viewer settings must follow the group", (diff.viewerToolbar.actionGroup as DefaultActionGroup).getChildren(actionManager).isNotEmpty())
    assertTrue(action() in group.getChildren(actionManager))
    assertTrue("The action must be visible in a Markdown diff", diff.isActionVisible())
  }

  fun testToggleTurnsLivePreviewOnAndOffForBothSides() {
    val diff = openDiff()
    assertInstanceOf(diff.viewer, SimpleDiffViewer::class.java)
    assertEquals(listOf(false, false), diff.livePreviewStates())

    diff.setSelected(true)
    assertEquals(listOf(true, true), diff.livePreviewStates())
    assertTrue(action().isSelected(diff.createActionEvent()))

    diff.setSelected(false)
    assertEquals(listOf(false, false), diff.livePreviewStates())
    assertFalse(action().isSelected(diff.createActionEvent()))
  }

  fun testNewViewerStartsInTheLastChosenState() {
    openDiff().setSelected(true)
    assertEquals(listOf(true, true), openDiff().livePreviewStates())

    openDiff().setSelected(false)
    assertEquals(listOf(false, false), openDiff().livePreviewStates())
  }

  fun testAddedFileGetsTheActionAndTheLastChosenState() {
    openDiff().setSelected(true)

    val diff = openDiff(left = DiffContentFactory.getInstance().createEmpty())
    assertInstanceOf(diff.viewer, SimpleOnesideDiffViewer::class.java)
    assertTrue(diff.isActionVisible())
    assertEquals(listOf(true), diff.livePreviewStates())

    diff.setSelected(false)
    assertEquals(listOf(false), diff.livePreviewStates())
  }

  fun testDisabledRegistryKeyHidesTheAction() {
    openDiff().setSelected(true)
    Registry.get(MarkdownEditorWithPreview.LIVE_PREVIEW_REGISTRY_KEY).setValue(false, testRootDisposable)

    val diff = openDiff()
    assertEquals("A new viewer must ignore the saved state", listOf(false, false), diff.livePreviewStates())
    assertFalse("The action must be hidden", diff.isActionVisible())
  }

  fun testPlainTextDiffHidesTheAction() {
    openDiff().setSelected(true)

    val diff = openDiff(fileType = PlainTextFileType.INSTANCE)
    assertEquals(listOf(false, false), diff.livePreviewStates())
    assertFalse("The action must be hidden in a plain-text diff", diff.isActionVisible())
  }

  private fun openDiff(
    fileType: FileType = MarkdownFileType.INSTANCE,
    left: DiffContent = createDiffContent("# Title\n\nSome **bold** text\n", fileType),
    base: DiffContent? = null,
  ): OpenDiff {
    val right = createDiffContent("# Title\n\nSome *italic* text\n", fileType)
    val request = if (base == null) SimpleDiffRequest(null, left, right, null, null)
    else SimpleDiffRequest(null, left, base, right, null, null, null)
    val panel = DiffRequestPanelImpl(project, null)
    Disposer.register(testRootDisposable, panel)
    panel.setRequest(request)
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    val actionManager = ActionManager.getInstance()
    val publicGroup = actionManager.getAction(IdeActions.DIFF_VIEWER_RIGHT_TOOLBAR)
    val toolbars = UIUtil.findComponentsOfType(panel.component, ActionToolbarImpl::class.java)
      .filter { it.place == ActionPlaces.DIFF_RIGHT_TOOLBAR }
    val toolbarWithGroup = toolbars.single { (it.actionGroup as DefaultActionGroup).getChildren(actionManager).contains(publicGroup) }
    val viewerToolbar = toolbars.single { it !== toolbarWithGroup }
    assertSame("The public group must be before viewer actions", toolbarWithGroup, toolbars.first())
    return OpenDiff(toolbarWithGroup, viewerToolbar)
  }

  private fun createDiffContent(text: String, fileType: FileType = MarkdownFileType.INSTANCE): DiffContent {
    return DiffContentFactory.getInstance().create(project, text, fileType)
  }

  private fun action(): ToggleAction = ActionManager.getInstance().getAction(ACTION_ID) as ToggleAction

  private inner class OpenDiff(
    val toolbarWithGroup: ActionToolbarImpl,
    val viewerToolbar: ActionToolbarImpl,
  ) {
    /** The data context of the toolbar: the diff content panel, as the toolbar actions see it. */
    private val dataContext: DataContext get() = DataManager.getInstance().getDataContext(viewerToolbar.targetComponent)

    val viewer: EditorDiffViewer get() = dataContext.getData(DiffDataKeys.DIFF_VIEWER) as EditorDiffViewer

    fun livePreviewStates(): List<Boolean> = viewer.editors.map { it.isLivePreviewEnabled() }

    fun createActionEvent(): AnActionEvent = TestActionEvent.createTestEvent(action(), dataContext)

    fun setSelected(state: Boolean) = action().setSelected(createActionEvent(), state)

    fun isActionVisible(): Boolean {
      val event = createActionEvent()
      ActionUtil.updateAction(action(), event)
      return event.presentation.isVisible
    }
  }

  private companion object {
    const val ACTION_ID = "Markdown.Diff.LivePreview"
    const val PROPERTY = "markdown.diff.live.preview"
  }
}
