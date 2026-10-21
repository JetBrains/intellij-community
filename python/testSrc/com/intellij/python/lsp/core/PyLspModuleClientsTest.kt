// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.lsp.core

import kotlin.io.path.Path
import com.intellij.testFramework.junit5.fixture.pathInProjectFixture
import com.intellij.idea.TestFor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspClientDescriptor
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.python.junit5Tests.framework.pyProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The platform gives an LSP server an identity that holds the root paths, not the module, so a
 * caller has to pick the client that answers for its own module itself. [clientFor] is that
 * pick. One server can answer for several modules, so the pick reads the whole served set.
 */
@TestApplication
@TestFor(issues = ["PY-92008"])
internal class PyLspModuleClientsTest {
  private val projectFixture = projectFixture(openAfterCreation = true)
  private val mainPyProject = projectFixture.pyProjectFixture()
  private val secondPyProject = projectFixture.pyProjectFixture(projectFixture.pathInProjectFixture(Path("second")))

  @Test
  fun `each module gets the client that answers for it`() {
    val main = mainPyProject.get()
    val second = secondPyProject.get()
    val mainClient = fakePyToolClient(main)
    val secondClient = fakePyToolClient(second)
    val clients = listOf(mainClient, secondClient)

    assertSame(mainClient, clients.clientFor(main))
    assertSame(secondClient, clients.clientFor(second))
  }

  @Test
  fun `one client answers for every module it serves`() {
    val main = mainPyProject.get()
    val second = secondPyProject.get()
    val client = fakePyToolClient(main, second)

    assertTrue(main in client.pyServedProjects)
    assertTrue(second in client.pyServedProjects)
    assertSame(client, listOf(client).clientFor(main))
    assertSame(client, listOf(client).clientFor(second))
  }

  @Test
  fun `the client of another module is not taken as a fallback`() {
    val main = mainPyProject.get()
    val second = secondPyProject.get()

    assertNull(listOf(fakePyToolClient(main)).clientFor(second))
  }

  @Test
  fun `no client at all answers for no module`() {
    assertNull(emptyList<LspClient>().clientFor(mainPyProject.get()))
  }

  @Test
  fun `a client of another integration serves no python module`() {
    val main = mainPyProject.get()
    val foreign = fakePyLspClient(ForeignDescriptor(main.residesOnModule.project))

    assertTrue(foreign.pyServedProjects.isEmpty())
    assertNull(listOf(foreign).clientFor(main))
  }

  /**
   * A server that shut down stays in the client list, so the status bar can show it. It answers
   * nothing, so a module must not get it.
   */
  @Test
  fun `a shut-down client answers for no module`() {
    val main = mainPyProject.get()

    assertNull(listOf(fakePyToolClient(main, serverState = LspServerState.ShutdownNormally)).clientFor(main))
  }

  /** The platform answers `null` to every request sent before the server runs, so the client answers nothing yet. */
  @Test
  fun `an initializing client answers for no module`() {
    val main = mainPyProject.get()

    assertNull(listOf(fakePyToolClient(main, serverState = LspServerState.Initializing)).clientFor(main))
  }

  private class ForeignDescriptor(project: Project) : LspClientDescriptor(project, "foreign") {
    override fun isSupportedFile(file: VirtualFile): Boolean = false
  }
}
