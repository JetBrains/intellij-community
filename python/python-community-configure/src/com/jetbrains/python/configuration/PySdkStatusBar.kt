// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.configuration

import com.intellij.ProjectTopics
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.util.Condition
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetProvider
import com.intellij.openapi.wm.impl.status.EditorBasedStatusBarPopup
import com.intellij.util.PlatformUtils
import com.intellij.util.text.trimMiddle
import com.jetbrains.python.inspections.PyInterpreterInspection
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.*
import com.jetbrains.python.sdk.add.PyAddSdkDialog
import java.util.function.Consumer

class PySdkStatusBarWidgetProvider : StatusBarWidgetProvider {
  override fun getWidget(project: Project): StatusBarWidget? = if (PlatformUtils.isPyCharm()) PySdkStatusBar(project) else null
}

class PySwitchSdkAction : AnAction("Switch Project Interpreter", null, null) {

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

private class PySdkStatusBar(project: Project) : EditorBasedStatusBarPopup(project) {

  private var module: Module? = null

  override fun getWidgetState(file: VirtualFile?): WidgetState {
    if (file == null) return WidgetState.HIDDEN

    module = ModuleUtil.findModuleForFile(file, project!!) ?: return WidgetState.HIDDEN

    val sdk = PythonSdkType.findPythonSdk(module)
    return if (sdk == null) {
      WidgetState("", noInterpreterMarker, true)
    }
    else {
      WidgetState("Current Interpreter: ${shortenNameAndPath(sdk)}]", shortenNameInBar(sdk), true)
    }
  }

  override fun registerCustomListeners() {
    project!!
      .messageBus
      .connect(this)
      .subscribe(
        ProjectTopics.PROJECT_ROOTS,
        object : ModuleRootListener {
          override fun rootsChanged(event: ModuleRootEvent) = update()
        }
      )
  }

  override fun createPopup(context: DataContext): ListPopup? = module?.let { PySdkPopupFactory(project!!, it).createPopup(context) }

  override fun ID(): String = "PythonInterpreter"

  override fun requiresWritableFile(): Boolean = false

  override fun createInstance(project: Project): StatusBarWidget = PySdkStatusBar(project)

  private fun shortenNameInBar(sdk: Sdk) = name(sdk).trimMiddle(50)
}

private class PySdkPopupFactory(val project: Project, val module: Module) {

  fun createPopup(context: DataContext): ListPopup? {
    val group = DefaultActionGroup()

    val moduleSdksByTypes = groupModuleSdksByTypes(PyConfigurableInterpreterList.getInstance(project).getAllPythonSdks(project), module) {
      PythonSdkType.isInvalid(it) ||
      PythonSdkType.hasInvalidRemoteCredentials(it) ||
      PythonSdkType.isIncompleteRemote(it) ||
      !LanguageLevel.SUPPORTED_LEVELS.contains(PythonSdkType.getLanguageLevelForSdk(it))
    }

    PyRenderedSdkType.values().forEachIndexed { index, type ->
      if (type in moduleSdksByTypes) {
        if (index != 0) group.addSeparator()
        group.addAll(moduleSdksByTypes.getValue(type).map { SwitchToSdkAction(it) })
      }
    }

    if (moduleSdksByTypes.isNotEmpty()) group.addSeparator()
    group.add(InterpreterSettingsAction())
    group.add(AddInterpreterAction())

    val currentSdk = PythonSdkType.findPythonSdk(module)
    return JBPopupFactory.getInstance().createActionGroupPopup(
      "Project Interpreter",
      group,
      context,
      JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
      false,
      null,
      -1,
      Condition { it is SwitchToSdkAction && it.sdk == currentSdk },
      null
    )
  }

  private fun shortenNameInPopup(sdk: Sdk) = name(sdk).trimMiddle(100)

  private inner class SwitchToSdkAction(val sdk: Sdk) : AnAction() {

    init {
      val presentation = templatePresentation
      presentation.setText(shortenNameInPopup(sdk), false)
      presentation.description = "Switch to ${shortenNameAndPath(sdk)}]"
      presentation.icon = icon(sdk)
    }

    override fun actionPerformed(e: AnActionEvent) {
      project.pythonSdk = sdk
      module.pythonSdk = sdk
    }
  }

  private inner class InterpreterSettingsAction : AnAction("Interpreter Settings...") {
    override fun actionPerformed(e: AnActionEvent) = PyInterpreterInspection.ConfigureInterpreterFix.showProjectInterpreterDialog(project)
  }

  private inner class AddInterpreterAction : AnAction("Add Interpreter...") {

    override fun actionPerformed(e: AnActionEvent) {
      val model = PyConfigurableInterpreterList.getInstance(project).model

      PyAddSdkDialog.show(
        project,
        module,
        model.sdks.asList(),
        Consumer {
          if (it != null && model.findSdk(it.name) == null) {
            model.addSdk(it)
            model.apply()
          }
        }
      )
    }
  }
}

private fun shortenNameAndPath(sdk: Sdk) = "${name(sdk)} [${path(sdk)}]".trimMiddle(150)

private fun name(sdk: Sdk): String {
  val (_, primary, secondary) = com.jetbrains.python.sdk.name(sdk)
  return if (secondary == null) primary else "$primary [$secondary]"
}