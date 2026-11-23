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
import com.intellij.openapi.Disposable
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
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.util.Disposer
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.lang.MarkdownFileType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class MarkdownDiffLivePreviewTest {
  private val myFixture by codeInsightModuleFixture(projectFixture)

  @TestDisposable
  lateinit var disposable: Disposable

  @BeforeEach
  fun setUp() {
    val properties = PropertiesComponent.getInstance()
    Disposer.register(disposable) { properties.unsetValue(PROPERTY) }
    HeadlessDataManager.fallbackToProductionDataManager(disposable)
  }

  @Test
  fun testRightToolbarStartsWithTheGroupThatHoldsTheAction(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val diff = openDiff()
      val actionManager = ActionManager.getInstance()
      val group = actionManager.getAction(IdeActions.DIFF_VIEWER_RIGHT_TOOLBAR) as DefaultActionGroup
      val children = (diff.toolbarWithGroup.actionGroup as DefaultActionGroup).getChildren(actionManager).filter { it !is Separator }
      assertEquals(listOf(group), children)
      assertTrue((diff.viewerToolbar.actionGroup as DefaultActionGroup).getChildren(actionManager).isNotEmpty(), "The viewer settings must follow the group")
      assertTrue(action() in group.getChildren(actionManager))
      assertTrue(diff.isActionVisible(), "The action must be visible in a Markdown diff")
    }
  }

  @Test
  fun testToggleTurnsLivePreviewOnAndOffForBothSides(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val diff = openDiff()
      assertInstanceOf(SimpleDiffViewer::class.java, diff.viewer)
      assertEquals(listOf(false, false), diff.livePreviewStates())

      diff.setSelected(true)
      assertEquals(listOf(true, true), diff.livePreviewStates())
      assertTrue(action().isSelected(diff.createActionEvent()))

      diff.setSelected(false)
      assertEquals(listOf(false, false), diff.livePreviewStates())
      assertFalse(action().isSelected(diff.createActionEvent()))
    }
  }

  @Test
  fun testNewViewerStartsInTheLastChosenState(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      openDiff().setSelected(true)
      assertEquals(listOf(true, true), openDiff().livePreviewStates())

      openDiff().setSelected(false)
      assertEquals(listOf(false, false), openDiff().livePreviewStates())
    }
  }

  @Test
  fun testAddedFileGetsTheActionAndTheLastChosenState(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      openDiff().setSelected(true)

      val diff = openDiff(left = DiffContentFactory.getInstance().createEmpty())
      assertInstanceOf(SimpleOnesideDiffViewer::class.java, diff.viewer)
      assertTrue(diff.isActionVisible())
      assertEquals(listOf(true), diff.livePreviewStates())

      diff.setSelected(false)
      assertEquals(listOf(false), diff.livePreviewStates())
    }
  }

  @Test
  fun testPlainTextDiffHidesTheAction(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      openDiff().setSelected(true)

      val diff = openDiff(fileType = PlainTextFileType.INSTANCE)
      assertEquals(listOf(false, false), diff.livePreviewStates())
      assertFalse(diff.isActionVisible(), "The action must be hidden in a plain-text diff")
    }
  }

  private fun openDiff(
    fileType: FileType = MarkdownFileType.INSTANCE,
    left: DiffContent = createDiffContent("# Title\n\nSome **bold** text\n", fileType),
    base: DiffContent? = null,
  ): OpenDiff {
    val right = createDiffContent("# Title\n\nSome *italic* text\n", fileType)
    val request = if (base == null) SimpleDiffRequest(null, left, right, null, null)
    else SimpleDiffRequest(null, left, base, right, null, null, null)
    val panel = DiffRequestPanelImpl(myFixture.project, null)
    Disposer.register(myFixture.testRootDisposable, panel)
    panel.setRequest(request)
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    val actionManager = ActionManager.getInstance()
    val publicGroup = actionManager.getAction(IdeActions.DIFF_VIEWER_RIGHT_TOOLBAR)
    val toolbars = UIUtil.findComponentsOfType(panel.component, ActionToolbarImpl::class.java)
      .filter { it.place == ActionPlaces.DIFF_RIGHT_TOOLBAR }
    val toolbarWithGroup = toolbars.single { (it.actionGroup as DefaultActionGroup).getChildren(actionManager).contains(publicGroup) }
    val viewerToolbar = toolbars.single { it !== toolbarWithGroup }
    assertSame(toolbarWithGroup, toolbars.first(), "The public group must be before viewer actions")
    return OpenDiff(toolbarWithGroup, viewerToolbar)
  }

  private fun createDiffContent(text: String, fileType: FileType = MarkdownFileType.INSTANCE): DiffContent {
    return DiffContentFactory.getInstance().create(myFixture.project, text, fileType)
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
    private val projectFixture = projectFixture(openAfterCreation = true)

    const val ACTION_ID = "Markdown.Diff.LivePreview"
    const val PROPERTY = "markdown.diff.live.preview"
  }
}
