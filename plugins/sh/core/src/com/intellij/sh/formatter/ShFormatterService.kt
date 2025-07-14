package com.intellij.sh.formatter

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project

interface ShFormatterService {
  fun isValidPath(path: String?): Boolean

  fun download(project: Project, onSuccess: Runnable, onFailure: Runnable)

  companion object {
    @JvmStatic
    fun getInstance(): ShFormatterService = ApplicationManager.getApplication().getService(ShFormatterService::class.java)
  }
}