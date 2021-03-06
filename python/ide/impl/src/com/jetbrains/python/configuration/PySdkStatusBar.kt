// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.configuration

import com.intellij.ProjectTopics
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.impl.status.EditorBasedStatusBarPopup
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonIdeLanguageCustomization
import com.jetbrains.python.sdk.PySdkPopupFactory
import com.jetbrains.python.sdk.PySdkPopupFactory.Companion.descriptionInPopup
import com.jetbrains.python.sdk.PySdkPopupFactory.Companion.shortenNameInPopup
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.sdk.noInterpreterMarker

private const val pySdkWidgetId: String = "pythonInterpreterWidget"

class PySdkStatusBarWidgetFactory : StatusBarWidgetFactory {

  override fun getId(): String = pySdkWidgetId

  override fun getDisplayName(): String = PyBundle.message("configurable.PyActiveSdkModuleConfigurable.python.interpreter.display.name")

  override fun isAvailable(project: Project): Boolean = PythonIdeLanguageCustomization.isMainlyPythonIde()

  override fun createWidget(project: Project): StatusBarWidget = PySdkStatusBar(project)

  override fun disposeWidget(widget: StatusBarWidget) = Disposer.dispose(widget)

  override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}

class PySwitchSdkAction : DumbAwareAction(PyBundle.message("switch.python.interpreter"), null, null) {

  override fun update(e: AnActionEvent) {
    e.presentation.isVisible = e.getData(CommonDataKeys.VIRTUAL_FILE) != null && e.project != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
    val project = e.project ?: return
    val module = ModuleUtil.findModuleForFile(file, project) ?: return

    val dataContext = e.dataContext
    PySdkPopupFactory(project, module).createPopup(dataContext)?.showInBestPositionFor(dataContext)
  }
}

private class PySdkStatusBar(project: Project) : EditorBasedStatusBarPopup(project, false) {

  private var module: Module? = null

  override fun getWidgetState(file: VirtualFile?): WidgetState {
    module = findModule(file) ?: return WidgetState.HIDDEN

    val sdk = PythonSdkUtil.findPythonSdk(module)
    return if (sdk == null) {
      WidgetState("", noInterpreterMarker, true)
    }
    else {
      WidgetState(PyBundle.message("current.interpreter", descriptionInPopup(sdk)), shortenNameInPopup(sdk, 50), true)
    }
  }

  override fun isEnabledForFile(file: VirtualFile?): Boolean = true

  override fun registerCustomListeners() {
    project
      .messageBus
      .connect(this)
      .subscribe(
        ProjectTopics.PROJECT_ROOTS,
        object : ModuleRootListener {
          override fun rootsChanged(event: ModuleRootEvent) = update()
        }
      )
  }

  override fun createPopup(context: DataContext): ListPopup? = module?.let { PySdkPopupFactory(project, it).createPopup(context) }

  override fun ID(): String = pySdkWidgetId

  override fun createInstance(project: Project): StatusBarWidget = PySdkStatusBar(project)

  private fun findModule(file: VirtualFile?): Module? {
    if (file != null) {
      val module = ModuleUtil.findModuleForFile(file, project)
      if (module != null) return module
    }

    return ModuleManager.getInstance(project).modules.singleOrNull()
  }
}
