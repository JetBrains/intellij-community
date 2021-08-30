// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.wizards

import com.intellij.ide.projectWizard.generators.JavaBuildSystemType
import com.intellij.ide.projectWizard.generators.JavaNewProjectWizard
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardStepSettings
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.ui.layout.*
import org.jetbrains.idea.maven.model.MavenId

class MavenJavaBuildSystemType : JavaBuildSystemType {
  override val name = "Maven"

  override fun createStep(context: WizardContext) = Step(context)

  class Step(private val context: WizardContext) : NewProjectWizardStep<Settings> {
    override val settings = Settings(context)

    override fun setupUI(builder: RowBuilder) {
      with(builder) {
        hideableRow(MavenWizardBundle.message("label.project.wizard.new.project.advanced.settings.title")) {
          row(MavenWizardBundle.message("label.project.wizard.new.project.group.id")) {
            textField(settings::groupId)
          }
          row(MavenWizardBundle.message("label.project.wizard.new.project.artifact.id")) {
            textField(settings::artifactId)
          }
        }
      }
    }

    override fun setupProject(project: Project) {
      val builder = InternalMavenModuleBuilder().apply {
        moduleJdk = JavaNewProjectWizard.SdkSettings.getSdk(context)

        parentProject = null
        aggregatorProject = null
        projectId = MavenId(settings.groupId, settings.artifactId, settings.version)
        isInheritGroupId = false
        isInheritVersion = false
      }

      ExternalProjectsManagerImpl.setupCreatedProject(project)
      builder.commit(project)
    }
  }

  class Settings(context: WizardContext) : NewProjectWizardStepSettings<Settings>(KEY, context) {
    var groupId: String = "org.example"
    var artifactId: String = ""
    var version: String = "1.0-SNAPSHOT"

    companion object {
      val KEY = Key.create<Settings>(Settings::class.java.name)
    }
  }
}