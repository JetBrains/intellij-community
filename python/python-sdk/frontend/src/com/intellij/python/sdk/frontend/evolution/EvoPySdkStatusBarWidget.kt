package com.intellij.python.sdk.frontend.evolution

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.icons.icon
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.components.service
import com.intellij.openapi.wm.impl.status.EditorBasedStatusBarPopup
import com.intellij.platform.project.projectId
import com.intellij.python.sdk.common.evolution.EvoLeafDto
import com.intellij.python.sdk.common.evolution.EvoNodeDto
import com.intellij.python.sdk.common.evolution.PyEvoRegistry
import com.intellij.python.sdk.common.evolution.PyInterpreterDto
import com.intellij.python.sdk.common.evolution.requestEvoCurrentInterpreter
import com.intellij.python.sdk.common.evolution.requestEvoNodes
import com.intellij.python.sdk.common.evolution.requestEvoShortcuts
import com.intellij.python.sdk.common.evolution.requestEvoAssociatedInterpreters
import com.intellij.python.sdk.common.evolution.requestEvoSdkConfigurationInProgress
import com.intellij.python.sdk.frontend.PySdkFrontendBundle
import com.intellij.python.sdk.frontend.evolution.components.EvoTreeStaticNodeElement
import com.intellij.ui.AnimatedIcon
import com.intellij.util.IconUtil
import com.intellij.util.messages.MessageBusConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.swing.Icon

private const val ID: String = "EvoPySdkStatusBarWidget"

/** How long (ms) after the popup is closed a re-open still reuses the built tree instead of rebuilding (rescanning). */
private fun popupTreeTtlMs(): Long = PyEvoRegistry.popupTreeCacheSeconds.toLong() * 1000

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
  /** Current Eel interpreter (for display) + popup data (nodes, associated interpreters, shortcuts), fetched asynchronously over RPC. */
  private data class Cached(
    val moduleName: String,
    val current: PyInterpreterDto?,
    val nodes: List<EvoNodeDto>,
    val associated: List<PyInterpreterDto>,
    /** "Shortcuts" rows (autoconfigure suggestions), fetched only when there is no current interpreter. */
    val shortcuts: List<EvoLeafDto>,
  )

  @Volatile
  private var cached: Cached? = null

  /** The module a load is currently in flight for, or `null` when idle. Guards against redundant re-fetches. */
  @Volatile
  private var loadingModule: String? = null

  /**
   * The popup tree built from the current [cached] data, reused when the widget is re-opened within the reuse window
   * (see [popupTreeTtlMs]) of being closed — so a mis-click close-and-reopen does not re-scan every tool. Rebuilt
   * once the window elapses (to pick up newly created environments) or immediately when [cached] changes. A rebuild
   * mints a fresh trace root (see `EvoPySdkSwitchPopupFactory.buildTree`).
   */
  @Volatile
  private var popupTree: EvoTreeStaticNodeElement? = null

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
  private fun configuringIcon(): Icon {
    val nodeId = project.service<EvoConfiguringTracker>().nodeId
    val toolIcon = nodeId?.let { id -> cached?.nodes?.firstOrNull { it.id == id }?.icon?.icon() }
    return AnimatedIcon.Fading(toolIcon ?: AllIcons.Language.Python)
  }

  override fun getWidgetState(file: VirtualFile?): WidgetState {
    if (configuring) {
      // Keep the widget enabled so its dynamic (self-animating) icon is painted directly — a disabled widget would
      // paint a static cached grayscale copy instead. The popup is blocked separately in createPopup while configuring.
      return WidgetState(
        PySdkFrontendBundle.message("evo.sdk.status.bar.configuring.description"),
        PySdkFrontendBundle.message("evo.sdk.status.bar.configuring.title"),
        true,
      ).apply { icon = configuringIcon() }
    }
    val module = file?.let { findModule(it) } ?: project.modules.firstOrNull() ?: return WidgetState.HIDDEN
    val moduleName = module.name

    val current = cached
    if (current == null || current.moduleName != moduleName) {
      if (loadingModule != moduleName) refresh(moduleName)
      // We have no data for this module yet — show a neutral animated "loading" state, not "No interpreter".
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
   * (Re)loads the widget data. The current interpreter — all the status bar needs to render — is fetched first
   * and shown immediately; the slower popup data (tool nodes require executable probing, target SDKs a full
   * scan) loads afterwards. The previous value stays visible until the new one arrives, so neither the initial
   * load nor a refresh ever flashes "No interpreter".
   */
  private fun refresh(moduleName: String) {
    loadingModule = moduleName
    scope.launch {
      val projectId = project.projectId()
      val interpreter = runCatching { requestEvoCurrentInterpreter(projectId, moduleName) }.getOrNull()
      val prev = cached?.takeIf { it.moduleName == moduleName }
      cached = Cached(moduleName, interpreter, prev?.nodes.orEmpty(), prev?.associated.orEmpty(), prev?.shortcuts.orEmpty())
      popupTree = null
      update()

      val nodes = runCatching { requestEvoNodes(projectId, moduleName) }.getOrNull().orEmpty()
      val associated = runCatching { requestEvoAssociatedInterpreters(projectId, moduleName) }.getOrNull().orEmpty()
      // The "Shortcuts" autoconfigure suggestions are only shown (and only worth computing) when there is no interpreter.
      val shortcuts = if (interpreter == null) runCatching { requestEvoShortcuts(projectId, moduleName) }.getOrNull().orEmpty() else emptyList()
      cached = Cached(moduleName, interpreter, nodes, associated, shortcuts)
      popupTree = null
      loadingModule = null
      update()
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
    if (loadingModule == moduleName || refreshingNodes) return
    refreshingNodes = true
    scope.launch {
      try {
        val nodes = runCatching { requestEvoNodes(project.projectId(), moduleName) }.getOrNull().orEmpty()
        val base = cached?.takeIf { it.moduleName == moduleName } ?: return@launch
        // Compare by node ids (stable identity) — a newly available or removed tool changes this set; icon/label
        // identity is irrelevant and IconId equality is not guaranteed across fetches.
        if (base.nodes.map { it.id } == nodes.map { it.id }) return@launch
        cached = base.copy(nodes = nodes)
        popupTree = null
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
        // The interpreter may have changed — re-fetch, but keep the current value visible (no blank/blink).
        // Skip while a load is already in flight; the trailing rootsChanged after startup settles refreshes it.
        val moduleName = cached?.moduleName ?: return
        if (loadingModule != moduleName) refresh(moduleName)
      }
    })
  }

  override fun createPopup(context: DataContext): ListPopup? {
    if (configuring) return null // no popup while a configuration is in progress
    val current = cached ?: return null
    // Reuse the tree only within the window measured from the last close, so a quick reopen after a mis-click
    // doesn't rescan; once it elapses (or the data changed) rebuild. The window restarts each time the popup closes.
    val reusable = popupTree?.takeIf { System.currentTimeMillis() - popupClosedAt < popupTreeTtlMs() }
    // Outside the reuse window, also re-probe the available tools so one installed since the last scan (e.g. via
    // Settings | Python | Tools) shows up — otherwise the node list would stay cached for the widget's whole life.
    // The re-probe is async (takes effect from the next open) and availability is backed by PyExecutableCache, so a
    // warm cache makes it near-instant; it only does real work after an install invalidated that cache.
    if (reusable == null) refreshNodes(current.moduleName)
    val factory = EvoPySdkSwitchPopupFactory(project, current.moduleName, current.current, current.nodes, current.associated, current.shortcuts, scope)
    val tree = reusable ?: factory.buildTree().also { popupTree = it }
    return factory.createPopup(tree, context) { popupClosedAt = System.currentTimeMillis() }
  }

  override fun ID(): String = ID

  override fun createInstance(project: Project): StatusBarWidget = EvoPySdkStatusBarWidget(project, scope)

  private fun findModule(file: VirtualFile): Module? =
    ModuleManager.getInstance(project).modules.firstOrNull { it.moduleContentScope.contains(file) }
}
