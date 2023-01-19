// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.wizards

import com.intellij.openapi.externalSystem.importing.AbstractOpenProjectProvider
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil.confirmLinkingUntrustedProject
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.ModulesProvider
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.projectImport.ProjectImportBuilder
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenUtil

class MavenOpenProjectProvider : AbstractOpenProjectProvider() {
  override val systemId: ProjectSystemId = MavenUtil.SYSTEM_ID

  val builder: MavenProjectBuilder
    get() = ProjectImportBuilder.EXTENSIONS_POINT_NAME.findExtensionOrFail(MavenProjectBuilder::class.java)

  override fun isProjectFile(file: VirtualFile): Boolean {
    return MavenUtil.isPomFile(file)
  }

  override fun linkToExistingProject(projectFile: VirtualFile, project: Project) {
    LOG.debug("Link Maven project '$projectFile' to existing project ${project.name}")

    val projectRoot = if (projectFile.isDirectory) projectFile else projectFile.parent

    if (confirmLinkingUntrustedProject(project, systemId, projectRoot.toNioPath())) {
      val builder = builder
      try {
        builder.isUpdate = MavenProjectsManager.getInstance(project).isMavenizedProject
        builder.setFileToImport(projectFile)
        if (builder.validate(null, project)) {
          builder.commit(project, null, ModulesProvider.EMPTY_MODULES_PROVIDER)
        }
      }
      finally {
        builder.cleanup()
      }
    }
  }
}