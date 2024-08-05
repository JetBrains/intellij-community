// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.configuration

import com.intellij.openapi.actionSystem.ActionUpdateThread
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
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.impl.status.EditorBasedStatusBarPopup
import com.intellij.util.PlatformUtils
import com.intellij.util.messages.MessageBusConnection
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonIdeLanguageCustomization
import com.jetbrains.python.sdk.PySdkPopupFactory
import com.jetbrains.python.sdk.PySdkPopupFactory.Companion.descriptionInPopup
import com.jetbrains.python.sdk.PySdkPopupFactory.Companion.shortenNameInPopup
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.sdk.noInterpreterMarker
import kotlinx.coroutines.CoroutineScope

private const val ID: String = "pythonInterpreterWidget"

fun isDataSpellInterpreterWidgetEnabled(): Boolean = PlatformUtils.isDataSpell() && Registry.`is`("dataspell.interpreter.widget")

private class PySdkStatusBarWidgetFactory : StatusBarWidgetFactory {
  override fun getId(): String = ID

  override fun getDisplayName(): String = PyBundle.message("configurable.PyActiveSdkModuleConfigurable.python.interpreter.display.name")

  override fun isAvailable(project: Project): Boolean {
    return PythonIdeLanguageCustomization.isMainlyPythonIde() && !isDataSpellInterpreterWidgetEnabled()
  }

  override fun createWidget(project: Project, scope: CoroutineScope): StatusBarWidget = PySdkStatusBar(project, scope)
}

private class PySwitchSdkAction : DumbAwareAction(PyBundle.message("switch.python.interpreter"), null, null) {
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

  override fun registerCustomListeners(connection: MessageBusConnection) {
    connection.subscribe(ModuleRootListener.TOPIC, object : ModuleRootListener {
      override fun rootsChanged(event: ModuleRootEvent) = update()
    })
  }

  override fun createPopup(context: DataContext): ListPopup? = module?.let { PySdkPopupFactory(it).createPopup(context) }

  override fun ID(): String = ID

  override fun createInstance(project: Project): StatusBarWidget = PySdkStatusBar(project, scope)

  private fun findModule(file: VirtualFile?): Module? {
    if (file != null) {
      val module = ModuleUtil.findModuleForFile(file, project)
      if (module != null) return module
    }

    return ModuleManager.getInstance(project).modules.singleOrNull()
  }
}
