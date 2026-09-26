package com.intellij.performanceTesting.freezes

import com.intellij.diagnostic.ExceptionAutoReportService
import com.intellij.diagnostic.ExceptionAutoReportUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class ErrorAutoReporterStateInternalAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
      e.presentation.isVisible = application.isInternal && e.project != null
      e.presentation.isEnabled = true
    }

    override fun actionPerformed(e: AnActionEvent) {
      val project = e.project ?: return
      e.coroutineScope.launch {
        val message = computeMessage()

        withContext(Dispatchers.EDT) {
          val file = LightVirtualFile("Error reporting status", PlainTextFileType.INSTANCE, message)
          FileEditorManager.getInstance(project).openFile(file, true, true)
        }
      }
    }

    private suspend fun computeMessage(): String {
      if (ExceptionAutoReportUtil.autoReportIsForbiddenForProduct) {
        return "Auto report is forbidden for this product"
      }
      if (!ExceptionAutoReportUtil.isAutoReportVisible()) {
        return "Auto report feature is invisible"
      }
      if (!ExceptionAutoReportUtil.isAutoReportEnabled()) {
        return "Auto report is disabled"
      }

      val resendAttempts = serviceOrNull<ExceptionAutoReportService>()?.getResendAttempts() ?: -1
      return when (resendAttempts) {
        -1 -> "No attempts to send exceptions have been made by now"
        0 -> "The latest batch of exceptions was successfully sent"
        else -> "$resendAttempts attempts to send made"
      }
    }
  }