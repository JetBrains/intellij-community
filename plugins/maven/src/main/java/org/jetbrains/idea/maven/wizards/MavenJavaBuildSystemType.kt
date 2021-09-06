// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.wizards

import com.intellij.ide.projectWizard.generators.JavaBuildSystemType
import com.intellij.ide.projectWizard.generators.JavaNewProjectWizard
import com.intellij.ide.wizard.AbstractNewProjectWizardChildStep
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import org.jetbrains.idea.maven.model.MavenId

class MavenJavaBuildSystemType : JavaBuildSystemType {
  override val name = "Maven"

  override fun createStep(parent: JavaNewProjectWizard.Step) = Step(parent)

  class Step(parent: JavaNewProjectWizard.Step) : AbstractNewProjectWizardChildStep<JavaNewProjectWizard.Step>(parent) {
    var groupId: String = "org.example"
    var artifactId: String = ""
    var version: String = "1.0-SNAPSHOT"

    override fun setupUI(builder: Panel) {
      with(builder) {
        hideableGroup(MavenWizardBundle.message("label.project.wizard.new.project.advanced.settings.title")) {
          row(MavenWizardBundle.message("label.project.wizard.new.project.group.id")) {
            textField()
              .bindText(::groupId)
              .horizontalAlign(HorizontalAlign.FILL)
          }
          row(MavenWizardBundle.message("label.project.wizard.new.project.artifact.id")) {
            textField()
              .bindText(::artifactId)
              .horizontalAlign(HorizontalAlign.FILL)
          }
        }
      }
    }

    override fun setupProject(project: Project) {
      val builder = InternalMavenModuleBuilder().apply {
        moduleJdk = parentStep.sdk

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