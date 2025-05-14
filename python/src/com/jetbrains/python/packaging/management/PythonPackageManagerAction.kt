// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.ProgressTitle
import com.intellij.openapi.vfs.findPsiFile
import com.intellij.platform.util.progress.reportSequentialProgress
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.jetbrains.python.PyBundle
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.errorProcessing.PyError
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.onFailure
import com.jetbrains.python.onSuccess
import com.jetbrains.python.packaging.PyPackageManager
import com.jetbrains.python.sdk.PythonSdkCoroutineService
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.sdk.associatedModuleDir
import com.jetbrains.python.sdk.pythonSdk
import com.jetbrains.python.util.ShowingMessageErrorSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.CoroutineContext
import kotlin.text.Regex.Companion.escape

/**
 * Abstract base class representing an action that interacts with a Python package manager to perform tool-specific operations on sdk.
 * Such as installing, updating, or uninstalling Python packages, updating lock files, etc.
 *
 * @param T The type of the Python package manager this action operates on.
 * @param V The result type of the background jobs performed by this action.
 */
@ApiStatus.Internal
abstract class PythonPackageManagerAction<T : PythonPackageManager, V> : DumbAwareAction() {
  protected val errorSink: ErrorSink = ShowingMessageErrorSync
  protected val scope: CoroutineScope = service<PythonSdkCoroutineService>().cs
  protected val context: CoroutineContext = Dispatchers.IO

  /**
   * The regex pattern that matches the file names that this action is applicable to.
   */
  protected open val fileNamesPattern: Regex = """^${escape(PY_PROJECT_TOML)}$""".toRegex()

  /**
   * Retrieves the manager instance associated with the given action event, see [AnActionEvent.getPythonPackageManager]
   *
   * @return the manager instance of type [T] associated with the action event, or null if there is no [T]-manager associated.
   */
  protected abstract fun getManager(e: AnActionEvent): T?

  /**
   * Executes the main logic of the action using the provided event and manager.
   *
   * @return [Result] which contains the successful result of type [V] or an error of type [PyError] if it fails.
   */
  protected abstract suspend fun execute(e: AnActionEvent, manager: T): PyResult<V>

  override fun update(e: AnActionEvent) {
    val isWatchedFile = e.editor()?.virtualFile?.name?.let { fileNamesPattern.matches(it) } ?: false
    val manager = if (isWatchedFile) getManager(e) else null

    with(e.presentation) {
      isVisible = manager != null
      isEnabled = manager?.isRunLocked() == false
    }
  }

  /**
   * Execution success callback, refreshes the environment and re-runs the inspection check.
   * Might be overridden by subclasses.
   */
  private suspend fun onSuccess(manager: T, document: Document?) {
    manager.refreshEnvironment()
    document?.reloadIntentions(manager.project)
  }

  private suspend fun executeScenarioWithinProgress(manager: T, e: AnActionEvent, document: Document?): PyResult<V> {
    return reportSequentialProgress(2) { reporter ->
      reporter.itemStep {
        execute(e, manager)
      }.onSuccess {
        reporter.itemStep(PyBundle.message("python.sdk.scanning.installed.packages")) {
          onSuccess(manager, document)
        }
      }.onFailure {
        errorSink.emit(it)
      }
    }
  }

  /**
   * This action saves the current document on fs because tools are command line tools, and they need actual files to be up to date
   * Handles errors via [errorSink]
   */
  override fun actionPerformed(e: AnActionEvent) {
    val manager = getManager(e) ?: return
    val document = e.editor()?.document

    document?.let {
      runInEdt {
        FileDocumentManager.getInstance().saveDocument(document)
      }
    }

    scope.launch(context) {
      manager.runSynchronized(e.presentation.text) {
        executeScenarioWithinProgress(manager, e, document)
      }
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

private fun AnActionEvent.editor(): Editor? = this.getData(CommonDataKeys.EDITOR)

private fun Document.virtualFile() = FileDocumentManager.getInstance().getFile(this)

private fun Editor.getPythonPackageManager(): PythonPackageManager? {
  val virtualFile = this.document.virtualFile() ?: return null
  val module = project?.let { ModuleUtil.findModuleForFile(virtualFile, it) } ?: return null
  val manager = module.pythonSdk?.let { sdk ->
    PythonPackageManager.forSdk(module.project, sdk)
  }
  return manager
}

internal inline fun <reified T : PythonPackageManager> AnActionEvent.getPythonPackageManager(): T? {
  return editor()?.getPythonPackageManager() as? T
}

/**
 * 1) Reloads package caches.
 * 2) [PyPackageManager] is deprecated but its implementations still have their own package caches, so need to refresh them too.
 * 3) some files likes uv.lock / poetry.lock might be added, so need to refresh module dir too.
 */
private suspend fun PythonPackageManager.refreshEnvironment() {
  PythonSdkUtil.getSitePackagesDirectory(sdk)?.refresh(true, true)
  sdk.associatedModuleDir?.refresh(true, false)
  PyPackageManager.getInstance(sdk).refreshAndGetPackages(true)
  reloadPackages()
}

/**
 * re-runs the inspection check using updated dependencies
 */
private suspend fun Document.reloadIntentions(project: Project) {
  readAction {
    val virtualFile = virtualFile() ?: return@readAction null
    virtualFile.findPsiFile(project)
  }?.let { psiFile ->
    DaemonCodeAnalyzer.getInstance(project).restart(psiFile)
  }
}


internal fun PythonPackageManager.isRunLocked(): Boolean {
  return CancellableJobSerialRunner.isRunLocked(this.sdk)
}

internal suspend fun <V> PythonPackageManager.runSynchronized(
  title: @ProgressTitle String,
  runnable: suspend () -> PyResult<V>,
): PyResult<V> {
  return CancellableJobSerialRunner.run(this.project, this.sdk, title, runnable)
}
