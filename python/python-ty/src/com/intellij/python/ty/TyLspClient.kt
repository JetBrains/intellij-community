package com.intellij.python.ty

import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.Lsp4jServer
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.platform.lsp.api.customization.LspGoToDefinitionDisabled
import com.intellij.python.lsp.core.PyLspTool
import com.intellij.python.lsp.core.PyLspToolCustomization
import com.intellij.python.lsp.core.PyLspToolIntegrationProvider
import com.intellij.python.lsp.core.PyLspToolDescriptor
import com.intellij.python.lsp.core.PyLspToolSettings
import com.intellij.python.lsp.core.pyLspAttachedDescriptor
import com.intellij.python.lsp.core.pyLspModulesToServeWith
import com.intellij.python.lsp.core.utils.PyLspServerModificationTracker
import org.eclipse.lsp4j.ConfigurationItem
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.WorkspaceFoldersChangeEvent

/** The configuration section ty asks for; see `Session::request_uninitialized_workspace_folder_configurations`. */
private const val TY_CONFIGURATION_SECTION = "ty"

class TyLspIntegrationProvider : PyLspToolIntegrationProvider() {
  override fun getDescriptor(module: Module): PyLspToolDescriptor = tyDescriptor(module)

  override fun pyTool(project: Project): PyLspTool<*> = TyPyTool.getInstance()

  override val servesEveryModule: Boolean get() = true
}

/**
 * The descriptor to start ty with for [module].
 *
 * ty keeps its settings for each workspace folder and resolves the environment against that
 * folder's root, so one server answers for every module of a multi-module project. This holds for
 * the type engine and for the External Tools tool: both start the server from this descriptor. A
 * module whose environment pins another ty version gets a server of its own, because one server runs
 * one binary. For a project of one served module the server answers for [module] alone.
 *
 * We send ty no interpreter. It finds the environment of each folder itself, and its
 * `python_extension` option refuses to be merged with the initialization options.
 */
internal fun tyDescriptor(module: Module): TyLspClientDescriptor {
  val served = pyLspModulesToServeWith(module, TyPyTool.getInstance())
  val descriptor = TyLspClientDescriptor(served.first(), served)
  // The type engine builds a descriptor and starts the server itself, so it must wire the provider
  // here. `fileOpened` may never run for the file that started the server.
  pyLspAttachedDescriptor(descriptor, TyLspIntegrationProvider::class.java)
  return descriptor
}

class TyLspClientDescriptor(
  module: Module,
  servedModules: List<Module> = listOf(module),
) : PyLspToolDescriptor(module, TyPyTool.getInstance(), servedModules) {
  override fun lspArguments(): List<String> =
    listOf("server")

  override val usesSourceRoots: Boolean get() = true

  override val usesExcludedRoots: Boolean get() = true

  override fun createInitializationOptions(): Map<String, Any>? {
    // The platform builds these options on its connect pool thread, which holds no lock, so the roots
    // are read here. The `workspace/configuration` replies only reuse them, because they run on the
    // LSP listener thread, see [sourceRoots].
    runReadActionBlocking { refreshModuleRoots() }
    return tyClientOptions()
  }

  override fun getWorkspaceConfiguration(item: ConfigurationItem): Map<String, Any>? =
    if (item.section == TY_CONFIGURATION_SECTION) tyClientOptions() else null

  /**
   * ty's client options, as accepted by both the `initializationOptions` and the `workspace/configuration`
   * channel. These are the two ways ty reads settings. Nothing is sent when the user configured neither
   * kind of root, so a `ty.toml` stays in charge.
   *
   * `environment.root` is "the root paths of the project, used for finding first-party modules". It
   * replaces ty's own layout detection, so the content roots are appended to keep the project root
   * first-party the way ty's default does. `src.exclude`, in contrast, is appended to ty's defaults
   * such as `node_modules` and `.git`. Its paths are absolute on purpose. A relative pattern is
   * resolved against the directory of the config file it came from, which an inline client
   * configuration does not have, and is then ignored without a message.
   */
  private fun tyClientOptions(): Map<String, Any>? {
    val sourceRoots = sourceRoots()
    val excludedRoots = excludedRoots()
    if (sourceRoots.isEmpty() && excludedRoots.isEmpty()) return null
    val configuration = buildMap<String, Any> {
      if (sourceRoots.isNotEmpty()) {
        val contentRoots = roots.mapNotNull { it.fileSystem.getNioPath(it)?.toString() }
        put("environment", mapOf("root" to (sourceRoots + contentRoots).distinct()))
      }
      if (excludedRoots.isNotEmpty()) {
        put("src", mapOf("exclude" to excludedRoots))
      }
    }
    return mapOf("configuration" to configuration)
  }

  /**
   * Computes the roots again, and tells ty only when they changed.
   *
   * ty has no `workspace/didChangeConfiguration` handler
   * ([ty#953](https://github.com/astral-sh/ty/issues/953)), so the notification pyrefly gets would be
   * ignored here. ty does re-request `workspace/configuration` for a workspace folder it does not know
   * yet, so re-adding the folder picks up the new roots without a restart of the process.
   *
   * The two notifications cannot be merged into one. ty processes all additions before the removals
   * within a single notification, which would leave the folder removed.
   *
   * ty does not report the re-read either ([ty#4463](https://github.com/astral-sh/ty/issues/4463)), so
   * the results the IDE already pulled stay stale until [LspClient.invalidateServerResults] drops them.
   */
  override suspend fun projectChangedAround(client: LspClient) {
    if (!readAction { refreshModuleRoots() }) return
    if (client.state != LspServerState.Running) return
    val folders = roots.map { WorkspaceFolder(getFileUri(it), it.name) }
    client.sendNotification {
      it.workspaceService.didChangeWorkspaceFolders(
        DidChangeWorkspaceFoldersParams(WorkspaceFoldersChangeEvent(emptyList(), folders)))
    }
    client.sendNotification {
      it.workspaceService.didChangeWorkspaceFolders(
        DidChangeWorkspaceFoldersParams(WorkspaceFoldersChangeEvent(folders, emptyList())))
    }
    // Everything the server already answered came from the old search path and stays cached until the
    // file changes, so ask for all of it again now that the push is on its way.
    client.invalidateServerResults()
    PyLspServerModificationTracker.getInstance(project).incModificationCount()
  }

  override val toolConfig: PyLspToolSettings
    get() = project.service<TyConfiguration>()

  override val lsp4jServerClass: Class<out Lsp4jServer>
    get() = TyLsp4jServer::class.java

  override val commandDescriptions: Map<String, String?> = mapOf(
    "ty.printDebugInformation" to "Print debug information",
  )

  override val lspCustomization: PyLspToolCustomization = object : PyLspToolCustomization(toolConfig, pyTool, project) {
    // workaround for IJPL-228417
    override val goToDefinitionCustomizer = LspGoToDefinitionDisabled

    override val diagnosticsSupport: PyLspToolDiagnosticsSupport = object : PyLspToolDiagnosticsSupport() {
      override fun shouldAskServerForDiagnostics(file: VirtualFile): Boolean =
        super.shouldAskServerForDiagnostics(file) && !file.isJupyterNotebook()
    }
  }
}

private fun VirtualFile.isJupyterNotebook(): Boolean =
  extension.equals("ipynb", ignoreCase = true)
