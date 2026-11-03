// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.lsp.core

import com.intellij.idea.TestFor
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.python.junit5Tests.framework.pyProjectFixture
import com.intellij.python.ty.TyLspClientDescriptor
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.createTestOpenProjectOptions
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.jetbrains.python.project.PyProject.Companion.asPyProject
import com.jetbrains.python.sdk.internal.PYTHON_MODULE_ID
import org.eclipse.lsp4j.ExecuteCommandOptions
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.ServerInfo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private const val COMMAND = "py89555.printDebugInformation"

/**
 * A server that advertises `executeCommandProvider` gets one application-wide action for each command, and each
 * action keeps the descriptor and its project. The actions must go when the server stops, and also when the project
 * closes first. The platform stops a server of a closing project on a pooled thread, so the stop can come after the
 * project is gone.
 */
@TestApplication
@TestFor(issues = ["PY-89555"])
internal class PyLspToolCommandActionsTest {
  private val projectFixture = projectFixture(openAfterCreation = true)
  private val pyProjectFixture = projectFixture.pyProjectFixture()
  private val closedProjectPath = tempPathFixture()

  /** A failed assertion must not leave an application-wide action that keeps the project of the next test. */
  @AfterEach
  fun unregisterCommandAction() {
    ActionManager.getInstance().unregisterAction(commandActionId)
  }

  @Test
  fun `the stop of the server unregisters its command actions`() {
    val descriptor = TyLspClientDescriptor(pyProjectFixture.get())
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
      TyLspClientDescriptor(pyProject).also {
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
  fun `the stop of a server keeps the command action that another server took over`() {
    val first = TyLspClientDescriptor(pyProjectFixture.get())
    val second = TyLspClientDescriptor(pyProjectFixture.get())
    first.lspServerListener.serverInitialized(initializeResult())
    second.lspServerListener.serverInitialized(initializeResult())
    val secondAction = commandAction()
    assertNotNull(secondAction)

    first.lspServerListener.serverStopped(true)
    assertSame(secondAction, commandAction(), "the first server must not unregister the action of the second one")

    second.lspServerListener.serverStopped(true)
    assertNull(commandAction())
  }

  /** A restart reuses the descriptor, and the old server can report its stop after the new one initialized. */
  @Test
  fun `the late stop of the old server keeps the command actions of the restarted one`() {
    val descriptor = TyLspClientDescriptor(pyProjectFixture.get())
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
    val descriptor = TyLspClientDescriptor(pyProjectFixture.get())
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
        val descriptors = List(2) { TyLspClientDescriptor(pyProjectFixture.get()) }
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
  fun `a server without server info names the command action after the tool`() {
    val descriptor = TyLspClientDescriptor(pyProjectFixture.get())
    descriptor.lspServerListener.serverInitialized(initializeResult(serverInfo = null))
    try {
      assertEquals("${descriptor.presentableName}: $COMMAND", commandAction()?.templateText)
    }
    finally {
      descriptor.lspServerListener.serverStopped(true)
    }
  }

  private fun initializeResult(serverInfo: ServerInfo? = ServerInfo("ty"), commands: List<String> = listOf(COMMAND)): InitializeResult =
    InitializeResult(ServerCapabilities().apply { executeCommandProvider = ExecuteCommandOptions(commands) }, serverInfo)

  private val presentableName by lazy { TyLspClientDescriptor(pyProjectFixture.get()).presentableName }

  private fun commandActionId(command: String): String = "LSP.Command.$presentableName.$command"

  private val commandActionId by lazy { commandActionId(COMMAND) }

  private fun commandAction(): AnAction? = ActionManager.getInstance().getAction(commandActionId)
}
