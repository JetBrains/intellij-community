// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.lsp.core

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.intention.CustomizableIntentionAction
import com.intellij.codeInsight.intention.FileModifier
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionActionWithOptions
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.util.IntentionName
import com.intellij.execution.ExecutionException
import com.intellij.icons.AllIcons
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.LangBundle
import com.intellij.modcommand.ModCommandAction
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.util.text.buildChildren
import com.intellij.openapi.util.text.buildHtml
import com.intellij.openapi.util.text.plus
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspClientDescriptor
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.LspIntegrationProvider
import com.intellij.platform.lsp.api.LspIntegrationProvider.LspClientStarter
import com.intellij.platform.lsp.api.LspServerListener
import com.intellij.platform.lsp.api.customization.LspCodeLensCustomizer
import com.intellij.platform.lsp.api.customization.LspCodeLensDisabled
import com.intellij.platform.lsp.api.customization.LspCompletionCustomizer
import com.intellij.platform.lsp.api.customization.LspCompletionSupport
import com.intellij.platform.lsp.api.customization.LspCustomization
import com.intellij.platform.lsp.api.customization.LspDiagnosticsCustomizer
import com.intellij.platform.lsp.api.customization.LspDiagnosticsDisabled
import com.intellij.platform.lsp.api.customization.LspDiagnosticsSupport
import com.intellij.platform.lsp.api.customization.LspHoverCustomizer
import com.intellij.platform.lsp.api.customization.LspHoverDisabled
import com.intellij.platform.lsp.api.customization.LspHoverSupport
import com.intellij.platform.lsp.api.customization.LspInlayHintSupport
import com.intellij.platform.lsp.api.customization.LspOptimizeImportsCustomizer
import com.intellij.platform.lsp.api.customization.LspOptimizeImportsDisabled
import com.intellij.platform.lsp.api.lsWidget.LspClientWidgetItem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.python.community.execService.BinaryToExec
import com.intellij.python.community.execService.asGeneralCommandLine
import com.intellij.python.lsp.core.utils.PyLspToolVersionTracker
import com.intellij.python.pytools.backend.PyTool
import com.intellij.python.pytools.backend.isActiveOn
import com.intellij.python.pytools.common.PY_EXTERNAL_TOOLS_SETTINGS_ID
import com.intellij.python.sdk.backend.toolExecutableWithBaseArgs
import com.intellij.ui.JBColor
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.jetbrains.python.PythonPluginDisposable
import com.jetbrains.python.packaging.common.PythonPackageManagementListener
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.psi.types.PyTypeEngineSettingsModificationTracker
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.PySdkListener
import com.jetbrains.python.sdk.pythonSdk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.eclipse.lsp4j.ConfigurationItem
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.InitializeResult
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import javax.swing.Icon

abstract class PyLspToolIntegrationProvider : LspIntegrationProvider {
  private val listenerConnectedForProjects: MutableSet<Project> = Collections.synchronizedSet(HashSet<Project>())

  override fun fileOpened(project: Project, file: VirtualFile, clientStarter: LspClientStarter) {
    // The tool decides first, because [getDescriptor] is not cheap. A tool that answers for every
    // module reads the interpreter and the installed packages of each one, see
    // [pyLspModulesToServeWith], and this runs in a read action for every file the user opens. A
    // project that runs neither the tool nor its type engine must pay none of that.
    if (!pyTool(project).isActiveOn(project))
      return

    // Find the module for this file
    val module = ModuleUtilCore.findModuleForFile(file, project) ?: return

    val descriptor = getDescriptor(module)
    if (!descriptor.isSupportedFile(file))
      return
    attach(descriptor)

    if (!descriptor.hasExecutable())
      return

    clientStarter.ensureClientStarted(descriptor)
  }

  /**
   * Names this provider on [descriptor] and subscribes to the project changes this provider watches.
   *
   * Every path that builds a descriptor calls this, not [fileOpened] alone. The type engine builds a
   * descriptor and starts the server itself, and `LspOpenedFilesService` then skips [fileOpened] for
   * a file the new server already covers. Without this call for that path the provider would never
   * subscribe, so no package event would ever re-group the modules, and `supportProvider` would stay
   * unset for a server that advertises `executeCommandProvider`.
   */
  @ApiStatus.Internal
  fun attach(descriptor: PyLspToolDescriptor) {
    descriptor.supportProvider = this
    val project = descriptor.project
    if (project.isDisposed || project in listenerConnectedForProjects) return

    // The set is app-level, because the provider is an application extension. So the disposable that
    // removes the project again is registered first, and the project goes into the set only after. An
    // entry without it would keep a closed project for the life of the IDE. `tryRegister` fails for a
    // project that closes, and this runs for each `TypeEvalContext`, so a closing project does reach it.
    val listenerDisposable = Disposer.newDisposable("Python LSP package listener")
    if (!Disposer.tryRegister(project, listenerDisposable)) return
    if (!listenerConnectedForProjects.add(project)) {
      Disposer.dispose(listenerDisposable)
      return
    }
    Disposer.register(listenerDisposable) {
      listenerConnectedForProjects.remove(project)
    }
    subscribeOnChanges(descriptor.pyTool, project, listenerDisposable)
    // The Python plugin can unload before the project closes, and the entry must go then too.
    if (!Disposer.tryRegister(PythonPluginDisposable.getInstance(project), Disposable { listenerConnectedForProjects.remove(project) })) {
      Disposer.dispose(listenerDisposable)
    }
  }

  override fun createWidgetItem(lspClient: LspClient, currentFile: VirtualFile?): LspClientWidgetItem =
    object : LspClientWidgetItem(lspClient, currentFile, icon = getIcon(lspClient)) {
      override val itemLabel: @NlsSafe String
        get() = presentableName(lspClient) + versionPostfix + rootPostfix

      // The External Tools page is a frontend contribution, so LspClientWidgetItem gets no settings class here,
      // and both settings actions come from this class.
      override fun createWidgetMainAction(): AnAction =
        OpenPyExternalToolsSettingsAction(widgetActionText, getIcon(lspClient))

      override fun createWidgetInlineActions(): List<AnAction> =
        super.createWidgetInlineActions() + OpenPyExternalToolsSettingsAction()
    }

  abstract fun getDescriptor(module: Module): PyLspToolDescriptor

  fun getIcon(lspClient: LspClient): Icon = (lspClient.descriptor as PyLspToolDescriptor).pyTool.icon

  /**
   * The tool this provider runs for [project].
   *
   * The same tool [getDescriptor] puts in its descriptor. It is stated here as well, because
   * [fileOpened] has to ask whether the tool is active before it builds a descriptor.
   */
  abstract fun pyTool(project: Project): PyLspTool<*>

  /**
   * Whether one server of this tool holds every served module.
   *
   * Only a tool whose server keeps one workspace for each folder, with its own interpreter, may set
   * this. [getDescriptor] of such a tool builds its descriptor from [pyLspModulesToServeWith]. A tool
   * that gives every folder the same interpreter keeps `false` and runs one server for each module,
   * and its servers never need a restart for a change of the folder set.
   */
  open val servesEveryModule: Boolean get() = false

  fun presentableName(lspClient: LspClient): @NlsSafe String = lspClient.initializeResult?.serverInfo?.name
                                                               ?: lspClient.descriptor.presentableName

  protected open fun subscribeOnChanges(pyTool: PyTool<*>, project: Project, parentDisposable: Disposable) {
    val executableChanged = PyToolChangeDebouncer(project.service<PyLspService>().cs) { pyTool.onExecutableChanged(project) }
    val connection = project.messageBus.connect(parentDisposable)
    connection.subscribe(PythonPackageManager.PACKAGE_MANAGEMENT_TOPIC, LspPackageListener(pyTool, project, executableChanged))
    connection.subscribe(ModuleRootListener.TOPIC, LspFolderSetListener(project))
    // A new module SDK can resolve another binary of the tool, and a running server keeps the old one.
    connection.subscribe(PySdkListener.TOPIC, object : PySdkListener {
      override fun moduleSdkUpdated(module: Module, prevSdk: Sdk?, newSdk: Sdk?) {
        if (module.project == project && prevSdk != newSdk) executableChanged.schedule()
      }
    })
    // A refresh of the serve keys lands without a project event, so it triggers the checks itself. A
    // server that started before the refresh can hold the wrong group, and a module that just got the
    // tool can need a server that nothing started.
    if (servesEveryModule) {
      val toolName = pyTool.packageName.name
      connection.subscribe(PY_LSP_SERVE_KEYS_CHANGED, PyLspServeKeysListener { changedTool ->
        if (changedTool != toolName) return@PyLspServeKeysListener
        project.service<PyLspService>().cs.launch {
          LspClientManager.getInstance(project).startClientsIfNeeded(this@PyLspToolIntegrationProvider::class.java)
          restartStaleClients(project)
        }
      })
    }
  }

  /**
   * Restarts the clients of this tool when the workspace folders they should serve change.
   *
   * The platform builds the server identity from the root paths and never sends
   * `didChangeWorkspaceFolders`, so a running server holds a stale folder set once a module or a
   * content root arrives or leaves. Without the restart the next start request builds a descriptor
   * with other roots, which starts a second server and leaves the first one running.
   *
   * A module that arrives or leaves also changes the project roots, so `rootsChanged` is the one
   * event to watch. The root manager moves its counter before it fires the event, so the cached
   * served set is fresh here. The restart only happens when the folder set really differs, see
   * [pyLspFolderSetIsStale].
   */
  inner class LspFolderSetListener(private val project: Project) : ModuleRootListener {
    override fun rootsChanged(event: ModuleRootEvent) {
      // A tool that runs one server for each module never grows a folder set, so it needs no restart.
      if (!servesEveryModule) return
      // `rootsChanged` runs inside a write action. Reading an interpreter and its packages there does
      // first-touch I/O, and it throws for a disposed SDK, which is worse than a late restart.
      project.service<PyLspService>().cs.launch {
        if (restartStaleClients(project)) return@launch
        // Every folder set still fits, but a module nested in a folder can have come or gone.
        val providerClass = this@PyLspToolIntegrationProvider::class.java
        for (client in LspClientManager.getInstance(project).getClients(providerClass)) {
          (client.descriptor as? PyLspToolDescriptor)?.projectChangedAround(client)
        }
      }
    }
  }

  /**
   * Restarts the clients of this tool when the folder set of one of them no longer matches its own
   * group, see [pyLspFolderSetIsStale]. Does nothing while every running client is up to date.
   *
   * Answers whether it stopped or restarted a client. A client it left alone keeps its folders, but
   * the project around it can still have changed, see [PyLspToolDescriptor.projectChangedAround].
   */
  private suspend fun restartStaleClients(project: Project): Boolean {
    // A tool that runs one server for each module has no groups, so no folder set of it is stale. The
    // group check would call each of its servers stale, because the group of a module holds the other
    // modules too, and it would restart them all on every package event.
    if (!servesEveryModule) return false
    // One check at a time. Two checks that begin together both see the old servers, and the second
    // one would stop the servers that the first one just started.
    return project.service<PyLspService>().restartMutex.withLock { restartStaleClientsLocked(project) }
  }

  /** Restarts the servers with the wrong folders or modules, one at a time, see [restartStaleClients]. */
  private suspend fun restartStaleClientsLocked(project: Project): Boolean {
    val providerClass = this@PyLspToolIntegrationProvider::class.java
    val lspClientManager = LspClientManager.getInstance(project)
    val descriptors = readAction {
      lspClientManager.getClients(providerClass).mapNotNull { it.descriptor as? PyLspToolDescriptor }
    }
    if (descriptors.isEmpty()) return false
    val pyTool = descriptors.first().pyTool
    // Outside the read action, because it reads the interpreter of every served module.
    val keys = pyLspRefreshServeKeys(project, pyTool)
    val (stale, servesNothing) = readAction {
      val served = pyLspServedModules(project)
      val keyOf = { module: Module -> keys[module] ?: pyLspServeKeyWithoutVersion(module) }
      pyLspFolderSetIsStale(descriptors, served, keyOf) to descriptors.any { pyLspServesNothing(it, served) }
    }

    // A server that answers for no served module keeps folders the project dropped, and its
    // descriptor holds every module it was built with, so a disposed module stays reachable while it
    // runs. A stop drops both. `startClientsIfNeeded` brings back what the open files still need.
    if (servesNothing) {
      thisLogger().info("A ${pyTool.lspServerName} server answers for no served module. Stopping its clients.")
      lspClientManager.stopClients(providerClass)
      lspClientManager.startClientsIfNeeded(providerClass)
      return true
    }
    if (!stale) return false

    thisLogger().info("The workspace folders of ${pyTool.lspServerName} changed. Restarting its clients.")
    lspClientManager.stopAndRestartClientsIfNeeded(providerClass)
    return true
  }

  /**
   * Starts the server when [pyTool] becomes a package of an interpreter of [project]. Stops the
   * server when the tool leaves every interpreter that a server can run against.
   */
  inner class LspPackageListener(
    val pyTool: PyTool<*>,
    val project: Project,
    private val executableChanged: PyToolChangeDebouncer,
  ) : PythonPackageManagementListener {
    /**
     * The tool version last seen in each interpreter, so a package event that changes nothing for
     * the tool does not restart a server. [NO_VERSION] stands for an interpreter without the tool.
     *
     * The version, and not a flag, because which modules one server holds depends on the version,
     * see [com.intellij.python.lsp.core.PyLspServeKey]. An upgrade in place keeps a flag equal, and it
     * would leave a running server with the old binary and a folder set the project no longer wants.
     *
     * This listener fires for one SDK at a time, and a project can hold several, so one shared entry
     * would let one interpreter without the tool stop a server that another interpreter needs.
     */
    private val versionBySdk: ConcurrentMap<String, String> = ConcurrentHashMap()

    /** Whether a client of this provider serves a module whose interpreter is [sdk]. */
    private suspend fun runsFor(sdk: Sdk): Boolean = readAction {
      LspClientManager.getInstance(project).getClients(this@PyLspToolIntegrationProvider::class.java)
        .any { client -> client.pyServedModules.any { !it.isDisposed && it.pythonSdk == sdk } }
    }

    override fun packagesChanged(sdk: Sdk) {
      // The topic is application level, so this fires for the interpreter of every open project.
      // Acting on another project's interpreter would restart the servers of this one for nothing
      // (PY-91655).
      //
      // The read action guards the project model. This runs on a pool thread with no lock of its
      // own, and `pyLspServedModules` reads the root manager of each module, which throws once a
      // write action disposes one.
      if (runReadActionBlocking { pyLspServedModules(project).none { it.pythonSdk == sdk } }) return

      val version = pyLspToolVersionOf(sdk, project, pyTool)
      val sdkKey = sdk.homePath ?: sdk.name
      // `put` answers `null` for an interpreter this listener never saw, which is not the same as an
      // interpreter that holds no copy of the tool. [NO_VERSION] keeps the two apart.
      val previous = versionBySdk.put(sdkKey, version ?: NO_VERSION)
      val seenBefore = previous != null
      val action = lspPackageVersionAction(seenBefore, previous?.takeUnless { it == NO_VERSION }, version)
      if (action == LspPackageAction.NONE) return

      // An upgrade in place leaves every server on the binary it started with, and the shared
      // answers of the tool describe that old binary. The debouncer merges a burst into one call.
      if (action == LspPackageAction.UPGRADE) executableChanged.schedule()

      // A version change re-groups the modules, so a running server can hold the wrong folder set.
      PyLspToolVersionTracker.getInstance(project).bump(pyTool.packageName.name)

      project.service<PyLspService>().cs.launch { handle(action, sdk, seenBefore) }
    }

    /**
     * Brings the servers of this tool in line with [action] on [sdk].
     *
     * A server that runs for [sdk] took its binary when it started. An install, an upgrade in place and
     * an uninstall each change the binary that the server should run, so the server restarts. The same
     * holds for a tool with one server for each module. The first event of an interpreter reports what
     * it already held, so it only starts what is missing.
     */
    private suspend fun handle(action: LspPackageAction, sdk: Sdk, seenBefore: Boolean) {
      val providerClass = this@PyLspToolIntegrationProvider::class.java
      val lspServerManager = LspClientManager.getInstance(project)
      if (action == LspPackageAction.STOP && noServedModuleHolds(pyTool)) {
        lspServerManager.stopClients(providerClass)
        return
      }
      // The keys are refreshed first, so the new server gets the fresh group and the right binary.
      if (servesEveryModule) pyLspRefreshServeKeys(project, pyTool)
      if (seenBefore && runsFor(sdk)) {
        project.service<PyLspService>().restartMutex.withLock {
          thisLogger().info("${pyTool.packageName.name} changed in '${sdk.name}' ($action). Restarting its clients.")
          lspServerManager.stopAndRestartClientsIfNeeded(providerClass)
        }
        return
      }
      if (action == LspPackageAction.START) lspServerManager.startClientsIfNeeded(providerClass)
      // No server runs for this interpreter, but the modules can re-group around it.
      restartStaleClients(project)
    }
  }

}

/**
 * Whether no served module of [project] holds the package of [pyTool].
 *
 * The interpreters are read outside the read action, because [PythonPackageManager.forSdk] creates a
 * manager on the first call and does first-touch I/O.
 */
private suspend fun PyLspToolIntegrationProvider.LspPackageListener.noServedModuleHolds(pyTool: PyTool<*>): Boolean {
  val sdks = readAction { pyLspServedModules(project).mapNotNull { it.pythonSdk } }
  return sdks.none { pyLspToolVersionOf(it, project, pyTool) != null }
}

/** Stands for an interpreter that holds no copy of the tool, so a map tells it from "never seen". */
private const val NO_VERSION: String = ""

/** The characters a glob reads as a pattern, so a path that holds one cannot go out as an exclude. */
private val GLOB_CHARACTERS: CharArray = charArrayOf('*', '?', '[', ']', '{', '}')

/**
 * Base class for a Python LSP tool descriptor.
 *
 * The server answers for the content roots of [servedModules], which holds [module] alone by
 * default. A tool whose server keeps one workspace for each folder passes [pyLspModulesToServeWith],
 * and then one server answers for the whole project. Such a tool also sets
 * [PyLspToolIntegrationProvider.servesEveryModule] to `true`.
 *
 * [module] is the primary module. It answers for a request that carries no workspace scope.
 */
abstract class PyLspToolDescriptor(
  val module: Module,
  val pyTool: PyLspTool<*>,
  val servedModules: List<Module> = listOf(module),
) : LspClientDescriptor(module.project, pyTool.lspServerName, *pyLspContentRootsOf(servedModules)) {
  /**
   * Whether this python LSP tool can serve Jupyter notebooks. Defaults to `true` because all current Python LSP tools support notebooks.
   */
  open val supportsNotebooks: Boolean get() = true

  override fun isSupportedFile(file: VirtualFile): Boolean =
    isPythonFile(file, notebookSupported = supportsNotebooks) && servedModuleOf(file) != null

  /**
   * The served modules that are still alive. A running client keeps its descriptor, and the
   * descriptor keeps every served module, so a module can be disposed while this server still runs.
   * Reading [ModuleRootManager] of a disposed module throws.
   */
  val liveServedModules: List<Module> get() = servedModules.filterNot { it.isDisposed }

  /** The served module that holds [file], or `null` when no served module does. */
  fun servedModuleOf(file: VirtualFile): Module? =
    ModuleUtilCore.findModuleForFile(file, project)?.takeIf { it in liveServedModules }

  /**
   * The served module that the `scopeUri` of [item] points at, or `null`.
   *
   * The server sends one `workspace/configuration` item for each workspace folder it wants the
   * settings of. Use this to answer with the settings of that folder, so every module gets its own
   * interpreter. A `null` result means the item names no folder this descriptor serves, and the
   * caller then answers for the primary module. Two cases give `null`, and they differ:
   *
   * - The item carries no `scopeUri`. It asks for the settings of the server's default workspace,
   *   which every server asks for once, next to the scoped items. This is expected, so the log entry
   *   is a debug one. The primary module answers, which is the wrong interpreter for every other
   *   served module, and only the scoped items keep those modules right.
   * - The item names a folder this descriptor does not serve. Nothing explains that, so it is a
   *   warning.
   */
  fun servedModuleForScope(item: ConfigurationItem): Module? {
    val scopeUri = item.scopeUri
    if (scopeUri == null) {
      thisLogger().debug("$presentableName asked for the configuration of its default workspace. " +
                         "The primary module '${module.name}' answers for it.")
      return null
    }
    val scopeFile = findFileByUri(scopeUri)
    val served = scopeFile?.let { file ->
      liveServedModules.firstOrNull { ModuleRootManager.getInstance(it).contentRoots.any { root -> root == file } }
    }
    if (served == null) {
      thisLogger().warn("$presentableName asked for the configuration of '$scopeUri', which is no served content root. " +
                        "The primary module '${module.name}' answers instead, so this folder may get the wrong interpreter.")
    }
    return served
  }

  /**
   * The tool binary and its base arguments, or `null` when no served module provides them.
   *
   * One server can answer for several modules, and the tool can be installed in only some of them.
   * Take the binary from any of them, or a module without the tool stops a server that the other
   * modules need. The modules whose interpreter holds the tool package come first, see
   * [executableCandidates]. The lookup falls back to `PATH` and `uvx` for any module, so without that
   * order the primary module would win with a binary the user did not install.
   */
  private fun findExecutable(): Pair<BinaryToExec, List<String>>? {
    for (served in executableCandidates()) {
      val executable = runBlockingMaybeCancellable {
        ModuleOrProject.ModuleAndProject(served).toolExecutableWithBaseArgs(pyTool, executableName)
      }
      executable.successOrNull?.let { return it }
      thisLogger().info("$presentableName has no executable in module '${served.name}'.")
    }
    return null
  }

  /**
   * The live served modules that may provide the binary, in the order to ask them. The rule is
   * [pyLspExecutableCandidates], applied to the serve-key snapshot of this tool.
   *
   * No `ifEmpty { listOf(module) }` fallback: `module` is a served module, so an empty answer means
   * it is disposed too, and reading the interpreter of a disposed module throws.
   */
  fun executableCandidates(): List<Module> = pyLspExecutableCandidates(liveServedModules, pyLspServeKeysView(project, pyTool))

  /** Whether some served module's interpreter provides the tool binary. */
  fun hasExecutable(): Boolean = findExecutable() != null

  /** The last computed [projectExcludes]. Only [refreshProjectExcludes] writes it. */
  @Volatile
  private var cachedProjectExcludes: List<String> = emptyList()

  /**
   * The paths a tool should leave out of this server's project, as absolute paths, as last computed.
   *
   * These are the [pyLspForeignNestedRoots] of this server. A path with a glob character in it is left
   * out, because the tool would read it as a pattern. The server then analyses that module once more,
   * which costs time and gives no wrong answer.
   *
   * This takes no lock and computes nothing. The platform answers `workspace/configuration` on the one
   * LSP listener thread of the server, and that thread also reads every response. A read action there
   * waits behind a pending write action, and meanwhile no response of this server gets read. A thread
   * that holds the read lock and waits for such a response then waits for its whole timeout. So
   * compute the value with [refreshProjectExcludes] on another thread first.
   */
  fun projectExcludes(): List<String> = cachedProjectExcludes

  /** Computes the [projectExcludes] again, and answers whether they changed. */
  @RequiresReadLock
  fun refreshProjectExcludes(): Boolean {
    val fresh = pyLspForeignNestedRoots(liveServedModules, project.modules.toList())
      .filter { path -> GLOB_CHARACTERS.none { it in path } }
    if (fresh == cachedProjectExcludes) return false
    cachedProjectExcludes = fresh
    return true
  }

  /**
   * Tells the running server behind [client] that the project changed around it while its own folder
   * set stayed the same. A module nested in one of its folders can have come or gone. A tool that sends
   * [projectExcludes] overrides this, computes them again, and makes the server read its settings when
   * they changed.
   */
  open suspend fun projectChangedAround(client: LspClient) {}

  abstract val toolConfig: PyLspToolSettings

  open val executableName: String get() = pyTool.packageName.name

  abstract fun lspArguments(): List<String>

  override fun createCommandLine(): GeneralCommandLine {
    @Suppress("HardCodedStringLiteral") // this text goes only to the IDE logs
    val noExecutable = liveServedModules.ifEmpty { null }
                         ?.let { "No module of ${it.joinToString { m -> m.name }} provides the $presentableName executable" }
                       ?: "Every module the $presentableName server serves is gone"
    val (binary, baseArgs) = findExecutable() ?: throw ExecutionException(noExecutable)
    val cmd = binary.asGeneralCommandLine().getOr { throw ExecutionException(it.error.message) }
      .withParameters(*baseArgs.toTypedArray(), *lspArguments().toTypedArray())
    return cmd
  }

  lateinit var supportProvider: PyLspToolIntegrationProvider

  private val registeredActionIds = mutableListOf<String>()

  override val lspServerListener: PyLspToolDescriptorLspServerListener = PyLspToolDescriptorLspServerListener()

  open inner class PyLspToolDescriptorLspServerListener : LspServerListener {
    override fun serverInitialized(params: InitializeResult) {
      dropCachedTypeContexts()
      val actionManager = ActionManager.getInstance()
      val commandProvider = params.capabilities.executeCommandProvider

      if (commandProvider == null) return
      // workaround for IJPL-196574
      commandProvider.commands.forEach { command ->
        val actionId = "LSP.Command.$presentableName.$command"
        if (actionManager.getAction(actionId) != null) {
          // workaround for PY-86023
          actionManager.unregisterAction(actionId)
        }
        if (command in commandDescriptions && commandDescriptions[command] == null) return@forEach
        val text = "${params.serverInfo.name}: " + (commandDescriptions[command] ?: command)
        val action = object : AnAction(text) {
          override fun actionPerformed(e: AnActionEvent) {
            val lspServerManager = LspClientManager.getInstance(project)

            lspServerManager
              .getClients(supportProvider::class.java)
              .firstOrNull()
              ?.let { server ->
                project.service<PyLspService>().cs.launch {
                  server.sendRequest {
                    it.workspaceService.executeCommand(ExecuteCommandParams(command, null))
                  }
                }
              }
          }
        }
        actionManager.registerAction(actionId, action)
        registeredActionIds.add(actionId)
      }
    }

    override fun serverStopped(shutdownNormally: Boolean) {
      dropCachedTypeContexts()
      val actionManager = ActionManager.getInstance()
      registeredActionIds.forEach { actionId ->
        actionManager.unregisterAction(actionId)
      }
      registeredActionIds.clear()
    }

    /**
     * A type context picks its engine once, when it is created, and the context cache keeps it until the
     * PSI changes. A context created while this server did not run has no engine, and one created before
     * a stop keeps an engine that no server answers. So each start and stop of the selected engine's
     * server drops the cached contexts.
     */
    private fun dropCachedTypeContexts() {
      if (pyTool.isSelectedAsTypeEngine(project)) {
        PyTypeEngineSettingsModificationTracker.getInstance(project).incModificationCount()
      }
    }
  }

  // workaround for IJPL-196574
  open val commandDescriptions: Map<String, String?> = emptyMap()

  override val lspCustomization: PyLspToolCustomization = PyLspToolCustomization(toolConfig, pyTool, project)
}

open class PyLspToolCustomization(
  val toolConfig: PyLspToolSettings,
  private val pyTool: PyLspTool<*>,
  private val project: Project,
) : LspCustomization() {
  val Diagnostic.presentableCode: String?
    get() = code?.get()?.toString()?.let { codeCustomizer(it) }

  open fun quickFixMessage(text: @IntentionName String): @IntentionName String = text

  open fun quickFixOptions(diagnostic: Diagnostic): List<IntentionAction> = emptyList()

  override val completionCustomizer: LspCompletionCustomizer = object : LspCompletionSupport() {
    override fun shouldRunCodeCompletion(parameters: CompletionParameters): Boolean =
      pyTool.isActiveOn(project) && toolConfig.completions == true
  }

  protected open val diagnosticsSupport: PyLspToolDiagnosticsSupport = PyLspToolDiagnosticsSupport()

  // instead of using `shouldAskServerForDiagnostics` we also want to avoid `publishDiagnostics`
  final override val diagnosticsCustomizer: LspDiagnosticsCustomizer
    get() = if (pyTool.isActiveOn(project) && toolConfig.inspections) diagnosticsSupport else LspDiagnosticsDisabled

  override val optimizeImportsCustomizer: LspOptimizeImportsCustomizer = LspOptimizeImportsDisabled

  override val inlayHintCustomizer: LspInlayHintSupport = object : LspInlayHintSupport() {
    override fun shouldAskServerForInlayHints(file: VirtualFile): Boolean =
      pyTool.isActiveOn(project) && toolConfig.inlayHints == true
  }

  override val hoverCustomizer: LspHoverCustomizer
    get() = if (pyTool.isActiveOn(project) && toolConfig.documentation == true) LspHoverSupport() else LspHoverDisabled

  override val codeLensCustomizer: LspCodeLensCustomizer = LspCodeLensDisabled

  open inner class PyLspToolDiagnosticsSupport : LspDiagnosticsSupport() {

    override fun getMessage(diagnostic: Diagnostic): String {
      val messageLines = super.getMessage(diagnostic).split("\n")
      // source is nullable
      val firstLine =
        messageLines[0] + "  ${diagnostic.source.orEmpty()}" + diagnostic.presentableCode?.let { "($it)" }.orEmpty() // NON-NLS
      if (messageLines.size == 1) return firstLine
      return firstLine + "\n" + messageLines.drop(1).joinToString("\n", prefix = "\n") // NON-NLS
    }

    override fun getTooltip(diagnostic: Diagnostic): String {
      val code = diagnostic.presentableCode
      val source = diagnostic.source
      val style = "color: gray"
      // workaround for IJPL-196845
      val codeLink = when {
        diagnostic.codeDescription?.href != null && code != null -> HtmlChunk.link(diagnostic.codeDescription?.href!!, code)
        code != null -> HtmlChunk.span(style).buildChildren { append(HtmlChunk.text(code)) }
        else -> null
      }
      val suffix = when {
        source != null && codeLink != null && diagnostic.codeDescription?.href != null -> {
          // "source(code)" with link
          HtmlChunk.span(style).buildChildren {
            append(HtmlChunk.text("$source("))
          } +
          codeLink +
          HtmlChunk.span(style).buildChildren {
            append(HtmlChunk.text(")"))
          }
        }
        source != null && code != null -> {
          // "source(code)" without link
          HtmlChunk.span(style).buildChildren {
            append(HtmlChunk.text("$source($code)"))
          }
        }
        source != null -> {
          // "source"
          HtmlChunk.span(style).buildChildren {
            append(HtmlChunk.text(source))
          }
        }
        else -> {
          // "code" or null (empty)
          codeLink
        }
      }

      val tooltip = super.getTooltip(diagnostic)
      // workaround for leading spaces getting removed
      val fixed = tooltip.replace(Regex("\n +")) { m ->
        val spaces = m.value.substring(1)
        "\n" + "\u00A0".repeat(spaces.length)
      }
      // workaround for IJPL-196664
      return buildHtml {
        append(HtmlChunk.text(fixed))
        if (suffix != null) {
          append(HtmlChunk.nbsp() + suffix)
        }
      }
    }

    // workaround for IJPL-196573
    override fun getHighlightSeverity(diagnostic: Diagnostic): HighlightSeverity? =
      when (diagnostic.severity) {
        DiagnosticSeverity.Hint -> HighlightSeverity.INFORMATION
        else -> super.getHighlightSeverity(diagnostic)
      }

    // workaround for IJPL-196573
    override fun getEnforcedTextAttributes(diagnostic: Diagnostic): TextAttributes? =
      when (diagnostic.severity) {
        DiagnosticSeverity.Information -> TextAttributes().apply {
          effectType = EffectType.WAVE_UNDERSCORE
          effectColor = JBColor.BLUE
        }
        DiagnosticSeverity.Hint -> TextAttributes().apply {
          effectType = EffectType.BOLD_DOTTED_LINE
          effectColor = JBColor.GRAY
        }
        else -> null
      }
  }

  open inner class PyLspAction(val diagnostic: Diagnostic, val action: IntentionAction) : IntentionAction by action,
                                                                                          CustomizableIntentionAction,
                                                                                          IntentionActionWithOptions {
    override fun getText(): @IntentionName String {
      return quickFixMessage(action.text)
    }

    override fun isShowSubmenu(): Boolean {
      return true
    }

    override fun getOptions(): List<IntentionAction> {
      return quickFixOptions(diagnostic)
    }

    override fun asIntention(): IntentionAction {
      return this
    }

    override fun getCombiningPolicy(): IntentionActionWithOptions.CombiningPolicy {
      // ideally this would be `InspectionOptionsOnly`, but at this time it not investigated
      return IntentionActionWithOptions.CombiningPolicy.IntentionOptionsOnly
    }

    //<editor-fold defaultstate="collapsed" desc="default method explicit delegates">
    override fun generatePreview(project: Project, editor: Editor, psiFile: PsiFile): IntentionPreviewInfo {
      return action.generatePreview(project, editor, psiFile)
    }

    override fun asModCommandAction(): ModCommandAction? {
      return action.asModCommandAction()
    }

    override fun getElementToMakeWritable(currentFile: PsiFile): PsiElement? {
      return action.getElementToMakeWritable(currentFile)
    }

    override fun getFileModifierForPreview(target: PsiFile): FileModifier? {
      return action.getFileModifierForPreview(target)
    }

    override fun isDumbAware(): Boolean {
      return action.isDumbAware
    }
    //</editor-fold>
  }

  open fun codeCustomizer(@Nls code: String): @Nls String = code
}

/** What [lspPackageAction] or [lspPackageVersionAction] tells the caller to do with the servers of one tool. */
@ApiStatus.Internal
enum class LspPackageAction { START, STOP, UPGRADE, NONE }

/**
 * What to do after the answer to "the tool is a package of this interpreter" moved from [previous] to [current].
 *
 * [previous] is `null` while the interpreter has no earlier answer. A first answer of `false` is no reason to
 * stop a server, because the tool also runs from a custom path, from `PATH`, or through `uvx`, and none of those
 * makes it a package. A first `false` stopped the server that the reopened editors had just started (PY-92163).
 * A first answer of `true` still starts the server, because an install is the event this rule exists for.
 */
@ApiStatus.Internal
fun lspPackageAction(previous: Boolean?, current: Boolean): LspPackageAction = when {
  previous == current -> LspPackageAction.NONE
  current -> LspPackageAction.START
  previous == null -> LspPackageAction.NONE
  else -> LspPackageAction.STOP
}

/**
 * [lspPackageAction] for the tool version of one interpreter. [previous] and [current] are `null` for an
 * interpreter that holds no copy of the tool. [seenBefore] is `false` while the interpreter has no earlier
 * answer, and then [previous] means nothing.
 *
 * Two different versions give [LspPackageAction.UPGRADE]. A running server keeps the binary it started
 * with, so an upgrade in place needs a restart as much as an install does.
 */
@ApiStatus.Internal
fun lspPackageVersionAction(seenBefore: Boolean, previous: String?, current: String?): LspPackageAction = when {
  !seenBefore -> lspPackageAction(previous = null, current = current != null)
  previous == current -> LspPackageAction.NONE
  previous != null && current != null -> LspPackageAction.UPGRADE
  else -> lspPackageAction(previous = previous != null, current = current != null)
}

/**
 * Opens the External Tools page.
 *
 * The page is a frontend contribution, so this module knows the id of the page and not the class of it.
 * [com.intellij.platform.lang.lsWidget.OpenSettingsAction] needs the class, so this action selects the page by id.
 */
private class OpenPyExternalToolsSettingsAction(
  text: @NlsActions.ActionText String = LangBundle.message("language.services.widget.open.settings.action"),
  icon: Icon = AllIcons.General.Settings,
) : AnAction(text, null, icon), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    ShowSettingsUtil.getInstance().showSettingsDialog(
      project,
      { it is SearchableConfigurable && it.id == PY_EXTERNAL_TOOLS_SETTINGS_ID },
      null,
    )
  }
}

@Service(Service.Level.PROJECT)
class PyLspService(val cs: CoroutineScope) {
  /** Lets one restart of the servers of this project run at a time. */
  @ApiStatus.Internal
  val restartMutex: Mutex = Mutex()
}
