package com.intellij.python.processOutput

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nls

interface ProcessOutputApi {
  companion object {
    private val EP_NAME = ExtensionPointName<ProcessOutputApi>("com.intellij.python.processOutput.processOutputApi")

    fun getInstance(): ProcessOutputApi? =
      EP_NAME.extensionList.firstOrNull()
  }

  fun specifyAdditionalInfo(project: Project, logId: Int, message: @Nls String?, isCritical: Boolean)

  fun tryOpenLogInToolWindow(project: Project, logId: Int): Boolean
}