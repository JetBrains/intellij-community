// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.wizards

import com.intellij.ide.wizard.NewProjectWizardBaseData
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.externalSystem.service.project.wizard.MavenizedNewProjectWizardStep
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.externalSystem.util.ui.DataView
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.layout.*
import icons.OpenapiIcons
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import javax.swing.Icon

abstract class MavenBuildSystemStep<ParentStep>(parent: ParentStep)
  : MavenizedNewProjectWizardStep<MavenProject, ParentStep>(parent)
  where ParentStep : NewProjectWizardBaseData, ParentStep : NewProjectWizardStep {

  override fun createView(data: MavenProject) = MavenDataView(data)

  override fun findAllParents(): List<MavenProject> {
    val project = context.project ?: return emptyList()
    val projectsManager = MavenProjectsManager.getInstance(project)
    return projectsManager.projects
  }

  override fun ValidationInfoBuilder.validateGroupId(): ValidationInfo? {
    if (groupId.isEmpty()) {
      return error(ExternalSystemBundle.message("external.system.mavenized.structure.wizard.group.id.missing.error",
        if (context.isCreatingNewProject) 1 else 0))
    }
    return validateCoordinates()
  }

  override fun ValidationInfoBuilder.validateArtifactId(): ValidationInfo? {
    if (artifactId.isEmpty()) {
      return error(ExternalSystemBundle.message("external.system.mavenized.structure.wizard.artifact.id.missing.error",
        if (context.isCreatingNewProject) 1 else 0))
    }
    return validateCoordinates()
  }

  private fun ValidationInfoBuilder.validateCoordinates(): ValidationInfo? {
    val mavenIds = parentsData.map { it.mavenId.groupId to it.mavenId.artifactId }.toSet()
    if (groupId to artifactId in mavenIds) {
      val message = ExternalSystemBundle.message("external.system.mavenized.structure.wizard.entity.coordinates.already.exists.error",
        if (context.isCreatingNewProject) 1 else 0, "$groupId:$artifactId")
      return error(message)
    }
    return null
  }

  override fun ValidationInfoBuilder.validateVersion(): ValidationInfo? {
    if (version.isEmpty()) {
      return error(ExternalSystemBundle.message("external.system.mavenized.structure.wizard.version.missing.error",
        if (context.isCreatingNewProject) 1 else 0))
    }
    return null
  }


  class MavenDataView(override val data: MavenProject) : DataView<MavenProject>() {
    override val location: String = data.directory
    override val icon: Icon = OpenapiIcons.RepositoryLibraryLogo
    override val presentationName: String = data.displayName
    override val groupId: String = data.mavenId.groupId ?: ""
    override val version: String = data.mavenId.version ?: ""
  }
}