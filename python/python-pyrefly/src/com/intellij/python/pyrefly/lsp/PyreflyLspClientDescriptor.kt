package com.intellij.python.pyrefly.lsp

import com.intellij.execution.process.BaseProcessHandler
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.lsp.api.Lsp4jServer
import com.intellij.platform.lsp.api.customization.LspFoldingRangeCustomizer
import com.intellij.platform.lsp.api.customization.LspFoldingRangeDisabled
import com.intellij.python.lsp.core.PyLspToolCustomization
import com.intellij.python.lsp.core.PyLspToolDescriptor
import com.intellij.python.lsp.core.typeEngine.PyTypeEngineUtils
import com.intellij.python.lsp.core.utils.PyLspServerModificationTracker
import com.intellij.python.pyrefly.PyreflyConfiguration
import com.intellij.python.pyrefly.PyreflyPyTool
import com.intellij.python.pyrefly.PyreflyUsageCollector
import com.intellij.python.pytools.configuration.ExecutableDiscoveryMode
import com.intellij.python.pytools.getState
import com.intellij.python.pytools.lsp.PyLspToolSettings
import com.jetbrains.python.codeInsight.typing.PyTypeShed
import com.jetbrains.python.sdk.pythonSdk
import org.eclipse.lsp4j.ConfigurationItem
import org.eclipse.lsp4j.InitializeResult

@Suppress("UsagesOfObsoleteApi")
class PyreflyLspClientDescriptor(module: Module) : PyLspToolDescriptor(module, PyreflyPyTool.getInstance()) {
  override val toolConfig: PyLspToolSettings
    get() = project.service<PyreflyConfiguration>()

  override val lsp4jServerClass: Class<out Lsp4jServer>
    get() = PyreflyLsp4jServer::class.java

  override val lspCustomization: PyLspToolCustomization = object : PyLspToolCustomization(toolConfig, pyTool, project) {
    override val foldingRangeCustomizer: LspFoldingRangeCustomizer = LspFoldingRangeDisabled
  }

  override val lspServerListener: PyLspToolDescriptorLspServerListener = object : PyLspToolDescriptorLspServerListener() {
    private var initialized = false

    override fun serverInitialized(params: InitializeResult) {
      super.serverInitialized(params)
      initialized = true
      PyreflyUsageCollector.logServerStartup(success = true, sdk = module.pythonSdk)
    }

    override fun serverStopped(shutdownNormally: Boolean) {
      super.serverStopped(shutdownNormally)
      if (!shutdownNormally && !initialized) {
        PyreflyUsageCollector.logServerStartup(success = false, sdk = module.pythonSdk)
      }
      initialized = false
      PyLspServerModificationTracker.getInstance(project).incModificationCount()
    }
  }

  override fun lspArguments(): List<String> =
    listOf(if (Registry.`is`("pyrefly.type.engine.tsp")) "tsp" else "lsp")

  override fun createInitializationOptions(): Map<String, Any>? {
    val homePath = module.pythonSdk?.homePath ?: return null
    return buildMap {
      put("pythonPath", homePath)
      put("pyrefly", buildPyreflyClientSettings())
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
    put("displayTypeErrors", "force-on")
    // Point Pyrefly at PyCharm's bundled typeshed so stdlib (and any third-party
    // packages typeshed knows about) is resolved from a directory PyCharm already
    // indexes. Without this, Pyrefly responds with URIs inside its own
    // materialized `<tmp>/pyrefly_bundled_typeshed_*` dir, and `PyClass.qualifiedName`
    // for those symbols ends up empty because PyCharm has no source/library root
    // for that path.
    PyTypeShed.directory
      ?.let { vf -> vf.fileSystem.getNioPath(vf)?.toString() }
      ?.let { put("typeshedPath", it) }
    // Stop Pyrefly from falling back to its bundled third-party stubs (pandas-stubs,
    // boto3-stubs, ...) when a package isn't available in the user's site-packages
    // and isn't in PyCharm's typeshed either. Those bundled stubs land under
    // `<tmp>/pyrefly_bundled_third_party_*` which the IDE doesn't index, so PSI
    // can't compute a qualifiedName for symbols resolved through them. With this
    // flag set, Pyrefly instead reports `MissingStubs` / `NotFound`, and the IDE
    // surfaces a regular diagnostic the user can act on (install <pkg>-stubs).
    put("disableBundledThirdPartyStubs", true)
  }

  override fun getWorkspaceConfiguration(item: ConfigurationItem): Map<String, Map<String, Any>> =
    mapOf("pyrefly" to buildPyreflyClientSettings())

  override fun startServerProcess(): BaseProcessHandler<*> {
    when (PyreflyPyTool.getInstance().getState(project).discoveryMode) {
      ExecutableDiscoveryMode.INTERPRETER -> {
        val pythonSdk = module.pythonSdk ?: error("Cannot find PythonSdk for module " + module.name)
        // Per-module check (not the single-module engine check): the Pyrefly tool runs against any
        // local, non-read-only interpreter, including in multi-module projects (PY-89705).
        if (!PyTypeEngineUtils.isLocalNonReadOnlySdk(module)) {
          error("Pyrefly is available only for local, non read-only interpreters. Current:$pythonSdk")
        }
      }
      ExecutableDiscoveryMode.PATH -> error("Path for pyrefly executable is not setup")
      ExecutableDiscoveryMode.UVX -> error("UVX mode is not yet supported for Pyrefly")
    }

    return super.startServerProcess()
  }
}