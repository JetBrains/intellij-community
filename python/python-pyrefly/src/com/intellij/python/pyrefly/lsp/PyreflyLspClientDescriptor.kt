// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyrefly.lsp

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.BaseProcessHandler
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.platform.lsp.api.customization.LspFoldingRangeCustomizer
import com.intellij.platform.lsp.api.customization.LspFoldingRangeDisabled
import com.intellij.python.lsp.core.PyLspToolCustomization
import com.intellij.python.lsp.core.PyLspToolDescriptor
import com.intellij.python.lsp.core.PyLspToolSettings
import com.intellij.python.lsp.core.isActiveOn
import com.intellij.python.lsp.core.isServedInterpreter
import com.intellij.python.pyrefly.BundledPyreflyConfiguration
import com.intellij.python.pyrefly.PyreflyBundle
import com.intellij.python.pyrefly.PyreflyConfiguration
import com.intellij.python.pyrefly.PyreflyExecutableProvider
import com.intellij.python.pyrefly.PyreflyPyTool
import com.intellij.python.pyrefly.PyreflyUsageCollector
import com.jetbrains.python.project.PyProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.ConfigurationItem
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.InitializeResult
import java.nio.file.Path
import kotlin.io.path.isExecutable

/**
 * A Pyrefly server for [servedProjects].
 *
 * With [runsBundledPyrefly], the server runs the binary of the plugin, and it reads [BundledPyreflyConfiguration].
 * Otherwise it runs the `pyrefly` of the environment for the External Tools tool, and it reads [PyreflyConfiguration].
 */
class PyreflyLspClientDescriptor(
  pyProject: PyProject,
  servedProjects: List<PyProject> = listOf(pyProject),
  val runsBundledPyrefly: Boolean = false,
) : PyLspToolDescriptor(
  pyProject,
  PyreflyPyTool.getInstance(),
  servedProjects,
  if (runsBundledPyrefly) PyreflyBundle.message("pyrefly.bundled.server.name") else PyreflyPyTool.getInstance().lspServerName,
) {
  override val toolConfig: PyLspToolSettings
    get() = if (runsBundledPyrefly) BundledPyreflyConfiguration.getInstance(project) else project.service<PyreflyConfiguration>()

  override val lspCustomization: PyLspToolCustomization = object : PyLspToolCustomization(toolConfig, pyTool, project, ::isActive) {
    override val foldingRangeCustomizer: LspFoldingRangeCustomizer = LspFoldingRangeDisabled

    override val diagnosticsSupport: PyLspToolDiagnosticsSupport = object : PyLspToolDiagnosticsSupport() {
      override val showSourceAndCode: Boolean
        get() = !runsBundledPyrefly
    }
  }

  override val lspServerListener: PyLspToolDescriptorLspServerListener = object : PyLspToolDescriptorLspServerListener() {
    private var initialized = false

    override fun serverInitialized(params: InitializeResult) {
      super.serverInitialized(params)
      initialized = true
      PyreflyUsageCollector.logServerStartup(success = true, interpreter = startedInterpreterOf(pyProject))
    }

    override fun serverStopped(shutdownNormally: Boolean) {
      super.serverStopped(shutdownNormally)
      // The platform can report the stop after the project is gone.
      if (project.isDisposed) return
      if (!shutdownNormally && !initialized) {
        PyreflyUsageCollector.logServerStartup(success = false, interpreter = startedInterpreterOf(pyProject))
      }
      initialized = false
    }
  }

  /** The `pyrefly` of the environment runs as a plain LSP tool. */
  override fun lspArguments(): List<String> = listOf("lsp")

  override val usesSourceRoots: Boolean = true

  override val usesExcludedRoots: Boolean = true

  /** Whether this server may serve now: the bundled one when it runs, the other one for the External Tools tool. */
  private fun isActive(): Boolean =
    runsBundledPyrefly || pyTool.isActiveOn(project)

  override fun hasExecutable(): Boolean {
    if (!runsBundledPyrefly) {
      return super.hasExecutable()
    }
    return PyreflyExecutableProvider.executableExists()
  }

  override suspend fun resolveCommandLine(): GeneralCommandLine {
    if (!runsBundledPyrefly) {
      return super.resolveCommandLine()
    }

    val executable = getPyreflyPath()
    if (!withContext(Dispatchers.IO) { executable.isExecutable() }) {
      throw ExecutionException(PyreflyBundle.message("pyrefly.executable.unavailable", executable))
    }
    return GeneralCommandLine(executable.toString())
      .withWorkingDirectory(pyProject.baseDir)
      .withParameters(lspArguments())
  }

  override fun createInitializationOptions(): Map<String, Any>? {
    val pythonPath = startedPythonPathOf(pyProject) ?: return null
    // The platform builds these options on its connect pool thread, which holds no lock, so the
    // excludes are read here. The `workspace/configuration` replies only reuse them, because they
    // run on the LSP listener thread, see [projectExcludes].
    runReadActionBlocking {
      refreshProjectExcludes()
      refreshModuleRoots()
    }
    return buildMap {
      put("pythonPath", pythonPath)
      put("pyrefly", buildPyreflyClientSettings())
      put("analysis", buildPyreflyAnalysisSettings())
    }
  }

  /**
   * Pyrefly's `apply_client_configuration` overwrites every known field on every call,
   * resetting any value missing from the payload to `None`. The IDE replies to both the
   * `initialize.initializationOptions` channel and the `workspace/configuration` request
   * for `"python"`, so any setting we want Pyrefly to keep has to appear in **both**
   * responses — otherwise the second response silently undoes the first.
   *
   * See [`apply_client_configuration`](https://github.com/facebook/pyrefly/blob/main/pyrefly/lib/lsp/non_wasm/workspace.rs)
   * and the upstream `remove_*_clears_and_flags_modified` tests for the exact contract.
   */
  private fun buildPyreflyClientSettings(): Map<String, Any> = buildMap {
    put("typeCheckingMode", typeCheckingMode)
    // Pyrefly keeps `extraPaths` as `search_path_from_args` and consults it before its own heuristics,
    // which is the priority "Sources Root" has in the IDE. Content roots are deliberately left out:
    // Pyrefly already derives them from the workspace folders, and repeating them here would lift
    // them above typeshed in import resolution.
    put("extraPaths", sourceRoots())
    // The directories the user excluded, plus the modules nested in a folder of this server that it
    // does not serve. Pyrefly would otherwise analyse such a module with the interpreter of the outer
    // folder. Pyrefly reads this key from 1.3.0-dev.1, and an older one ignores it. See
    // [excludedRoots] and [projectExcludes].
    put("extraProjectExcludes", (excludedRoots() + projectExcludes()).distinct())
  }

  private fun buildPyreflyAnalysisSettings(): Map<String, Any> = mapOf(
    "completeFunctionParens" to true,
    "typeCheckingMode" to typeCheckingMode,
  )

  private val typeCheckingMode: String
    get() = if (runsBundledPyrefly) "jet-basic" else "default"

  /**
   * Pyrefly keeps one workspace for each folder, and each workspace holds its own interpreter. It
   * asks for the configuration of one folder at a time, so the reply holds the interpreter of that
   * folder's module and every module gets its own. An item with no scope configures
   * pyrefly's default workspace, which the primary module answers for.
   *
   * The reply carries `pythonPath` in every project, whether the server holds one module or all of
   * them. Pyrefly resets a field the reply leaves out, see [buildPyreflyClientSettings], so a reply
   * without it would drop the interpreter that [createInitializationOptions] sent.
   */
  override fun getWorkspaceConfiguration(item: ConfigurationItem): Map<String, Any> = buildMap {
    val scopeProject = servedProjectForScope(item) ?: pyProject.takeUnless { it.residesOnModule.isDisposed }
    scopeProject?.let(::startedPythonPathOf)?.let { put("pythonPath", it) }
    put("pyrefly", buildPyreflyClientSettings())
    put("analysis", buildPyreflyAnalysisSettings())
  }

  /**
   * Computes the [projectExcludes] and the [sourceRoots] again, and tells pyrefly only when they
   * changed. Pyrefly then asks for `workspace/configuration` again for each folder, and the replies
   * carry the new values. It does not read the payload of the notification.
   */
  override suspend fun projectChangedAround(client: LspClient) {
    // `or`, not `||`: both have to run, whichever one reports a change.
    if (!readAction { refreshProjectExcludes() or refreshModuleRoots() }) return
    if (client.state != LspServerState.Running) return
    client.sendNotification { it.workspaceService.didChangeConfiguration(DidChangeConfigurationParams(emptyMap<String, Any>())) }
    // Everything the server already answered came from the old search path and stays cached until the
    // file changes, so ask for all of it again now that the push is on its way.
    client.invalidateServerResults()
  }

  override fun startServerProcess(): BaseProcessHandler<*> {
    // Pyrefly always runs against the module interpreter (discovery mode is no longer selectable).
    val interpreter = currentInterpreterOf(pyProject) ?: error("Cannot find the Python interpreter of module " + module.name)
    // The Pyrefly tool runs against any native, non-read-only interpreter, including in multi-module projects (PY-89705).
    if (!isServedInterpreter(interpreter)) {
      error("Pyrefly is available only for native, non read-only interpreters. Current: $interpreter")
    }

    val handler = super.startServerProcess()
    try {
      PyreflyServerHeartbeat.getInstance(project).track(handler)
    }
    catch (e: Exception) {
      // The server already runs. A failure here must not leave it without an owner.
      if (e is ControlFlowException) {
        handler.destroyProcess()
        throw e
      }
      LOG.warn("The Pyrefly server runs without the resource heartbeat", e)
    }
    return handler
  }

  /** The Python binary of the interpreter that [pyProject] had when the server started, as pyrefly's `pythonPath`. */
  private fun startedPythonPathOf(pyProject: PyProject): String? = startedInterpreterOf(pyProject)?.pythonBinary?.toString()

  private companion object {
    const val PYREFLY_BINARY_PATH_PROPERTY = "pyrefly.binary.path"

    suspend fun getPyreflyPath(): Path =
      System.getProperty(PYREFLY_BINARY_PATH_PROPERTY)?.takeIf { it.isNotBlank() }?.let(Path::of)
      ?: PyreflyExecutableProvider.getExecutable()
  }
}
