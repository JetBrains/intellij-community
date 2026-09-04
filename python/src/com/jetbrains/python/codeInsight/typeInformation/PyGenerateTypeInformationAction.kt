// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.typeInformation

import com.intellij.CommonBundle
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.Messages
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import com.jetbrains.python.sdk.PythonSdkUpdater
import com.jetbrains.python.sdk.pythonSdk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

internal class PyGenerateTypeInformationAction : DumbAwareAction(
  PyBundle.message("action.Python.GenerateTypeInformation.text"),
  PyBundle.message("action.Python.GenerateTypeInformation.description"),
  null,
) {
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible =
      PyTypeInformationGenerator.EP_NAME.extensionList.isNotEmpty() && findSdk(e) != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val sdk = findSdk(e) ?: return

    PyPackageCoroutine.launch(project, Dispatchers.IO) {
      val generator = findApplicableTypeInformationGenerator(PyTypeInformationGenerator.EP_NAME.extensionList) {
        it.isApplicable(project, sdk)
      }
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
            generator.presentableName,
            sdk.name,
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
        try {
          generator.generate(project, sdk)
        }
        catch (e: CancellationException) {
          throw e
        }
        catch (e: Exception) {
          PyTypeInformationGenerationResult.Failure(e.message ?: e.javaClass.simpleName)
        }
      }

      when (result) {
        PyTypeInformationGenerationResult.Success -> {
          PythonSdkUpdater.scheduleUpdate(sdk, project)
          notify(
            project,
            NotificationType.INFORMATION,
            PyBundle.message("python.type.information.success.title"),
            PyBundle.message("python.type.information.success.message", generator.presentableName),
          )
        }
        is PyTypeInformationGenerationResult.Failure -> {
          fileLogger().warn("${generator.presentableName} type information generation failed: ${result.details}")
          notify(
            project,
            NotificationType.ERROR,
            PyBundle.message("python.type.information.failed.title"),
            PyBundle.message("python.type.information.failed.message", generator.presentableName),
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

internal suspend fun findApplicableTypeInformationGenerator(
  generators: Iterable<PyTypeInformationGenerator>,
  isApplicable: suspend (PyTypeInformationGenerator) -> Boolean,
): PyTypeInformationGenerator? {
  for (generator in generators) {
    try {
      if (isApplicable(generator)) return generator
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Exception) {
      fileLogger().warn("Failed to check ${generator.presentableName} type information generator", e)
    }
  }
  return null
}
