package com.intellij.python.pyproject.model.internal

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.externalSystem.autolink.ExternalSystemProjectLinkListener
import com.intellij.openapi.externalSystem.autolink.ExternalSystemUnlinkedProjectAware
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.io.IOException
import java.nio.file.Path


internal class PyExternalSystemUnlinkedProjectAware : ExternalSystemUnlinkedProjectAware {
  override val systemId: ProjectSystemId = SYSTEM_ID

  override fun isBuildFile(project: Project, buildFile: VirtualFile): Boolean = PyOpenProjectProvider.canOpenProject(buildFile)

  override fun isLinkedProject(project: Project, externalProjectPath: String): Boolean = isProjectLinked(project)

  override suspend fun unlinkProject(project: Project, externalProjectPath: String) {
    unlinkProjectWithProgress(project, externalProjectPath)
  }

  override suspend fun linkAndLoadProjectAsync(project: Project, externalProjectPath: String) {
    val path = try {
      Path.of(externalProjectPath)
    }
    catch (e: IOException) {
      logger.warn("Provided path is wrong, probably workspace is broken: ${externalProjectPath}", e)
      null
    }
    linkProjectWithProgressInBackground(project, path)
  }

  override fun subscribe(project: Project, listener: ExternalSystemProjectLinkListener, parentDisposable: Disposable) {
    project.messageBus.connect(parentDisposable).subscribe(PROJECT_LINKER_AWARE_TOPIC, listener)
  }
}

private val logger = fileLogger()