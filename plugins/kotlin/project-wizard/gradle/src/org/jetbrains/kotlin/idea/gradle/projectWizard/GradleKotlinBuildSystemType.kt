// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradle.projectWizard

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.Key
import com.intellij.ui.layout.*
import org.jetbrains.kotlin.tools.projectWizard.KotlinBuildSystemType
import org.jetbrains.plugins.gradle.util.GradleBundle

class GradleKotlinBuildSystemType : KotlinBuildSystemType<GradleKotlinBuildSystemSettings>("Gradle") {
    override val settingsKey = GradleKotlinBuildSystemSettings.KEY
    override fun createSettings() = GradleKotlinBuildSystemSettings()

    override fun setupProject(project: Project, settings: GradleKotlinBuildSystemSettings, context: WizardContext) {
        TODO("Not yet implemented")
    }

    override fun advancedSettings(settings: GradleKotlinBuildSystemSettings, context: WizardContext): DialogPanel =
        panel {
            hideableRow(GradleBundle.message("label.project.wizard.new.project.advanced.settings.title")) {
                row {
                    cell { label(GradleBundle.message("label.project.wizard.new.project.group.id")) }
                    cell {
                        textField(settings::groupId)
                    }
                }

                row {
                    cell { label(GradleBundle.message("label.project.wizard.new.project.artifact.id")) }
                    cell {
                        textFieldWithBrowseButton(
                            settings::artifactId, GradleBundle.message("label.project.wizard.new.project.artifact.id"), null,
                            FileChooserDescriptorFactory.createSingleFolderDescriptor()
                        )
                    }
                }

                row {
                    cell { label(GradleBundle.message("label.project.wizard.new.project.version")) }
                    cell {
                        textFieldWithBrowseButton(
                            settings::version,
                            GradleBundle.message("label.project.wizard.new.project.version"), null,
                            FileChooserDescriptorFactory.createSingleFolderDescriptor()
                        )
                    }
                }
            }.largeGapAfter()
        }
}

class GradleKotlinBuildSystemSettings {
    var groupId: String = ""
    var artifactId: String = ""
    var version: String = ""

    companion object {
        val KEY = Key.create<GradleKotlinBuildSystemSettings>(GradleKotlinBuildSystemSettings::class.java.name)
    }
}