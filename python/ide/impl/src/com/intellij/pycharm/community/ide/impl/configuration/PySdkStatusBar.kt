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
import com.jetbrains.python.sdk.noInterpreterMarker
import com.jetbrains.python.sdk.pyInterpreterPresentation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val ID: String = "pythonInterpreterWidget"

internal class PySdkStatusBarWidgetFactory : StatusBarWidgetFactory {
  override fun getId(): String = ID

  override fun getDisplayName(): String = PyBundle.message("configurable.PyActiveSdkModuleConfigurable.python.interpreter.display.name")

  override fun isAvailable(project: Project): Boolean {
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
    return if (sdk == null) {
      WidgetState("", noInterpreterMarker, true)
    }
    else {

      val presentation = sdk.pyInterpreterPresentation()
      WidgetState(PyBundle.message("current.interpreter", presentation.description), presentation.shortName, true).also {
        it.icon = IconUtil.desaturate(presentation.icon)
      }
    }
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
