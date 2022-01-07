// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.plugins.groovy.wizard

import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.project.Project
import com.intellij.util.io.systemIndependentPath
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.idea.maven.wizards.MavenNewProjectWizardStep
import org.jetbrains.plugins.groovy.config.wizard.BuildSystemGroovyNewProjectWizard
import org.jetbrains.plugins.groovy.config.wizard.GroovyNewProjectWizard

class MavenGroovyNewProjectWizard : BuildSystemGroovyNewProjectWizard {
  override val name = MAVEN

  override fun createStep(parent: GroovyNewProjectWizard.Step) = Step(parent)

  class Step(parent: GroovyNewProjectWizard.Step) : MavenNewProjectWizardStep<GroovyNewProjectWizard.Step>(parent) {
    override fun setupProject(project: Project) {
      val builder = MavenGroovyNewProjectBuilder().apply {
        moduleJdk = sdk
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
  }

  companion object {
    @JvmField
    val MAVEN = MavenUtil.SYSTEM_ID.readableName
  }
}