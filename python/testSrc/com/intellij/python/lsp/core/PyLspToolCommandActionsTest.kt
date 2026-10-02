// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.lsp.core

import com.intellij.idea.TestFor
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.python.junit5Tests.framework.pyModuleFixture
import com.intellij.python.ty.TyLspClientDescriptor
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.createTestOpenProjectOptions
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
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
  private val moduleFixture = projectFixture.pyModuleFixture("main")
  private val closedProjectPath = tempPathFixture()

  /** A failed assertion must not leave an application-wide action that keeps the project of the next test. */
  @AfterEach
  fun unregisterCommandAction() {
    ActionManager.getInstance().unregisterAction(commandActionId)
  }

  @Test
  fun `the stop of the server unregisters its command actions`() {
    val descriptor = TyLspClientDescriptor(moduleFixture.get())
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
        ModuleManager.getInstance(project).newModule(closedProjectPath.get().resolve("main.iml"), PYTHON_MODULE_ID)
      }
      TyLspClientDescriptor(module).also {
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
    val first = TyLspClientDescriptor(moduleFixture.get())
    val second = TyLspClientDescriptor(moduleFixture.get())
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
    val descriptor = TyLspClientDescriptor(moduleFixture.get())
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
    val descriptor = TyLspClientDescriptor(moduleFixture.get())
    descriptor.lspServerListener.serverStopped(false)
    descriptor.lspServerListener.serverInitialized(initializeResult())
    assertNotNull(commandAction())

    descriptor.lspServerListener.serverStopped(true)
    assertNull(commandAction())
  }

  @Test
  fun `a server without server info names the command action after the tool`() {
    val descriptor = TyLspClientDescriptor(moduleFixture.get())
    descriptor.lspServerListener.serverInitialized(initializeResult(serverInfo = null))
    try {
      assertEquals("${descriptor.presentableName}: $COMMAND", commandAction()?.templateText)
    }
    finally {
      descriptor.lspServerListener.serverStopped(true)
    }
  }

  private fun initializeResult(serverInfo: ServerInfo? = ServerInfo("ty")): InitializeResult =
    InitializeResult(ServerCapabilities().apply { executeCommandProvider = ExecuteCommandOptions(listOf(COMMAND)) }, serverInfo)

  private val commandActionId by lazy { "LSP.Command.${TyLspClientDescriptor(moduleFixture.get()).presentableName}.$COMMAND" }

  private fun commandAction(): AnAction? = ActionManager.getInstance().getAction(commandActionId)
}
