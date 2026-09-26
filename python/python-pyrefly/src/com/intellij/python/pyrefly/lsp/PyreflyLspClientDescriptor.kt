package com.intellij.python.pyrefly.lsp

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.execution.process.BaseProcessHandler
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.lsp.api.Lsp4jServer
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.platform.lsp.api.customization.LspFoldingRangeCustomizer
import com.intellij.platform.lsp.api.customization.LspFoldingRangeDisabled
import com.intellij.python.lsp.core.PyLspToolCustomization
import com.intellij.python.lsp.core.PyLspToolDescriptor
import com.intellij.python.lsp.core.typeEngine.PyTypeEngineUtils
import com.intellij.python.lsp.core.utils.PyLspServerModificationTracker
import com.intellij.python.pyrefly.PyreflyConfiguration
import com.intellij.python.pyrefly.PyreflyPyTool
import com.intellij.python.pyrefly.PyreflyUsageCollector
import com.intellij.python.lsp.core.PyLspToolSettings
import com.jetbrains.python.codeInsight.typing.PyTypeShed
import com.jetbrains.python.sdk.pythonSdk
import org.eclipse.lsp4j.ConfigurationItem
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.InitializeResult

@Suppress("UsagesOfObsoleteApi")
class PyreflyLspClientDescriptor(
  module: Module,
  servedModules: List<Module> = listOf(module),
) : PyLspToolDescriptor(module, PyreflyPyTool.getInstance(), servedModules) {
  override val toolConfig: PyLspToolSettings
    get() = project.service<PyreflyConfiguration>()

  override val lsp4jServerClass: Class<out Lsp4jServer>
    get() = PyreflyLsp4jServer::class.java

  override val lspCustomization: PyLspToolCustomization = object : PyLspToolCustomization(toolConfig, pyTool, project) {
    override val foldingRangeCustomizer: LspFoldingRangeCustomizer = LspFoldingRangeDisabled

    override val diagnosticsSupport: PyLspToolDiagnosticsSupport = object : PyLspToolDiagnosticsSupport() {
      override fun createAnnotation(
        holder: AnnotationHolder,
        diagnostic: Diagnostic,
        textRange: TextRange,
        quickFixes: List<IntentionAction>,
      ) {
        if (isSuppressedPyreflyDiagnostic(diagnostic)) return
        val customizedQuickFixes = customizePyreflyQuickFixes(holder, diagnostic, textRange, quickFixes)
        super.createAnnotation(holder, diagnostic, textRange, customizedQuickFixes)
      }
    }
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

  override val usesSourceRoots: Boolean = true

  override val usesExcludedRoots: Boolean = true

  override fun createInitializationOptions(): Map<String, Any>? {
    val homePath = module.pythonSdk?.homePath ?: return null
    // The platform builds these options on its connect pool thread, which holds no lock, so the
    // excludes are read here. The `workspace/configuration` replies only reuse them, because they
    // run on the LSP listener thread, see [projectExcludes].
    runReadActionBlocking {
      refreshProjectExcludes()
      refreshModuleRoots()
    }
    return buildMap {
      put("pythonPath", homePath)
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
    // the default value for "typeCheckingMode" is "auto", which will often disable all error messages
    // to keep behaviour close to pycharm, we set it to "default" instead of the default
    put("typeCheckingMode", "default")
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

  private fun buildPyreflyAnalysisSettings(): Map<String, Any> = mapOf("completeFunctionParens" to true)

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
    val scopeModule = servedModuleForScope(item) ?: module.takeUnless { it.isDisposed }
    scopeModule?.pythonSdk?.homePath?.let { put("pythonPath", it) }
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
    PyLspServerModificationTracker.getInstance(project).incModificationCount()
  }

  override fun startServerProcess(): BaseProcessHandler<*> {
    // Pyrefly always runs against the module interpreter (discovery mode is no longer selectable).
    val pythonSdk = module.pythonSdk ?: error("Cannot find PythonSdk for module " + module.name)
    // Per-module check (not the single-module engine check): the Pyrefly tool runs against any
    // local, non-read-only interpreter, including in multi-module projects (PY-89705).
    if (!PyTypeEngineUtils.isLocalNonReadOnlySdk(module)) {
      error("Pyrefly is available only for local, non read-only interpreters. Current:$pythonSdk")
    }

    return super.startServerProcess()
  }
}
