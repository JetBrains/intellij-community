package com.intellij.python.sdkConfigurator.backend.impl

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.python.pyproject.model.api.CreateSdkNotFilesResult
import com.intellij.python.pyproject.model.api.InterpreterConfigurationResult
import com.intellij.python.pyproject.model.api.autoConfigureSdkDoNotCreateFiles
import com.intellij.python.pyproject.model.api.configureSdkIfNeeded
import com.intellij.python.pyproject.model.evolution.EvoPyProjectModel
import com.intellij.python.pyproject.model.evolution.setOnWorkspace
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.evolution.EvoWorkspace
import com.jetbrains.python.project.PyProject.Companion.asPyProject
import com.jetbrains.python.project.PyProject.Companion.getPyProjects
import com.jetbrains.python.onFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.annotations.ApiStatus
import kotlin.time.Duration.Companion.seconds

/**
 * Gives each workspace of [project] without an interpreter the environment that already exists for it, such as the
 * `.venv` of its root. It creates no files. Only the root of a workspace is configured, see [EvoWorkspace]. Every member
 * follows the root, with no detection of its own, see [setOnWorkspace].
 */
@ApiStatus.Internal // Opened for tests only: we can't put tests here because configurators are in communuty.impl
suspend fun configureSdkAutomatically(project: Project): Unit = withContext(Dispatchers.Default) {
  // The snapshot follows the modules asynchronously, so wait for one that holds each Python project of this walk. A
  // module that goes away or stops being a Python project meanwhile is skipped. Such a module can leave the snapshot
  // equal, and then no new snapshot comes. So the wait has a limit, and the current snapshot is used after it.
  val pyProjects = project.getPyProjects()
  val model = EvoPyProjectModel.getInstance(project)
  val snapshot = withTimeoutOrNull(SNAPSHOT_TIMEOUT) {
    model.snapshotFlow().first { snapshot ->
      pyProjects.all { snapshot.forPyProject(it) != null || it.residesOnModule.isDisposed || it.residesOnModule.asPyProject() == null }
    }
  } ?: model.snapshot().also { logger.warn("No snapshot holds each Python project after $SNAPSHOT_TIMEOUT, use the current one") }
  supervisorScope {
    for (workspace in snapshot.workspaces) {
      // If module is disposed, the coroutine gets cancelled, but we still need to configure other workspaces
      launch {
        val result = workspace.pyProject.configureSdkIfNeeded { autoConfigureSdkDoNotCreateFiles() } ?: return@launch
        result.log(workspace.module) { error ->
          when (error) {
            is CreateSdkNotFilesResult.NoFiles -> "No files found on disk"
            is CreateSdkNotFilesResult.SdkCreationError -> "SDK creation error ${error.error}"
          }
        }
        if (result is InterpreterConfigurationResult.Configured) workspace.followRoot(result.interpreter)
      }
    }
  }
}

/** Makes [interpreter], the one of the root, the interpreter of every member, when a member has another one or none. */
private suspend fun EvoWorkspace.followRoot(interpreter: PythonInterpreter) {
  if (members.all { it.interpreter == interpreter }) return
  setOnWorkspace(interpreter).onFailure { logger.warn("Cannot give the members of $baseDir the interpreter of their root: $it") }
}

private fun <T : Any> InterpreterConfigurationResult<T>.log(module: Module, logForConfigError: (err: T) -> @NlsSafe String) {
  val (success, message) = when (this) {
    is InterpreterConfigurationResult.Configured -> {
      Pair(true, "configured with $interpreter")
    }
    is InterpreterConfigurationResult.NotConfigured -> {
      Pair(false, logForConfigError(reason))
    }
    is InterpreterConfigurationResult.ParentHasNoInterpreter -> {
      Pair(false, "parent module has no SDK")
    }
    is InterpreterConfigurationResult.ToolNotInstalled -> {
      Pair(false, "no required tool installed: ${this.tool.toolToInstall}")
    }
  }
  logger.debug { "module $module config:${if (success) "success" else "error"}: $message" }
}

private val logger = fileLogger()

/** How long [configureSdkAutomatically] waits for the snapshot. A recompute takes milliseconds, so this is only a guard. */
private val SNAPSHOT_TIMEOUT = 30.seconds
