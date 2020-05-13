// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.util.Condition
import com.intellij.util.text.trimMiddle
import com.intellij.util.ui.SwingHelper
import com.jetbrains.python.PyBundle
import com.jetbrains.python.configuration.PyConfigurableInterpreterList
import com.jetbrains.python.inspections.PyInterpreterInspection
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.add.PyAddSdkDialog
import java.util.function.Consumer

class PySdkPopupFactory(val project: Project, val module: Module) {

  companion object {
    private fun nameInPopup(sdk: Sdk): String {
      val (_, primary, secondary) = name(sdk)
      return if (secondary == null) primary else "$primary [$secondary]"
    }

    fun shortenNameInPopup(sdk: Sdk, maxLength: Int) = nameInPopup(sdk).trimMiddle(maxLength)

    fun descriptionInPopup(sdk: Sdk) = "${nameInPopup(sdk)} [${path(sdk)}]".trimMiddle(150)

    fun createAndShow(project: Project, module: Module) {
      DataManager.getInstance()
        .dataContextFromFocusAsync
        .onSuccess {
          val popup = PySdkPopupFactory(project, module).createPopup(it) ?: return@onSuccess

          val component = SwingHelper.getComponentFromRecentMouseEvent()
          if (component != null) {
            popup.showUnderneathOf(component)
          }
          else {
            popup.showInBestPositionFor(it)
          }
        }
    }
  }

  fun createPopup(context: DataContext): ListPopup? {
    val group = DefaultActionGroup()

    val interpreterList = PyConfigurableInterpreterList.getInstance(project)
    val moduleSdksByTypes = groupModuleSdksByTypes(interpreterList.getAllPythonSdks(project), module) {
      PythonSdkUtil.isInvalid(it) ||
      PythonSdkType.hasInvalidRemoteCredentials(it) ||
      PythonSdkType.isIncompleteRemote(it) ||
      !LanguageLevel.SUPPORTED_LEVELS.contains(PythonSdkType.getLanguageLevelForSdk(it))
    }

    val model = interpreterList.model
    PyRenderedSdkType.values().forEachIndexed { index, type ->
      if (type in moduleSdksByTypes) {
        if (index != 0) group.addSeparator()
        group.addAll(moduleSdksByTypes.getValue(type).mapNotNull { model.findSdk(it) }.map { SwitchToSdkAction(it) })
      }
    }

    if (moduleSdksByTypes.isNotEmpty()) group.addSeparator()
    group.add(InterpreterSettingsAction())
    group.add(AddInterpreterAction())

    val currentSdk = module.pythonSdk
    return JBPopupFactory.getInstance().createActionGroupPopup(
      PyBundle.message("python.sdk.popup.title"),
      group,
      context,
      JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
      false,
      null,
      -1,
      Condition { it is SwitchToSdkAction && it.sdk == currentSdk },
      null
    ).apply { setHandleAutoSelectionBeforeShow(true) }
  }

  private fun switchToSdk(sdk: Sdk) {
    (sdk.sdkType as PythonSdkType).setupSdkPaths(sdk)
    project.pythonSdk = sdk
    module.pythonSdk = sdk
  }

  private inner class SwitchToSdkAction(val sdk: Sdk) : DumbAwareAction() {

    init {
      val presentation = templatePresentation
      presentation.setText(shortenNameInPopup(sdk, 100), false)
      presentation.description = PyBundle.message("python.sdk.switch.to", descriptionInPopup(sdk))
      presentation.icon = icon(sdk)
    }

    override fun actionPerformed(e: AnActionEvent) = switchToSdk(sdk)
  }

  private inner class InterpreterSettingsAction : DumbAwareAction(PyBundle.messagePointer("python.sdk.popup.interpreter.settings")) {
    override fun actionPerformed(e: AnActionEvent) {
      PyInterpreterInspection.InterpreterSettingsQuickFix.showPythonInterpreterSettings(project, module)
    }
  }

  private inner class AddInterpreterAction : DumbAwareAction(PyBundle.messagePointer("python.sdk.popup.add.interpreter")) {

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
            switchToSdk(it)
          }
        }
      )
    }
  }
}