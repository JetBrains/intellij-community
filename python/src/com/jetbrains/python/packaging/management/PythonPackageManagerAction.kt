// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.findPsiFile
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.jetbrains.python.PyBundle
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.errorProcessing.PyError
import com.jetbrains.python.onFailure
import com.jetbrains.python.onSuccess
import com.jetbrains.python.packaging.PyPackageManager
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.sdk.associatedModuleDir
import com.jetbrains.python.sdk.pythonSdk
import com.jetbrains.python.util.ShowingMessageErrorSync
import kotlinx.coroutines.Deferred
import kotlin.text.Regex.Companion.escape

/**
 * Abstract base class representing an action that interacts with a Python package manager to perform tool-specific operations on sdk.
 * Such as installing, updating, or uninstalling Python packages, updating lock files, etc.
 *
 * @param T The type of the Python package manager this action operates on.
 * @param V The result type of the background jobs performed by this action.
 */
abstract class PythonPackageManagerAction<T : PythonPackageManager, V> : DumbAwareAction() {
  val errorSink: ErrorSink = ShowingMessageErrorSync

  /**
   * The regex pattern that matches the file names that this action is applicable to.
   */
  open val fileNamesPattern: Regex = """^${escape(PY_PROJECT_TOML)}$""".toRegex()

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
  protected abstract suspend fun execute(e: AnActionEvent, manager: T): Result<V, PyError>

  override fun update(e: AnActionEvent) {
    val isWatchedFile = e.editor()?.virtualFile?.name?.let { fileNamesPattern.matches(it) } ?: false
    val manager = if (isWatchedFile) getManager(e) else null
    val lastExecutedJob = manager?.getLastExecutedJob()

    with(e.presentation) {
      isVisible = manager != null
      isEnabled = lastExecutedJob?.isActive != true
    }
  }

  /**
   * Execution success callback, refreshes the environment and re-runs the inspection check.
   * Might be overridden by subclasses.
   */
  protected open suspend fun onSuccess(manager: T, document: Document?) {
    withBackgroundProgress(manager.project, PyBundle.message("python.sdk.scanning.installed.packages")) {
      manager.refreshEnvironment()
      document?.reloadIntentions(manager.project)
    }
  }

  /**
   * This action saves the current document on fs because tools are command line tools, and they need actual files to be up to date
   * Handles errors via [errorSink]
   */
  private fun T.executeActionJob(e: AnActionEvent): Deferred<Result<V, PyError>>? = runDeferredJob {
    val document = e.editor()?.document

    document?.let {
      writeAction {
        FileDocumentManager.getInstance().saveDocument(document)
      }
    }

    withBackgroundProgress(project, e.presentation.text) {
      execute(e, this@executeActionJob)
    }.onSuccess {
      onSuccess(this, document)
    }.onFailure {
      errorSink.emit(it)
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val manager = getManager(e)
    manager?.executeActionJob(e)
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
  return editor()?.getPythonPackageManager().let { it as? T }
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
