// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.tests.interpreters.lspTools

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.LspIntegrationProvider
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.testFramework.awaitDiagnosticsFromLspServer
import com.intellij.platform.lsp.testFramework.awaitFileOpenedByLspServer
import com.intellij.platform.lsp.util.messageIfStringOrEmpty
import com.intellij.python.pytools.backend.PyTool
import com.intellij.python.pytools.backend.PyToolsState
import com.intellij.python.test.env.junit5.LspToolVersions
import com.intellij.python.test.env.junit5.installToolPackage
import com.intellij.testFramework.common.DEFAULT_TEST_TIMEOUT
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.junit.jupiter.api.Assertions.assertTrue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Shared helpers for the end-to-end LSP-tool env tests (Ruff, ty, basedpyright).
 *
 * These tests deliberately use the real tool executables installed into the test venv, so the
 * whole [com.intellij.python.lsp.core.PyLspToolIntegrationProvider] pipeline is exercised: executable
 * discovery, server start-up, formatting / import optimization and `textDocument/publishDiagnostics`.
 */

/**
 * The time limit for one call of a platform LSP await function.
 * It must be shorter than [DEFAULT_TEST_TIMEOUT], because the platform function fails when its own limit expires.
 */
private val LSP_EVENT_WAIT_LIMIT = 5.seconds

/**
 * Wait until the LSP client of [providerClass] has at least one diagnostic of [DiagnosticSeverity.Error]
 * severity for [file], then return all such error diagnostics.
 *
 * The diagnostics come straight from the LSP client cache, not from the IDE daemon.
 * Thus a daemon restart, for example after `workspace/inlayHint/refresh`, has no effect on the check.
 * The cache can be empty or incomplete, because a server can send diagnostics several times for one file.
 * So the function checks the cache again after each [awaitDiagnosticsFromLspServer] event.
 * A notification can arrive between the cache check and the subscription.
 * In that case, [LSP_EVENT_WAIT_LIMIT] makes the function check the cache again.
 *
 * [awaitDiagnosticsFromLspServer] fails when any LSP server of the project shuts down.
 * A restart after a package install is normal, so the function continues to wait for the new client.
 * It fails only when the client of [providerClass] shuts down unexpectedly.
 */
internal suspend fun awaitLspErrorDiagnostics(
  project: Project,
  file: VirtualFile,
  providerClass: Class<out LspIntegrationProvider>,
): List<Diagnostic> =
  withTimeout(2.minutes) {
    var errors = readLspErrorDiagnostics(project, file, providerClass)
    while (errors.isEmpty()) {
      try {
        withTimeoutOrNull(LSP_EVENT_WAIT_LIMIT) { awaitDiagnosticsFromLspServer(project, file) }
      }
      catch (e: AssertionError) {
        assertNoUnexpectedShutdown(project, providerClass, e)
        delay(LSP_EVENT_WAIT_LIMIT)
      }
      errors = readLspErrorDiagnostics(project, file, providerClass)
    }
    errors
  }

private fun assertNoUnexpectedShutdown(project: Project, providerClass: Class<out LspIntegrationProvider>, cause: AssertionError) {
  val crashed = LspClientManager.getInstance(project).getClients(providerClass).any { it.state == LspServerState.ShutdownUnexpectedly }
  if (crashed) {
    throw AssertionError("The LSP server of ${providerClass.simpleName} shut down unexpectedly", cause)
  }
}

private suspend fun readLspErrorDiagnostics(
  project: Project,
  file: VirtualFile,
  providerClass: Class<out LspIntegrationProvider>,
): List<Diagnostic> {
  val client = LspClientManager.getInstance(project).getClients(providerClass).firstOrNull() as? LspClientImpl ?: return emptyList()
  return readAction { client.getDiagnosticsAndQuickFixes(file) }
    .map { it.diagnostic }
    .filter { it.severity == DiagnosticSeverity.Error }
}

/**
 * Assert that [errors] contains a diagnostic that is actually about the `int = "..."` type mismatch
 * from the shared `typecheck.py` snippet — not merely that some error was reported. Matches on the
 * stable parts of both checkers' wording rather than an exact string:
 *  - ty:           ``Object of type `Literal["not an int"]` is not assignable to `int` ``
 *  - basedpyright: `Type "Literal['not an int']" is not assignable to declared type "int"`
 */
internal fun assertReportsIntAssignmentError(errors: List<Diagnostic>, tool: String) {
  assertTrue(errors.any { error ->
    val message = error.messageIfStringOrEmpty
    message.contains("assignable", ignoreCase = true) && message.contains("int")
  }) {
    "Expected $tool to report an int-assignability error for `x: int = \"not an int\"`, got: $errors"
  }
}

/**
 * Wait until an LSP server opens [file], for at most 90 seconds.
 *
 * [awaitFileOpenedByLspServer] fails after [DEFAULT_TEST_TIMEOUT]. A cold tool start on CI can take longer.
 * So this function calls it again until the 90 seconds expire.
 * A repeated call is safe, because each call also sees the files that a server opened before the call.
 */
internal suspend fun awaitFileOpenedByLspTool(project: Project, file: VirtualFile): Unit =
  withTimeout(90.seconds) {
    do {
      val opened = withTimeoutOrNull(LSP_EVENT_WAIT_LIMIT) { awaitFileOpenedByLspServer(project, file) } != null
    }
    while (!opened)
  }

/**
 * Stop the LSP server(s) of [providerClass] and wait until they are gone, so the external tool
 * process (and its `ProcessWaitFor` thread) terminates before the test framework's thread-leak
 * check runs at tear-down.
 */
internal suspend fun stopLspClientsAndWait(project: Project, providerClass: Class<out LspIntegrationProvider>) {
  val manager = LspClientManager.getInstance(project)
  manager.stopClients(providerClass)
  withTimeout(60.seconds) {
    while (manager.getClients(providerClass).isNotEmpty()) {
      delay(50.milliseconds)
    }
  }
}

/**
 * Enable [pyTool] for [project] and install its pinned version (see [LspToolVersions]) into this
 * module's venv exactly once per test class (guarded by [toolInstalled]). Tool-specific settings are
 * applied via [configure], which the caller owns because the configuration services share no common
 * writable surface.
 */
internal suspend fun Module.enableLspToolAndInstall(
  project: Project,
  pyTool: PyTool,
  toolInstalled: AtomicBoolean,
  configure: () -> Unit,
) {
  PyToolsState.getInstance(project).setEnabled(pyTool, true)
  configure()
  if (toolInstalled.compareAndSet(false, true)) {
    installToolPackage(LspToolVersions.requirement(pyTool))
  }
}

/**
 * Stop the LSP server(s) of [providerClass] and close all editors, so each test starts from a clean
 * editor state and no external tool process survives into the thread-leak check. Use from `@AfterEach`.
 */
internal suspend fun tearDownLspTool(project: Project, providerClass: Class<out LspIntegrationProvider>) {
  stopLspClientsAndWait(project, providerClass)
  edtWriteAction {
    FileEditorManagerEx.getInstanceEx(project).closeAllFiles()
    EditorHistoryManager.getInstance(project).removeAllFiles()
  }
}
