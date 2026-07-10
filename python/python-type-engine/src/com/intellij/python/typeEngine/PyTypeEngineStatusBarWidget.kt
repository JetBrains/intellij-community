// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.typeEngine

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.impl.status.EditorBasedStatusBarPopup
import com.intellij.python.lsp.core.listener.PyLspListener
import com.intellij.python.lsp.core.typeEngine.PyTypeEngineConfigurable
import com.intellij.python.lsp.core.typeEngine.PyTypeEngineProjectSettings
import com.intellij.python.lsp.core.typeEngine.PyTypeEngineProvider
import com.intellij.python.lsp.core.typeEngine.PyTypeEngineType
import com.intellij.python.lsp.core.typeEngine.PyTypeEngineUsageCollector
import com.intellij.python.lsp.core.typeEngine.PyTypeEngineUtils
import com.intellij.util.messages.MessageBusConnection
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.hasInstalledPackageSnapshot
import com.jetbrains.python.pyi.PyiFileType
import com.jetbrains.python.sdk.PySdkListener
import com.jetbrains.python.sdk.pythonSdk
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.cancellation.CancellationException

private const val ID: String = "pythonTypeEngineWidget"

/**
 * Factory for creating the Type Engine status bar widget.
 */
internal class PyTypeEngineStatusBarWidgetFactory : StatusBarWidgetFactory {
  override fun getId(): String = ID

  override fun getDisplayName(): String = TypeEngineBundle.message("display.name")

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
  scope: CoroutineScope,
) : EditorBasedStatusBarPopup(project = project, isWriteableFileRequired = false, scope = scope) {
  override fun ID(): String = ID

  override fun createInstance(project: Project): StatusBarWidget = PyTypeEngineStatusBarWidget(project, scope)

  override fun getWidgetState(file: VirtualFile?): WidgetState {
    if (file?.fileType !is PythonFileType)
      return WidgetState.HIDDEN
    if (!PyTypeEngineUtils.isExternalTypeEngineSupported(project))
      return WidgetState.HIDDEN

    val typeEngine = PyTypeEngineProjectSettings.getInstance(project).typeEngine
    return WidgetState(
      TypeEngineBundle.message("widget.type.tooltip", typeEngine.displayName),
      TypeEngineBundle.message("widget.type.text", typeEngine.displayName),
      true
    )
  }

  override fun registerCustomListeners(connection: MessageBusConnection) {
    connection.subscribe(ModuleListener.TOPIC, object : ModuleListener {
      override fun moduleRemoved(project: Project, module: Module) {
        tryUpdateWidgetState()
      }

      override fun modulesAdded(project: Project, modules: List<Module?>) {
        if (!project.isInitialized)
          return
        tryUpdateWidgetState()
      }
    })

    connection.subscribe(PyLspListener.TOPIC, object : PyLspListener {
      override fun onTypeSettingsChange() {
        tryUpdateWidgetState()
      }
    })

    connection.subscribe(PySdkListener.TOPIC, object : PySdkListener {
      override fun moduleSdkUpdated(module: Module, prevSdk: Sdk?, newSdk: Sdk?) {
        tryUpdateWidgetState()
      }
    })
  }

  override fun isEnabledForFile(file: VirtualFile?): Boolean = file?.fileType in setOf(PythonFileType.INSTANCE, PyiFileType.INSTANCE)

  override fun createPopup(context: DataContext): ListPopup {
    PyTypeEngineUsageCollector.logStatusBarWidgetClicked(project)
    val group = DefaultActionGroup()

    val module = context.getData(PlatformCoreDataKeys.MODULE)
    // Add engine selection actions
    PyTypeEngineProvider.getSupportedTypes(project).forEach {
      group.add(SelectEngineAction(it, module))
    }

    // Add separator
    group.addSeparator()

    // Add settings action
    group.add(OpenTypeEngineSettingsAction())

    return JBPopupFactory.getInstance().createActionGroupPopup(
      TypeEngineBundle.message("display.name"),
      group,
      context,
      JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
      false,
      ActionPlaces.STATUS_BAR_PLACE
    )
  }

  /**
   * Action to select a specific type engine.
   */
  private inner class SelectEngineAction(
    private val engine: PyTypeEngineType,
    val module: Module?,
  ) : DumbAwareAction(engine.displayName, null, null) {

    override fun actionPerformed(e: AnActionEvent) {
      val settings = PyTypeEngineProjectSettings.getInstance(project)
      settings.typeEngine = engine
      PyTypeEngineUsageCollector.logEngineChanged(project, engine)
      tryUpdateWidgetState()
    }

    @Suppress("UsagesOfObsoleteApi")
    override fun update(e: AnActionEvent) {
      val currentEngine = PyTypeEngineProjectSettings.getInstance(project).typeEngine
      if (engine == PyTypeEngineType.PYREFLY) {
        e.presentation.putClientProperty(ActionUtil.SECONDARY_ICON, AllIcons.General.Beta)
      }
      e.presentation.icon = if (currentEngine == engine)
        AllIcons.Actions.Checked
      else
        null

      if (engine == PyTypeEngineType.PYCHARM) {
        return
      }

      val sdk = module?.pythonSdk
      if (sdk == null) {
        e.presentation.isEnabledAndVisible = false
        return
      }

      val packageManager = PythonPackageManager.forSdk(project, sdk)
      val isInstalled = packageManager.hasInstalledPackageSnapshot(engine.packageName)
      if (!isInstalled && engine == PyTypeEngineType.PYREFLY) {
        e.presentation.text = TypeEngineBundle.message("action.engine.pyrefly.install.and.use")
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

  /**
   * Action to open Type Engine settings.
   */
  private inner class OpenTypeEngineSettingsAction : DumbAwareAction(
    TypeEngineBundle.message("open.settings"),
    null,
    AllIcons.General.Settings
  ) {
    override fun actionPerformed(e: AnActionEvent) {
      PyTypeEngineUsageCollector.logSettingsOpened(project)
      ShowSettingsUtil.getInstance().showSettingsDialog(
        project,
        PyTypeEngineConfigurable::class.java
      )
    }
  }
}