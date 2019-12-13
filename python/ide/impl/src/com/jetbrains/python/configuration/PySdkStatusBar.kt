// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.configuration

import com.intellij.ProjectTopics
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetProvider
import com.intellij.openapi.wm.impl.status.EditorBasedStatusBarPopup
import com.intellij.psi.codeStyle.statusbar.CodeStyleStatusBarWidget
import com.intellij.util.text.trimMiddle
import com.jetbrains.python.PythonIdeLanguageCustomization
import com.jetbrains.python.sdk.PySdkPopupFactory
import com.jetbrains.python.sdk.PySdkPopupFactory.Companion.descriptionInPopup
import com.jetbrains.python.sdk.PySdkPopupFactory.Companion.nameInPopup
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.sdk.noInterpreterMarker

class PySdkStatusBarWidgetProvider : StatusBarWidgetProvider {
  override fun getWidget(project: Project): StatusBarWidget? =
    if (PythonIdeLanguageCustomization.isMainlyPythonIde()) PySdkStatusBar(project) else null

  override fun getAnchor(): String = StatusBar.Anchors.after(CodeStyleStatusBarWidget.WIDGET_ID)
}

class PySwitchSdkAction : DumbAwareAction("Switch Project Interpreter", null, null) {

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
    if (file == null) return WidgetState.HIDDEN

    module = ModuleUtil.findModuleForFile(file, project) ?: return WidgetState.HIDDEN

    val sdk = PythonSdkUtil.findPythonSdk(module)
    return if (sdk == null) {
      WidgetState("", noInterpreterMarker, true)
    }
    else {
      WidgetState("Current Interpreter: ${descriptionInPopup(sdk)}]", shortenNameInBar(sdk), true)
    }
  }

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

  override fun ID(): String = "PythonInterpreter"

  override fun requiresWritableFile(): Boolean = false

  override fun createInstance(project: Project): StatusBarWidget = PySdkStatusBar(project)

  private fun shortenNameInBar(sdk: Sdk) = nameInPopup(sdk).trimMiddle(50)
}
