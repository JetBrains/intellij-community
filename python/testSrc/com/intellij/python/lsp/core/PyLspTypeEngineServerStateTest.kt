// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.lsp.core

import com.intellij.idea.TestFor
import com.intellij.platform.lsp.api.LspServerListener
import com.intellij.python.junit5Tests.framework.pyModuleFixture
import com.intellij.python.lsp.core.typeEngine.PyTypeEngineProjectSettings
import com.intellij.python.lsp.core.typeEngine.PyTypeEngineSettingsState
import com.intellij.python.lsp.core.typeEngine.PyTypeEngineType
import com.intellij.python.ty.TyLspClientDescriptor
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.jetbrains.python.psi.types.PyTypeEngineSettingsModificationTracker
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.ServerCapabilities
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A type context picks its engine when it is created, and the context cache keeps it until the PSI
 * changes. A context created while the engine's server did not run has no engine. The start and the
 * stop of that server must drop the cached contexts, or the file keeps PyCharm's own inference until
 * the next edit.
 */
@TestApplication
@TestFor(issues = ["PY-92008"])
internal class PyLspTypeEngineServerStateTest {
  private val projectFixture = projectFixture(openAfterCreation = true)
  private val moduleFixture = projectFixture.pyModuleFixture("main")

  @Test
  fun `the start of the server of the selected engine drops the cached contexts`() {
    val listener = tyListener(selected = PyTypeEngineType.TY)
    assertTrue(dropsCachedContexts { listener.serverInitialized(InitializeResult(ServerCapabilities())) })
  }

  @Test
  fun `the stop of the server of the selected engine drops the cached contexts`() {
    val listener = tyListener(selected = PyTypeEngineType.TY)
    assertTrue(dropsCachedContexts { listener.serverStopped(true) })
  }

  @Test
  fun `the server of a tool that is not the selected engine keeps the cached contexts`() {
    val listener = tyListener(selected = PyTypeEngineType.PYCHARM)
    assertFalse(dropsCachedContexts {
      listener.serverInitialized(InitializeResult(ServerCapabilities()))
      listener.serverStopped(true)
    })
  }

  /** The server listener of a ty client for the module, in a project that selects [selected]. */
  private fun tyListener(selected: PyTypeEngineType): LspServerListener {
    val module = moduleFixture.get()
    // `loadState` sets the engine without the settings-change message, which would start servers.
    PyTypeEngineProjectSettings.getInstance(module.project).loadState(PyTypeEngineSettingsState(selected))
    return TyLspClientDescriptor(module, listOf(module)).lspServerListener
  }

  private fun dropsCachedContexts(action: () -> Unit): Boolean {
    val tracker = PyTypeEngineSettingsModificationTracker.getInstance(moduleFixture.get().project)
    val before = tracker.modificationCount
    action()
    return tracker.modificationCount != before
  }
}
