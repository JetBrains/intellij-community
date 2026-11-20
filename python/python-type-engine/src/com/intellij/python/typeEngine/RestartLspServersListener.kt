package com.intellij.python.typeEngine

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.psi.PsiDocumentManager
import com.intellij.python.lsp.core.LspPackageAction
import com.intellij.python.lsp.core.getInstalledToolPackage
import com.intellij.python.lsp.core.listener.PyLspListener
import com.intellij.python.lsp.core.lspPackageAction
import com.intellij.python.lsp.core.pyLspServedProjects
import com.intellij.python.lsp.core.typeEngine.PyTypeEngineProjectSettings
import com.intellij.python.lsp.core.typeEngine.PyTypeEngineProvider
import com.intellij.python.lsp.core.typeEngine.PyTypeEngineType
import com.intellij.python.lsp.core.typeEngine.PyTypeEngineUtils
import com.intellij.python.pyproject.model.evolution.EvoPyProjectModel
import com.intellij.python.pyrefly.PyreflyPyTool
import com.intellij.python.pyrefly.PyreflyUsageCollector
import com.intellij.python.requirements.pyRequirement
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreterKey
import com.intellij.python.sdk.backend.key
import com.jetbrains.python.packaging.PythonVersionValue
import com.jetbrains.python.packaging.common.PythonPackageManagementListener
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.ui.PythonPackageManagerUI
import com.jetbrains.python.packaging.management.ui.installPyRequirementsBackground
import com.jetbrains.python.packaging.requirement.PyRequirementRelation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class RestartLspServersListener(val project: Project) : PyLspListener, PythonPackageManagementListener {
  /**
   * Follows the snapshots. The engine choice resets when the engine is not supported: on the first snapshot, and on a
   * change from supported to not supported. The servers start for the first snapshot on their own, so they restart only
   * when the interpreter of a served Python project changes after it.
   */
  internal class SnapshotActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
      val listener = RestartLspServersListener(project)
      project.service<TypeInferenceCoroutine>().coroutineScope.launch {
        var previous: TypeEngineInput? = null
        EvoPyProjectModel.getInstance(project).snapshotFlow()
          .map { snapshot -> TypeEngineInput.of(project, snapshot) }
          .distinctUntilChanged()
          .collect { input ->
            val before = previous
            previous = input
            // A project saved with an external engine can open with no interpreter that the engine serves.
            if ((before == null || before.isSupported) && !input.isSupported) {
              listener.updateModules()
            }
            if (before != null && before.servedInterpreters != input.servedInterpreters) {
              listener.updateLspServers()
            }
          }
      }
    }
  }

  /** The part of a snapshot that the engine choice and the servers depend on. */
  private data class TypeEngineInput(val isSupported: Boolean, val servedInterpreters: Map<Module, PythonInterpreter?>) {
    companion object {
      fun of(project: Project, snapshot: EvoPyProjectModel.Snapshot): TypeEngineInput = TypeEngineInput(
        PyTypeEngineUtils.isExternalTypeEngineSupported(project, snapshot),
        pyLspServedProjects(snapshot).associate { it.residesOnModule to snapshot.forPyProject(it)?.interpreter },
      )
    }
  }

  /**
   * Turns the Pyrefly engine off when the user removes the Pyrefly package from the last interpreter of the project
   * that held it.
   *
   * The topic is application level, so this fires for the interpreters of every open project. An interpreter of
   * another project is no reason to turn off the engine of this one (PY-91655). Only a removal counts. Pyrefly also
   * runs from a custom path, from `PATH` or through `uvx`, and then no interpreter holds the package. The bundled
   * Pyrefly is a file in the plugin, so it needs no package at all.
   */
  override fun packagesChanged(interpreter: PythonInterpreter) {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      return
    }
    project.service<TypeInferenceCoroutine>().coroutineScope.launch {
      val snapshot = EvoPyProjectModel.getInstance(project).snapshot()
      val servedInterpreters = pyLspServedProjects(snapshot).mapNotNull { snapshot.forPyProject(it)?.interpreter }.distinct()
      if (interpreter !in servedInterpreters) return@launch

      // The answer is recorded for every engine. A user can select pyrefly while it is a package already, and then
      // no package event comes before the removal.
      val pyrefly = PyreflyPyTool.getInstance()
      val action = project.service<PyreflyPackageState>().record(interpreter) {
        PythonPackageManager.forPythonInterpreter(interpreter).getInstalledToolPackage(pyrefly) != null
      }
      if (action != LspPackageAction.STOP) return@launch

      val typeEngineProjectSettings = PyTypeEngineProjectSettings.getInstance(project)
      if (typeEngineProjectSettings.typeEngine != PyTypeEngineType.PYREFLY || PyreflyPyTool.isBundledPyreflyEnabled()) return@launch

      // Ask every served interpreter, not only the one that changed. One module of a multi-module
      // project that loses pyrefly must not turn the engine off for the modules that still hold it.
      val isInstalledSomewhere = servedInterpreters.any { served ->
        PythonPackageManager.forPythonInterpreter(served).getInstalledToolPackage(pyrefly) != null
      }
      if (isInstalledSomewhere) {
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

      // We still auto-install Pyrefly when it becomes the selected engine; the per-module "already
      // installed?" check makes this a no-op when it is up to date.
      if (typeEngineProjectSettings.typeEngine == PyTypeEngineType.PYREFLY && !PyreflyPyTool.isBundledPyreflyEnabled()) {
        // Only the served Python projects: in a multi-module project one interpreter Pyrefly cannot drive
        // must not stop the install for the others. The test is the same one the tool itself uses, so a
        // remote interpreter gets no install it cannot use.
        val snapshot = EvoPyProjectModel.getInstance(project).snapshot()
        pyLspServedProjects(snapshot).forEach { pyProject ->
          val interpreter = snapshot.forPyProject(pyProject)?.interpreter ?: return@forEach
          val managerUI = PythonPackageManagerUI.forPythonInterpreter(interpreter)

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

  internal fun updateModules() {
    val isSupported = PyTypeEngineUtils.isExternalTypeEngineSupported(project)
    val typeEngineProjectSettings = PyTypeEngineProjectSettings.getInstance(project)

    if (!isSupported && typeEngineProjectSettings.typeEngine != PyTypeEngineType.PYCHARM) {
      typeEngineProjectSettings.typeEngine = PyTypeEngineType.PYCHARM
      onTypeSettingsChange()
    }
  }

  internal fun updateLspServers() {
    project.service<TypeInferenceCoroutine>().coroutineScope.launch {
      PyTypeEngineProvider.updateLspServers(project)
      withContext(Dispatchers.EDT) {
        PsiDocumentManager.getInstance(project).reparseFiles(listOf(), true)
      }
    }
  }
}

/**
 * Whether Pyrefly was a package of each interpreter of the project at its last package event. An interpreter that is
 * absent has no answer yet.
 *
 * Each key holds only the owner directory and the ref of the interpreter, see [PythonInterpreterKey]. So the entry of
 * a removed interpreter holds no SDK.
 */
@Service(Service.Level.PROJECT)
private class PyreflyPackageState {
  private val mutex = Mutex()

  /** Guarded by [mutex]. */
  private val isPackageOf = HashMap<PythonInterpreterKey, Boolean>()

  /**
   * Records the answer of [isPackage] for [interpreter], and answers the move from the earlier answer, see
   * [lspPackageAction].
   *
   * Each package event runs in its own coroutine, and the coroutines run in no fixed order. The read and the store
   * happen under one lock, so the stored answer is always the one that was read last.
   */
  suspend fun record(interpreter: PythonInterpreter, isPackage: () -> Boolean): LspPackageAction = mutex.withLock {
    val current = isPackage()
    lspPackageAction(isPackageOf.put(interpreter.key, current), current)
  }
}
