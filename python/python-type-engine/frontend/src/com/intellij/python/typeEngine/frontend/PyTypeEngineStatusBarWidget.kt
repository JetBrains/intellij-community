// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.typeEngine.frontend

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.impl.status.EditorBasedStatusBarPopup
import com.intellij.openapi.wm.impl.status.StatusBarUtil
import com.intellij.platform.project.projectId
import com.intellij.psi.PsiManager
import com.intellij.python.typeEngine.common.PyTypeEngineApi
import com.intellij.python.typeEngine.common.PyTypeEngineEvent
import com.intellij.python.typeEngine.common.PyTypeEngineEventRequest
import com.intellij.python.typeEngine.common.PyTypeEngineId
import com.intellij.python.typeEngine.common.PyTypeEngineSelectionRequest
import com.intellij.ui.components.Badge
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.pyi.PyiFileType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

private const val ID: String = "pythonTypeEngineWidget"

/**
 * Factory for creating the Type Engine status bar widget.
 */
internal class PyTypeEngineStatusBarWidgetFactory : StatusBarWidgetFactory {
  override fun getId(): String = ID

  override fun getDisplayName(): String = TypeEngineFrontendBundle.message("display.name")

  override fun isAvailable(project: Project): Boolean {
    return Registry.`is`("pycharm.type.engine", true)
  }

  override fun createWidget(project: Project, scope: CoroutineScope): StatusBarWidget {
    return PyTypeEngineStatusBarWidget(project, scope)
  }
}

/**
 * Status bar widget that displays the current Type Engine selection.
 */
private class PyTypeEngineStatusBarWidget(
  project: Project,
  coroutineScope: CoroutineScope,
) : EditorBasedStatusBarPopup(project = project, isWriteableFileRequired = false, scope = coroutineScope) {
  init {
    scope.launch {
      PyTypeEngineFrontendState.getInstance(project).states().collect {
        tryUpdateWidgetState()
      }
    }
  }

  override fun ID(): String = ID

  override fun createInstance(project: Project): StatusBarWidget = PyTypeEngineStatusBarWidget(project, scope)

  override fun getSelectedFile(): VirtualFile? =
    statusBar?.let(StatusBarUtil::getCurrentFileEditor)?.file ?: super.getSelectedFile()

  override fun getWidgetState(file: VirtualFile?): WidgetState {
    if (!isEnabledForFile(file))
      return WidgetState.HIDDEN
    val state = PyTypeEngineFrontendState.getInstance(project).get()
    if (state.supported.size <= 1)
      return WidgetState.HIDDEN

    val typeEngineName = frontendFor(state.selected)?.presentableName ?: return WidgetState.HIDDEN
    return WidgetState(
      TypeEngineFrontendBundle.message("widget.type.tooltip", typeEngineName),
      TypeEngineFrontendBundle.message("widget.type.text", typeEngineName),
      true
    )
  }

  override fun isEnabledForFile(file: VirtualFile?): Boolean =
    file != null && (file.fileType in setOf(PythonFileType.INSTANCE, PyiFileType.INSTANCE) || file.isPythonNotebook(project))

  override fun createPopup(context: DataContext): ListPopup {
    logEvent(PyTypeEngineEvent.STATUS_WIDGET_CLICKED)
    val group = DefaultActionGroup()

    PyTypeEngineFrontend.getSupported(project).forEach {
      group.add(SelectEngineAction(it.id))
    }

    group.addSeparator()
    group.add(OpenTypeEngineSettingsAction())

    return JBPopupFactory.getInstance().createActionGroupPopup(
      TypeEngineFrontendBundle.message("display.name"),
      group,
      context,
      JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
      false,
      ActionPlaces.STATUS_BAR_PLACE
    )
  }

  private inner class SelectEngineAction(
    private val engine: PyTypeEngineId,
  ) : DumbAwareAction(frontendFor(engine)?.presentableName, null, null) {

    override fun actionPerformed(e: AnActionEvent) {
      scope.launch {
        val state = PyTypeEngineApi.getInstance().select(
          PyTypeEngineSelectionRequest(project.projectId(), engine, emptySet())
        )
        PyTypeEngineFrontendState.getInstance(project).apply(state)
      }
    }

    override fun update(e: AnActionEvent) {
      val state = PyTypeEngineFrontendState.getInstance(project).get()
      if (engine == PyTypeEngineId.PYREFLY) {
        e.presentation.putClientProperty(ActionUtil.SECONDARY_ICON, Badge.beta)
      }
      e.presentation.icon = if (state.selected == engine)
        AllIcons.Actions.Checked
      else
        null

      if (engine !in state.installed && engine == PyTypeEngineId.PYREFLY) {
        e.presentation.text = TypeEngineFrontendBundle.message("action.engine.pyrefly.install.and.use")
      }
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
  }

  private fun tryUpdateWidgetState() {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      return
    }
    if (project.isDisposed) {
      return
    }
    @Suppress("PyExceptionTooBroad")
    try {
      update()
    }
    catch (t: CancellationException) {
      throw t
    }
    catch (t: Throwable) {
      thisLogger().warn("Cannot update type widget", t)
      //ignore
    }
  }

  private inner class OpenTypeEngineSettingsAction : DumbAwareAction(
    TypeEngineFrontendBundle.message("open.settings"),
    null,
    AllIcons.General.Settings
  ) {
    override fun actionPerformed(e: AnActionEvent) {
      logEvent(PyTypeEngineEvent.SETTINGS_OPENED)
      ShowSettingsUtil.getInstance().showSettingsDialog(
        project,
        PyTypeEngineConfigurable::class.java
      )
    }
  }

  private fun frontendFor(engine: PyTypeEngineId): PyTypeEngineFrontend? =
    PyTypeEngineFrontend.EP_NAME.extensionList.firstOrNull { it.id == engine }

  private fun logEvent(event: PyTypeEngineEvent) {
    scope.launch {
      PyTypeEngineApi.getInstance().logEvent(PyTypeEngineEventRequest(project.projectId(), event))
    }
  }
}

private fun VirtualFile.isPythonNotebook(project: Project): Boolean =
  extension.equals("ipynb", ignoreCase = true) &&
  PsiManager.getInstance(project).findFile(this)?.viewProvider?.languages?.any { it.isKindOf(PythonLanguage.INSTANCE) } == true