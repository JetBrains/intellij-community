// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.wizards

import com.intellij.ide.projectWizard.generators.JavaBuildSystemType
import com.intellij.ide.projectWizard.generators.JavaNewProjectWizard
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.project.Project
import com.intellij.ui.layout.*
import org.jetbrains.idea.maven.model.MavenId

class MavenJavaBuildSystemType : JavaBuildSystemType {
  override val name = "Maven"

  override fun createStep(context: WizardContext) = Step(context)

  class Step(context: WizardContext) : NewProjectWizardStep(context) {
    var groupId: String = "org.example"
    var artifactId: String = ""
    var version: String = "1.0-SNAPSHOT"

    override fun setupUI(builder: RowBuilder) {
      with(builder) {
        hideableRow(MavenWizardBundle.message("label.project.wizard.new.project.advanced.settings.title")) {
          row(MavenWizardBundle.message("label.project.wizard.new.project.group.id")) {
            textField(::groupId)
          }
          row(MavenWizardBundle.message("label.project.wizard.new.project.artifact.id")) {
            textField(::artifactId)
          }
        }
      }
    }

    override fun setupProject(project: Project) {
      val builder = InternalMavenModuleBuilder().apply {
        moduleJdk = JavaNewProjectWizard.SdkStep.getSdk(context)

        parentProject = null
        aggregatorProject = null
        projectId = MavenId(groupId, artifactId, version)
        isInheritGroupId = false
        isInheritVersion = false
      }

      ExternalProjectsManagerImpl.setupCreatedProject(project)
      builder.commit(project)
    }
  }
}