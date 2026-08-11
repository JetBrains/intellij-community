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
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.impl.status.EditorBasedStatusBarPopup
import com.intellij.platform.project.projectId
import com.intellij.python.sdk.common.evolution.EvoNodeDto
import com.intellij.python.sdk.common.evolution.EvoSdkDto
import com.intellij.python.sdk.common.evolution.requestEvoCurrentSdk
import com.intellij.python.sdk.common.evolution.requestEvoNodes
import com.intellij.python.sdk.frontend.PySdkFrontendBundle
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.text.trimMiddle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val ID: String = "EvoPySdkStatusBarWidget"

internal class PySdkStatusBarWidgetFactory : StatusBarWidgetFactory {
  override fun getId(): String = ID

  override fun getDisplayName(): String = PySdkFrontendBundle.message("evo.sdk.status.bar.widget.factory.display.name")

  override fun isAvailable(project: Project): Boolean = Registry.`is`("python.evolution.widget")

  override fun createWidget(project: Project, scope: CoroutineScope): StatusBarWidget = EvoPySdkStatusBarWidget(project, scope)
}

private class EvoPySdkStatusBarWidget(project: Project, scope: CoroutineScope) : EditorBasedStatusBarPopup(
  project = project,
  isWriteableFileRequired = false,
  scope = scope,
) {
  /** Current interpreter + popup nodes for [moduleName], fetched asynchronously over RPC (backend-only discovery). */
  private data class Cached(val moduleName: String, val sdk: EvoSdkDto?, val nodes: List<EvoNodeDto>)

  @Volatile
  private var cached: Cached? = null

  @Volatile
  private var loadingModule: String? = null

  override fun getWidgetState(file: VirtualFile?): WidgetState {
    val module = file?.let { findModule(it) } ?: project.modules.firstOrNull() ?: return WidgetState.HIDDEN
    val moduleName = module.name

    val current = cached
    if (current == null || current.moduleName != moduleName) {
      if (loadingModule != moduleName) {
        loadingModule = moduleName
        scope.launch {
          val projectId = project.projectId()
          val dto = runCatching { requestEvoCurrentSdk(projectId, moduleName) }.getOrNull()
          val nodes = runCatching { requestEvoNodes(projectId, moduleName) }.getOrNull().orEmpty()
          cached = Cached(moduleName, dto, nodes)
          loadingModule = null
          update()
        }
      }
      return WidgetState(
        PySdkFrontendBundle.message("evo.sdk.undefined.description"),
        PySdkFrontendBundle.message("evo.sdk.undefined.title"),
        true,
      ).apply { icon = AllIcons.General.BalloonWarning }
    }

    val sdk = current.sdk
    val text = (sdk?.getTitle() ?: PySdkFrontendBundle.message("evo.sdk.undefined.title")).trimMiddle(50)
    val toolTip = (sdk?.getDescription() ?: PySdkFrontendBundle.message("evo.sdk.undefined.description")).trimMiddle(150)
    return WidgetState(toolTip, text, true).apply {
      icon = sdk?.icon?.icon() ?: AllIcons.General.BalloonWarning
    }
  }

  override fun isEnabledForFile(file: VirtualFile?): Boolean = true

  override fun registerCustomListeners(connection: MessageBusConnection) {
    connection.subscribe(ModuleRootListener.TOPIC, object : ModuleRootListener {
      override fun rootsChanged(event: ModuleRootEvent) {
        cached = null
        loadingModule = null
        update()
      }
    })
  }

  override fun createPopup(context: DataContext): ListPopup? {
    val current = cached ?: return null
    return EvoPySdkSwitchPopupFactory(project, current.moduleName, current.sdk, current.nodes, scope).createPopup(context)
  }

  override fun ID(): String = ID

  override fun createInstance(project: Project): StatusBarWidget = EvoPySdkStatusBarWidget(project, scope)

  private fun findModule(file: VirtualFile): Module? =
    ModuleManager.getInstance(project).modules.firstOrNull { it.moduleContentScope.contains(file) }
}
