// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.tests.interpreters.lspTools

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.LspClientManagerListener
import com.intellij.platform.lsp.api.LspIntegrationProvider
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.python.junit5Tests.framework.env.pySdkFixture
import com.intellij.python.pytools.PyTool
import com.intellij.python.pytools.PyToolsState
import com.intellij.python.test.env.junit5.LspToolVersions
import com.intellij.python.test.env.junit5.pyVenvFixture
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.jetbrains.python.isSuccess
import com.jetbrains.python.packaging.PyRequirementParser
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.jetbrains.python.packaging.management.PythonPackageInstallRequest
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.repository.PyPiPackageRepository
import com.jetbrains.python.sdk.pythonSdk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.junit.jupiter.api.Assertions.assertTrue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
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
 * Install [requirement] (a PEP 508 requirement string, e.g. `ruff==0.15.18`) into the module's venv
 * via the real package manager. Retried with a short backoff to tolerate flaky network access on CI.
 */
internal suspend fun Module.installToolPackage(requirement: String) {
  val packageManager = PythonPackageManager.forSdk(project, pythonSdk!!)
  val spec = PythonRepositoryPackageSpecification(PyPiPackageRepository, PyRequirementParser.fromLine(requirement)!!)
  val installRequest = PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications(listOf(spec))

  var lastError: Throwable? = null
  repeat(3) { attempt ->
    try {
      val result = packageManager.installPackage(installRequest)
      assertTrue(result.isSuccess) { "Failed to install '$requirement': ${result.errorOrNull}" }
      CodeInsightTestFixtureImpl.ensureIndexesUpToDate(project)
      return
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Throwable) {
      // Either the install reported failure (AssertionError) or the package manager threw (e.g. a
      // network error); both are worth retrying.
      lastError = e
      if (attempt < 2) delay(2.seconds * (attempt + 1))
    }
  }
  throw lastError!!
}

/**
 * Poll the LSP client of [providerClass] until it has published at least one diagnostic of
 * [DiagnosticSeverity.Error] severity for [file], then return all such error diagnostics.
 *
 * The diagnostics are read straight from the LSP client's `publishDiagnostics` cache rather than
 * through the IDE daemon, so the assertion is deterministic and unaffected by asynchronous daemon
 * restarts that some servers trigger (e.g. via `workspace/inlayHint/refresh`). Polling (instead of
 * listening for events) also avoids races with the server opening the file before we subscribe.
 */
internal suspend fun awaitLspErrorDiagnostics(
  project: Project,
  file: VirtualFile,
  providerClass: Class<out LspIntegrationProvider>,
): List<Diagnostic> =
  withTimeout(2.minutes) {
    while (true) {
      val client = LspClientManager.getInstance(project).getClients(providerClass).firstOrNull()
      if (client is LspClientImpl) {
        val errors = readAction { client.getDiagnosticsAndQuickFixes(file) }
          .map { it.diagnostic }
          .filter { it.severity == DiagnosticSeverity.Error }
        if (errors.isNotEmpty()) return@withTimeout errors
      }
      delay(200.milliseconds)
    }
    @Suppress("UNREACHABLE_CODE")
    emptyList()
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
    val message = error.message.orEmpty()
    message.contains("assignable", ignoreCase = true) && message.contains("int")
  }) {
    "Expected $tool to report an int-assignability error for `x: int = \"not an int\"`, got: $errors"
  }
}

/**
 * Suspend until the LSP server reports that it has opened [targetFile].
 *
 * Reimplemented here (rather than reusing `com.intellij.platform.lsp.testFramework`, which this
 * module does not depend on) on top of the [LspClientManager] listener API.
 */
internal suspend fun awaitFileOpenedByLspServer(
  project: Project,
  targetFile: VirtualFile,
  testRootDisposable: Disposable,
): Unit =
  withTimeout(90.seconds) {
    suspendCancellableCoroutine { cont ->
      LspClientManager.getInstance(project).addListener(
        object : LspClientManagerListener {
          override fun serverStateChanged(lspClient: LspClient) {
            if (lspClient.state in arrayOf(LspServerState.ShutdownNormally, LspServerState.ShutdownUnexpectedly) && cont.isActive) {
              cont.resumeWith(Result.failure(AssertionError("LSP server initialization failed")))
            }
          }

          override fun fileOpened(lspClient: LspClient, file: VirtualFile) {
            if (file == targetFile && cont.isActive) {
              cont.resume(Unit)
            }
          }
        },
        testRootDisposable,
        sendEventsForExistingClients = true,
      )
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
 * Per-test-class fixtures shared by the LSP-tool env tests: a project with a Python module backed by a
 * real venv, plus a [com.intellij.testFramework.fixtures.CodeInsightTestFixture]. Bundling them here
 * keeps each test's `companion object` down to the delegated accessors it actually uses.
 *
 * Store it in a `companion object` (static) field so the project / module / venv are created once per
 * class and reused across the class's test methods, matching [toolInstalled]'s install-once semantics.
 */
internal class PyLspToolEnvFixtures {
  /** Guards the one-time tool install per test class; see [enableLspToolAndInstall]. */
  val toolInstalled: AtomicBoolean = AtomicBoolean(false)

  private val tempPathFixture = tempPathFixture()
  val projectFixture = projectFixture(openAfterCreation = true)
  val moduleFixture = projectFixture.moduleFixture(tempPathFixture, addPathToSourceRoot = true)
  val venvFixture = pySdkFixture().pyVenvFixture(
    where = tempPathFixture,
    addToSdkTable = true,
    moduleFixture = moduleFixture,
  )
  val codeInsightFixture = codeInsightFixture(projectFixture, tempPathFixture)
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
