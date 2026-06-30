// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.SlowOperations
import com.intellij.util.ui.SwingHelper
import com.jetbrains.python.PyBundle
import com.intellij.ui.popup.ActionPopupOptions
import com.intellij.ui.popup.ActionPopupStep
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.ui.popup.list.ListPopupModel
import com.jetbrains.python.configuration.observeSdkConfigurationInProgress
import com.jetbrains.python.inspections.interpreter.InterpreterSettingsQuickFix
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory
import com.jetbrains.python.run.codeCouldProbablyBeRunWithConfig
import org.jetbrains.annotations.ApiStatus
import java.util.function.Supplier

class PySdkPopupFactory(val module: Module) {

  companion object {
    @Deprecated("")
    fun shortenNameInPopup(sdk: Sdk, maxLength: Int): @NlsSafe String = sdk.pyInterpreterPresentation().compactName(maxLength, keepPrefix = false)

    @ApiStatus.Internal
    fun createAndShow(module: Module) {
      DataManager.getInstance()
        .dataContextFromFocusAsync
        .onSuccess {
          val popup = PySdkPopupFactory(module).createPopup(it)

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

  /**
   * Creates the Python interpreter widget popup.
   *
   * Built with a custom [ActionPopupStep] rather than [JBPopupFactory.createActionGroupPopup]: the
   * default step snapshots each item's selectable state and the item list at build time and cannot
   * refresh them in place while the popup is open. Following the evolution widget's `EvoActionPopupStep`,
   * this step instead reads live state on each query — `isSelectable` reflects the current
   * SDK-configuration lock for the "Add New Interpreter" submenu, and `getValues` returns the current
   * item list.
   *
   * While the popup is open, [observeSdkConfigurationInProgress] re-renders it on every lock-state
   * change (so "Add New Interpreter" greys out while a configuration runs and re-enables when it
   * finishes) and rebuilds the item list once a configuration finishes (so a newly created interpreter
   * appears), all without reopening — mirroring the interpreter settings panel (PY-88522).
   */
  @ApiStatus.Internal
  fun createPopup(context: DataContext): ListPopup {
    val asyncContext = Utils.createAsyncDataContext(context)
    val title = PyBundle.message("configurable.PyActiveSdkModuleConfigurable.python.interpreter.display.name")
    val currentSdk = module.pythonSdk
    val options = ActionPopupOptions.forAid(
      JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false, -1,
      Condition { it is SwitchToSdkAction && it.sdk == currentSdk },
    )

    fun buildContent(): Pair<List<PopupFactoryImpl.ActionItem>, AnAction> {
      val (group, addInterpreterGroup) = buildInterpreterActionGroup()
      val items = ActionPopupStep.createActionItems(group, asyncContext, ActionPlaces.POPUP, PresentationFactory(), options)
      return items to addInterpreterGroup
    }

    var content = buildContent()
    val step = object : ActionPopupStep(content.first, title, Supplier { asyncContext }, ActionPlaces.POPUP, PresentationFactory(), options) {
      override fun getValues(): List<PopupFactoryImpl.ActionItem> = content.first

      override fun isSelectable(value: PopupFactoryImpl.ActionItem): Boolean =
        if (value.action === content.second) !module.project.isSdkConfigurationInProgress.value
        else super.isSelectable(value)
    }
    val popup = ListPopupImpl(module.project, null, step, null).apply { setHandleAutoSelectionBeforeShow(true) }

    fun rerender() = (popup.list.model as? ListPopupModel<*>)?.syncModel()
    observeSdkConfigurationInProgress(
      module.project, popup.content,
      { rerender() },
      {
        content = buildContent()
        rerender()
      },
    )
    return popup
  }

  /**
   * Builds the interpreter widget popup group: the assignable interpreters, the "Add New Interpreter"
   * submenu, and the "Interpreter Settings" / "Manage Packages" actions. Returns the group together
   * with its "Add New Interpreter" sub-group so the step can recognize and grey it out.
   */
  private fun buildInterpreterActionGroup(): Pair<DefaultActionGroup, AnAction> {
    val group = DefaultActionGroup()
    addSwitchInterpreterActions(group)

    val addInterpreterGroup = DefaultActionGroup(PyBundle.message("python.sdk.action.add.new.interpreter.text"), true)
    addInterpreterGroup.addAll(collectAddInterpreterActions(ModuleOrProject.ModuleAndProject(module)) { })
    ActionManager.getInstance().getAction("Python.NewInterpreter.Extra")?.let { addInterpreterGroup.add(it) }
    group.add(addInterpreterGroup)

    group.addSeparator()
    group.add(InterpreterSettingsAction())
    group.add(object : AnAction(PyBundle.message("python.packaging.interpreter.widget.manage.packages")) {
      override fun actionPerformed(e: AnActionEvent) {
        ToolWindowManager.getInstance(module.project).getToolWindow("Python Packages")?.show()
      }
    })
    return group to addInterpreterGroup
  }

  /** Adds the assignable interpreters to [group] as [SwitchToSdkAction]s, grouped by [PyRenderedSdkType] with separators. */
  private fun addSwitchInterpreterActions(group: DefaultActionGroup) {
    val moduleSdksByTypes = SlowOperations.knownIssue("PY-76167").use {
      groupModuleSdksByTypes(module.project.getAssignablePythonSdks(module), module) {
        !it.isSdkSeemsValid || !LanguageLevel.SUPPORTED_LEVELS.contains(PythonSdkType.getLanguageLevelForSdk(it))
      }
    }
    val targetModuleSitsOn = PythonInterpreterTargetEnvironmentFactory.getTargetModuleResidesOn(module)
    PyRenderedSdkType.entries.forEachIndexed { index, type ->
      if (index != 0) group.addSeparator()
      val sdksByType = moduleSdksByTypes[type]?.distinctBy {
        it.sdkAdditionalData?.javaClass to it.homePath
      } ?: return@forEachIndexed

      val uniqueSdks = if (type == PyRenderedSdkType.REMOTE) {
        sdksByType.filter {
          targetModuleSitsOn == null ||
          targetModuleSitsOn.codeCouldProbablyBeRunWithConfig(it.targetAdditionalData?.targetEnvironmentConfiguration)
        }
      }
      else {
        sdksByType.distinctBy { it.sdkAdditionalData?.javaClass to it.homePath }
      }

      group.addAll(uniqueSdks.map { SwitchToSdkAction(it) })
    }
    if (moduleSdksByTypes.isNotEmpty()) group.addSeparator()
  }

  private inner class SwitchToSdkAction(val sdk: Sdk) : DumbAwareAction() {

    init {
      val presentation = sdk.pyInterpreterPresentation()
      with (templatePresentation) {
        setText(presentation.longName, false)
        description = PyBundle.message("python.sdk.switch.to", presentation.description)
        icon = presentation.icon
      }
    }

    override fun actionPerformed(e: AnActionEvent) = runWithSdkConfigurationLock(module.project) { module.pythonSdk = sdk }
  }

  private inner class InterpreterSettingsAction : DumbAwareAction(PyBundle.messagePointer("python.sdk.popup.interpreter.settings")) {
    override fun actionPerformed(e: AnActionEvent) {
      InterpreterSettingsQuickFix.showPythonInterpreterSettings(module.project, module)
    }
  }
}