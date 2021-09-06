// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.wizards

import com.intellij.ide.projectWizard.generators.JavaBuildSystemType
import com.intellij.ide.projectWizard.generators.JavaNewProjectWizard
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.externalSystem.service.project.wizard.MavenizedNewProjectWizardStep
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.externalSystem.util.ui.DataView
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.layout.*
import com.intellij.util.io.systemIndependentPath
import icons.OpenapiIcons
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenUtil
import javax.swing.Icon

class MavenJavaBuildSystemStep(
  parent: JavaNewProjectWizard.Step
) : MavenizedNewProjectWizardStep<MavenProject, JavaNewProjectWizard.Step>(parent) {

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

  override fun setupProject(project: Project) {
    val builder = InternalMavenModuleBuilder().apply {
      moduleJdk = parentStep.sdk
      name = parentStep.name
      contentEntryPath = parentStep.projectPath.systemIndependentPath

      parentProject = parentData
      aggregatorProject = parentData
      projectId = MavenId(groupId, artifactId, version)
      isInheritGroupId = parentData?.mavenId?.groupId == groupId
      isInheritVersion = parentData?.mavenId?.version == version
    }

    ExternalProjectsManagerImpl.setupCreatedProject(project)
    builder.commit(project)
  }

  class MavenDataView(override val data: MavenProject) : DataView<MavenProject>() {
    override val location: String = data.directory
    override val icon: Icon = OpenapiIcons.RepositoryLibraryLogo
    override val presentationName: String = data.displayName
    override val groupId: String = data.mavenId.groupId ?: ""
    override val version: String = data.mavenId.version ?: ""
  }

  class Factory : JavaBuildSystemType {
    override val name = MavenUtil.SYSTEM_ID.readableName

    override fun createStep(parent: JavaNewProjectWizard.Step) = MavenJavaBuildSystemStep(parent)
  }
}