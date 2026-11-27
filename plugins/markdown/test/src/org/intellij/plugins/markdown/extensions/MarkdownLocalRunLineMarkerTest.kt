// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.extensions

import com.intellij.execution.Executor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.injected.editor.EditorWindow
import com.intellij.lang.Language
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.rpc.lite.LiteRemoteApiProviderService
import com.intellij.platform.rpc.lite.RemoteApiProvider
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightProjectFixture
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil
import com.intellij.sh.ShLanguage
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.replaceService
import fleet.rpc.RemoteApi
import fleet.rpc.RemoteApiDescriptor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.MarkdownCodeFenceRunner
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.MarkdownCommandRunnerPreviewExtension
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.MarkdownCommandWorkingDirectoryPaths
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.MarkdownRunner
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.MarkdownRunnerContext
import org.intellij.plugins.markdown.settings.MarkdownExtensionsSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import javax.swing.JPanel

@TestApplication
internal class MarkdownLocalRunLineMarkerTest {
  private val projectFixture = codeInsightProjectFixture()
  private val fixture by codeInsightFixture(projectFixture)

  @TestDisposable
  private lateinit var disposable: Disposable

  @BeforeEach
  fun setUp() {
    val settings = MarkdownExtensionsSettings.getInstance()
    val previousState = settings.extensionsEnabledState.toMutableMap()
    Disposer.register(disposable) { settings.extensionsEnabledState = previousState }
    settings.extensionsEnabledState[MarkdownCommandRunnerPreviewExtension.EXTENSION_ID] = true
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `the run context action uses frontend state and resolves the working directory`(injected: Boolean): Unit = timeoutRunBlocking {
    val backendPaths = MarkdownCommandWorkingDirectoryPaths("/backend/project", "/backend/project/docs", "/docs")
    installProvider(WorkingDirectoryProvider(backendPaths), this)
    val runner = CapturingRunner()
    ExtensionTestUtil.maskExtensions(MarkdownCodeFenceRunner.EP_NAME, listOf(runner), disposable)
    val element = configureFence()

    withContext(Dispatchers.EDT) {
      val hostFile = element.containingFile
      val hostEditor = fixture.editor
      val offset = hostFile.text.indexOf("pwd")
      hostEditor.caretModel.moveToOffset(offset)
      val file = if (injected) {
        requireNotNull(InjectedLanguageManager.getInstance(fixture.project).findInjectedElementAt(hostFile, offset)).containingFile
      }
      else {
        hostFile
      }
      val editor = if (injected) InjectedLanguageUtil.getInjectedEditorForInjectedFile(hostEditor, file) else hostEditor
      assertEquals(injected, editor is EditorWindow)
      val action = requireNotNull(ActionManager.getInstance().getAction("Markdown.RunCodeFence"))
      val dataContext = SimpleDataContext.builder()
        .add(CommonDataKeys.PROJECT, fixture.project)
        .add(CommonDataKeys.EDITOR, editor)
        .add(CommonDataKeys.PSI_FILE, file)
        .build()
      val event = AnActionEvent.createEvent(
        dataContext, action.templatePresentation.clone(), ActionPlaces.KEYBOARD_SHORTCUT, ActionUiKind.NONE, null,
      )

      action.update(event)
      assertTrue(event.presentation.isEnabledAndVisible)
      action.actionPerformed(event)

      val (sourceFileUrl, showTargetChooser, component, _, _, paths, fenceLanguage) = runner.context.await()
      assertEquals("pwd", runner.command?.trim())
      assertSame(fixture.project, runner.project)
      assertEquals(hostFile.virtualFile.url, sourceFileUrl)
      assertEquals("bash", fenceLanguage)
      assertEquals(backendPaths, paths)
      assertSame(editor.contentComponent, component)
      assertFalse(showTargetChooser)
    }
  }

  @Test
  fun `the frontend calls a legacy runner with the original overload`(): Unit = timeoutRunBlocking {
    installProvider(null, this)
    val legacy = LegacyCapturingRunner()
    ExtensionTestUtil.maskExtensions(MarkdownRunner.EP_NAME, listOf(legacy), disposable)
    ExtensionTestUtil.maskExtensions(MarkdownCodeFenceRunner.EP_NAME, listOf(CapturingRunner()), disposable)
    val project = projectFixture.get()
    val runner = requireNotNull(MarkdownCodeFenceRunner.findRunner(ShLanguage.INSTANCE))

    val accepted = withContext(Dispatchers.EDT) {
      runner.run(
        "echo legacy", project,
        MarkdownRunnerContext(
          "file:///legacy.md", false, JPanel(), 0, 0, MarkdownCommandWorkingDirectoryPaths("/legacy", "/legacy", ""),
        ),
      )
    }

    assertTrue(accepted)
    assertEquals("Legacy Runner", runner.title())
    assertEquals("echo legacy", legacy.command)
    assertEquals("/legacy", legacy.workingDirectory)
    assertSame(project, legacy.project)
    assertSame(DefaultRunExecutor.getRunExecutorInstance(), legacy.executor)
  }

  @Test
  fun `the frontend keeps the legacy context overload and return value`(): Unit = timeoutRunBlocking {
    installProvider(null, this)
    var receivedContext: MarkdownRunnerContext? = null
    var receivedExecutor: Executor? = null
    val legacy = object : LegacyCapturingRunner() {
      override fun run(command: String, project: Project, executor: Executor, context: MarkdownRunnerContext): Boolean {
        receivedContext = context
        receivedExecutor = executor
        return false
      }

      override fun run(command: String, project: Project, workingDirectory: String?, executor: Executor): Boolean =
        error("The context overload must handle the command.")
    }
    ExtensionTestUtil.maskExtensions(MarkdownRunner.EP_NAME, listOf(legacy), disposable)
    val project = projectFixture.get()
    val runner = requireNotNull(MarkdownCodeFenceRunner.findRunner(ShLanguage.INSTANCE))
    val context = withContext(Dispatchers.EDT) {
      MarkdownRunnerContext(
        "file:///legacy.md", true, JPanel(), 20, 40, MarkdownCommandWorkingDirectoryPaths("/project", "/project/docs", "/docs"),
      )
    }

    val accepted = withContext(Dispatchers.EDT) { runner.run("echo legacy", project, context) }

    assertFalse(accepted)
    assertSame(context, receivedContext)
    assertSame(DefaultRunExecutor.getRunExecutorInstance(), receivedExecutor)
  }

  @Test
  fun `a connected legacy runner declines commands until its API is available`(): Unit = timeoutRunBlocking {
    val provider = UnresolvedProvider()
    installProvider(provider, this)
    val legacy = LegacyCapturingRunner()
    ExtensionTestUtil.maskExtensions(MarkdownRunner.EP_NAME, listOf(legacy), disposable)
    val project = projectFixture.get()
    val runner = requireNotNull(MarkdownCodeFenceRunner.findRunner(ShLanguage.INSTANCE))
    assertTrue(LiteRemoteApiProviderService.isConnected())

    val accepted = withContext(Dispatchers.EDT) {
      runner.run(
        "echo legacy", project,
        MarkdownRunnerContext("file:///legacy.md", false, JPanel(), 0, 0, MarkdownCommandWorkingDirectoryPaths("/", "/", "")),
      )
    }

    assertFalse(accepted)
    assertNull(legacy.command)
    assertEquals(1, provider.tryResolutions)
  }

  private fun installProvider(provider: RemoteApiProvider?, scope: CoroutineScope) {
    ExtensionTestUtil.maskExtensions(RemoteApiProvider.EP_NAME, listOfNotNull(provider), disposable, fireEvents = false)
    ApplicationManager.getApplication().replaceService(
      LiteRemoteApiProviderService::class.java, LiteRemoteApiProviderService(scope), disposable,
    )
  }

  private suspend fun configureFence(): PsiElement = withContext(Dispatchers.EDT) {
    val file = fixture.addFileToProject("docs/local.md", "```bash\npwd\n```")
    fixture.openFileInEditor(file.virtualFile)
    requireNotNull(file.findElementAt(3))
  }

  private class CapturingRunner : MarkdownCodeFenceRunner {
    var command: String? = null
    var project: Project? = null
    val context = CompletableDeferred<MarkdownRunnerContext>()

    override fun isApplicable(language: Language?): Boolean = language?.id == "Shell Script"

    override fun run(command: String, project: Project, context: MarkdownRunnerContext): Boolean {
      this.command = command
      this.project = project
      this.context.complete(context)
      return true
    }

    override fun title(): String = "Test Runner"
  }

  private open class LegacyCapturingRunner : MarkdownRunner {
    var command: String? = null
    var project: Project? = null
    var workingDirectory: String? = null
    var executor: Executor? = null

    override fun isApplicable(language: Language?): Boolean = language?.id == "Shell Script"

    override fun run(command: String, project: Project, workingDirectory: String?, executor: Executor): Boolean {
      this.command = command
      this.project = project
      this.workingDirectory = workingDirectory
      this.executor = executor
      return true
    }

    override fun title(): String = "Legacy Runner"
  }

  private class WorkingDirectoryProvider(private val paths: MarkdownCommandWorkingDirectoryPaths) : RemoteApiProvider {
    override suspend fun <T : RemoteApi<Unit>> resolve(descriptor: RemoteApiDescriptor<T>): T = descriptor.clientStub { method, _ ->
      assertEquals("getWorkingDirectoryPaths", method)
      paths
    }

    override fun <T : RemoteApi<Unit>> tryResolve(descriptor: RemoteApiDescriptor<T>): T? =
      error("The action must resolve the API asynchronously.")
  }

  private class UnresolvedProvider : RemoteApiProvider {
    var tryResolutions = 0

    override suspend fun <T : RemoteApi<Unit>> resolve(descriptor: RemoteApiDescriptor<T>): T =
      error("The runner must not wait for an API.")

    override fun <T : RemoteApi<Unit>> tryResolve(descriptor: RemoteApiDescriptor<T>): T? {
      tryResolutions++
      return null
    }
  }
}
