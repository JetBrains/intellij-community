package com.intellij.python.pyproject.model.internal

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectAware
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectId
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectListener
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectReloadContext
import com.intellij.openapi.project.Project
import java.nio.file.Path

internal class PyExternalSystemProjectAware(override val projectId: ExternalSystemProjectId, override val settingsFiles: Set<String>, private val project: Project, private val projectModelRoot: Path) : ExternalSystemProjectAware {

  override fun subscribe(listener: ExternalSystemProjectListener, parentDisposable: Disposable) {
    project.messageBus.connect(parentDisposable).subscribe(PROJECT_AWARE_TOPIC, listener)
  }

  override fun reloadProject(context: ExternalSystemProjectReloadContext) {
    linkProjectWithProgressInBackground(project, projectModelRoot)
  }
}