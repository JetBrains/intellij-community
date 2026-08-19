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
import com.intellij.openapi.wm.impl.status.EditorBasedStatusBarPopup
import com.intellij.platform.project.projectId
import com.intellij.python.sdk.common.evolution.EvoNodeDto
import com.intellij.python.sdk.common.evolution.PyEvoRegistry
import com.intellij.python.sdk.common.evolution.PyInterpreterDto
import com.intellij.python.sdk.common.evolution.requestEvoCurrentInterpreter
import com.intellij.python.sdk.common.evolution.requestEvoNodes
import com.intellij.python.sdk.common.evolution.requestEvoAssociatedInterpreters
import com.intellij.python.sdk.common.evolution.requestEvoSdkConfigurationInProgress
import com.intellij.python.sdk.frontend.PySdkFrontendBundle
import com.intellij.python.sdk.frontend.evolution.components.EvoTreeStaticNodeElement
import com.intellij.util.IconUtil
import com.intellij.util.messages.MessageBusConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.Icon

private const val ID: String = "EvoPySdkStatusBarWidget"

/** How long (ms) after the popup is closed a re-open still reuses the built tree instead of rebuilding (rescanning). */
private fun popupTreeTtlMs(): Long = PyEvoRegistry.popupTreeCacheSeconds.toLong() * 1000

/**
 * A self-animating spinner made from the Python logo (used for the "loading" and "configuring" states): it rotates the
 * logo by a wall-clock-derived angle and schedules its own repaint via the [Component] passed to [paintIcon], so it
 * animates in the status bar (which has no repaint timer) without any external ticker. Painting stops on its own once
 * the widget swaps in another icon.
 */
private object PythonSpinnerIcon : Icon {
  private val base: Icon = AllIcons.Language.Python
  private const val PERIOD_MS: Long = 1000 // one full rotation per second
  private const val FRAME_MS: Long = 60    // ~16 fps

  override fun getIconWidth(): Int = base.iconWidth
  override fun getIconHeight(): Int = base.iconHeight

  override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
    val g2 = g.create() as Graphics2D
    try {
      g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
      val angle = 2.0 * Math.PI * (System.currentTimeMillis() % PERIOD_MS) / PERIOD_MS
      g2.rotate(angle, x + base.iconWidth / 2.0, y + base.iconHeight / 2.0)
      base.paintIcon(c, g2, x, y)
    }
    finally {
      g2.dispose()
    }
    c?.repaint(FRAME_MS)
  }
}

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
  /** Current Eel interpreter (for display) + popup data (nodes, associated interpreters), fetched asynchronously over RPC. */
  private data class Cached(
    val moduleName: String,
    val current: PyInterpreterDto?,
    val nodes: List<EvoNodeDto>,
    val associated: List<PyInterpreterDto>,
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
        update()
      }
    }
  }

  override fun getWidgetState(file: VirtualFile?): WidgetState {
    if (configuring) {
      // Keep the widget enabled so its dynamic (self-animating) icon is painted directly — a disabled widget would
      // paint a static cached grayscale copy instead. The popup is blocked separately in createPopup while configuring.
      return WidgetState(
        PySdkFrontendBundle.message("evo.sdk.status.bar.configuring.description"),
        PySdkFrontendBundle.message("evo.sdk.status.bar.configuring.title"),
        true,
      ).apply { icon = PythonSpinnerIcon }
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
      ).apply { icon = PythonSpinnerIcon }
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
      cached = Cached(moduleName, interpreter, prev?.nodes.orEmpty(), prev?.associated.orEmpty())
      popupTree = null
      update()

      val nodes = runCatching { requestEvoNodes(projectId, moduleName) }.getOrNull().orEmpty()
      val associated = runCatching { requestEvoAssociatedInterpreters(projectId, moduleName) }.getOrNull().orEmpty()
      cached = Cached(moduleName, interpreter, nodes, associated)
      popupTree = null
      loadingModule = null
      update()
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
    val factory = EvoPySdkSwitchPopupFactory(project, current.moduleName, current.current, current.nodes, current.associated, scope)
    // Reuse the tree only within the window measured from the last close, so a quick reopen after a mis-click
    // doesn't rescan; once it elapses (or the data changed) rebuild. The window restarts each time the popup closes.
    val tree = popupTree?.takeIf { System.currentTimeMillis() - popupClosedAt < popupTreeTtlMs() }
               ?: factory.buildTree().also { popupTree = it }
    return factory.createPopup(tree, context) { popupClosedAt = System.currentTimeMillis() }
  }

  override fun ID(): String = ID

  override fun createInstance(project: Project): StatusBarWidget = EvoPySdkStatusBarWidget(project, scope)

  private fun findModule(file: VirtualFile): Module? =
    ModuleManager.getInstance(project).modules.firstOrNull { it.moduleContentScope.contains(file) }
}
