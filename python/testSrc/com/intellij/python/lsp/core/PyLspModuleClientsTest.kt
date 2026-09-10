// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.lsp.core

import com.intellij.idea.TestFor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspClientDescriptor
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.python.junit5Tests.framework.pyModuleFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The platform gives an LSP server an identity that holds the root paths, not the module, so a
 * caller has to pick the client that answers for its own module itself. [clientForModule] is that
 * pick. One server can answer for several modules, so the pick reads the whole served set.
 */
@TestApplication
@TestFor(issues = ["PY-92008"])
internal class PyLspModuleClientsTest {
  private val projectFixture = projectFixture(openAfterCreation = true)
  private val mainModule = projectFixture.pyModuleFixture("main")
  private val secondModule = projectFixture.pyModuleFixture("second")

  @Test
  fun `each module gets the client that answers for it`() {
    val main = mainModule.get()
    val second = secondModule.get()
    val mainClient = fakePyToolClient(main)
    val secondClient = fakePyToolClient(second)
    val clients = listOf(mainClient, secondClient)

    assertSame(mainClient, clients.clientForModule(main))
    assertSame(secondClient, clients.clientForModule(second))
  }

  @Test
  fun `one client answers for every module it serves`() {
    val main = mainModule.get()
    val second = secondModule.get()
    val client = fakePyToolClient(main, second)

    assertTrue(main in client.pyServedModules)
    assertTrue(second in client.pyServedModules)
    assertSame(client, listOf(client).clientForModule(main))
    assertSame(client, listOf(client).clientForModule(second))
  }

  @Test
  fun `the client of another module is not taken as a fallback`() {
    val main = mainModule.get()
    val second = secondModule.get()

    assertNull(listOf(fakePyToolClient(main)).clientForModule(second))
  }

  @Test
  fun `no client at all answers for no module`() {
    assertNull(emptyList<LspClient>().clientForModule(mainModule.get()))
  }

  @Test
  fun `a client of another integration serves no python module`() {
    val main = mainModule.get()
    val foreign = fakePyLspClient(ForeignDescriptor(main.project))

    assertTrue(foreign.pyServedModules.isEmpty())
    assertNull(listOf(foreign).clientForModule(main))
  }

  /**
   * A server that shut down stays in the client list, so the status bar can show it. It answers
   * nothing, so a module must not get it.
   */
  @Test
  fun `a shut-down client answers for no module`() {
    val main = mainModule.get()

    assertNull(listOf(fakePyToolClient(main, serverState = LspServerState.ShutdownNormally)).clientForModule(main))
  }

  /** The platform answers `null` to every request sent before the server runs, so the client answers nothing yet. */
  @Test
  fun `an initializing client answers for no module`() {
    val main = mainModule.get()

    assertNull(listOf(fakePyToolClient(main, serverState = LspServerState.Initializing)).clientForModule(main))
  }

  private class ForeignDescriptor(project: Project) : LspClientDescriptor(project, "foreign") {
    override fun isSupportedFile(file: VirtualFile): Boolean = false
  }
}
