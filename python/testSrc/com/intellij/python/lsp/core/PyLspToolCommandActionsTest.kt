// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.lsp.core

import com.intellij.idea.TestFor
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.lsp.api.Lsp4jServer
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspClientDescriptor
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.python.junit5Tests.framework.pyProjectAt
import com.intellij.python.junit5Tests.framework.pyProjectFixture
import com.intellij.python.ruff.server.RuffLspClientDescriptor
import com.intellij.python.ruff.server.RuffLspIntegrationProvider
import com.intellij.python.ty.TyLspClientDescriptor
import com.intellij.python.ty.TyLspIntegrationProvider
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.createTestOpenProjectOptions
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.project.PyProject.Companion.asPyProject
import com.jetbrains.python.project.project
import com.jetbrains.python.sdk.internal.PYTHON_MODULE_ID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.ExecuteCommandOptions
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.ServerInfo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private const val COMMAND = "py89555.printDebugInformation"

/**
 * A server that advertises `executeCommandProvider` gets one application-wide action for each command. The servers of
 * all projects share that action. The action must go when the last server that holds it stops, and also when the
 * project of that server closes first. The platform stops a server of a closing project on a pooled thread, so the stop
 * can come after the project is gone.
 */
@TestApplication
@TestFor(issues = ["PY-89555"])
internal class PyLspToolCommandActionsTest {
  private val projectFixture = projectFixture(openAfterCreation = true)
  private val pyProjectFixture = projectFixture.pyProjectFixture()
  private val closedProjectPath = tempPathFixture()
  private val alphaPath = tempPathFixture()
  private val alphaProject = projectFixture.pyProjectFixture(alphaPath)
  private val betaPath = tempPathFixture()
  private val betaProject = projectFixture.pyProjectFixture(betaPath)

  /** A failed assertion must not leave an application-wide action to the next test. */
  @AfterEach
  fun unregisterCommandAction() {
    PyLspCommandActions.getInstance().releaseAll()
    ActionManager.getInstance().unregisterAction(commandActionId)
  }

  @Test
  fun `the stop of the server unregisters its command actions`() {
    val descriptor = descriptorOf(pyProjectFixture.get())
    descriptor.lspServerListener.serverInitialized(initializeResult())
    assertNotNull(commandAction())

    descriptor.lspServerListener.serverStopped(true)
    assertNull(commandAction())
  }

  @Test
  fun `the close of the project unregisters the command actions of a running server`(): Unit = timeoutRunBlocking {
    val projectManager = ProjectManagerEx.getInstanceEx()
    val project = projectManager.newProjectAsync(closedProjectPath.get(), createTestOpenProjectOptions())
    val descriptor = try {
      val module: Module = edtWriteAction {
        ModuleManager.getInstance(project).newModule(closedProjectPath.get().resolve("main.iml"), PYTHON_MODULE_ID).also {
          // A Python project needs a content root.
          ModuleRootModificationUtil.addContentRoot(it, closedProjectPath.get().toString())
        }
      }
      val pyProject = checkNotNull(module.asPyProject()) { "'${module.name}' must be a Python project" }
      descriptorOf(pyProject).also {
        it.lspServerListener.serverInitialized(initializeResult())
        assertNotNull(commandAction())
      }
    }
    finally {
      projectManager.forceCloseProjectAsync(project)
    }
    assertNull(commandAction(), "the closed project must not keep the command action")

    assertDoesNotThrow { descriptor.lspServerListener.serverStopped(true) }
  }

  @Test
  fun `the stop of a server keeps the command action that another server still holds`() {
    val first = descriptorOf(pyProjectFixture.get())
    val second = descriptorOf(pyProjectFixture.get())
    first.lspServerListener.serverInitialized(initializeResult())
    second.lspServerListener.serverInitialized(initializeResult())
    val sharedAction = commandAction()
    assertNotNull(sharedAction)

    first.lspServerListener.serverStopped(true)
    assertSame(sharedAction, commandAction(), "the first server must not unregister the action that the second one holds")

    second.lspServerListener.serverStopped(true)
    assertNull(commandAction())
  }

  /** A restart reuses the descriptor, and the old server can report its stop after the new one initialized. */
  @Test
  fun `the late stop of the old server keeps the command actions of the restarted one`() {
    val descriptor = descriptorOf(pyProjectFixture.get())
    descriptor.lspServerListener.serverInitialized(initializeResult())
    descriptor.lspServerListener.serverInitialized(initializeResult())
    val restartedAction = commandAction()
    assertNotNull(restartedAction)

    descriptor.lspServerListener.serverStopped(true)
    assertSame(restartedAction, commandAction(), "the stop of the old server must not unregister the action of the new one")

    descriptor.lspServerListener.serverStopped(true)
    assertNull(commandAction())
  }

  /** A server that fails to initialize also reports a stop, and that stop must not take the actions of a later start. */
  @Test
  fun `the stop of a server that never initialized keeps the command actions of the next start`() {
    val descriptor = descriptorOf(pyProjectFixture.get())
    descriptor.lspServerListener.serverStopped(false)
    descriptor.lspServerListener.serverInitialized(initializeResult())
    assertNotNull(commandAction())

    descriptor.lspServerListener.serverStopped(true)
    assertNull(commandAction())
  }

  /** Two servers of one tool run for two groups of modules, and they can initialize at the same time. */
  @Test
  @TestFor(issues = ["PY-93375"])
  fun `two servers that initialize at the same time take each command ID without a collision`() {
    val commands = (1..50).map { "$COMMAND$it" }
    val result = initializeResult(commands = commands)
    val actionIds = commands.map { commandActionId(it) }
    val executor = Executors.newFixedThreadPool(2)
    try {
      repeat(20) {
        val descriptors = List(2) { descriptorOf(pyProjectFixture.get()) }
        val start = CyclicBarrier(descriptors.size)
        descriptors
          .map { descriptor -> executor.submit { start.await(); descriptor.lspServerListener.serverInitialized(result) } }
          .forEach { it.get(30, TimeUnit.SECONDS) }
        assertEquals(emptyList<String>(), actionIds.filter { ActionManager.getInstance().getAction(it) == null },
                     "each command needs an action while the servers run")

        descriptors.forEach { it.lspServerListener.serverStopped(true) }
        assertEquals(emptyList<String>(), actionIds.filter { ActionManager.getInstance().getAction(it) != null },
                     "the stop of both servers must remove each command action")
      }
    }
    finally {
      executor.shutdownNow()
    }
  }

  @Test
  @TestFor(issues = ["PY-91655"])
  fun `the close of another project keeps the command action of this project`(): Unit = timeoutRunBlocking {
    val descriptor = descriptorOf(pyProjectFixture.get())
    descriptor.lspServerListener.serverInitialized(initializeResult())
    try {
      withOtherProject { otherProject ->
        descriptorOf(otherProject).lspServerListener.serverInitialized(initializeResult())
        assertNotNull(commandAction())
      }
      assertNotNull(commandAction(), "the server of this project still runs, so its command action must stay")
    }
    finally {
      descriptor.lspServerListener.serverStopped(true)
    }
    assertNull(commandAction())
  }

  /**
   * The servers of all projects share the action, so the action keeps no project. It must run the command on a server
   * of the project of the event.
   */
  @Test
  @TestFor(issues = ["PY-91655"])
  fun `the command action runs on the server of the project of the event`(): Unit = timeoutRunBlocking {
    val thisProject = projectFixture.get()
    val sent = CompletableDeferred<LspClient>()
    val thisClient = commandClient(descriptorOf(pyProjectFixture.get()), sent = sent)
    val action = PyLspCommandAction("ty: $COMMAND", TyLspIntegrationProvider::class.java, COMMAND) { project ->
      if (project == thisProject) listOf(thisClient) else emptyList()
    }

    withOtherProject { otherProject ->
      val otherEvent = updated(action, otherProject.project)
      assertFalse(otherEvent.presentation.isEnabledAndVisible, "the other project runs no server of the tool")
    }
    val thisEvent = updated(action, thisProject)
    assertTrue(thisEvent.presentation.isEnabledAndVisible)
    withContext(Dispatchers.EDT) { action.actionPerformed(thisEvent) }
    assertSame(thisClient, sent.await())
  }

  /** A tool with one server for each module must run the command for the module of the file. */
  @Test
  @TestFor(issues = ["PY-91655"])
  fun `the command goes to the running server of the file`(): Unit = timeoutRunBlocking {
    val betaPy = Files.writeString(betaPath.get().resolve("beta.py"), "")
    val betaFile = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(betaPy))
    val alphaClient = commandClient(descriptorOf(alphaProject.get()))
    val betaClient = commandClient(descriptorOf(betaProject.get()))
    val stoppedBetaClient = commandClient(descriptorOf(betaProject.get()), serverState = LspServerState.ShutdownNormally)
    val otherBetaClient = fakePyLspClient(descriptorOf(betaProject.get()))

    readAction {
      assertSame(betaClient, pyLspCommandClient(listOf(alphaClient, stoppedBetaClient, otherBetaClient, betaClient), COMMAND, betaFile))
      assertSame(alphaClient, pyLspCommandClient(listOf(stoppedBetaClient, alphaClient, betaClient), COMMAND, null))
      assertSame(alphaClient, pyLspCommandClient(listOf(stoppedBetaClient, alphaClient), COMMAND, betaFile))
      assertNull(pyLspCommandClient(listOf(stoppedBetaClient, otherBetaClient), COMMAND, betaFile))
    }
  }

  @Test
  fun `a server without server info names the command action after the tool`() {
    val descriptor = descriptorOf(pyProjectFixture.get())
    descriptor.lspServerListener.serverInitialized(initializeResult(serverInfo = null))
    try {
      assertEquals("${descriptor.presentableName}: $COMMAND", commandAction()?.templateText)
    }
    finally {
      descriptor.lspServerListener.serverStopped(true)
    }
  }

  @Test
  @TestFor(issues = ["PY-86194"])
  fun `the Ruff autofix command gets an action that needs an editor`() {
    val descriptor = RuffLspClientDescriptor(pyProjectFixture.get()).also { it.supportProvider = RuffLspIntegrationProvider() }
    // The listener of the base class registers the actions without the Ruff version lookup, which needs a Ruff executable.
    val listener = descriptor.PyLspToolDescriptorLspServerListener()
    listener.serverInitialized(initializeResult(ServerInfo("Ruff"), listOf("ruff.applyAutofix", "ruff.applyOrganizeImports")))
    try {
      val action = ActionManager.getInstance().getAction(commandActionId(descriptor, "ruff.applyAutofix"))
      assertNotNull(action)
      assertEquals("Ruff: Fix all auto-fixable problems", action.templateText)
      assertNull(ActionManager.getInstance().getAction(commandActionId(descriptor, "ruff.applyOrganizeImports")))

      val event = TestActionEvent.createTestEvent(action)
      action.update(event)
      assertFalse(event.presentation.isEnabled, "the command needs the document of an editor")
    }
    finally {
      listener.serverStopped(true)
    }
  }

  /** A restart after an upgrade can report another server name, and the shared action must show the new one. */
  @Test
  fun `a restart with another server name renames the command action`() {
    val descriptor = descriptorOf(pyProjectFixture.get())
    descriptor.lspServerListener.serverInitialized(initializeResult(serverInfo = ServerInfo("ty 0.1")))
    descriptor.lspServerListener.serverInitialized(initializeResult(serverInfo = ServerInfo("ty 0.2")))
    try {
      assertEquals("ty 0.2: $COMMAND", commandAction()?.templateText)
    }
    finally {
      // The first stop is the late stop of the old server.
      repeat(2) { descriptor.lspServerListener.serverStopped(true) }
    }
    assertNull(commandAction())
  }

  /** A server that hides a command removes an action of another caller under its ID, see PY-86023. */
  @Test
  fun `the removal of a foreign action keeps the action that a server holds`() {
    val registry = PyLspCommandActions.getInstance()
    val actionManager = ActionManager.getInstance()
    actionManager.registerAction(commandActionId, object : AnAction("foreign") {
      override fun actionPerformed(e: AnActionEvent) {}
    })
    registry.removeForeign(commandActionId)
    assertNull(commandAction(), "no server holds the ID, so the foreign action must go")

    val descriptor = descriptorOf(pyProjectFixture.get())
    descriptor.lspServerListener.serverInitialized(initializeResult())
    try {
      val held = commandAction()
      registry.removeForeign(commandActionId)
      assertSame(held, commandAction(), "a server holds the action, so it must stay")
    }
    finally {
      descriptor.lspServerListener.serverStopped(true)
    }
  }

  private fun initializeResult(serverInfo: ServerInfo? = ServerInfo("ty"), commands: List<String> = listOf(COMMAND)): InitializeResult =
    InitializeResult(ServerCapabilities().apply { executeCommandProvider = ExecuteCommandOptions(commands) }, serverInfo)

  /** Opens a second project with one Python project, runs [block] with it, and closes the project. */
  private suspend fun withOtherProject(block: suspend (PyProject) -> Unit) {
    val projectManager = ProjectManagerEx.getInstanceEx()
    val project = projectManager.newProjectAsync(closedProjectPath.get(), createTestOpenProjectOptions())
    try {
      block(project.pyProjectAt(closedProjectPath.get().resolve("other")))
    }
    finally {
      projectManager.forceCloseProjectAsync(project)
    }
  }

  private suspend fun updated(action: AnAction, project: Project) =
    TestActionEvent.createTestEvent(action, SimpleDataContext.getProjectContext(project)).also { event ->
      readAction { action.update(event) }
    }

  /** A client of a server that advertises [COMMAND]. It completes [sent] with itself on the first request. */
  private fun commandClient(
    descriptor: LspClientDescriptor,
    serverState: LspServerState = LspServerState.Running,
    sent: CompletableDeferred<LspClient>? = null,
  ): LspClient =
    object : LspClient by fakePyLspClient(descriptor, serverState) {
      override val initializeResult: InitializeResult = initializeResult()

      override suspend fun <R> sendRequest(lsp4jSender: (Lsp4jServer) -> CompletableFuture<R>): R? {
        sent?.complete(this)
        return null
      }
    }

  private fun descriptorOf(pyProject: PyProject): TyLspClientDescriptor =
    TyLspClientDescriptor(pyProject).also { it.supportProvider = TyLspIntegrationProvider() }

  private val presentableName by lazy { TyLspClientDescriptor(pyProjectFixture.get()).presentableName }

  private fun commandActionId(command: String): String = "LSP.Command.$presentableName.$command"

  private fun commandActionId(descriptor: PyLspToolDescriptor, command: String): String =
    "LSP.Command.${descriptor.presentableName}.$command"

  private val commandActionId by lazy { commandActionId(COMMAND) }

  private fun commandAction(): AnAction? = ActionManager.getInstance().getAction(commandActionId)
}
