// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.wizards

import com.intellij.ide.projectWizard.generators.BuildSystemJavaNewProjectWizard
import com.intellij.ide.projectWizard.generators.JavaNewProjectWizard
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.project.Project
import com.intellij.util.io.systemIndependentPath
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.utils.MavenUtil

class MavenJavaNewProjectWizard : BuildSystemJavaNewProjectWizard {
  override val name = MavenUtil.SYSTEM_ID.readableName

  override fun createStep(parent: JavaNewProjectWizard.Step) =
    object : MavenNewProjectWizardStep<JavaNewProjectWizard.Step>(parent) {
      override fun setupProject(project: Project) {
        val builder = InternalMavenModuleBuilder().apply {
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
}