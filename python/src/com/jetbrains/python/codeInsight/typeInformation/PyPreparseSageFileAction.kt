// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.typeInformation

import com.intellij.CommonBundle
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.execution.target.value.constant
import com.intellij.execution.target.value.targetPath
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import com.jetbrains.python.sdk.PythonExecuteUtils
import com.jetbrains.python.sdk.pythonSdk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.Charset
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes

internal class PyPreparseSageFileAction : DumbAwareAction(
  PyBundle.message("action.Python.PreparseSageFile.text"),
  PyBundle.message("action.Python.PreparseSageFile.description"),
  null,
) {
  override fun update(e: AnActionEvent) {
    val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
    e.presentation.isEnabledAndVisible = file != null && isSageFile(file) && findSdk(e) != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val sdk = findSdk(e) ?: return
    val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

    PyPackageCoroutine.launch(project, Dispatchers.IO) {
      val packageManager = PythonPackageManager.forSdk(project, sdk)
      val packageNames = packageManager.listInstalledPackages().map { it.name }
      if (!SageMathTypeInformationGenerator.hasSageMathDistribution(packageNames)) {
        notify(
          project,
          NotificationType.WARNING,
          PyBundle.message("python.preparse.sage.failed.title"),
          PyBundle.message("python.preparse.sage.not.applicable.message", sdk.name),
        )
        return@launch
      }
      if (packageNames.none { SageMathTypeInformationGenerator.normalizePackageName(it) == SageMathTypeInformationGenerator.ENGINE_PACKAGE }) {
        notify(
          project,
          NotificationType.WARNING,
          PyBundle.message("python.preparse.sage.failed.title"),
          PyBundle.message("python.preparse.sage.engine.missing.message"),
        )
        return@launch
      }
      if (ModuleUtilCore.findModuleForFile(file, project) == null) {
        notify(
          project,
          NotificationType.WARNING,
          PyBundle.message("python.preparse.sage.failed.title"),
          PyBundle.message("python.preparse.sage.outside.module.roots.message"),
        )
        return@launch
      }
      if (isAlreadyConverted(file)) {
        notify(
          project,
          NotificationType.INFORMATION,
          PyBundle.message("python.preparse.sage.already.converted.title"),
          PyBundle.message("python.preparse.sage.already.converted.message"),
        )
        return@launch
      }

      val confirmed = withContext(Dispatchers.EDT) {
        Messages.showOkCancelDialog(
          project,
          PyBundle.message("python.preparse.sage.confirmation.message", file.name),
          PyBundle.message("python.preparse.sage.confirmation.title"),
          PyBundle.message("python.preparse.sage.confirmation.convert"),
          CommonBundle.getCancelButtonText(),
          Messages.getWarningIcon(),
        ) == Messages.OK
      }
      if (!confirmed) return@launch

      val output = withBackgroundProgress(
        project,
        PyBundle.message("python.preparse.sage.progress", file.name),
        cancellable = true,
      ) {
        runPreparse(project, sdk, file)
      }
      if (!output.isSuccessful) {
        notify(
          project,
          NotificationType.ERROR,
          PyBundle.message("python.preparse.sage.failed.title"),
          PyBundle.message("python.preparse.sage.failed.message", output.failureDetails()),
        )
        return@launch
      }

      FileDocumentManager.getInstance().reloadFiles(file)
      DaemonCodeAnalyzer.getInstance(project).restart()
      notify(
        project,
        NotificationType.INFORMATION,
        PyBundle.message("python.preparse.sage.success.title"),
        PyBundle.message("python.preparse.sage.success.message"),
      )
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  private suspend fun runPreparse(project: Project, sdk: Sdk, file: VirtualFile): ProcessOutput {
    val result = PythonExecuteUtils.createProcess(
      project = project,
      module = ModuleUtilCore.findModuleForFile(file, project),
      sdk = sdk,
      pyModuleToRun = SageMathTypeInformationGenerator.ENGINE_MODULE,
      runArgs = listOf(constant("preparse"), targetPath(Path.of(file.path))),
      envs = emptyMap(),
      workingDir = null,
      additionalUploadLocalDir = null,
    )
    val handler = CapturingProcessHandler(result.process, Charset.defaultCharset(), result.commandPresentation)
    return withContext(Dispatchers.IO) {
      handler.runProcess(PREPARSE_TIMEOUT.inWholeMilliseconds.toInt(), true)
    }
  }

  private fun isAlreadyConverted(file: VirtualFile): Boolean {
    val content = runReadAction { VfsUtilCore.loadText(file) }
    return hasSageConversionMarker(content)
  }

  private fun findSdk(e: AnActionEvent): Sdk? {
    val project = e.project ?: return null
    val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
    val moduleSdk = file?.let { ModuleUtilCore.findModuleForFile(it, project)?.pythonSdk }
    return moduleSdk ?: project.pythonSdk ?: ModuleManager.getInstance(project).modules.firstNotNullOfOrNull { it.pythonSdk }
  }

  private fun notify(project: Project, type: NotificationType, title: String, message: String) {
    NotificationGroupManager.getInstance()
      .getNotificationGroup(NOTIFICATION_GROUP_ID)
      .createNotification(title, message, type)
      .notify(project)
  }

  companion object {
    private const val NOTIFICATION_GROUP_ID = "Python type information"
    private val PREPARSE_TIMEOUT = 5.minutes
  }
}

internal fun isSageFile(file: VirtualFile): Boolean = file.extension == "sage"

internal fun hasSageConversionMarker(text: String): Boolean =
  "# Converted by sage-pycharm-stubgen" in text

private val ProcessOutput.isSuccessful: Boolean
  get() = !isTimeout && exitCode == 0

private fun ProcessOutput.failureDetails(): String {
  if (isTimeout) return "Process timed out"
  return stderr.ifBlank { stdout }.trim().ifBlank { "Process exited with code $exitCode" }
}
