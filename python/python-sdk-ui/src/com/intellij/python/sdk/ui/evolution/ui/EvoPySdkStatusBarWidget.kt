package com.intellij.python.sdk.ui.evolution.ui

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.impl.status.EditorBasedStatusBarPopup
import com.intellij.python.sdk.ui.PySdkUiBundle
import com.intellij.python.sdk.ui.evolution.sdk.EvoModuleSdk
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.text.trimMiddle
import kotlinx.coroutines.CoroutineScope

private const val ID: String = "EvoPySdkStatusBarWidget"


private class PySdkStatusBarWidgetFactory : StatusBarWidgetFactory {
  override fun getId(): String = ID

  override fun getDisplayName(): String = PySdkUiBundle.message("evo.sdk.status.bar.widget.factory.display.name")

  override fun isAvailable(project: Project) = Registry.`is`("python.evolution.widget")

  override fun createWidget(project: Project, scope: CoroutineScope): StatusBarWidget = EvoPySdkStatusBarWidget(project, scope)
}


private class EvoPySdkStatusBarWidget(project: Project, scope: CoroutineScope) : EditorBasedStatusBarPopup(
  project = project,
  isWriteableFileRequired = false,
  scope = scope
) {
  private var currentSdks: Pair<Sdk?, EvoModuleSdk>? = null

  private val isMultiModules: Boolean
    get() = project.modules.size > 1

  private fun EvoModuleSdk.getTitle(): String {
    val sdkTitle = evoSdk?.getTitle() ?: PySdkUiBundle.message("evo.sdk.undefined.title")
    //val title = if (isMultiModules) "${sdkTitle.trimMiddle(30)} [${module.name}]" else sdkTitle
    return sdkTitle.trimMiddle(50)
  }

  private fun EvoModuleSdk.getDescription(): String {
    val sdkDescription = evoSdk?.getDescription() ?: PySdkUiBundle.message("evo.sdk.undefined.description")
    //val description = if (isMultiModules) "$sdkDescription [${module.name}]" else sdkDescription
    return sdkDescription.trimMiddle(150)
  }

  inner class EvoWidgetBuilder(val evoModuleSdk: EvoModuleSdk) {
    fun buildWidgetState(): WidgetState {
      val text = evoModuleSdk.getTitle().trimMiddle(50)
      val toolTip = evoModuleSdk.getDescription().trimMiddle(150)
      val state = WidgetState(
        toolTip = toolTip,
        text = text,
        isActionEnabled = true
      ).apply { icon = evoModuleSdk.getIcon() }

      return state
    }
  }

  override fun getWidgetState(file: VirtualFile?): WidgetState {
    val module = file?.let { findModule(it) } ?: project.modules.firstOrNull()
    val state = when {
      module == null -> WidgetState.HIDDEN
      else -> {
        val (_, evoModuleSdk) = EvoModuleSdk.Companion.findForModule(module).also { this.currentSdks = it }
        val builder = EvoWidgetBuilder(evoModuleSdk)
        builder.buildWidgetState()
      }
    }
    return state
  }

  override fun isEnabledForFile(file: VirtualFile?): Boolean = true

  override fun registerCustomListeners(connection: MessageBusConnection) {
    connection.subscribe(ModuleRootListener.TOPIC, object : ModuleRootListener {
      override fun rootsChanged(event: ModuleRootEvent) = update()
    })
  }

  override fun createPopup(context: DataContext): ListPopup? {
    val (pySdk, evoSdk) = this.currentSdks ?: return null
    //return EvoPySdkDynamicPopupFactory(pySdk,evoSdk, isMultiModules, scope, this).createPopup(context)
    //return EvoPySdkPopupFactory(pySdk,evoSdk, isMultiModules).createPopup(context)
    return EvoPySdkSwitchPopupFactory(pySdk, evoSdk, isMultiModules, scope).createPopup(context)
  }

  override fun ID() = ID

  override fun createInstance(project: Project) = EvoPySdkStatusBarWidget(project, scope)

  private fun findModule(file: VirtualFile): Module? {
    val module = ModuleManager.getInstance(project).modules.firstOrNull { it.moduleContentScope.contains(file) }

    return module
  }
}
