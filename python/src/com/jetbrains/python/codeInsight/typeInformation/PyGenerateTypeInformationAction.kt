// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.typeInformation

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.CommonBundle
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.Messages
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import com.jetbrains.python.sdk.PythonSdkUpdater
import com.jetbrains.python.sdk.pythonSdk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class PyGenerateTypeInformationAction : DumbAwareAction(
  PyBundle.message("action.Python.GenerateTypeInformation.text"),
  PyBundle.message("action.Python.GenerateTypeInformation.description"),
  null,
) {
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = findSdk(e) != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val sdk = findSdk(e) ?: return

    PyPackageCoroutine.launch(project, Dispatchers.IO) {
      val packageManager = PythonPackageManager.forSdk(project, sdk)
      val generator = PyTypeInformationGenerator.EP_NAME.extensionList.firstOrNull { it.isApplicable(packageManager) }
      if (generator == null) {
        notify(
          project,
          NotificationType.WARNING,
          PyBundle.message("python.type.information.not.found.title"),
          PyBundle.message("python.type.information.not.found.message", sdk.name),
        )
        return@launch
      }

      val confirmed = withContext(Dispatchers.EDT) {
        Messages.showOkCancelDialog(
          project,
          PyBundle.message(
            "python.type.information.confirmation.message",
            generator.enginePackageName,
            sdk.name,
            generator.presentableName,
          ),
          PyBundle.message("python.type.information.confirmation.title"),
          PyBundle.message("python.type.information.confirmation.generate"),
          CommonBundle.getCancelButtonText(),
          Messages.getQuestionIcon(),
        ) == Messages.OK
      }
      if (!confirmed) return@launch

      val result = withBackgroundProgress(
        project,
        PyBundle.message("python.type.information.progress", generator.presentableName),
        cancellable = true,
      ) {
        generator.generate(project, sdk)
      }

      when (result) {
        PyTypeInformationGenerationResult.Success -> {
          PythonSdkUpdater.scheduleUpdate(sdk, project)
          DaemonCodeAnalyzer.getInstance(project).restart()
          notify(
            project,
            NotificationType.INFORMATION,
            PyBundle.message("python.type.information.success.title"),
            PyBundle.message("python.type.information.success.message", generator.presentableName),
          )
        }
        is PyTypeInformationGenerationResult.Failure -> {
          thisLogger().warn("${generator.presentableName} type information generation failed: ${result.details}")
          val messageKey = when (result.stage) {
            PyTypeInformationGenerationResult.Stage.INSTALL_ENGINE -> "python.type.information.install.failed.message"
            PyTypeInformationGenerationResult.Stage.GENERATE -> "python.type.information.generate.failed.message"
          }
          notify(
            project,
            NotificationType.ERROR,
            PyBundle.message("python.type.information.failed.title"),
            PyBundle.message(messageKey, generator.presentableName),
          )
        }
      }
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

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
  }
}
