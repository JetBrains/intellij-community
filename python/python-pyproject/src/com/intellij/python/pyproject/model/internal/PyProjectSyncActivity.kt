package com.intellij.python.pyproject.model.internal

import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

internal class PyProjectSyncActivity : ProjectActivity {
  init {
    if (!projectModelEnabled) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    linkProjectWithProgressInBackground(project)
  }
}