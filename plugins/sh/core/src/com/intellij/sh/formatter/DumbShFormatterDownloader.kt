package com.intellij.sh.formatter

import com.intellij.openapi.project.Project

internal object DumbShFormatterDownloader : ShFormatterDownloader {
  override fun isValidPath(path: String?): Boolean {
    return true
  }

  override fun download(project: Project, onSuccess: Runnable, onFailure: Runnable) {
    onFailure.run()
  }
}