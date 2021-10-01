// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.wizards

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.externalSystem.service.project.wizard.MavenizedStructureWizardStep
import com.intellij.openapi.externalSystem.util.ui.DataView
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.io.FileUtil.createSequentFileName
import com.intellij.ui.layout.*
import icons.OpenapiIcons
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import java.io.File
import javax.swing.Icon

class MavenStructureWizardStep(
  private val builder: AbstractMavenModuleBuilder,
  context: WizardContext
) : MavenizedStructureWizardStep<MavenProject>(context) {

  override fun getHelpId() = "reference.dialogs.new.project.fromScratch.maven"

  override fun getBuilderId(): String? = builder.builderId

  override fun createView(data: MavenProject) = MavenDataView(data)

  override fun findAllParents(): List<MavenProject> {
    val project = context.project ?: return emptyList()
    val projectsManager = MavenProjectsManager.getInstance(project)
    return projectsManager.projects
  }

  override fun updateProjectData() {
    context.projectBuilder = builder
    builder.aggregatorProject = parentData
    builder.parentProject = parentData
    builder.projectId = MavenId(groupId, artifactId, version)
    builder.setInheritedOptions(
      parentData?.mavenId?.groupId == groupId,
      parentData?.mavenId?.version == version
    )
    builder.name = entityName
    builder.contentEntryPath = location
  }

  override fun _init() {
    builder.name?.let { entityName = it }
    builder.projectId?.let { projectId ->
      projectId.groupId?.let { groupId = it }
      projectId.artifactId?.let { artifactId = it }
      projectId.version?.let { version = it }
    }
  }

  override fun suggestName(): String {
    val projectFileDirectory = File(context.projectFileDirectory)
    val moduleNames = findAllModules().map { it.name }.toSet()
    val artifactIds = parentsData.map { it.mavenId.artifactId }.toSet()
    return createSequentFileName(projectFileDirectory, "untitled", "") {
      !it.exists() && it.name !in moduleNames && it.name !in artifactIds
    }
  }

  override fun ValidationInfoBuilder.validateGroupId(): ValidationInfo? {
    return validateCoordinates() ?: superValidateGroupId()
  }

  override fun ValidationInfoBuilder.validateArtifactId(): ValidationInfo? {
    return validateCoordinates() ?: superValidateArtifactId()
  }

  private fun ValidationInfoBuilder.validateCoordinates(): ValidationInfo? {
    val mavenIds = parentsData.map { it.mavenId.groupId to it.mavenId.artifactId }.toSet()
    if (groupId to artifactId in mavenIds) {
      val message = MavenWizardBundle.message("maven.structure.wizard.entity.coordinates.already.exists.error",
                                              context.presentationName.capitalize(), "$groupId:$artifactId")
      return error(message)
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