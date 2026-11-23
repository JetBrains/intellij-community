// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions

import com.intellij.codeInsight.daemon.impl.LineMarkersPass.getDisplayedLineMarkers
import com.intellij.execution.Executor
import com.intellij.icons.AllIcons
import com.intellij.ide.actions.runAnything.activity.RunAnythingProvider
import com.intellij.ide.actions.runAnything.activity.RunAnythingProviderBase
import com.intellij.lang.Language
import com.intellij.markdown.backend.services.MarkdownFrontendRunnerRequestService
import com.intellij.markdown.frontend.execution.MarkdownFrontendRunnerFlowService
import com.intellij.markdown.jcef.preview.HtmlSourceTextPreprocessor
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.editorId
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.BaseProjectDirectories
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.project.projectId
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.EditorTestFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.replaceService
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.CommandRunnerExtension
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.MarkdownRunner
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.MarkdownRunnerContext
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.PreviewCommandRunnability
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.getMarkdownCommandWorkingDirectoryPaths
import org.intellij.plugins.markdown.service.MarkdownFrontendRunnerRequest
import org.intellij.plugins.markdown.settings.MarkdownSettings
import org.intellij.plugins.markdown.ui.preview.BrowserPipe
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanel
import org.intellij.plugins.markdown.ui.preview.PreviewClickConfirmation
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.Point
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.SwingUtilities
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

@TestApplication
class MarkdownCommandRunnerLineMarkersTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  @TestDisposable
  lateinit var disposable: Disposable

  private val markdownRunnerEp = ExtensionPointName.create<MarkdownRunner>("org.intellij.markdown.markdownRunner")
  private val capturingRunner = CapturingRunner()
  private lateinit var runnerDisposable: Disposable
  private lateinit var registeredRunners: List<MarkdownRunner>

  @BeforeEach
  fun setUp() {
    registeredRunners = markdownRunnerEp.extensionList
    runnerDisposable = Disposer.newDisposable()
    Disposer.register(disposable, runnerDisposable)
    ExtensionTestUtil.maskExtensions(markdownRunnerEp, listOf(capturingRunner), runnerDisposable)
    val testMdFile = myFixture.addFileToProject("foo/test.md", "```bash\npwd\n```")
    timeoutRunBlocking {
      withContext(Dispatchers.EDT) {
        myFixture.openFileInEditor(testMdFile.virtualFile)
      }
    }
  }

  @AfterEach
  fun tearDown() {
    MarkdownSettings.getInstance().useFileDirectoryForCommands = null
  }

  @Test
  fun `block run marker is shown for shell code fence`() {
    myFixture.doHighlighting()
    val markers = timeoutRunBlocking { readAction { getDisplayedLineMarkers(myFixture.editor.document, myFixture.project) } }
    assertNotNull(markers.firstOrNull { it.icon == AllIcons.RunConfigurations.TestState.Run_run })
  }

  @Test
  fun `line run marker includes continued lines`(): Unit = timeoutRunBlocking {
    ExtensionTestUtil.maskExtensions(RunAnythingProvider.EP_NAME, listOf(object : RunAnythingProviderBase<String>() {
      override fun findMatchingValue(dataContext: DataContext, pattern: String): String? = pattern.takeIf { it.startsWith("npm start ") }
      override fun getCommand(value: String): String = value
      override fun execute(dataContext: DataContext, value: String) = Unit
    }), disposable)
    val file = myFixture.addFileToProject("foo/README.md", """
      ```
      npm start -- \
      --port=9090 \
      --host=example.com
      npm stop
      ```
    """.trimIndent())
    withContext(Dispatchers.EDT) {
      myFixture.openFileInEditor(file.virtualFile)

      myFixture.doHighlighting()

      val marker = getDisplayedLineMarkers(myFixture.editor.document, myFixture.project).single { it.icon == AllIcons.RunConfigurations.TestState.Run }
      assertEquals(1, myFixture.editor.document.getLineNumber(marker.startOffset))
      assertEquals("Run 'npm start -- --port=9090 --host=example.com'", marker.lineMarkerTooltip)
    }
  }

  @Test
  fun `line run marker recognizes asdf command`(): Unit = timeoutRunBlocking {
    val file = myFixture.addFileToProject("foo/asdf.md", "Run `asdf install nodejs 18.16.0`")
    withContext(Dispatchers.EDT) {
      myFixture.openFileInEditor(file.virtualFile)
      myFixture.doHighlighting()

      val markers = getDisplayedLineMarkers(myFixture.editor.document, myFixture.project)
      assertNotNull(markers.firstOrNull { it.icon == AllIcons.RunConfigurations.TestState.Run })
    }
  }

  @Test
  fun `run context shortcut executes a shell code fence`(): Unit = timeoutRunBlocking {
    MarkdownSettings.getInstance().useFileDirectoryForCommands = true
    withContext(Dispatchers.EDT) {
      val editor = myFixture.editor
      editor.caretModel.moveToOffset(editor.document.text.indexOf("pwd"))
      val shortcut = KeymapUtil.getPrimaryShortcut("RunClass") as KeyboardShortcut
      val stroke = shortcut.firstKeyStroke
      val inputEvent = KeyEvent(
        editor.contentComponent, KeyEvent.KEY_PRESSED, 0, stroke.modifiers, stroke.keyCode, KeyEvent.CHAR_UNDEFINED,
      )

      assertTrue(EditorTestFixture.performEditorShortcut(editor, myFixture.project, shortcut, inputEvent, "run shortcut"))
      awaitFrontendRunner()
      assertEquals("pwd", capturingRunner.capturedCommand?.trim())
    }
  }

  @Test
  fun `terminal fence markers do not require a backend runner`(): Unit = timeoutRunBlocking {
    Disposer.dispose(runnerDisposable)
    ExtensionTestUtil.maskExtensions(markdownRunnerEp, emptyList(), disposable)

    withContext(Dispatchers.EDT) {
      for (alias in listOf("shell", "bash", "sh", "zsh", "console", "powershell", "posh", "pwsh", "PowerShell")) {
        val file = myFixture.addFileToProject("foo/$alias.md", "```$alias\npwd\n```")
        myFixture.openFileInEditor(file.virtualFile)
        myFixture.doHighlighting()
        val markers = getDisplayedLineMarkers(myFixture.editor.document, myFixture.project)
        val marker = markers.firstOrNull { it.icon == AllIcons.RunConfigurations.TestState.Run_run }
        assertNotNull(marker, "The $alias fence must have a block marker")
        val action = (marker!!.createGutterRenderer() as GutterIconRenderer).clickAction!!
        assertEquals("Run in Terminal", action.templatePresentation.text)
      }
    }
  }

  @Test
  fun `preview preserves distinct Bash and Zsh fence languages`(): Unit = timeoutRunBlocking {
    MarkdownSettings.getInstance().useFileDirectoryForCommands = true
    val pipe = TestBrowserPipe()
    val extension = createPreviewExtension(pipe)
    val command = "ls \"$(pwd)\""
    val bashHtml = extension.processCodeBlock(command, "bash")
    val zshHtml = extension.processCodeBlock(command, "zsh")
    assertFalse(bashHtml == zshHtml)

    withContext(Dispatchers.EDT) {
      for (shell in listOf("bash", "zsh")) {
        capturingRunner.capturedCommand = null
        clickPreviewBlock(pipe, if (shell == "bash") bashHtml else zshHtml)
        awaitFrontendRunner()

        assertEquals(command, capturingRunner.capturedCommand)
        assertEquals(shell, capturingRunner.capturedContext?.fenceLanguage)
      }
    }
  }

  @Test
  fun `block marker runs only the prompt lines of a console block`(): Unit = timeoutRunBlocking {
    MarkdownSettings.getInstance().useFileDirectoryForCommands = true
    val file = myFixture.addFileToProject("foo/console.md", "```console\n$ echo one\none\n# whoami\nroot\n```")
    withContext(Dispatchers.EDT) {
      myFixture.openFileInEditor(file.virtualFile)

      fireBlockMarkerAction()

      assertEquals(" echo one\n whoami", capturingRunner.capturedCommand)
    }
  }

  @Test
  fun `preview runs only the prompt lines of a console block`(): Unit = timeoutRunBlocking {
    replacePreviewCommandRunnability("")
    MarkdownSettings.getInstance().useFileDirectoryForCommands = true
    val pipe = TestBrowserPipe()
    val extension = createPreviewExtension(pipe)
    val content = "$ echo one\none"
    val consoleHtml = extension.processCodeBlock(content, "console")
    val shellHtml = extension.processCodeBlock(content, "shell")

    withContext(Dispatchers.EDT) {
      clickPreviewBlock(pipe, consoleHtml)
      PlatformTestUtil.waitWithEventsDispatching("The console command did not execute", { capturingRunner.capturedCommand != null }, 10)

      assertEquals(" echo one", capturingRunner.capturedCommand)
      capturingRunner.capturedCommand = null

      clickPreviewBlock(pipe, shellHtml)
      PlatformTestUtil.waitWithEventsDispatching("The shell command did not execute", { capturingRunner.capturedCommand != null }, 10)

      assertEquals(" echo one\none", capturingRunner.capturedCommand)
    }
  }

  @Test
  fun `PowerShell block reaches the frontend intact without the language plugin`(): Unit = timeoutRunBlocking {
    assertNull(Language.findLanguageByID("PowerShell"))
    capturingRunner.acceptsLanguage = registeredRunners.single { it.javaClass.simpleName == "ShMarkdownRunner" }::isApplicable
    MarkdownSettings.getInstance().useFileDirectoryForCommands = true
    val command = "\$text = @'\n# text\n\n'@\n\$env:EXAMPLE = \$text"
    val file = myFixture.addFileToProject("foo/powershell.md", "```powershell title=demo\n$command\n```")
    withContext(Dispatchers.EDT) {
      myFixture.openFileInEditor(file.virtualFile)

      fireBlockMarkerAction()

      assertEquals(command, capturingRunner.capturedCommand?.trim())
    }
  }

  @Test
  fun `preview preserves PowerShell commands and strips shell comments`(): Unit = timeoutRunBlocking {
    capturingRunner.acceptsLanguage = registeredRunners.single { it.javaClass.simpleName == "ShMarkdownRunner" }::isApplicable
    MarkdownSettings.getInstance().useFileDirectoryForCommands = true
    val pipe = TestBrowserPipe()
    val extension = createPreviewExtension(pipe)
    val command = "\$value = 'hello' # comment"
    val shellHtml = extension.processCodeBlock(command, "shell")
    val powerShellHtml = extension.processCodeBlock(command, "powershell")
    assertFalse(shellHtml == powerShellHtml)

    withContext(Dispatchers.EDT) {
      clickPreviewBlock(pipe, powerShellHtml)
      PlatformTestUtil.waitWithEventsDispatching("The PowerShell runner did not execute", { capturingRunner.capturedCommand != null }, 10)

      assertEquals(command, capturingRunner.capturedCommand)
      capturingRunner.capturedCommand = null

      clickPreviewBlock(pipe, shellHtml)
      PlatformTestUtil.waitWithEventsDispatching("The shell command did not execute", { capturingRunner.capturedCommand != null }, 10)

      assertEquals("\$value = 'hello'", capturingRunner.capturedCommand)
    }
  }

  @Test
  fun `preview line icon of a fence runs after the second render`() {
    MarkdownSettings.getInstance().useFileDirectoryForCommands = true
    val command = "echo second-render-${System.nanoTime()}"
    val runnability = replacePreviewCommandRunnability(command)
    val file = myFixture.addFileToProject("foo/second-render.md", "```shell\n$command\n```").virtualFile
    val pipe = TestBrowserPipe()
    createRegisteredPreviewExtension(createPreviewPanel(file, pipe))

    renderPreview(file)
    val html = renderPreview(file)

    clickPreviewLine(pipe, html)
    assertEquals(command, runnability.executedCommand?.trim())
  }

  @Test
  fun `preview puts the run-line icon of a fence before the text of its line`() {
    val command = "echo plain-${System.nanoTime()}"
    replacePreviewCommandRunnability(command)
    val file = myFixture.addFileToProject("foo/plain.md", "```shell\necho first\n$command\n```").virtualFile
    createRegisteredPreviewExtension(createPreviewPanel(file, TestBrowserPipe()))

    val html = renderPreview(file)

    val lineIcon = html.indexOf("class='run-icon-line code-block'")
    assertTrue(lineIcon >= 0, "The command has no colored token, but it must still get a run-line icon: $html")
    assertTrue(lineIcon < html.indexOf(command), "The run-line icon must come before the text of its line: $html")
    assertTrue(lineIcon > html.indexOf("echo first"), "The run-line icon must come after the previous line: $html")
  }

  @Test
  fun `preview gives the first line of a fence a run-line icon also when the fence has a block icon`() {
    val command = "echo first-${System.nanoTime()}"
    replacePreviewCommandRunnability(command)
    val file = myFixture.addFileToProject("foo/first-line.md", "```shell\n$command\necho second\n```").virtualFile
    createRegisteredPreviewExtension(createPreviewPanel(file, TestBrowserPipe()))

    val html = renderPreview(file)

    val blockIcon = html.indexOf("class='run-icon code-block'")
    val lineIcon = html.indexOf("class='run-icon-line code-block'")
    assertTrue(blockIcon >= 0, "The fence must have a block icon: $html")
    assertTrue(lineIcon >= 0, "The first line must have a run-line icon: $html")
    assertTrue(blockIcon < lineIcon, "The block icon comes first, for the outer column: $html")
    assertTrue(lineIcon < html.indexOf(command, lineIcon), "The run-line icon must come before the text of its line: $html")
  }

  @Test
  fun `preview gives the first line of a fence a run-line icon when the fence has no block icon`() {
    MarkdownSettings.getInstance().useFileDirectoryForCommands = true
    capturingRunner.acceptsLanguage = registeredRunners.single { it.javaClass.simpleName == "ShMarkdownRunner" }::isApplicable
    val command = "echo no-block-${System.nanoTime()}"
    val runnability = replacePreviewCommandRunnability(command)
    val file = myFixture.addFileToProject("foo/no-block.md", "```text\n$command\n```").virtualFile
    val pipe = TestBrowserPipe()
    createRegisteredPreviewExtension(createPreviewPanel(file, pipe))

    val html = renderPreview(file)

    assertFalse(html.contains("class='run-icon code-block'"), "No block runner handles a text fence: $html")
    clickPreviewLine(pipe, html)
    assertEquals(command, runnability.executedCommand?.trim())
  }

  @Test
  fun `preview block icon runs after the preview extensions reload`(): Unit = timeoutRunBlocking {
    MarkdownSettings.getInstance().useFileDirectoryForCommands = true
    val command = "echo reload-${System.nanoTime()}"
    replacePreviewCommandRunnability(command)
    val file = myFixture.addFileToProject("foo/reload.md", "```shell\n$command\n```").virtualFile
    val panel = createPreviewPanel(file, TestBrowserPipe())
    val extension = createRegisteredPreviewExtension(panel)
    val html = renderPreview(file)

    withContext(Dispatchers.EDT) {
      Disposer.dispose(extension)
      createRegisteredPreviewExtension(panel)

      clickPreviewBlock(panel.browserPipe as TestBrowserPipe, html)
      PlatformTestUtil.waitWithEventsDispatching("The block runner did not execute", { capturingRunner.capturedCommand != null }, 10)
      assertEquals(command, capturingRunner.capturedCommand?.trim())
    }
  }

  @Test
  fun `unsupported fences have no block marker without a runner`(): Unit = timeoutRunBlocking {
    Disposer.dispose(runnerDisposable)
    ExtensionTestUtil.maskExtensions(markdownRunnerEp, emptyList(), disposable)

    withContext(Dispatchers.EDT) {
      for (language in listOf("markdown", "unknown-test-language")) {
        val file = myFixture.addFileToProject("foo/$language.md", "```$language\ntext\n```")
        myFixture.openFileInEditor(file.virtualFile)
        myFixture.doHighlighting()
        val markers = getDisplayedLineMarkers(myFixture.editor.document, myFixture.project)
        assertNull(markers.firstOrNull { it.icon == AllIcons.RunConfigurations.TestState.Run_run })
      }
    }
  }

  @Test
  fun `block run marker invokes runner with base directory as working directory`(): Unit = timeoutRunBlocking {
    MarkdownSettings.getInstance().useFileDirectoryForCommands = false
    withContext(Dispatchers.EDT) {
      fireBlockMarkerAction()
      val expected = BaseProjectDirectories.getInstance(myFixture.project).getBaseDirectoryFor(myFixture.file.virtualFile)?.canonicalPath
      assertEquals(expected, capturingRunner.capturedDir)
    }
  }

  @Test
  fun `block run marker invokes runner with file directory as working directory when setting is enabled`(): Unit = timeoutRunBlocking {
    MarkdownSettings.getInstance().useFileDirectoryForCommands = true
    withContext(Dispatchers.EDT) {
      fireBlockMarkerAction()
      assertEquals(myFixture.file.virtualFile.parent.canonicalPath, capturingRunner.capturedDir)
    }
  }

  @Test
  fun `frontend uses the backend project directory for a file without a parent`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      checkDirectoryForParentlessFile(useFileDirectory = false)
    }
  }

  @Test
  fun `frontend uses the backend file directory for a file without a parent`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      checkDirectoryForParentlessFile(useFileDirectory = true)
    }
  }

  @RequiresEdt
  private fun checkDirectoryForParentlessFile(useFileDirectory: Boolean) {
    MarkdownSettings.getInstance().useFileDirectoryForCommands = useFileDirectory
    val backendFile = myFixture.file.virtualFile
    val frontendFile = object : LightVirtualFile("remote.md", myFixture.file.text) {
      override fun getPath(): String = backendFile.path
    }
    assertNull(frontendFile.parent)
    val editorFactory = EditorFactory.getInstance()
    val document = requireNotNull(FileDocumentManager.getInstance().getDocument(frontendFile))
    val editor = editorFactory.createEditor(document, myFixture.project)
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
  fun `block run marker passes the Markdown source file to the runner`(): Unit = timeoutRunBlocking {
    MarkdownSettings.getInstance().useFileDirectoryForCommands = true

    withContext(Dispatchers.EDT) {
      fireBlockMarkerAction()

      assertEquals(myFixture.file.virtualFile.url, capturingRunner.capturedContext?.sourceFileUrl)
      assertEquals("bash", capturingRunner.capturedContext?.fenceLanguage)
    }
  }

  @Test
  fun `frontend continues after a runner failure`(): Unit = timeoutRunBlocking {
    MarkdownSettings.getInstance().useFileDirectoryForCommands = true
    capturingRunner.failNext = true
    withContext(Dispatchers.EDT) {
      fireBlockMarkerAction()
      capturingRunner.capturedCommand = null

      fireBlockMarkerAction()

      assertEquals("pwd", capturingRunner.capturedCommand?.trim())
    }
  }

  @Test
  fun `gutter popup action requests the target chooser`(): Unit = timeoutRunBlocking {
    MarkdownSettings.getInstance().useFileDirectoryForCommands = true

    withContext(Dispatchers.EDT) {
      fireBlockMarkerAction(place = ActionPlaces.EDITOR_GUTTER_POPUP)

      assertTrue(capturingRunner.capturedContext?.showTargetChooser == true)
    }
  }

  @Test
  fun `frontend runs an existing target without choosing a working directory`(): Unit = timeoutRunBlocking {
    capturingRunner.reuseTarget = true

    withContext(Dispatchers.EDT) {
      fireBlockMarkerAction(place = ActionPlaces.EDITOR_GUTTER_POPUP)

      assertNull(capturingRunner.capturedDir)
      assertNull(MarkdownSettings.getInstance().useFileDirectoryForCommands)
      assertTrue(capturingRunner.capturedContext?.showTargetChooser == true)
    }
  }

  @Test
  fun `keyboard gutter action anchors the chooser after scrolling`(): Unit = timeoutRunBlocking {
    MarkdownSettings.getInstance().useFileDirectoryForCommands = true
    val prefix = "\n".repeat(100)
    val file = myFixture.addFileToProject("foo/scrolled.md", prefix + "```shell\npwd\n```")
    withContext(Dispatchers.EDT) {
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
  }

  @Test
  fun `gutter popup action anchors the chooser at the mouse click`(): Unit = timeoutRunBlocking {
    MarkdownSettings.getInstance().useFileDirectoryForCommands = true
    withContext(Dispatchers.EDT) {
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
  }

  @Test
  fun `frontend runner uses the action editor`(): Unit = timeoutRunBlocking {
    MarkdownSettings.getInstance().useFileDirectoryForCommands = true
    withContext(Dispatchers.EDT) {
      val editorFactory = EditorFactory.getInstance()
      val actionEditor = editorFactory.createEditor(myFixture.editor.document, myFixture.project)
      try {
        fireBlockMarkerAction(editor = actionEditor)

        assertEquals("pwd", capturingRunner.capturedCommand?.trim())
        assertSame(actionEditor.contentComponent, capturingRunner.capturedContext?.component)
      }
      finally {
        editorFactory.releaseEditor(actionEditor)
      }
    }
  }

  @Test
  fun `gutter popup action tells the user that it opens the terminal chooser`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      myFixture.doHighlighting()
      val markers = getDisplayedLineMarkers(myFixture.editor.document, myFixture.project)
      val blockMarker = markers.first { it.icon == AllIcons.RunConfigurations.TestState.Run_run }
      val action = (blockMarker.createGutterRenderer() as GutterIconRenderer).clickAction!!
      val dataContext = SimpleDataContext.builder().add(CommonDataKeys.PROJECT, myFixture.project).build()
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
  }

  @Test
  fun `block run marker strips trailing hash comment from command`(): Unit = timeoutRunBlocking {
    MarkdownSettings.getInstance().useFileDirectoryForCommands = true
    val testMdFile = myFixture.addFileToProject(
      "foo/withComment.md",
      "```bash\nnpm run dev       # start Vite dev server (HMR, localhost:5173)\n```"
    )
    withContext(Dispatchers.EDT) {
      myFixture.openFileInEditor(testMdFile.virtualFile)
      fireBlockMarkerAction()
      val command = capturingRunner.capturedCommand
      assertNotNull(command, "Runner was not invoked")
      assertEquals("npm run dev", command!!.trim())
    }
  }

  @Test
  fun `only the latest click remains queued before frontend startup`() {
    val queue = MarkdownFrontendRunnerRequestService()
    Disposer.register(disposable, queue)
    val request = createFrontendRequest()
    repeat(100) { queue.request(request.copy(command = "echo $it")) }

    val received = timeoutRunBlocking { queue.requests().first() }

    assertEquals("echo 99", received.command)
  }

  @Test
  fun `a recent click remains available before frontend startup`() {
    val timeSource = TestTimeSource()
    val queue = MarkdownFrontendRunnerRequestService.createForTest(timeSource)
    Disposer.register(disposable, queue)
    val request = createFrontendRequest()
    queue.request(request)
    timeSource += 4.seconds

    val received = timeoutRunBlocking { queue.requests().first() }

    assertEquals(request, received)
  }

  @Test
  fun `a click expires after five seconds without a frontend collector`(): Unit = timeoutRunBlocking {
    val timeSource = TestTimeSource()
    val queue = MarkdownFrontendRunnerRequestService.createForTest(timeSource)
    Disposer.register(disposable, queue)
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
      projectId = myFixture.project.projectId(),
      languageId = myFixture.file.language.id,
      command = "pwd",
      sourceFileUrl = myFixture.file.virtualFile.url,
      showTargetChooser = true,
      offset = 3,
      workingDirectoryPaths = requireNotNull(getMarkdownCommandWorkingDirectoryPaths(myFixture.project, myFixture.file.virtualFile)),
      editorId = myFixture.editor.editorId(),
    )
  }

  private fun createPreviewExtension(pipe: BrowserPipe? = null): CommandRunnerExtension {
    val panel = createPreviewPanel(myFixture.file.virtualFile, pipe)
    return CommandRunnerExtension(panel, CommandRunnerExtension.Provider()).also { Disposer.register(disposable, it) }
  }

  private fun createRegisteredPreviewExtension(panel: MarkdownHtmlPanel): CommandRunnerExtension {
    val provider = requireNotNull(MarkdownExtensionsUtil.findBrowserExtensionProvider<CommandRunnerExtension.Provider>())
    val extension = provider.createBrowserExtension(panel) as CommandRunnerExtension
    Disposer.register(disposable, extension)
    return extension
  }

  private fun renderPreview(file: VirtualFile): String {
    return runReadAction {
      val document = requireNotNull(FileDocumentManager.getInstance().getDocument(file))
      HtmlSourceTextPreprocessor().preprocessText(myFixture.project, document, file)
    }
  }

  private fun replacePreviewCommandRunnability(runnableCommand: String): RecordingPreviewCommandRunnability {
    val runnability = RecordingPreviewCommandRunnability(runnableCommand)
    ApplicationManager.getApplication().replaceService(PreviewCommandRunnability::class.java, runnability, disposable)
    return runnability
  }

  private fun createPreviewPanel(virtualFile: VirtualFile, pipe: BrowserPipe?): MarkdownHtmlPanel {
    val component = myFixture.editor.component
    val project = myFixture.project
    val panel = object : MarkdownHtmlPanel {
      override fun getComponent(): JComponent = component
      override fun getProject(): Project = project
      override fun getVirtualFile(): VirtualFile = virtualFile
      override fun getBrowserPipe(): BrowserPipe? = pipe
      override fun setHtml(html: String, initialScrollOffset: Int, document: VirtualFile?) = Unit
      override fun reloadWithOffset(offset: Int) = Unit
      override fun addScrollListener(listener: MarkdownHtmlPanel.ScrollListener) = Unit
      override fun removeScrollListener(listener: MarkdownHtmlPanel.ScrollListener) = Unit
      override fun dispose() = Unit
    }
    Disposer.register(disposable, panel)
    return panel
  }

  private fun clickPreviewBlock(pipe: TestBrowserPipe, html: String) {
    val command = requireNotNull(Regex("data-command='([^']+)'").find(html)).groupValues[1]
    pipe.receive("runBlock", "$command::0:0:0")
  }

  private fun clickPreviewLine(pipe: TestBrowserPipe, html: String) {
    val command = requireNotNull(Regex("class='run-icon-line[^']*'[^>]*data-command='([^']+)'").find(html)) {
      "The preview has no run-line icon: $html"
    }.groupValues[1]
    pipe.receive("runLine", "$command:0:0:${PreviewClickConfirmation.GENUINE_CLICK}")
  }

  private class RecordingPreviewCommandRunnability(private val runnableCommand: String) : PreviewCommandRunnability {
    var executedCommand: String? = null

    override fun isRunnable(project: Project, file: VirtualFile?, command: String, allowRunConfigurations: Boolean): Boolean {
      return command == runnableCommand
    }

    override fun execute(project: Project, file: VirtualFile?, command: String, executorId: String, workingDirectory: String) {
      executedCommand = command
    }

    override suspend fun resolvePending(): Boolean = false
  }

  @RequiresEdt
  private fun fireBlockMarkerAction(
    place: String = ActionPlaces.EDITOR_GUTTER,
    editor: Editor = myFixture.editor,
    inputEvent: MouseEvent? = MouseEvent(editor.component, MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, 1, false),
  ) {
    myFixture.doHighlighting()
    val markers = getDisplayedLineMarkers(myFixture.editor.document, myFixture.project)
    val blockMarker = markers.first { it.icon == AllIcons.RunConfigurations.TestState.Run_run }
    val action = (blockMarker.createGutterRenderer() as GutterIconRenderer).clickAction!!
    val dataContext = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, myFixture.project)
      .add(CommonDataKeys.EDITOR, editor)
      .build()
    val event = AnActionEvent.createEvent(dataContext, action.templatePresentation.clone(), place, ActionUiKind.NONE, inputEvent)
    action.actionPerformed(event)
    assertNull(capturingRunner.capturedCommand, "The backend action must delegate execution to the frontend")
    awaitFrontendRunner()
  }

  @RequiresEdt
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
    var acceptsLanguage: (Language?) -> Boolean = { true }

    override fun isApplicable(language: Language?): Boolean = acceptsLanguage(language)

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

  private class TestBrowserPipe : BrowserPipe {
    private val handlers = mutableMapOf<String, BrowserPipe.Handler>()

    override fun send(type: String, data: String) = Unit

    override fun subscribe(type: String, handler: BrowserPipe.Handler) {
      handlers[type] = handler
    }

    override fun removeSubscription(type: String, handler: BrowserPipe.Handler) {
      handlers.remove(type, handler)
    }

    fun receive(type: String, data: String) {
      check(!handlers.getValue(type).processMessageReceived(data))
    }

    override fun dispose() {
      handlers.clear()
    }
  }
}
