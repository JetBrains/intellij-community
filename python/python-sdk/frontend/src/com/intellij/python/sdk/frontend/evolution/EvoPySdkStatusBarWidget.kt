package com.intellij.python.sdk.frontend.evolution

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.icons.icon
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.impl.status.EditorBasedStatusBarPopup
import com.intellij.platform.project.projectId
import com.intellij.python.sdk.common.evolution.EvoLeafDto
import com.intellij.python.sdk.common.evolution.EvoNodeDto
import com.intellij.python.sdk.common.evolution.EvoWorkspaceDto
import com.intellij.python.sdk.common.evolution.PyEvoRegistry
import com.intellij.python.sdk.common.evolution.PyInterpreterDto
import com.intellij.python.sdk.common.evolution.evoRpcOrNull
import com.intellij.python.sdk.common.evolution.requestEvoAssociatedInterpreters
import com.intellij.python.sdk.common.evolution.requestEvoCurrentInterpreter
import com.intellij.python.sdk.common.evolution.requestEvoIsPythonModule
import com.intellij.python.sdk.common.evolution.requestEvoNodes
import com.intellij.python.sdk.common.evolution.requestEvoSdkConfigurationInProgress
import com.intellij.python.sdk.common.evolution.requestEvoShortcuts
import com.intellij.python.sdk.common.evolution.requestEvoWorkspace
import com.intellij.python.sdk.frontend.PySdkFrontendBundle
import com.intellij.python.sdk.frontend.evolution.components.EvoTreeStaticNodeElement
import com.intellij.ui.AnimatedIcon
import com.intellij.util.Function
import com.intellij.util.IconUtil
import com.intellij.util.PlatformUtils
import com.intellij.util.messages.MessageBusConnection
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import javax.swing.Icon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val ID: String = "EvoPySdkStatusBarWidget"

/** How long (ms) after the popup is closed a re-open still reuses the built tree instead of rebuilding (rescanning). */
private fun popupTreeTtlMs(): Long = PyEvoRegistry.popupTreeCacheSeconds.toLong() * 1000

/** Fading Python logo for the neutral "loading" state (no specific tool yet); configuring uses the tool's own logo. */
private val PYTHON_FADING_ICON: Icon = AnimatedIcon.Fading(AllIcons.Language.Python)

/** A stamp no real project-model generation can equal (they count up from zero), i.e. "refetch this on sight". */
private const val NEVER_A_GENERATION: Long = Long.MIN_VALUE

internal class PySdkStatusBarWidgetFactory : StatusBarWidgetFactory {
  override fun getId(): String = ID

  override fun getDisplayName(): String = PySdkFrontendBundle.message("evo.sdk.status.bar.widget.factory.display.name")

  override fun isAvailable(project: Project): Boolean = PyEvoRegistry.isWidgetEnabled

  override fun createWidget(project: Project, scope: CoroutineScope): StatusBarWidget = EvoPySdkStatusBarWidget(project, scope)
}

private class EvoPySdkStatusBarWidget(project: Project, scope: CoroutineScope) : EditorBasedStatusBarPopup(
  project = project,
  isWriteableFileRequired = false,
  scope = scope,
) {
  /** Current Eel interpreter (for display) + popup data (nodes, associated interpreters, shortcuts), fetched asynchronously over RPC. */
  private data class Cached(
    /**
     * The module this data belongs to, held by identity rather than by name alone: a name is not a durable key, since
     * removing a module frees its name for another one, and the entry must not then be served to that impostor.
     */
    val module: Module,
    val moduleName: String,
    /**
     * The [ProjectRootModificationTracker] value this was computed at. Anything that could change the answers — a
     * Python facet added to or removed from a module, a module renamed, an SDK reassigned — bumps that counter, so a
     * mismatch means the entry is out of date and gets refetched (while still being shown, so nothing blinks).
     */
    val stamp: Long,
    /**
     * Whether the module is a Python one (a `PyProject`). False keeps the widget hidden and leaves every other field
     * empty — nothing else is worth asking the backend for.
     */
    val isPyProject: Boolean,
    val current: PyInterpreterDto?,
    /** The tool workspace the module takes part in (`null` when standalone) — named in the popup title. */
    val workspace: EvoWorkspaceDto?,
    val nodes: List<EvoNodeDto>,
    val associated: List<PyInterpreterDto>,
    /** "Shortcuts" rows (autoconfigure suggestions), fetched only when there is no current interpreter. */
    val shortcuts: List<EvoLeafDto>,
  ) {
    /**
     * Whether this entry can still be acted on. [moduleName] is what every backend call is addressed to, and the
     * backend resolves it by name — so once the module is renamed or removed, that name resolves to nothing and every
     * call made with it fails with "module not found". Such an entry is dead, not merely stale.
     */
    val isUsable: Boolean get() = !module.isDisposed && module.name == moduleName
  }

  /**
   * Data for every module the user has visited, by name — so switching back and forth between two modules' files
   * renders from memory instead of re-running the whole fetch each time. Entries are validated on read against both
   * the module's identity and the project-model [Cached.stamp], and evicted when their module is disposed.
   */
  private val cache = ConcurrentHashMap<String, Cached>()

  /** Modules a load is currently in flight for. Guards against redundant re-fetches. */
  private val loading: MutableSet<String> = ConcurrentHashMap.newKeySet()

  /** The module [getWidgetState] last rendered a visible state for — the one a popup would be about. */
  @Volatile
  private var shownModule: String? = null

  /** Current project-model generation; see [Cached.stamp]. */
  private fun modelStamp(): Long = ProjectRootModificationTracker.getInstance(project).modificationCount

  /** Hidden, and no module is on show — so a popup cannot be opened against a stale one. */
  private fun hidden(): WidgetState {
    shownModule = null
    return WidgetState.HIDDEN
  }

  /** Forgets the built tree, so the next open rebuilds it against current data. */
  private fun dropPopupTree() {
    popupTree = null
    popupTreeModule = null
  }

  /**
   * Drops every entry that can no longer be acted on (see [Cached.isUsable]) — and the built tree with them, when it
   * was built from one.
   */
  private fun evictUnusable() {
    cache.values.removeIf { !it.isUsable }
    if (popupTreeModule?.let { !cache.containsKey(it) } == true) dropPopupTree()
  }

  /**
   * The remembered data for [module], or `null` when there is none. An entry filed under the same name but belonging
   * to a *different* module is not it — see [Cached.module]. A stale entry is still returned: the caller shows it and
   * refreshes behind it.
   */
  private fun cachedFor(module: Module): Cached? = cache[module.name]?.takeIf { it.module == module && it.isUsable }

  /**
   * The popup tree built from the current [cache] data, reused when the widget is re-opened within the reuse window
   * (see [popupTreeTtlMs]) of being closed — so a mis-click close-and-reopen does not re-scan every tool. Rebuilt
   * once the window elapses (to pick up newly created environments) or immediately when [cache] changes. A rebuild
   * mints a fresh trace root (see `EvoPySdkSwitchPopupFactory.buildTree`).
   */
  @Volatile
  private var popupTree: EvoTreeStaticNodeElement? = null

  /**
   * The module [popupTree] was built for. Its nodes address the backend by that name (their lazy loaders captured it),
   * so a tree is only ever reusable for the very module it was built from — never for the next one the user looks at,
   * and never after that module was renamed.
   */
  @Volatile
  private var popupTreeModule: String? = null

  /** When the popup was last closed (epoch ms); the [popupTreeTtlMs] reuse window is measured from this moment. */
  @Volatile
  private var popupClosedAt: Long = 0

  /** True while the backend SDK-configuration lock is held — the widget then shows a spinner instead of the interpreter. */
  @Volatile
  private var configuring: Boolean = false

  init {
    // Mirror the backend SDK-configuration lock: while held, show a spinner and disable the popup (no stale
    // interpreter/actions while a create-or-select is running).
    scope.launch {
      requestEvoSdkConfigurationInProgress(project.projectId()).collect { inProgress ->
        configuring = inProgress
        if (!inProgress) project.service<EvoConfiguringTracker>().nodeId = null   // stop attributing the fade to a tool
        update()
      }
    }
  }

  /**
   * The per-tool fading icon for the "configuring" state: the logo of the tool whose environment is being set up
   * (recorded by the select/create action in [EvoConfiguringTracker]), or the Python logo when the tool is unknown
   * (e.g. autoconfigure / associated interpreters).
   */
  private fun configuringIcon(cached: Cached): Icon {
    val nodeId = project.service<EvoConfiguringTracker>().nodeId
    val toolIcon = nodeId?.let { id -> cached.nodes.firstOrNull { it.id == id }?.icon?.icon() }
    return AnimatedIcon.Fading(toolIcon ?: AllIcons.Language.Python)
  }

  override fun getWidgetState(file: VirtualFile?): WidgetState {
    val module = moduleFor(file) ?: return hidden()
    val moduleName = module.name

    val current = cachedFor(module)
    // Whether the module is Python at all is only known once the backend has answered, and a Python interpreter widget
    // has no business appearing over a Java file in the meantime — so stay hidden until then, rather than flashing.
    if (current == null) {
      refresh(module)
      return hidden()
    }
    // Out of date (a facet or an SDK changed under us): keep showing what we have and refetch behind it, so the widget
    // never blinks out on a project-model change.
    if (current.stamp != modelStamp()) refresh(module)
    if (!current.isPyProject) return hidden()
    shownModule = moduleName

    if (configuring) {
      // Keep the widget enabled so its dynamic (self-animating) icon is painted directly — a disabled widget would
      // paint a static cached grayscale copy instead. The popup is blocked separately in createPopup while configuring.
      return WidgetState(
        PySdkFrontendBundle.message("evo.sdk.status.bar.configuring.description"),
        PySdkFrontendBundle.message("evo.sdk.status.bar.configuring.title"),
        true,
      ).apply { icon = configuringIcon(current) }
    }

    if (moduleName in loading && current.current == null) {
      // The module is Python, but its interpreter is still being fetched — a neutral animated "loading" state, not
      // the "No interpreter" warning.
      return WidgetState(
        PySdkFrontendBundle.message("evo.sdk.loading.description"),
        PySdkFrontendBundle.message("evo.sdk.status.bar.loading.title"),
        true,
      ).apply { icon = PYTHON_FADING_ICON }
    }

    val interpreter = current.current
    if (interpreter == null) {
      return WidgetState(
        PySdkFrontendBundle.message("evo.sdk.undefined.description"),
        PySdkFrontendBundle.message("evo.sdk.undefined.title"),
        true,
      ).apply { icon = AllIcons.General.BalloonWarning }
    }
    // Match the classic interpreter widget: desaturated tool icon + the interpreter's short name as text.
    return WidgetState(interpreter.description, interpreter.title, true).apply {
      icon = IconUtil.desaturate(interpreter.icon.icon())
    }
  }

  /**
   * (Re)loads the widget data. Whether the module is Python at all is settled first, and a non-Python one stops right
   * there — it costs one call instead of the four below, none of which (tool probing, a full SDK scan) mean anything
   * for a Java module. Then the current interpreter — all the status bar needs to render — is fetched and shown
   * immediately, and the slower popup data loads afterwards. The previous value stays visible until the new one
   * arrives, so neither the initial load nor a refresh ever flashes "No interpreter".
   */
  private fun refresh(module: Module) {
    val moduleName = module.name
    if (!loading.add(moduleName)) return // already in flight
    scope.launch {
      // Read the generation before fetching, so a model change *during* the fetch leaves the result marked stale
      // rather than passing for current.
      val stamp = modelStamp()
      val projectId = project.projectId()
      val prev = cachedFor(module)
      fun publish(entry: Cached) {
        cache[moduleName] = entry
        if (popupTreeModule == moduleName) dropPopupTree() // the tree was built from what we just replaced
        update()
      }
      try {
        // Treat an RPC failure as "not Python": staying hidden is the safe way to be wrong, and the stamp check or the
        // next module change retries.
        if (evoRpcOrNull { requestEvoIsPythonModule(projectId, moduleName) } != true) {
          publish(Cached(module, moduleName, stamp, isPyProject = false, null, null, emptyList(), emptyList(), emptyList()))
          return@launch
        }

        val interpreter = evoRpcOrNull { requestEvoCurrentInterpreter(projectId, moduleName) }
        publish(Cached(module, moduleName, stamp, true, interpreter,
                       prev?.workspace, prev?.nodes.orEmpty(), prev?.associated.orEmpty(), prev?.shortcuts.orEmpty()))

        val workspace = evoRpcOrNull { requestEvoWorkspace(projectId, moduleName) }
        val nodes = evoRpcOrNull { requestEvoNodes(projectId, moduleName) }.orEmpty()
        val associated = evoRpcOrNull { requestEvoAssociatedInterpreters(projectId, moduleName) }.orEmpty()
        // The "Shortcuts" autoconfigure suggestions are only shown (and only worth computing) when there is no interpreter.
        val shortcuts = if (interpreter == null) evoRpcOrNull { requestEvoShortcuts(projectId, moduleName) }.orEmpty() else emptyList()
        publish(Cached(module, moduleName, stamp, true, interpreter, workspace, nodes, associated, shortcuts))
      }
      finally {
        loading.remove(moduleName)
        update()
      }
    }
  }

  /** Guards against stacking concurrent node re-probes (see [refreshNodes]). */
  @Volatile
  private var refreshingNodes: Boolean = false

  /**
   * Re-probes just the available tool nodes for [moduleName] (keeping the shown interpreter and the associated
   * list), so a tool installed since the last scan appears without redoing the heavier current-interpreter and
   * associated-SDK scans of a full [refresh]. If the node set actually changed, the built tree is dropped so the
   * next open rebuilds from the fresh nodes. Skipped while a full refresh or another node re-probe is in flight.
   */
  private fun refreshNodes(moduleName: String) {
    if (moduleName in loading || refreshingNodes) return
    refreshingNodes = true
    scope.launch {
      try {
        val nodes = evoRpcOrNull { requestEvoNodes(project.projectId(), moduleName) }.orEmpty()
        val base = cache[moduleName] ?: return@launch
        // Compare by node ids (stable identity) — a newly available or removed tool changes this set; icon/label
        // identity is irrelevant and IconId equality is not guaranteed across fetches.
        if (base.nodes.map { it.id } == nodes.map { it.id }) return@launch
        cache[moduleName] = base.copy(nodes = nodes)
        if (popupTreeModule == moduleName) dropPopupTree()
        update()
      }
      finally {
        refreshingNodes = false
      }
    }
  }

  override fun isEnabledForFile(file: VirtualFile?): Boolean = true

  override fun registerCustomListeners(connection: MessageBusConnection) {
    connection.subscribe(ModuleRootListener.TOPIC, object : ModuleRootListener {
      override fun rootsChanged(event: ModuleRootEvent) {
        // Every entry is now behind the project-model generation, so the next getWidgetState refetches the module in
        // front of the user while still showing what it has. Entries whose module this change left unaddressable are
        // a different matter — drop those outright.
        evictUnusable()
        update()
      }
    })
    // A module rename does not go through rootsChanged, so without this the widget would only notice at the next
    // click — which would find its data addressed to a name the backend can no longer resolve, and open nothing.
    connection.subscribe(ModuleListener.TOPIC, object : ModuleListener {
      override fun modulesRenamed(project: Project, modules: List<Module>, oldNameProvider: Function<in Module, String>) {
        for (module in modules) {
          val oldName = oldNameProvider.`fun`(module)
          val entry = cache.remove(oldName)?.takeIf { it.module == module } ?: continue
          // The data still describes this module — only the name the backend is addressed by changed. Re-file it under
          // the new one and mark it stale, so the widget keeps rendering instead of blinking out while it refetches.
          cache[module.name] = entry.copy(moduleName = module.name, stamp = NEVER_A_GENERATION)
          // Follow the rename here too, so a click landing before the next render still finds the module on show.
          if (shownModule == oldName) shownModule = module.name
          if (popupTreeModule == oldName) dropPopupTree() // its nodes captured the old name
        }
        update()
      }

      override fun moduleRemoved(project: Project, module: Module) {
        evictUnusable()
        update()
      }
    })
  }

  override fun createPopup(context: DataContext): ListPopup? {
    if (configuring) return null // no popup while a configuration is in progress
    // The popup belongs to the module the widget is currently showing; a non-Python one is hidden by getWidgetState
    // and has no data behind it, so it can never open one. [Cached.isUsable] is re-checked here rather than trusted
    // from the last render: a rename between that render and this click leaves the entry addressing a name the backend
    // can no longer resolve, and opening it would fail every row with "module not found".
    val current = shownModule?.let { cache[it] }?.takeIf { it.isPyProject && it.isUsable } ?: return null
    // Reuse the tree only for the module it was built from, and only within the window measured from the last close,
    // so a quick reopen after a mis-click doesn't rescan; otherwise rebuild. The window restarts on each close.
    val reusable = popupTree?.takeIf { popupTreeModule == current.moduleName && System.currentTimeMillis() - popupClosedAt < popupTreeTtlMs() }
    // Outside the reuse window, also re-probe the available tools so one installed since the last scan (e.g. via
    // Settings | Python | Tools) shows up — otherwise the node list would stay cached for the widget's whole life.
    // The re-probe is async (takes effect from the next open) and availability is backed by PyExecutableCache, so a
    // warm cache makes it near-instant; it only does real work after an install invalidated that cache.
    if (reusable == null) refreshNodes(current.moduleName)
    val factory = EvoPySdkSwitchPopupFactory(project, current.moduleName, current.current, current.workspace, current.nodes, current.associated, current.shortcuts, scope)
    val tree = reusable ?: factory.buildTree(context).also { popupTree = it; popupTreeModule = current.moduleName }
    return factory.createPopup(tree, context) { popupClosedAt = System.currentTimeMillis() }
  }

  override fun ID(): String = ID

  override fun createInstance(project: Project): StatusBarWidget = EvoPySdkStatusBarWidget(project, scope)

  /**
   * The module the widget speaks for: the one owning [file], falling back in PyCharm to the project's root module.
   *
   * That fallback is what keeps the widget in the status bar when the focused file belongs to no module — a file dragged
   * in from outside the project, a scratch — or when no file is focused at all. Without it the widget vanished until a
   * module's file was focused again, and the interpreter could be neither seen nor switched (PY-90708).
   *
   * PyCharm only, deliberately: there the project *is* the Python project, so the root module's interpreter is the one
   * the user means. In IDEA a Python module is one of many, and claiming one for an unrelated file would hang a Python
   * interpreter widget over, say, a Java file.
   */
  private fun moduleFor(file: VirtualFile?): Module? {
    file?.let { findModule(it) }?.let { return it }
    if (!PlatformUtils.isPyCharm()) return null
    return rootModule()
  }

  /**
   * The module rooted *at* the project root — not merely the first one the model lists, which in a multi-module project
   * would be an arbitrary answer for a file that belongs to none of them.
   *
   * "Root module" is defined the same way `preserveRootModule` in `python-pyproject` defines it: the module one of whose
   * content roots is the project base path. [Project.getBasePath] is that same path — both it and
   * `IProjectStore.projectBasePath` return `storeDescriptor.historicalProjectBasePath` — so the two agree on which
   * module this is. `null` when the project root belongs to no module, which leaves the widget hidden as before.
   */
  private fun rootModule(): Module? {
    val projectRoot = project.basePath?.let { Path.of(it) } ?: return null
    return ModuleManager.getInstance(project).modules.firstOrNull { module ->
      ModuleRootManager.getInstance(module).contentRoots.any { it.toNioPathOrNull() == projectRoot }
    }
  }

  private fun findModule(file: VirtualFile): Module? =
    ModuleManager.getInstance(project).modules.firstOrNull { it.moduleContentScope.contains(file) }
}
