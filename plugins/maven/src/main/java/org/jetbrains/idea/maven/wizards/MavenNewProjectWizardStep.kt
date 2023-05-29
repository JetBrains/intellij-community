// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.wizards

import com.intellij.ide.JavaUiBundle
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logSdkChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logSdkFinished
import com.intellij.ide.wizard.NewProjectWizardBaseData
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.setupProjectFromBuilder
import com.intellij.openapi.externalSystem.service.project.wizard.MavenizedNewProjectWizardStep
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.externalSystem.util.ui.DataView
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.projectRoots.impl.DependentSdkType
import com.intellij.openapi.roots.ui.configuration.sdkComboBox
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.ValidationInfoBuilder
import icons.OpenapiIcons
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import javax.swing.Icon

abstract class MavenNewProjectWizardStep<ParentStep>(parent: ParentStep) :
  MavenizedNewProjectWizardStep<MavenProject, ParentStep>(parent),
  MavenNewProjectWizardData
  where ParentStep : NewProjectWizardStep,
        ParentStep : NewProjectWizardBaseData {

  final override val sdkProperty = propertyGraph.property<Sdk?>(null)

  final override var sdk by sdkProperty

  protected fun setupJavaSdkUI(builder: Panel) {
    builder.row(JavaUiBundle.message("label.project.wizard.new.project.jdk")) {
      val sdkTypeFilter = { it: SdkTypeId -> it is JavaSdkType && it !is DependentSdkType }
      sdkComboBox(context, sdkProperty, StdModuleTypes.JAVA.id, sdkTypeFilter)
        .columns(COLUMNS_MEDIUM)
        .whenItemSelectedFromUi { logSdkChanged(sdk) }
        .onApply { logSdkFinished(sdk) }
    }.bottomGap(BottomGap.SMALL)
  }

  override fun createView(data: MavenProject) = MavenDataView(data)

  override fun findAllParents(): List<MavenProject> {
    val project = context.project ?: return emptyList()
    val projectsManager = MavenProjectsManager.getInstance(project)
    return projectsManager.projects
  }

  override fun ValidationInfoBuilder.validateGroupId(): ValidationInfo? {
    return validateCoordinates()
  }

  override fun ValidationInfoBuilder.validateArtifactId(): ValidationInfo? {
    return validateCoordinates()
  }

  private fun ValidationInfoBuilder.validateCoordinates(): ValidationInfo? {
    val mavenIds = parentsData.map { it.mavenId.groupId to it.mavenId.artifactId }.toSet()
    if (groupId to artifactId in mavenIds) {
      val message = ExternalSystemBundle.message(
        "external.system.mavenized.structure.wizard.entity.coordinates.already.exists.error",
        context.isCreatingNewProjectInt,
        "$groupId:$artifactId"
      )
      return error(message)
    }
    return null
  }

  protected fun <T : AbstractMavenModuleBuilder> linkMavenProject(project: Project, builder: T, configure: (T) -> Unit = {}): Module? {
    builder.moduleJdk = sdk
    builder.name = parentStep.name
    builder.contentEntryPath = "${parentStep.path}/${parentStep.name}"

    builder.isCreatingNewProject = context.isCreatingNewProject

    builder.parentProject = parentData
    builder.aggregatorProject = parentData
    builder.projectId = MavenId(groupId, artifactId, version)
    builder.isInheritGroupId = parentData?.mavenId?.groupId == groupId
    builder.isInheritVersion = parentData?.mavenId?.version == version

    configure(builder)

    return setupProjectFromBuilder(project, builder)
  }

  class MavenDataView(override val data: MavenProject) : DataView<MavenProject>() {
    override val location: String = data.directory
    override val icon: Icon = OpenapiIcons.RepositoryLibraryLogo
    override val presentationName: String = data.displayName
    override val groupId: String = data.mavenId.groupId ?: ""
    override val version: String = data.mavenId.version ?: ""
  }
}