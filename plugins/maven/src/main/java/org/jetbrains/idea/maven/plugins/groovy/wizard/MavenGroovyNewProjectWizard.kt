// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.plugins.groovy.wizard

import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.Panel
import com.intellij.util.io.systemIndependentPath
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.idea.maven.wizards.MavenNewProjectWizardStep
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.config.wizard.BuildSystemGroovyNewProjectWizard
import org.jetbrains.plugins.groovy.config.wizard.GROOVY_SDK_FALLBACK_VERSION
import org.jetbrains.plugins.groovy.config.wizard.GroovyNewProjectWizard
import org.jetbrains.plugins.groovy.config.wizard.groovySdkComboBox
import java.util.*

class MavenGroovyNewProjectWizard : BuildSystemGroovyNewProjectWizard {
  override val name = MAVEN

  override val ordinal: Int = 1

  override fun createStep(parent: GroovyNewProjectWizard.Step) = Step(parent)

  class Step(parent: GroovyNewProjectWizard.Step) : MavenNewProjectWizardStep<GroovyNewProjectWizard.Step>(parent) {

    private val groovySdkVersionProperty = propertyGraph.graphProperty<Optional<String>> { Optional.empty() }

    private var groovySdkVersion by groovySdkVersionProperty

    override fun setupSettingsUI(builder: Panel) {
      super.setupSettingsUI(builder)
      builder.row(GroovyBundle.message("label.groovy.sdk")) { groovySdkComboBox(groovySdkVersionProperty) }
    }

    override fun setupProject(project: Project) {
      val builder = MavenGroovyNewProjectBuilder(groovySdkVersion.orElse(GROOVY_SDK_FALLBACK_VERSION)).apply {
        moduleJdk = sdk
        name = parentStep.name
        parentProject = parentData
        contentEntryPath = parentStep.projectPath.systemIndependentPath
        aggregatorProject = parentData
        projectId = MavenId(groupId, artifactId, version)
        isInheritGroupId = parentData?.mavenId?.groupId == groupId
        isInheritVersion = parentData?.mavenId?.version == version
        createSampleCode = addSampleCode
      }

      ExternalProjectsManagerImpl.setupCreatedProject(project)
      builder.commit(project)
    }
  }

  companion object {
    @JvmField
    val MAVEN = MavenUtil.SYSTEM_ID.readableName
  }
}