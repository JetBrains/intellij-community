package com.intellij.python.sdk.frontend.evolution

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.icons.icon
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.impl.status.EditorBasedStatusBarPopup
import com.intellij.platform.project.projectId
import com.intellij.python.sdk.common.evolution.EvoLeafDto
import com.intellij.python.sdk.common.evolution.EvoNodeDto
import com.intellij.python.sdk.common.evolution.EvoPyProjectDto
import com.intellij.python.sdk.common.evolution.PyEvoRegistry
import com.intellij.python.sdk.common.evolution.PyEvoWidgetCollector
import com.intellij.python.sdk.common.evolution.PyInterpreterDto
import com.intellij.python.sdk.common.evolution.evoRpcOrNull
import com.intellij.python.sdk.common.evolution.requestEvoAssociatedInterpreters
import com.intellij.python.sdk.common.evolution.requestEvoCurrentInterpreter
import com.intellij.python.sdk.common.evolution.requestEvoNodes
import com.intellij.python.sdk.common.evolution.requestEvoPyProjects
import com.intellij.python.sdk.common.evolution.requestEvoSdkConfigurationInProgress
import com.intellij.python.sdk.common.evolution.requestEvoShortcuts
import com.intellij.python.sdk.frontend.PySdkFrontendBundle
import com.intellij.python.sdk.frontend.evolution.components.EvoTreeStaticNodeElement
import com.intellij.ui.AnimatedIcon
import com.intellij.util.messages.MessageBusConnection
import java.util.concurrent.ConcurrentHashMap
import javax.swing.Icon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.intellij.openapi.util.Disposer
import com.intellij.ui.awt.RelativePoint
import java.awt.Point

private const val ID: String = "EvoPySdkStatusBarWidget"

/** How long (ms) after the popup is closed a re-open still reuses the built tree instead of rebuilding (rescanning). */
private fun popupTreeTtlMs(): Long = PyEvoRegistry.popupTreeCacheSeconds.toLong() * 1000

/**
 * How long (ms) the widget keeps a fetched "Shortcuts" list before it asks the backend again.
 *
 * The suggestions change with the project on disk — a dependency file written, a tool installed — and nothing tells the
 * frontend about it. So they are re-read on a timer instead, as the "no interpreter configured" inspection re-reads them
 * on each of its own runs. Matches the backend cache behind the answer (`PySdkConfiguratorsCache`), which makes a
 * re-read that finds no change cost one RPC call and no probe.
 */
private const val SHORTCUTS_TTL_MS: Long = 20_000

/** Fading Python logo for the neutral "loading" state (no specific tool yet); configuring uses the tool's own logo. */
private val PYTHON_FADING_ICON: Icon = AnimatedIcon.Fading(AllIcons.Language.Python)

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
  /**
   * The project's Python structure, as the backend pushes it: every `PyProject` that exists, which one is the project's
   * own, and how they group into tool workspaces.
   *
   * The frontend cannot derive any of this — `PyProject` is a backend notion — so until the first emission arrives the
   * widget knows of no target at all and stays hidden. Every later emission replaces this wholesale, so the three
   * questions below are always answered from one consistent generation.
   */
  private class Structure(dtos: List<EvoPyProjectDto>) {
    private val byKey: Map<String, EvoPyProjectDto> = dtos.associateBy { it.key }

    /** The `PyProject` rooted at the project's own base dir; `null` when the project root is not a Python one. */
    val main: EvoPyProjectDto? = dtos.firstOrNull { it.isMain }

    val keys: Set<String> get() = byKey.keys

    operator fun get(key: String): EvoPyProjectDto? = byKey[key]

    /**
     * The `PyProject` [module] is, or `null` when it is not a Python module at all.
     *
     * Matched by content root rather than by module name: a `PyProject`'s key *is* one of its module's content roots
     * (see `PyProjectImpl`), and a path — unlike a name — is not reassigned by a rename. Both sides spell it
     * system-independently, so this is plain string equality.
     */
    fun of(module: Module): EvoPyProjectDto? =
      ModuleRootManager.getInstance(module).contentRoots.firstNotNullOfOrNull { byKey[it.path] }

    /** Display name of the workspace [target] takes part in, or `null` when it is standalone. */
    fun workspaceRootName(target: EvoPyProjectDto): String? = target.workspaceRootKey?.let { byKey[it] }?.name
  }

  /** Current Eel interpreter (for display) + popup data (nodes, associated interpreters, shortcuts), fetched asynchronously over RPC. */
  private data class Cached(
    /**
     * The [ProjectRootModificationTracker] value this was computed at. Anything that could change the answers — an SDK
     * reassigned, a root added — bumps that counter, so a mismatch means the entry is out of date and gets refetched
     * (while still being shown, so nothing blinks). Changes to the *set* of `PyProject`s do not come through here:
     * those arrive as a new [Structure].
     */
    val stamp: Long,
    val current: PyInterpreterDto?,
    val nodes: List<EvoNodeDto>,
    val associated: List<PyInterpreterDto>,
    /** "Shortcuts" rows (autoconfigure suggestions), fetched only when there is no current interpreter. */
    val shortcuts: List<EvoLeafDto>,
    /** When [shortcuts] was last read (epoch ms); the [SHORTCUTS_TTL_MS] window is measured from this moment. */
    val shortcutsAt: Long,
  )

  /**
   * Data for every target the user has visited, by [EvoPyProjectDto.key] — so switching back and forth between two
   * files' projects renders from memory instead of re-running the whole fetch each time. Entries are validated on read
   * against the project-model [Cached.stamp], and evicted when their key leaves the [Structure].
   */
  private val cache = ConcurrentHashMap<String, Cached>()

  /** Targets a load is currently in flight for. Guards against redundant re-fetches. */
  private val loading: MutableSet<String> = ConcurrentHashMap.newKeySet()

  /** The pushed project structure; `null` until the backend's first emission. */
  @Volatile
  private var structure: Structure? = null

  /** The target [getWidgetState] last rendered a visible state for — the one a popup would be about. */
  @Volatile
  private var shownKey: String? = null

  /** Current project-model generation; see [Cached.stamp]. */
  private fun modelStamp(): Long = ProjectRootModificationTracker.getInstance(project).modificationCount

  /** Hidden, and no target is on show — so a popup cannot be opened against a stale one. */
  private fun hidden(): WidgetState {
    shownKey = null
    return WidgetState.HIDDEN
  }

  /** Forgets the built tree, so the next open rebuilds it against current data. */
  private fun dropPopupTree() {
    popupTree = null
    popupTreeKey = null
  }

  /**
   * Drops every entry whose target no longer exists — and the built tree with it, when it was built from one. Called on
   * each new [Structure], which is the only thing that can retire a key.
   */
  private fun evictUnknown() {
    val known = structure?.keys ?: return
    cache.keys.removeIf { it !in known }
    if (popupTreeKey?.let { it !in known } == true) dropPopupTree()
  }

  /**
   * The popup tree built from the current [cache] data, reused when the widget is re-opened within the reuse window
   * (see [popupTreeTtlMs]) of being closed — so a mis-click close-and-reopen does not re-scan every tool. Rebuilt
   * once the window elapses (to pick up newly created environments) or immediately when [cache] changes. A rebuild
   * mints a fresh trace root (see `EvoPySdkSwitchPopupFactory.buildTree`).
   */
  @Volatile
  private var popupTree: EvoTreeStaticNodeElement? = null

  /**
   * The target [popupTree] was built for. Its nodes address the backend by that key (their lazy loaders captured it),
   * so a tree is only ever reusable for the very target it was built from, never for the next one the user looks at.
   */
  @Volatile
  private var popupTreeKey: String? = null

  /** When the popup was last closed (epoch ms); the [popupTreeTtlMs] reuse window is measured from this moment. */
  @Volatile
  private var popupClosedAt: Long = 0

  /** True while the backend SDK-configuration lock is held — the widget then shows a spinner instead of the interpreter. */
  @Volatile
  private var configuring: Boolean = false

  init {
    scope.launch {
      requestEvoPyProjects(project.projectId()).collect { dtos ->
        structure = Structure(dtos)
        evictUnknown()
        // Also retire the built tree outright: it was built against the previous structure — its popup title names the
        // workspace a target takes part in — so reusing it would show what the project looked like a moment ago.
        dropPopupTree()
        update()
      }
    }
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
  private fun configuringIcon(cached: Cached?): Icon {
    val nodeId = project.service<EvoConfiguringTracker>().nodeId
    val toolIcon = nodeId?.let { id -> cached?.nodes?.firstOrNull { it.id == id }?.icon?.icon() }
    return AnimatedIcon.Fading(toolIcon ?: AllIcons.Language.Python)
  }

  override fun getWidgetState(file: VirtualFile?): WidgetState {
    val target = targetFor(file) ?: return hidden()
    shownKey = target.key

    val cached = cache[target.key]
    // Nothing fetched yet, or out of date (an SDK changed under us) — fetch behind whatever we are showing, so the
    // widget never blinks out. Unlike before, there is no "is this Python at all" question to wait on: the target came
    // out of the structure, so it *is* a PyProject and the widget can show its loading state right away.
    if (cached == null || cached.stamp != modelStamp()) refresh(target.key)
    // The suggestions the popup would show go stale on their own, without anything the checks above can see. Re-read
    // them here, where the widget is told about the edits that change them, so the next open is built from a fresh list.
    else if (cached.current == null && System.currentTimeMillis() - cached.shortcutsAt > SHORTCUTS_TTL_MS) {
      refreshShortcuts(target.key)
    }

    if (configuring) {
      // Keep the widget enabled so its dynamic (self-animating) icon is painted directly — a disabled widget would
      // paint a static cached grayscale copy instead. The popup is blocked separately in createPopup while configuring.
      return WidgetState(
        PySdkFrontendBundle.message("evo.sdk.status.bar.configuring.description"),
        PySdkFrontendBundle.message("evo.sdk.status.bar.configuring.title"),
        true,
      ).apply { icon = configuringIcon(cached) }
    }

    val interpreter = cached?.current
    if (interpreter == null && target.key in loading) {
      // The interpreter is still being fetched — a neutral animated "loading" state, not the "No interpreter" warning.
      return WidgetState(
        PySdkFrontendBundle.message("evo.sdk.loading.description"),
        PySdkFrontendBundle.message("evo.sdk.status.bar.loading.title"),
        true,
      ).apply { icon = PYTHON_FADING_ICON }
    }

    if (interpreter == null) {
      return WidgetState(
        PySdkFrontendBundle.message("evo.sdk.undefined.description"),
        PySdkFrontendBundle.message("evo.sdk.undefined.title"),
        true,
      ).apply { icon = AllIcons.General.BalloonWarning }
    }
    // The tool's own icon, as the tool draws it, plus the interpreter's short name as text.
    return WidgetState(interpreter.description, interpreter.title, true).apply {
      icon = interpreter.icon.icon()
    }
  }

  /**
   * (Re)loads the widget data for [key]. The current interpreter — all the status bar needs to render — is fetched
   * first and shown immediately, and the slower popup data loads afterwards. The previous value stays visible until the
   * new one arrives, so neither the initial load nor a refresh ever flashes "No interpreter".
   */
  private fun refresh(key: String) {
    if (!loading.add(key)) return // already in flight
    scope.launch {
      // Read the generation before fetching, so a model change *during* the fetch leaves the result marked stale
      // rather than passing for current.
      val stamp = modelStamp()
      val projectId = project.projectId()
      val prev = cache[key]
      fun publish(entry: Cached) {
        cache[key] = entry
        if (popupTreeKey == key) dropPopupTree() // the tree was built from what we just replaced
        update()
      }
      try {
        val interpreter = evoRpcOrNull { requestEvoCurrentInterpreter(projectId, key) }
        publish(Cached(stamp, interpreter, prev?.nodes.orEmpty(), prev?.associated.orEmpty(),
                       prev?.shortcuts.orEmpty(), prev?.shortcutsAt ?: 0))

        val nodes = evoRpcOrNull { requestEvoNodes(projectId, key) }.orEmpty()
        val associated = evoRpcOrNull { requestEvoAssociatedInterpreters(projectId, key) }.orEmpty()
        // The "Shortcuts" autoconfigure suggestions are only shown (and only worth computing) when there is no interpreter.
        val shortcuts = if (interpreter == null) evoRpcOrNull { requestEvoShortcuts(projectId, key) }.orEmpty() else emptyList()
        publish(Cached(stamp, interpreter, nodes, associated, shortcuts, System.currentTimeMillis()))
      }
      finally {
        loading.remove(key)
        update()
      }
    }
  }

  /** Guards against stacking concurrent node re-probes (see [refreshNodes]). */
  @Volatile
  private var refreshingNodes: Boolean = false

  /**
   * Re-probes just the available tool nodes for [key] (keeping the shown interpreter and the associated list), so a
   * tool installed since the last scan appears without redoing the heavier current-interpreter and associated-SDK
   * scans of a full [refresh]. If the node set actually changed, the built tree is dropped so the next open rebuilds
   * from the fresh nodes. Skipped while a full refresh or another node re-probe is in flight.
   */
  private fun refreshNodes(key: String) {
    if (key in loading || refreshingNodes) return
    refreshingNodes = true
    scope.launch {
      try {
        val nodes = evoRpcOrNull { requestEvoNodes(project.projectId(), key) }.orEmpty()
        val base = cache[key] ?: return@launch
        // Compare by node ids (stable identity) — a newly available or removed tool changes this set; icon/label
        // identity is irrelevant and IconId equality is not guaranteed across fetches.
        if (base.nodes.map { it.id } == nodes.map { it.id }) return@launch
        cache[key] = base.copy(nodes = nodes)
        if (popupTreeKey == key) dropPopupTree()
        update()
      }
      finally {
        refreshingNodes = false
      }
    }
  }

  /** Guards against stacking concurrent shortcut re-reads (see [refreshShortcuts]). */
  @Volatile
  private var refreshingShortcuts: Boolean = false

  /**
   * Re-reads the "Shortcuts" suggestions for [key] once their [SHORTCUTS_TTL_MS] window has run out, keeping everything
   * else the entry holds. The read stamps the entry whatever it finds, so an unchanged list costs one call per window;
   * only a changed list drops the built tree and repaints, so the popup opens on what the IDE would suggest now.
   *
   * Skipped while a full [refresh] or another re-read is in flight.
   */
  private fun refreshShortcuts(key: String) {
    if (key in loading || refreshingShortcuts) return
    refreshingShortcuts = true
    scope.launch {
      try {
        val shortcuts = evoRpcOrNull { requestEvoShortcuts(project.projectId(), key) }.orEmpty()
        val base = cache[key] ?: return@launch
        cache[key] = base.copy(shortcuts = shortcuts, shortcutsAt = System.currentTimeMillis())
        // Compare by what a row says and what it does: an IconId is not guaranteed to be equal across fetches, so the
        // DTOs themselves cannot answer this.
        if (base.shortcuts.map { it.title to it.ref } == shortcuts.map { it.title to it.ref }) return@launch
        if (popupTreeKey == key) dropPopupTree()
        update()
      }
      finally {
        refreshingShortcuts = false
      }
    }
  }

  override fun isEnabledForFile(file: VirtualFile?): Boolean = true

  override fun registerCustomListeners(connection: MessageBusConnection) {
    connection.subscribe(ModuleRootListener.TOPIC, object : ModuleRootListener {
      override fun rootsChanged(event: ModuleRootEvent) {
        // Every entry is now behind the project-model generation, so the next getWidgetState refetches the target in
        // front of the user while still showing what it has. Nothing needs evicting here: a key can only be retired by
        // a new Structure, and that arrives on its own.
        update()
      }
    })
  }

  override fun createPopup(context: DataContext): ListPopup? {
    if (configuring) return null // no popup while a configuration is in progress
    // The popup belongs to the target the widget is currently showing. It is re-resolved against the current structure
    // rather than trusted from the last render: a change between that render and this click may have retired the key,
    // and opening it would fail every row with "Python project not found".
    val structure = this.structure ?: return null
    val target = shownKey?.let { structure[it] } ?: return null
    val cached = cache[target.key] ?: return null
    // Reuse the tree only for the target it was built from, and only within the window measured from the last close,
    // so a quick reopen after a mis-click doesn't rescan; otherwise rebuild. The window restarts on each close.
    val reusable = popupTree?.takeIf { popupTreeKey == target.key && System.currentTimeMillis() - popupClosedAt < popupTreeTtlMs() }
    // Outside the reuse window, also re-probe the available tools so one installed since the last scan (e.g. via
    // Settings | Python | Tools) shows up — otherwise the node list would stay cached for the widget's whole life.
    // The re-probe is async (takes effect from the next open) and availability is backed by PyExecutableCache, so a
    // warm cache makes it near-instant; it only does real work after an install invalidated that cache.
    if (reusable == null) refreshNodes(target.key)
    PyEvoWidgetCollector.popupOpened(project, hasInterpreter = cached.current != null, toolCount = cached.nodes.size)
    val factory = EvoPySdkSwitchPopupFactory(project, target.key, target.name, structure.workspaceRootName(target),
                                             cached.current, cached.nodes, cached.associated, cached.shortcuts, scope,
                                             reopenPopup = ::reopenPopup)
    val tree = reusable ?: factory.buildTree(context).also { popupTree = it; popupTreeKey = target.key }
    return factory.createPopup(tree, context) { popupClosedAt = System.currentTimeMillis() }
  }

  /**
   * Shows the popup again, for a row that changed the list it is showing — the "More Tools" row.
   *
   * A popup is laid out and placed once, so a list that gained rows has to be reopened rather than grown underneath the
   * user. Repeats what `EditorBasedStatusBarPopup.showPopup` does, because that method is private and takes the
   * `MouseEvent` of a real click: the popup's own preferred height, anchored so its bottom sits on the widget.
   *
   * [createPopup] reuses the tree the closing popup was built from — the reuse window is measured from that close, which
   * has just happened — so this costs no rescan and each tool node keeps whatever it had already loaded.
   */
  private fun reopenPopup() {
    val popup = createPopup(context) ?: return
    popup.show(RelativePoint(component, Point(0, -popup.content.preferredSize.height)))
    Disposer.register(this, popup)
  }

  override fun ID(): String = ID

  override fun createInstance(project: Project): StatusBarWidget = EvoPySdkStatusBarWidget(project, scope)

  /**
   * The `PyProject` the widget speaks for: the one owning [file], falling back to the project's own.
   *
   * A file that belongs to a module speaks for that module's `PyProject` and for nothing else — so in a mixed project a
   * Java file leaves the widget hidden rather than borrowing an unrelated interpreter.
   *
   * The fallback covers a file that belongs to no module — one dragged in from outside the project, a scratch — and the
   * case of no file being focused at all. Without it the widget vanished until a module's file was focused again, and
   * the interpreter could be neither seen nor switched (PY-90708). It applies whenever the project *is* a Python
   * project, i.e. whenever a `PyProject` is rooted at the project's base dir: in PyCharm that is always (a plain Python
   * module is kept at the project root even with no `pyproject.toml` declaring one), and in IDEA exactly when the
   * project root really is Python — which is what the widget used to approximate with a PyCharm-only check.
   */
  private fun targetFor(file: VirtualFile?): EvoPyProjectDto? {
    val structure = this.structure ?: return null
    val module = file?.let { findModule(it) }
    return if (module != null) structure.of(module) else structure.main
  }

  private fun findModule(file: VirtualFile): Module? =
    ModuleManager.getInstance(project).modules.firstOrNull { it.moduleContentScope.contains(file) }
}
