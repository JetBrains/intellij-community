// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.tests.interpreters.lspTools

import com.intellij.formatting.service.AsyncDocumentFormattingService
import com.intellij.formatting.service.FormattingService
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.LspIntegrationProvider
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.testFramework.awaitDiagnosticsFromLspServer
import com.intellij.platform.lsp.testFramework.awaitFileOpenedByLspServer
import com.intellij.platform.lsp.util.messageIfStringOrEmpty
import com.intellij.psi.PsiManager
import com.intellij.python.lsp.core.pyServedProjects
import com.intellij.python.pytools.backend.PyTool
import com.intellij.python.pytools.backend.PyToolsState
import com.intellij.python.test.env.junit5.LspToolVersions
import com.intellij.python.test.env.junit5.installToolPackage
import com.intellij.testFramework.common.DEFAULT_TEST_TIMEOUT
import com.jetbrains.python.project.PyProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Shared helpers for the end-to-end LSP-tool env tests (Ruff, ty, basedpyright, Zuban).
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
 * Wait until the error diagnostics of the LSP client of [providerClass] for [file] satisfy [until], then
 * return all diagnostics of [DiagnosticSeverity.Error] severity. By default, one error is enough.
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
  until: (List<Diagnostic>) -> Boolean = { it.isNotEmpty() },
): List<Diagnostic> =
  withTimeout(2.minutes) {
    var errors = readLspErrorDiagnostics(project, file, providerClass)
    while (!until(errors)) {
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
 * Waits until the diagnostics for [file] of the client that [findClient] returns satisfy [until], and returns them.
 * Fails with the last diagnostics after 2 minutes.
 *
 * It waits for the events the way [awaitLspErrorDiagnostics] does. Use it when the project runs more than one
 * client of a tool, because [awaitLspErrorDiagnostics] reads only the first one.
 *
 * Each check calls [findClient] again. A server can restart while the test waits, and the client from before the
 * restart publishes nothing more. A client of [providerClass] that shuts down unexpectedly fails the wait at once.
 */
internal suspend fun awaitLspDiagnostics(
  project: Project,
  file: VirtualFile,
  providerClass: Class<out LspIntegrationProvider>,
  findClient: () -> LspClient?,
  until: (List<Diagnostic>) -> Boolean,
): List<Diagnostic> {
  var diagnostics = findClient().diagnosticsOf(file)
  val reached = withTimeoutOrNull(2.minutes) {
    while (!until(diagnostics)) {
      try {
        withTimeoutOrNull(LSP_EVENT_WAIT_LIMIT) { awaitDiagnosticsFromLspServer(project, file) }
      }
      catch (e: AssertionError) {
        // A server of the project shut down. Another client can still publish, or a new one can start.
        assertNoUnexpectedShutdown(project, providerClass, e)
        delay(LSP_EVENT_WAIT_LIMIT)
      }
      diagnostics = findClient().diagnosticsOf(file)
    }
  }
  return if (reached != null) diagnostics else fail("The diagnostics of ${file.path} did not reach the expected state: $diagnostics")
}

/** The modules of the Python projects that the server behind [this] answers for. */
internal val LspClient.pyServedModules: List<Module>
  get() = pyServedProjects.map { it.residesOnModule }

/** The running client of [providerClass] that [condition] accepts, or `null`. */
internal fun runningLspClient(
  project: Project,
  providerClass: Class<out LspIntegrationProvider>,
  condition: (LspClient) -> Boolean = { true },
): LspClient? =
  LspClientManager.getInstance(project).getClients(providerClass).firstOrNull { it.state == LspServerState.Running && condition(it) }

/**
 * Waits until a running client of [providerClass] serves [module] and [condition] holds for it, and returns the client.
 * Fails with [failure] after 90 seconds.
 *
 * A later restart can replace the client, so a caller that waits again later looks the client up again.
 */
internal suspend fun awaitLspClientOf(
  project: Project,
  providerClass: Class<out LspIntegrationProvider>,
  module: Module,
  failure: String,
  condition: (LspClient) -> Boolean = { true },
): LspClient {
  val client = withTimeoutOrNull(90.seconds) {
    var found: LspClient? = null
    while (found == null) {
      found = runningLspClient(project, providerClass) { module in it.pyServedModules && condition(it) }
      if (found == null) delay(200.milliseconds)
    }
    found
  }
  return client ?: fail(failure)
}

/** Whether [client] is the only client of [providerClass] in its project, and serves exactly [modules]. */
internal fun isOnlyLspServerOf(client: LspClient, providerClass: Class<out LspIntegrationProvider>, modules: Set<Module>): Boolean =
  client.pyServedModules.toSet() == modules &&
  LspClientManager.getInstance(client.descriptor.project).getClients(providerClass).size == 1

/** Writes [text] into the file `main.py` of [directory], and checks that the file belongs to [module]. */
internal suspend fun writeModuleFile(project: Project, directory: Path, module: Module, text: String): VirtualFile {
  val path = directory.resolve("main.py")
  withContext(Dispatchers.IO) { path.writeText(text) }
  val file = checkNotNull(VirtualFileManager.getInstance().refreshAndFindFileByNioPath(path)) { "$path is not in the VFS" }
  val owner = readAction { ModuleUtilCore.findModuleForFile(file, project) }
  assertEquals(module, owner, "$path must belong to module '${module.name}'")
  return file
}

private suspend fun LspClient?.diagnosticsOf(file: VirtualFile): List<Diagnostic> {
  val client = this as? LspClientImpl ?: return emptyList()
  return readAction { client.getDiagnosticsAndQuickFixes(file) }.map { it.diagnostic }
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
 *
 * [awaitFileOpenedByLspServer] also fails when any LSP server of the project shuts down. A restart after a package
 * install or after a new interpreter is normal, so this function then waits for the new server. A client of
 * [providerClass] that shuts down unexpectedly fails the wait at once.
 */
internal suspend fun awaitFileOpenedByLspTool(
  project: Project,
  file: VirtualFile,
  providerClass: Class<out LspIntegrationProvider>,
): Unit =
  withTimeout(90.seconds) {
    do {
      val opened = try {
        withTimeoutOrNull(LSP_EVENT_WAIT_LIMIT) { awaitFileOpenedByLspServer(project, file) } != null
      }
      catch (e: AssertionError) {
        assertNoUnexpectedShutdown(project, providerClass, e)
        delay(LSP_EVENT_WAIT_LIMIT)
        false
      }
    }
    while (!opened)
  }

/**
 * Wait until `Reformat Code` on [file] goes to a formatter other than the formatter of the IDE.
 *
 * Ruff 0.16.2 and later register the formatting capability after the server starts, so an open file does not
 * yet mean that the server formats. A reformat before the registration falls back to the IDE formatter.
 * The Ruff executable and the LSP server both format through an [AsyncDocumentFormattingService].
 */
internal suspend fun awaitFormatterOtherThanIde(project: Project, file: VirtualFile): Unit =
  withTimeout(LSP_EVENT_WAIT_LIMIT) {
    while (!readAction {
        val psiFile = PsiManager.getInstance(project).findFile(file) ?: return@readAction false
        FormattingService.EP_NAME.extensionList.any { it is AsyncDocumentFormattingService && it.canFormat(psiFile) }
      }) {
      delay(50.milliseconds)
    }
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
 * Enable [pyTool] for [project] and install its pinned version (see [LspToolVersions]) into the
 * interpreter of this project exactly once per test class (guarded by [toolInstalled]). Tool-specific settings are
 * applied via [configure], which the caller owns because the configuration services share no common
 * writable surface.
 */
internal suspend fun PyProject.enableLspToolAndInstall(
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
