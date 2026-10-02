package com.intellij.python.ty

import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.lsp.api.Lsp4jServer
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.platform.lsp.api.customization.LspGoToDefinitionDisabled
import com.intellij.python.lsp.core.PyLspTool
import com.intellij.python.lsp.core.PyLspToolCustomization
import com.intellij.python.lsp.core.PyLspToolDescriptor
import com.intellij.python.lsp.core.PyLspToolIntegrationProvider
import com.intellij.python.lsp.core.PyLspToolSettings
import com.intellij.python.lsp.core.pyLspAttachedDescriptor
import com.intellij.python.lsp.core.pyLspProjectsToServeWith
import com.intellij.python.lsp.core.utils.PyLspServerModificationTracker
import com.intellij.python.sdk.backend.NativePythonInterpreter
import com.jetbrains.python.project.PyProject
import org.eclipse.lsp4j.ConfigurationItem
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.WorkspaceFoldersChangeEvent
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting

/** The configuration section ty asks for; see `Session::request_uninitialized_workspace_folder_configurations`. */
private const val TY_CONFIGURATION_SECTION = "ty"

class TyLspIntegrationProvider : PyLspToolIntegrationProvider() {
  override fun getDescriptor(pyProject: PyProject): PyLspToolDescriptor = tyDescriptor(pyProject)

  override fun pyTool(project: Project): PyLspTool<*> = TyPyTool.getInstance()

  override val servesEveryModule: Boolean get() = true
}

/**
 * The descriptor to start ty with for [pyProject].
 *
 * ty keeps its settings for each workspace folder and resolves the environment against that
 * folder's root, so one server answers for every Python project of a multi-module project. This holds
 * for the type engine and for the External Tools tool: both start the server from this descriptor. A
 * Python project whose environment pins another ty version gets a server of its own, because one
 * server runs one binary. For a project of one served Python project the server answers for
 * [pyProject] alone.
 *
 * Each folder gets the interpreter of its own Python project, see [TyLspClientDescriptor.getWorkspaceConfiguration].
 */
internal fun tyDescriptor(pyProject: PyProject): TyLspClientDescriptor {
  val served = pyLspProjectsToServeWith(pyProject, TyPyTool.getInstance())
  val descriptor = TyLspClientDescriptor(served.first(), served)
  // The type engine builds a descriptor and starts the server itself, so it must wire the provider
  // here. `fileOpened` may never run for the file that started the server.
  pyLspAttachedDescriptor(descriptor, TyLspIntegrationProvider::class.java)
  return descriptor
}

class TyLspClientDescriptor(
  pyProject: PyProject,
  servedProjects: List<PyProject> = listOf(pyProject),
) : PyLspToolDescriptor(pyProject, TyPyTool.getInstance(), servedProjects) {
  override fun lspArguments(): List<String> =
    listOf("server")

  override val usesSourceRoots: Boolean get() = true

  override val usesExcludedRoots: Boolean get() = true

  // ty says "Please refer to the logs for more details" and writes the details, such as a project load error, to stderr.
  override val offerServerLogInNotifications: Boolean = true

  override fun createInitializationOptions(): Map<String, Any>? {
    // The platform builds these options on its connect pool thread, which holds no lock, so the roots
    // are read here. The `workspace/configuration` replies only reuse them, because they run on the
    // LSP listener thread, see [sourceRoots].
    runReadActionBlocking { refreshModuleRoots() }
    return tyClientOptions()
  }

  /**
   * The settings of one workspace folder: the [tyClientOptions] and the interpreter of the folder's Python project.
   *
   * The interpreter is the one that the Python project had when the server started, see [startedInterpreterOf]. A
   * new interpreter restarts the server, so the reply needs no lock and computes no interpreter.
   *
   * The interpreter goes out as `pythonExtension`, the option that the ty VS Code extension sends for the
   * interpreter selected in the editor. ty gives this option the lowest precedence. An `environment.python`
   * in a ty configuration wins over it, and so does the environment of a uv workspace.
   *
   * The option must not be in the initialization options. ty merges those into the settings of each
   * folder, and it panics on a `pythonExtension` in both.
   */
  override fun getWorkspaceConfiguration(item: ConfigurationItem): Map<String, Any>? {
    if (item.section != TY_CONFIGURATION_SECTION) return null
    val options = tyClientOptions()
    val interpreter = startedInterpreterOf(servedProjectForScope(item) ?: pyProject) ?: return options
    val pythonExtension = pythonExtensionOf(interpreter) ?: return options
    return options.orEmpty() + ("pythonExtension" to pythonExtension)
  }

  /**
   * [interpreter] as ty reads it in the `pythonExtension` option, or `null` when the VFS does not know its files.
   * ty then finds an environment itself. The lookup does not refresh the VFS.
   *
   * The descriptor translates both paths, so they name the interpreter as ty sees it in WSL or Docker too.
   * ty 0.0.37 and later read only `sysPrefix`. ty 0.0.36 and earlier also need `uri`, and they drop all the settings
   * of the folder without it. The Python version is left out, because these versions also need the patch version and
   * `sys.version` for it. ty reads the version from the environment.
   */
  private fun pythonExtensionOf(interpreter: NativePythonInterpreter): Map<String, Any>? {
    val fileManager = VirtualFileManager.getInstance()
    val executable = fileManager.findFileByNioPath(interpreter.pythonBinary) ?: return null
    val sysPrefix = interpreter.pythonHomePath?.let(fileManager::findFileByNioPath) ?: tySysPrefixOf(executable) ?: return null
    val executableInfo = mapOf("uri" to getFileUri(executable), "sysPrefix" to getFilePath(sysPrefix))
    return mapOf("activeEnvironment" to mapOf("executable" to executableInfo))
  }

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

/**
 * The `sys.prefix` of the interpreter [executable], for an interpreter with no detected environment root.
 *
 * A Windows base installation and an interpreter whose detection failed have no such root.
 * A POSIX prefix keeps the interpreter in `bin`, and a virtual environment on Windows keeps it in `Scripts`.
 * A base installation on Windows keeps it in the prefix itself. The rule reads only the directory name, so
 * it also holds for an interpreter in WSL or Docker, whatever the OS of the IDE. An interpreter in any other
 * directory is taken as a base installation.
 */
@ApiStatus.Internal
@VisibleForTesting
fun tySysPrefixOf(executable: VirtualFile): VirtualFile? {
  val directory = executable.parent ?: return null
  return if (directory.name.lowercase() in PREFIX_SUBDIRECTORIES) directory.parent else directory
}

private val PREFIX_SUBDIRECTORIES: Set<String> = setOf("bin", "scripts")
