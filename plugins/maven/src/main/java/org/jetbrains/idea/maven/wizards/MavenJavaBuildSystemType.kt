// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.wizards

import com.intellij.ide.projectWizard.generators.JavaBuildSystemType
import com.intellij.ide.projectWizard.generators.JavaSettings
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.Key
import com.intellij.ui.layout.*
import org.jetbrains.idea.maven.model.MavenId

class MavenJavaBuildSystemType : JavaBuildSystemType<MavenJavaBuildSystemSettings>("Maven") {
  override val settingsKey = MavenJavaBuildSystemSettings.KEY

  override fun createSettings() = MavenJavaBuildSystemSettings()

  override fun setupProject(project: Project, settings: MavenJavaBuildSystemSettings, context: WizardContext) {
    val languageSettings = JavaSettings.KEY.get(context)

    val builder = InternalMavenModuleBuilder().apply {
      moduleJdk = languageSettings.sdk

      parentProject = null
      aggregatorProject = null
      projectId = MavenId(settings.groupId, settings.artifactId, settings.version)
      isInheritGroupId = false
      isInheritVersion = false
    }

    ExternalProjectsManagerImpl.setupCreatedProject(project)
    builder.commit(project)
  }

  override fun advancedSettings(settings: MavenJavaBuildSystemSettings, context: WizardContext): DialogPanel =
    panel {
      hideableRow(MavenWizardBundle.message("label.project.wizard.new.project.advanced.settings.title")) {
        row(MavenWizardBundle.message("label.project.wizard.new.project.group.id")) {
          textField(settings::groupId)
        }
        row(MavenWizardBundle.message("label.project.wizard.new.project.artifact.id")) {
          textField(settings::artifactId)
        }
        row(MavenWizardBundle.message("label.project.wizard.new.project.version")) {
          textField(settings::version)
        }
      }
    }
}

class MavenJavaBuildSystemSettings {
  var groupId: String = ""
  var artifactId: String = ""
  var version: String = ""

  companion object {
    val KEY = Key.create<MavenJavaBuildSystemSettings>(MavenJavaBuildSystemSettings::class.java.name)
  }
}