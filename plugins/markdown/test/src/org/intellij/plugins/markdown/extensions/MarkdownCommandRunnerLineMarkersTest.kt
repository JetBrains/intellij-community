// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.execution.Executor
import com.intellij.icons.AllIcons
import com.intellij.lang.Language
import com.intellij.markdown.backend.services.MarkdownFrontendRunnerRequestService
import com.intellij.markdown.frontend.runner.MarkdownFrontendRunnerFlowService
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.editorId
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.BaseProjectDirectories
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.project.projectId
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.MarkdownRunner
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.MarkdownRunnerContext
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.getMarkdownCommandWorkingDirectoryPaths
import org.intellij.plugins.markdown.settings.MarkdownSettings
import org.intellij.plugins.markdown.service.MarkdownFrontendRunnerRequest
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.awt.Point
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

@RunWith(JUnit4::class)
class MarkdownCommandRunnerLineMarkersTest : BasePlatformTestCase() {

  private val markdownRunnerEp = ExtensionPointName.create<MarkdownRunner>("org.intellij.markdown.markdownRunner")
  private val capturingRunner = CapturingRunner()
  private lateinit var runnerDisposable: Disposable

  override fun setUp() {
    super.setUp()
    runnerDisposable = Disposer.newDisposable()
    Disposer.register(testRootDisposable, runnerDisposable)
    ExtensionTestUtil.maskExtensions(markdownRunnerEp, listOf(capturingRunner), runnerDisposable)
    val testMdFile = myFixture.addFileToProject("foo/test.md", "```shell\npwd\n```")
    myFixture.openFileInEditor(testMdFile.virtualFile)
  }

  override fun tearDown() {
    try {
      MarkdownSettings.getInstance(project).useFileDirectoryForCommands = null
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  @Test
  fun `block run marker is shown for shell code fence`() {
    myFixture.doHighlighting()
    val markers = DaemonCodeAnalyzerImpl.getLineMarkers(myFixture.editor.document, project)
    assertNotNull(markers.firstOrNull { it.icon == AllIcons.RunConfigurations.TestState.Run_run })
  }

  @Test
  fun `shell fence markers do not require a backend runner`() {
    Disposer.dispose(runnerDisposable)
    ExtensionTestUtil.maskExtensions(markdownRunnerEp, emptyList(), testRootDisposable)

    for (alias in listOf("shell", "bash", "sh", "zsh")) {
      val file = myFixture.addFileToProject("foo/$alias.md", "```$alias\npwd\n```")
      myFixture.openFileInEditor(file.virtualFile)
      myFixture.doHighlighting()
      val markers = DaemonCodeAnalyzerImpl.getLineMarkers(myFixture.editor.document, project)
      val marker = markers.firstOrNull { it.icon == AllIcons.RunConfigurations.TestState.Run_run }
      assertNotNull("The $alias fence must have a block marker", marker)
      val action = (marker!!.createGutterRenderer() as GutterIconRenderer).clickAction!!
      assertEquals("Run in Terminal", action.templatePresentation.text)
    }
  }

  @Test
  fun `unsupported fences have no block marker without a runner`() {
    Disposer.dispose(runnerDisposable)
    ExtensionTestUtil.maskExtensions(markdownRunnerEp, emptyList(), testRootDisposable)

    for (language in listOf("markdown", "unknown-test-language")) {
      val file = myFixture.addFileToProject("foo/$language.md", "```$language\ntext\n```")
      myFixture.openFileInEditor(file.virtualFile)
      myFixture.doHighlighting()
      val markers = DaemonCodeAnalyzerImpl.getLineMarkers(myFixture.editor.document, project)
      assertNull(markers.firstOrNull { it.icon == AllIcons.RunConfigurations.TestState.Run_run })
    }
  }

  @Test
  fun `block run marker invokes runner with base directory as working directory`() {
    MarkdownSettings.getInstance(project).useFileDirectoryForCommands = false
    fireBlockMarkerAction()
    val expected = BaseProjectDirectories.getInstance(project).getBaseDirectoryFor(myFixture.file.virtualFile)?.canonicalPath
    assertEquals(expected, capturingRunner.capturedDir)
  }

  @Test
  fun `block run marker invokes runner with file directory as working directory when setting is enabled`() {
    MarkdownSettings.getInstance(project).useFileDirectoryForCommands = true
    fireBlockMarkerAction()
    assertEquals(myFixture.file.virtualFile.parent.canonicalPath, capturingRunner.capturedDir)
  }

  @Test
  fun `frontend uses the backend project directory for a file without a parent`() {
    checkDirectoryForParentlessFile(useFileDirectory = false)
  }

  @Test
  fun `frontend uses the backend file directory for a file without a parent`() {
    checkDirectoryForParentlessFile(useFileDirectory = true)
  }

  private fun checkDirectoryForParentlessFile(useFileDirectory: Boolean) {
    MarkdownSettings.getInstance(project).useFileDirectoryForCommands = useFileDirectory
    val backendFile = myFixture.file.virtualFile
    val frontendFile = object : LightVirtualFile("remote.md", myFixture.file.text) {
      override fun getPath(): String = backendFile.path
    }
    assertNull(frontendFile.parent)
    val editorFactory = EditorFactory.getInstance()
    val document = requireNotNull(FileDocumentManager.getInstance().getDocument(frontendFile))
    val editor = editorFactory.createEditor(document, project)
    val request = createFrontendRequest().copy(editorId = editor.editorId())
    try {
      ApplicationManager.getApplication().getService(MarkdownFrontendRunnerRequestService::class.java).request(request)
      awaitFrontendRunner()

      val paths = request.workingDirectoryPaths
      assertFalse(paths.fileDirectory == paths.projectDirectory)
      assertEquals(if (useFileDirectory) paths.fileDirectory else paths.projectDirectory, capturingRunner.capturedDir)
    }
    finally {
      editorFactory.releaseEditor(editor)
    }
  }

  @Test
  fun `block run marker passes the Markdown source file to the runner`() {
    MarkdownSettings.getInstance(project).useFileDirectoryForCommands = true

    fireBlockMarkerAction()

    assertEquals(myFixture.file.virtualFile.url, capturingRunner.capturedContext?.sourceFileUrl)
  }

  @Test
  fun `frontend continues after a runner failure`() {
    MarkdownSettings.getInstance(project).useFileDirectoryForCommands = true
    capturingRunner.failNext = true
    fireBlockMarkerAction()
    capturingRunner.capturedCommand = null

    fireBlockMarkerAction()

    assertEquals("pwd", capturingRunner.capturedCommand?.trim())
  }

  @Test
  fun `gutter popup action requests the target chooser`() {
    MarkdownSettings.getInstance(project).useFileDirectoryForCommands = true

    fireBlockMarkerAction(place = ActionPlaces.EDITOR_GUTTER_POPUP)

    assertTrue(capturingRunner.capturedContext?.showTargetChooser == true)
  }

  @Test
  fun `frontend runs an existing target without choosing a working directory`() {
    capturingRunner.reuseTarget = true

    fireBlockMarkerAction(place = ActionPlaces.EDITOR_GUTTER_POPUP)

    assertNull(capturingRunner.capturedDir)
    assertNull(MarkdownSettings.getInstance(project).useFileDirectoryForCommands)
    assertTrue(capturingRunner.capturedContext?.showTargetChooser == true)
  }

  @Test
  fun `keyboard gutter action anchors the chooser after scrolling`() {
    MarkdownSettings.getInstance(project).useFileDirectoryForCommands = true
    val prefix = "\n".repeat(100)
    val file = myFixture.addFileToProject("foo/scrolled.md", prefix + "```shell\npwd\n```")
    myFixture.openFileInEditor(file.virtualFile)
    val editor = myFixture.editor as EditorEx
    EditorTestUtil.setEditorVisibleSize(editor, 80, 10)
    editor.scrollingModel.disableAnimation()
    editor.scrollingModel.scrollVertically(95 * editor.lineHeight)
    assertTrue(editor.scrollingModel.verticalScrollOffset > 0)

    fireBlockMarkerAction(place = ActionPlaces.EDITOR_GUTTER_POPUP, inputEvent = null)

    val context = requireNotNull(capturingRunner.capturedContext)
    val expectedPoint = SwingUtilities.convertPoint(
      editor.contentComponent, editor.logicalPositionToXY(LogicalPosition(100, 3)), editor.component,
    )
    val actualPoint = SwingUtilities.convertPoint(context.component, Point(context.x, context.y), editor.component)
    assertEquals(expectedPoint, actualPoint)
  }

  @Test
  fun `gutter popup action anchors the chooser at the mouse click`() {
    MarkdownSettings.getInstance(project).useFileDirectoryForCommands = true
    val editor = myFixture.editor
    val clickPoint = Point(240, 80)
    val screenPoint = Point(clickPoint).also { SwingUtilities.convertPointToScreen(it, editor.component) }
    val inputEvent = MouseEvent(
      editor.component, MouseEvent.MOUSE_RELEASED, 0, 0, clickPoint.x, clickPoint.y,
      screenPoint.x, screenPoint.y, 1, false, MouseEvent.BUTTON1,
    )

    fireBlockMarkerAction(place = ActionPlaces.EDITOR_GUTTER_POPUP, inputEvent = inputEvent)

    val context = requireNotNull(capturingRunner.capturedContext)
    val actualPoint = SwingUtilities.convertPoint(context.component, Point(context.x, context.y), editor.component)
    assertEquals(clickPoint, actualPoint)
  }

  @Test
  fun `frontend runner uses the action editor`() {
    MarkdownSettings.getInstance(project).useFileDirectoryForCommands = true
    val editorFactory = EditorFactory.getInstance()
    val actionEditor = editorFactory.createEditor(myFixture.editor.document, project)
    try {
      fireBlockMarkerAction(editor = actionEditor)

      assertEquals("pwd", capturingRunner.capturedCommand?.trim())
      assertSame(actionEditor.contentComponent, capturingRunner.capturedContext?.component)
    }
    finally {
      editorFactory.releaseEditor(actionEditor)
    }
  }

  @Test
  fun `gutter popup action tells the user that it opens the terminal chooser`() {
    myFixture.doHighlighting()
    val markers = DaemonCodeAnalyzerImpl.getLineMarkers(myFixture.editor.document, project)
    val blockMarker = markers.first { it.icon == AllIcons.RunConfigurations.TestState.Run_run }
    val action = (blockMarker.createGutterRenderer() as GutterIconRenderer).clickAction!!
    val dataContext = SimpleDataContext.builder().add(CommonDataKeys.PROJECT, project).build()
    val inputEvent = MouseEvent(myFixture.editor.component, MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, 1, false)
    val event = AnActionEvent.createEvent(
      dataContext,
      action.templatePresentation.clone(),
      ActionPlaces.EDITOR_GUTTER_POPUP,
      ActionUiKind.NONE,
      inputEvent,
    )

    action.update(event)

    assertEquals("Choose Terminal...", event.presentation.text)
  }

  @Test
  fun `block run marker strips trailing hash comment from command`() {
    MarkdownSettings.getInstance(project).useFileDirectoryForCommands = true
    val testMdFile = myFixture.addFileToProject(
      "foo/withComment.md",
      "```bash\nnpm run dev       # start Vite dev server (HMR, localhost:5173)\n```"
    )
    myFixture.openFileInEditor(testMdFile.virtualFile)
    fireBlockMarkerAction()
    val command = capturingRunner.capturedCommand
    assertNotNull("Runner was not invoked", command)
    assertEquals("npm run dev", command!!.trim())
  }

  @Test
  fun `only the latest click remains queued before frontend startup`() {
    val queue = MarkdownFrontendRunnerRequestService()
    Disposer.register(testRootDisposable, queue)
    val request = createFrontendRequest()
    repeat(100) { queue.request(request.copy(command = "echo $it")) }

    val received = timeoutRunBlocking { queue.requests().first() }

    assertEquals("echo 99", received.command)
  }

  @Test
  fun `a recent click remains available before frontend startup`() {
    val timeSource = TestTimeSource()
    val queue = MarkdownFrontendRunnerRequestService.createForTest(timeSource)
    Disposer.register(testRootDisposable, queue)
    val request = createFrontendRequest()
    queue.request(request)
    timeSource += 4.seconds

    val received = timeoutRunBlocking { queue.requests().first() }

    assertEquals(request, received)
  }

  @Test
  fun `a click expires after five seconds without a frontend collector`() = timeoutRunBlocking {
    val timeSource = TestTimeSource()
    val queue = MarkdownFrontendRunnerRequestService.createForTest(timeSource)
    Disposer.register(testRootDisposable, queue)
    val request = createFrontendRequest()
    queue.request(request)
    timeSource += 5.seconds

    val received = async(start = CoroutineStart.UNDISPATCHED) { queue.requests().first() }
    assertFalse(received.isCompleted)
    val freshRequest = request.copy(command = "echo fresh")
    queue.request(freshRequest)

    assertEquals(freshRequest, received.await())
  }

  @Test
  fun `a disposed queue ignores new clicks`() {
    val queue = MarkdownFrontendRunnerRequestService()
    Disposer.dispose(queue)

    queue.request(createFrontendRequest())
  }

  private fun createFrontendRequest(): MarkdownFrontendRunnerRequest {
    return MarkdownFrontendRunnerRequest(
      projectId = project.projectId(),
      languageId = myFixture.file.language.id,
      command = "pwd",
      sourceFileUrl = myFixture.file.virtualFile.url,
      showTargetChooser = true,
      offset = 3,
      workingDirectoryPaths = requireNotNull(getMarkdownCommandWorkingDirectoryPaths(project, myFixture.file.virtualFile)),
      editorId = myFixture.editor.editorId(),
    )
  }

  private fun fireBlockMarkerAction(
    place: String = ActionPlaces.EDITOR_GUTTER,
    editor: Editor = myFixture.editor,
    inputEvent: MouseEvent? = MouseEvent(editor.component, MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, 1, false),
  ) {
    myFixture.doHighlighting()
    val markers = DaemonCodeAnalyzerImpl.getLineMarkers(myFixture.editor.document, project)
    val blockMarker = markers.first { it.icon == AllIcons.RunConfigurations.TestState.Run_run }
    val action = (blockMarker.createGutterRenderer() as GutterIconRenderer).clickAction!!
    val dataContext = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, project)
      .add(CommonDataKeys.EDITOR, editor)
      .build()
    val event = AnActionEvent.createEvent(dataContext, action.templatePresentation.clone(), place, ActionUiKind.NONE, inputEvent)
    action.actionPerformed(event)
    assertNull("The backend action must delegate execution to the frontend", capturingRunner.capturedCommand)
    awaitFrontendRunner()
  }

  private fun awaitFrontendRunner() {
    service<MarkdownFrontendRunnerFlowService>()
    PlatformTestUtil.waitWithEventsDispatching("The frontend runner did not execute", { capturingRunner.capturedCommand != null }, 10)
  }

  private class CapturingRunner : MarkdownRunner {
    var capturedDir: String? = "not-captured"
    var capturedCommand: String? = null
    var capturedContext: MarkdownRunnerContext? = null
    var failNext = false
    var reuseTarget = false

    override fun isApplicable(language: Language?) = true

    override fun run(command: String, project: Project, workingDirectory: String?, executor: Executor): Boolean {
      capturedDir = workingDirectory
      capturedCommand = command
      if (failNext) {
        failNext = false
        error("Test runner failure")
      }
      return true
    }

    override fun run(
      command: String,
      project: Project,
      executor: Executor,
      context: MarkdownRunnerContext,
    ): Boolean {
      capturedContext = context
      if (reuseTarget) return run(command, project, null, executor)
      return super.run(command, project, executor, context)
    }

    override fun title() = "Test Runner"
  }
}
