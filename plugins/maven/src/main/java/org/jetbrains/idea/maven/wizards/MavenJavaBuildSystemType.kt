// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.wizards

import com.intellij.ide.projectWizard.generators.JavaBuildSystemType
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.Key
import com.intellij.ui.layout.*

class MavenJavaBuildSystemType : JavaBuildSystemType<MavenJavaBuildSystemSettings>("Maven") {
  override val settingsKey = MavenJavaBuildSystemSettings.KEY

  override fun createSettings() = MavenJavaBuildSystemSettings()

  override fun setupProject(project: Project, settings: MavenJavaBuildSystemSettings, context: WizardContext) {
    TODO("Not yet implemented")
  }

  override fun advancedSettings(settings: MavenJavaBuildSystemSettings, context: WizardContext): DialogPanel =
    panel {
      hideableRow(MavenWizardBundle.message("label.project.wizard.new.project.advanced.settings.title")) {
        row {
          cell { label(MavenWizardBundle.message("label.project.wizard.new.project.group.id")) }
          cell {
            textField(settings::groupId)
          }
        }

        row {
          cell { label(MavenWizardBundle.message("label.project.wizard.new.project.artifact.id")) }
          cell {
            textFieldWithBrowseButton(settings::artifactId, MavenWizardBundle.message("label.project.wizard.new.project.artifact.id"), null,
                                      FileChooserDescriptorFactory.createSingleFolderDescriptor())
          }
        }

        row {
          cell { label(MavenWizardBundle.message("label.project.wizard.new.project.version")) }
          cell {
            textFieldWithBrowseButton(settings::version,
                                      MavenWizardBundle.message("label.project.wizard.new.project.version"), null,
                                      FileChooserDescriptorFactory.createSingleFolderDescriptor())
          }
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