package com.intellij.python.typeEngine

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.psi.PsiDocumentManager
import com.intellij.python.lsp.core.listener.PyLspListener
import com.intellij.python.lsp.core.typeEngine.PyTypeEngineProjectSettings
import com.intellij.python.lsp.core.typeEngine.PyTypeEngineProvider
import com.intellij.python.lsp.core.typeEngine.PyTypeEngineType
import com.intellij.python.lsp.core.typeEngine.PyTypeEngineUtils
import com.intellij.python.pyrefly.PyreflyPyTool
import com.intellij.python.pyrefly.PyreflyUsageCollector
import com.intellij.python.pytools.PyTool
import com.intellij.python.pytools.PyToolsState
import com.intellij.python.pytools.ui.PyToolTypeEnginePreview
import com.intellij.python.pytools.ui.getInstalledToolPackage
import com.jetbrains.python.extensions.getSdk
import com.jetbrains.python.packaging.PythonVersionValue
import com.jetbrains.python.packaging.common.PythonPackageManagementListener
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.ui.PythonPackageManagerUI
import com.jetbrains.python.packaging.management.ui.installPyRequirementsBackground
import com.intellij.python.requirements.pyRequirement
import com.jetbrains.python.packaging.requirement.PyRequirementRelation
import com.jetbrains.python.sdk.PySdkListener
import com.jetbrains.python.sdk.isReadOnly
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class RestartLspServersListener(val project: Project) : PyLspListener, ModuleListener, PySdkListener,
                                                                 PythonPackageManagementListener {
  override fun moduleSdkUpdated(module: Module, prevSdk: Sdk?, newSdk: Sdk?) {
    updateModules()
    updateLspServers()
  }

  override fun modulesAdded(project: Project, modules: List<Module?>) {
    updateModules()
  }

  override fun moduleRemoved(project: Project, module: Module) {
    updateModules()
  }

  override fun packagesChanged(sdk: Sdk) {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      return
    }
    val typeEngineProjectSettings = PyTypeEngineProjectSettings.getInstance(project)

    if (typeEngineProjectSettings.typeEngine != PyTypeEngineType.PYREFLY)
      return

    val manager = PythonPackageManager.forSdk(project, sdk)
    project.service<TypeInferenceCoroutine>().coroutineScope.launch {
      val isInstalled = manager.getInstalledToolPackage(PyreflyPyTool.getInstance()) != null
      if (isInstalled) {
        return@launch
      }

      //If package has been removed change type settings to default
      typeEngineProjectSettings.typeEngine = PyTypeEngineType.PYCHARM

      val notificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("Python Type Engine")
      val notification = notificationGroup.createNotification(TypeEngineBundle.message("python.type.engine.notification.package.removed"),
                                                              NotificationType.INFORMATION)
      withContext(Dispatchers.EDT) {
        notification.notify(project)
      }
    }
  }

  override fun onTypeSettingsChange() {
    project.service<TypeInferenceCoroutine>().coroutineScope.launch {
      val typeEngineProjectSettings = PyTypeEngineProjectSettings.getInstance(project)

      // Selecting an engine enables its External Tools tool (committed here, when the engine change is
      // applied), so it works even if the External Tools page was never opened. Persist only the flag;
      // the engine's LSP server is handled by updateLspServers.
      val state = PyToolsState.getInstance(project)
      PyTool.findByPackageName(typeEngineProjectSettings.typeEngine.packageName)?.let { tool ->
        if (!state.isEnabled(tool)) state.setEnabled(tool, true)
      }

      // Tools the user chose to turn off while switching the engine away from them (the "turn the tool
      // off too?" prompt). Committed here so it works without opening the External Tools page.
      val preview = PyToolTypeEnginePreview.getInstance(project)
      val toDisable = preview.pendingDisable.get()
      if (toDisable.isNotEmpty()) {
        withContext(Dispatchers.EDT) {
          toDisable.forEach { pkg ->
            PyTool.findByPackageName(pkg)?.let { tool ->
              if (state.isEnabled(tool)) {
                state.setEnabled(tool, false)
                tool.onEnabledChanged(project, false)
              }
            }
          }
        }
        preview.pendingDisable.set(emptySet())
      }

      // We still auto-install Pyrefly when it becomes the selected engine; the per-module "already
      // installed?" check makes this a no-op when it is up to date.
      if (typeEngineProjectSettings.typeEngine == PyTypeEngineType.PYREFLY) {
        project.modules.forEach { module ->
          val pythonSdk = module.getSdk() ?: return@forEach
          val managerUI = PythonPackageManagerUI.forSdk(project, pythonSdk)
          if (pythonSdk.isReadOnly) {
            return@launch
          }

          val pythonPackage = managerUI.manager.getInstalledToolPackage(PyreflyPyTool.getInstance())
          val version = pythonPackage?.version?.let { PythonVersionValue.parse(it) }?.successOrNull
          val minimumVersion = "0.60.0"
          val typeResolveSupported = PythonVersionValue.parse(minimumVersion).successOrNull!!
          if (version == null || version < typeResolveSupported) {
            val primaryPackageName = PyreflyPyTool.getInstance().packageName.name
            val pyRequirement = pyRequirement(primaryPackageName, PyRequirementRelation.GTE, minimumVersion)
            val result = managerUI.installPyRequirementsBackground(listOf(pyRequirement))
            PyreflyUsageCollector.logPyreflyAutoInstalled(result != null)
          }
        }
      }
      updateLspServers()
    }
  }

  private fun updateModules() {
    val isSupported = PyTypeEngineUtils.isExternalTypeEngineSupported(project)
    val typeEngineProjectSettings = PyTypeEngineProjectSettings.getInstance(project)

    if (!isSupported && typeEngineProjectSettings.typeEngine != PyTypeEngineType.PYCHARM) {
      typeEngineProjectSettings.typeEngine = PyTypeEngineType.PYCHARM
      onTypeSettingsChange()
    }
  }

  private fun updateLspServers() {
    project.service<TypeInferenceCoroutine>().coroutineScope.launch {
      PyTypeEngineProvider.updateLspServers(project)
      withContext(Dispatchers.EDT) {
        PsiDocumentManager.getInstance(project).reparseFiles(listOf(), true)
      }
    }
  }
}