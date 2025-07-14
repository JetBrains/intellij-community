package com.intellij.sh.backend.formatter

import com.intellij.openapi.project.Project
import com.intellij.sh.formatter.ShFormatterService

class ShBackendFormatterService : ShFormatterService {
  override fun isValidPath(path: String?): Boolean {
    return ShShfmtFormatterUtil.isValidPath(path)
  }

  override fun download(project: Project, onSuccess: Runnable, onFailure: Runnable) {
    ShShfmtFormatterUtil.download(project, onSuccess, onFailure)
  }
}