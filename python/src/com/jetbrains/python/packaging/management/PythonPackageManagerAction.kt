// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.CommonDataKeys.PSI_FILE
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.management.ui.PythonPackageManagerUI
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import com.jetbrains.python.sdk.pythonSdk
import com.jetbrains.python.util.ShowingMessageErrorSync
import kotlinx.coroutines.Dispatchers
import org.jetbrains.annotations.ApiStatus
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
   * @return true if the successful result or false if it fails.
   */
  protected abstract suspend fun execute(e: AnActionEvent, manager: T): PyResult<Unit>

  override fun update(e: AnActionEvent) {
    val virtualFile = e.getData(PlatformDataKeys.VIRTUAL_FILE)
    val isWatchedFile = isWatchedFile(virtualFile)
    val manager = if (isWatchedFile) getManager(e) else null

    with(e.presentation) {
      isVisible = manager != null
      isEnabled = manager?.isRunLocked() == false
    }
  }

  protected open fun isWatchedFile(virtualFile: VirtualFile?): Boolean = virtualFile?.name?.let { fileNamesPattern.matches(it) } ?: false

  /**
   * This action saves the current document on fs because tools are command line tools, and they need actual files to be up to date
   * Handles errors via [errorSink]
   */
  override fun actionPerformed(e: AnActionEvent) {
    val manager = getManager(e) ?: return
    val psiFile = e.getData(PSI_FILE) ?: return
    ModuleUtil.findModuleForFile(psiFile) ?: return

    PyPackageCoroutine.launch(e.project, Dispatchers.IO) {
      edtWriteAction {
        FileDocumentManager.getInstance().saveAllDocuments()
      }

      PythonPackageManagerUI.forPackageManager(manager).executeCommand(e.presentation.text) {
        execute(e, manager).mapSuccess {
          DaemonCodeAnalyzer.getInstance(psiFile.project).restart(psiFile)
        }
      }
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

internal inline fun <reified T : PythonPackageManager> AnActionEvent.getPythonPackageManager(): T? {
  val virtualFile = getData(CommonDataKeys.VIRTUAL_FILE) ?: return null
  val project = project ?: return null
  val module = ModuleUtil.findModuleForFile(virtualFile, project) ?: return null
  val sdk = module.pythonSdk ?: return null
  return PythonPackageManager.forSdk(project, sdk) as? T
}


internal fun PythonPackageManager.isRunLocked(): Boolean {
  return CancellableJobSerialRunner.isRunLocked(this.sdk)
}

