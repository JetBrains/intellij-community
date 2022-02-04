// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.plugins.groovy.wizard

import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logAddSampleCodeChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logArtifactIdChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logGroupIdChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logParentChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logSdkChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logSdkFinished
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logVersionChanged
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.Panel
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.idea.maven.wizards.MavenNewProjectWizardStep
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.config.wizard.*
import org.jetbrains.plugins.groovy.config.wizard.GroovyNewProjectWizardUsageCollector.Companion.logGroovyLibrarySelected

class MavenGroovyNewProjectWizard : BuildSystemGroovyNewProjectWizard {
  override val name = MAVEN

  override val ordinal: Int = 1

  override fun createStep(parent: GroovyNewProjectWizard.Step) = Step(parent)

  class Step(parent: GroovyNewProjectWizard.Step) :
    MavenNewProjectWizardStep<GroovyNewProjectWizard.Step>(parent),
    BuildSystemGroovyNewProjectWizardData by parent {

    private val addSampleCodeProperty = propertyGraph.property(false)

    var addSampleCode by addSampleCodeProperty

    override fun setupSettingsUI(builder: Panel) {
      super.setupSettingsUI(builder)
      builder.row(GroovyBundle.message("label.groovy.sdk")) { groovySdkComboBox(groovySdkMavenVersionProperty) }
      builder.addSampleCodeCheckbox(addSampleCodeProperty)
    }

    override fun setupProject(project: Project) {
      val builder = MavenGroovyNewProjectBuilder(groovySdkMavenVersion.orElse(GROOVY_SDK_FALLBACK_VERSION)).apply {
        moduleJdk = sdk
        name = parentStep.name
        parentProject = parentData
        contentEntryPath = "${parentStep.path}/${parentStep.name}"
        aggregatorProject = parentData
        projectId = MavenId(groupId, artifactId, version)
        isInheritGroupId = parentData?.mavenId?.groupId == groupId
        isInheritVersion = parentData?.mavenId?.version == version
        createSampleCode = addSampleCode
      }

      ExternalProjectsManagerImpl.setupCreatedProject(project)
      builder.commit(project)

      logSdkFinished(sdk)
    }

    init {
      sdkProperty.afterChange { logSdkChanged(it) }
      parentProperty.afterChange { logParentChanged(!it.isPresent) }
      addSampleCodeProperty.afterChange { logAddSampleCodeChanged() }
      groupIdProperty.afterChange { logGroupIdChanged() }
      artifactIdProperty.afterChange { logArtifactIdChanged() }
      versionProperty.afterChange { logVersionChanged() }
      groovySdkMavenVersionProperty.afterChange { if (it.isPresent) logGroovyLibrarySelected(context, it.get()) }
    }
  }

  companion object {
    @JvmField
    val MAVEN = MavenUtil.SYSTEM_ID.readableName
  }
}