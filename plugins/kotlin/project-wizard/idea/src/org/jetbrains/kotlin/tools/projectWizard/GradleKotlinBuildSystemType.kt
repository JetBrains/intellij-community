// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.layout.*
import org.jetbrains.plugins.gradle.util.GradleBundle

class GradleKotlinBuildSystemType : KotlinBuildSystemType<GradleKotlinBuildSystemSettings>("Gradle") {
    override var settingsFactory = { GradleKotlinBuildSystemSettings() }

    override fun setupProject(project: Project, languageSettings: KotlinSettings, settings: GradleKotlinBuildSystemSettings) {
        TODO("Not yet implemented")
    }

    override fun advancedSettings(languageSettings: GradleKotlinBuildSystemSettings): DialogPanel =
        panel {
            hideableRow(GradleBundle.message("label.project.wizard.new.project.advanced.settings.title")) {
                row {
                    cell { label(GradleBundle.message("label.project.wizard.new.project.group.id")) }
                    cell {
                        textField(languageSettings::groupId)
                    }
                }

                row {
                    cell { label(GradleBundle.message("label.project.wizard.new.project.artifact.id")) }
                    cell {
                        textFieldWithBrowseButton(
                            languageSettings::artifactId, GradleBundle.message("label.project.wizard.new.project.artifact.id"), null,
                            FileChooserDescriptorFactory.createSingleFolderDescriptor()
                        )
                    }
                }

                row {
                    cell { label(GradleBundle.message("label.project.wizard.new.project.version")) }
                    cell {
                        textFieldWithBrowseButton(
                            languageSettings::version,
                            GradleBundle.message("label.project.wizard.new.project.version"), null,
                            FileChooserDescriptorFactory.createSingleFolderDescriptor()
                        )
                    }
                }
            }
        }
}

class GradleKotlinBuildSystemSettings {
    var groupId: String = ""
    var artifactId: String = ""
    var version: String = ""
}