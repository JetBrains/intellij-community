// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.conda

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.util.ExceptionUtil
import com.jetbrains.python.PyBundle
import com.jetbrains.python.actions.InstallCondaActionImpl
import icons.PythonIcons.Python.Anaconda

/**
 * @author Aleksey.Rostovskiy
 */
class InstallCondaAction : AnAction(PyBundle.message("action.SetupMiniconda.actionNameWithDots"),
                                    null,
                                    Anaconda),
                           DumbAware {
  private val LOG = Logger.getInstance(InstallCondaAction::class.java)

  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project
    val title = PyBundle.message("action.SetupMiniconda.actionName")

    val dialog = InstallCondaActionDialog(project)
    dialog.show()

    if (!dialog.isOK) return

    val path = dialog.getPathInstallation().also {
      LOG.info("Path is specified to $it")
    }

    object : Task.Backgroundable(project, title) {
      override fun run(indicator: ProgressIndicator) {
        try {
          val handler = InstallCondaActionImpl.installationHandler(path) { line ->
            indicator.text2 = line
          }

          handler.runProcessWithProgressIndicator(indicator).also { LOG.info(it.stdout) }

          when (handler.exitCode) {
            0 -> reportSuccess(project, path)
            137 -> reportFailure(project, PyBundle.message("action.SetupMiniconda.installCanceled"))
            else -> reportFailure(project)
          }
        }
        catch (e: Exception) {
          LOG.warn(e)
          reportFailure(project, e)
        }
      }
    }.queue()
  }

  private fun reportSuccess(project: Project?, path: String) {
    Notifications.Bus.notify(
      Notification(Notifications.SYSTEM_MESSAGES_GROUP_ID,
                   PyBundle.message("action.SetupMiniconda.installSuccess"),
                   "Successfully installed to $path",
                   NotificationType.INFORMATION),
      project)
  }

  private fun reportFailure(project: Project?, e: Exception) {
    return reportFailure(project, ExceptionUtil.getNonEmptyMessage(e, "Internal error"))
  }

  private fun reportFailure(project: Project?, message: String = "Internal error") {
    Notifications.Bus.notify(
      Notification(Notifications.SYSTEM_MESSAGES_GROUP_ID,
                   PyBundle.message("action.SetupMiniconda.installFailed"),
                   message,
                   NotificationType.ERROR),
      project)
  }
}