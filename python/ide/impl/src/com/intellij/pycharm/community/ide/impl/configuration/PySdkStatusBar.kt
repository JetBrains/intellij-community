// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.configuration

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.python.sdk.common.evolution.PyEvoRegistry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.impl.status.EditorBasedStatusBarPopup
import com.intellij.util.messages.MessageBusConnection
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonIdeLanguageCustomization
import com.jetbrains.python.packaging.widget.resolvePythonWidgetContext
import com.jetbrains.python.sdk.PySdkPopupFactory
import com.intellij.util.IconUtil
import com.intellij.ide.ui.icons.icon
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.sdk.backend.asItem
import com.intellij.python.sdk.backend.pythonInterpreterAsync
import com.intellij.python.sdk.common.PyInterpreterItem
import com.jetbrains.python.sdk.noInterpreterMarker
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.Nls
import kotlinx.coroutines.launch

private const val ID: String = "pythonInterpreterWidget"

internal class PySdkStatusBarWidgetFactory : StatusBarWidgetFactory {
  override fun getId(): String = ID

  override fun getDisplayName(): String = PyBundle.message("configurable.PyActiveSdkModuleConfigurable.python.interpreter.display.name")

  override fun isAvailable(project: Project): Boolean {
    // Superseded by the Evo interpreter widget when its registry flag is on.
    if (PyEvoRegistry.isWidgetEnabled) return false
    return PythonIdeLanguageCustomization.isMainlyPythonIde()
  }

  override fun createWidget(project: Project, scope: CoroutineScope): StatusBarWidget = PySdkStatusBar(project, scope)
}

internal class PySwitchSdkAction : DumbAwareAction(PyBundle.message("switch.python.interpreter"), null, null) {
  override fun update(e: AnActionEvent) {
    e.presentation.isVisible = e.getData(CommonDataKeys.VIRTUAL_FILE) != null && e.project != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
    val project = e.project ?: return
    val module = ModuleUtil.findModuleForFile(file, project) ?: return

    val dataContext = e.dataContext
    PySdkPopupFactory(module).createPopup(dataContext).showInBestPositionFor(dataContext)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}

private class PySdkStatusBar(project: Project, scope: CoroutineScope) : EditorBasedStatusBarPopup(project = project,
                                                                                                  isWriteableFileRequired = false,
                                                                                                  scope = scope) {
  private var module: Module? = null

  /**
   * The last interpreter reading, and the SDK it was read for.
   *
   * Kept because [getWidgetState] runs inside a read action and reading an interpreter runs it, which must not happen
   * under the read lock. A miss shows the SDK's own name and asks for the reading in the background.
   */
  @Volatile
  private var lastRead: Pair<Sdk, PyInterpreterItem>? = null

  override fun install(statusBar: StatusBar) {
    super.install(statusBar)
    // statusBar.currentEditor resolves asynchronously via serviceAsync chain (with null as initial value);
    // subscribe to it so the widget re-evaluates once the editor becomes available
    scope.launch {
      statusBar.currentEditor.collect { update() }
    }
  }

  override fun getWidgetState(file: VirtualFile?): WidgetState {
    val (module, sdk) = resolvePythonWidgetContext(project, file) ?: return WidgetState.HIDDEN
    this.module = module
    if (sdk == null) return WidgetState("", noInterpreterMarker, true)

    val item = lastRead?.takeIf { it.first == sdk }?.second
    if (item == null) {
      scope.launch {
        lastRead = sdk to sdk.pythonInterpreterAsync().asItem()
        update()
      }
      return WidgetState(PyBundle.message("current.interpreter", sdk.homePath.orEmpty()), sdk.name, true)
    }

    return WidgetState(tooltipFor(item), item.shortName, true).also {
      it.icon = IconUtil.desaturate(item.icon.icon())
    }
  }

  /**
   * The widget tooltip: the interpreter path, and why it is flagged when it is.
   *
   * Until PY-91967 the reason reached `idea.log` only, so a crossed icon here said nothing about itself.
   */
  private fun tooltipFor(item: PyInterpreterItem): @Nls String {
    val current = PyBundle.message("current.interpreter", item.description)
    val reason = item.problem?.reason ?: return current
    return "$current\n$reason"
  }

  override fun isEnabledForFile(file: VirtualFile?): Boolean = true

  override fun registerCustomListeners(connection: MessageBusConnection) {
    connection.subscribe(ModuleRootListener.TOPIC, object : ModuleRootListener {
      override fun rootsChanged(event: ModuleRootEvent) = update()
    })
  }

  override fun createPopup(context: DataContext): ListPopup? = module?.let { PySdkPopupFactory(it).createPopup(context) }

  override fun ID(): String = ID

  override fun createInstance(project: Project): StatusBarWidget = PySdkStatusBar(project, scope)
}
