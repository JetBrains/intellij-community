// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.maven.projectWizard

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.layout.*
import org.jetbrains.idea.maven.wizards.MavenWizardBundle
import org.jetbrains.kotlin.tools.projectWizard.KotlinBuildSystemType
import org.jetbrains.kotlin.tools.projectWizard.KotlinSettings

class MavenKotlinBuildSystemType : KotlinBuildSystemType<MavenKotlinBuildSystemSettings>("Maven") {
    override var settingsFactory = { MavenKotlinBuildSystemSettings() }

    override fun setupProject(project: Project, languageSettings: KotlinSettings, settings: MavenKotlinBuildSystemSettings) {
        TODO("Not yet implemented")
    }

    override fun advancedSettings(settings: MavenKotlinBuildSystemSettings): DialogPanel =
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
                        textFieldWithBrowseButton(
                            settings::artifactId, MavenWizardBundle.message("label.project.wizard.new.project.artifact.id"), null,
                            FileChooserDescriptorFactory.createSingleFolderDescriptor()
                        )
                    }
                }

                row {
                    cell { label(MavenWizardBundle.message("label.project.wizard.new.project.version")) }
                    cell {
                        textFieldWithBrowseButton(
                            settings::version,
                            MavenWizardBundle.message("label.project.wizard.new.project.version"), null,
                            FileChooserDescriptorFactory.createSingleFolderDescriptor()
                        )
                    }
                }
            }
        }
}

class MavenKotlinBuildSystemSettings {
    var groupId: String = ""
    var artifactId: String = ""
    var version: String = ""
}

