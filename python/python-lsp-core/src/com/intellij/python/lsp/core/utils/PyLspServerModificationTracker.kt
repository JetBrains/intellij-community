package com.intellij.python.lsp.core.utils

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SimpleModificationTracker

@Service(Service.Level.PROJECT)
class PyLspServerModificationTracker: SimpleModificationTracker() {
  companion object {
    fun getInstance(project: Project): PyLspServerModificationTracker = project.service()
  }
}