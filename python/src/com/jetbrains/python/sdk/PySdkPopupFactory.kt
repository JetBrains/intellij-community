// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk

import com.intellij.ide.DataManager
import com.intellij.ide.ui.icons.icon
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.EDT
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
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.python.sdk.backend.asInterpreterRef
import com.intellij.python.sdk.common.PyInterpreterItem
import com.intellij.util.ui.SwingHelper
import com.intellij.util.ui.launchOnShow
import com.jetbrains.python.PyBundle
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import com.intellij.ui.popup.ActionPopupOptions
import com.intellij.ui.popup.ActionPopupStep
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.ui.popup.list.ListPopupModel
import com.jetbrains.python.configuration.observeSdkConfigurationInProgress
import com.jetbrains.python.inspections.interpreter.InterpreterSettingsQuickFix
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory
import com.jetbrains.python.run.codeCouldProbablyBeRunWithConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.util.function.Supplier

class PySdkPopupFactory(val module: Module) {

  companion object {
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

    fun buildContent(interpreters: Map<PyRenderedSdkType, List<PyInterpreterItem>>): Pair<List<PopupFactoryImpl.ActionItem>, AnAction> {
      val (group, addInterpreterGroup) = buildInterpreterActionGroup(interpreters)
      val items = ActionPopupStep.createActionItems(group, asyncContext, ActionPlaces.POPUP, PresentationFactory(), options)
      return items to addInterpreterGroup
    }

    // The interpreter rows arrive from `loadInterpreters` below. Deciding whether to flag one runs it, which must not
    // happen on the EDT, so the popup opens with the rows that need no interpreter and fills the rest in.
    var content = buildContent(emptyMap())
    val step = object : ActionPopupStep(content.first, title, Supplier { asyncContext }, ActionPlaces.POPUP, PresentationFactory(), options) {
      override fun getValues(): List<PopupFactoryImpl.ActionItem> = content.first

      override fun isSelectable(value: PopupFactoryImpl.ActionItem): Boolean =
        if (value.action === content.second) !module.project.isSdkConfigurationInProgress.value
        else super.isSelectable(value)
    }
    val popup = ListPopupImpl(module.project, null, step, null).apply { setHandleAutoSelectionBeforeShow(true) }

    fun rerender() = (popup.list.model as? ListPopupModel<*>)?.syncModel()

    // Replays its one value, so the collector below loads the rows as soon as the popup shows, and again whenever a
    // configuration finishes and a newly created interpreter has to appear.
    val reload = MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }
    popup.content.launchOnShow("PySdkPopupFactory.interpreters") {
      reload.collect {
        val interpreters = withContext(Dispatchers.IO) {
          module.project.getAssignablePythonSdks(module).groupInterpreterItemsByTypes(module)
        }
        withContext(Dispatchers.EDT) {
          content = buildContent(interpreters)
          rerender()
        }
      }
    }

    observeSdkConfigurationInProgress(
      module.project, popup.content,
      { rerender() },
      { reload.tryEmit(Unit) },
    )
    return popup
  }

  /**
   * Builds the interpreter widget popup group: the assignable interpreters, the "Add New Interpreter"
   * submenu, and the "Interpreter Settings" / "Manage Packages" actions. Returns the group together
   * with its "Add New Interpreter" sub-group so the step can recognize and grey it out.
   */
  private fun buildInterpreterActionGroup(interpreters: Map<PyRenderedSdkType, List<PyInterpreterItem>>): Pair<DefaultActionGroup, AnAction> {
    val group = DefaultActionGroup()
    addSwitchInterpreterActions(group, interpreters)

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

  /**
   * Adds [interpreters] to [group] as [SwitchToSdkAction]s, grouped by [PyRenderedSdkType] with separators.
   *
   * Each row needs its interpreter's SDK as well as its item: to tell two interpreters of the same kind and path
   * apart, to judge whether a target can run the module's code, and to assign it. An item whose interpreter is gone
   * is dropped.
   */
  private fun addSwitchInterpreterActions(group: DefaultActionGroup, interpreters: Map<PyRenderedSdkType, List<PyInterpreterItem>>) {
    val targetModuleSitsOn = PythonInterpreterTargetEnvironmentFactory.getTargetModuleResidesOn(module)
    PyRenderedSdkType.entries.forEachIndexed { index, type ->
      if (index != 0) group.addSeparator()
      val rows = interpreters[type]
                   ?.withSdks()
                   ?.distinctBy { (_, sdk) -> sdk.sdkAdditionalData?.javaClass to sdk.homePath }
                 ?: return@forEachIndexed

      val uniqueRows = if (type == PyRenderedSdkType.REMOTE) {
        rows.filter { (_, sdk) ->
          targetModuleSitsOn == null ||
          targetModuleSitsOn.codeCouldProbablyBeRunWithConfig(sdk.targetAdditionalData?.targetEnvironmentConfiguration)
        }
      }
      else {
        rows
      }

      group.addAll(uniqueRows.map { (item, sdk) -> SwitchToSdkAction(item, sdk) })
    }
    if (interpreters.isNotEmpty()) group.addSeparator()
  }

  /**
   * These rows paired with the SDK each one names, dropping a row whose interpreter is gone.
   *
   * Reads the SDK table once, so a whole list costs one pass rather than a lookup per row. A list is built off the EDT
   * and the actions from it later, so an interpreter can be renamed or removed in between — that is the dropped row.
   */
  private fun List<PyInterpreterItem>.withSdks(): List<Pair<PyInterpreterItem, Sdk>> {
    val byRef = PythonSdkUtil.getAllSdks().associateBy { it.asInterpreterRef() }
    return mapNotNull { item -> byRef[item.ref]?.let { item to it } }
  }

  private inner class SwitchToSdkAction(item: PyInterpreterItem, val sdk: Sdk) : DumbAwareAction() {

    init {
      with (templatePresentation) {
        setText(item.longName, false)
        description = PyBundle.message("python.sdk.switch.to", item.description)
        icon = item.icon.icon()
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