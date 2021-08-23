// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.BuildSystemType
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.Key
import com.intellij.ui.UIBundle
import com.intellij.ui.layout.*

abstract class KotlinBuildSystemType<P>(override val name: String) : BuildSystemType<P> {
    companion object {
        var EP_NAME = ExtensionPointName<KotlinBuildSystemType<*>>("com.intellij.newProjectWizard.buildSystem.kotlin")
    }
}

class IntelliJKotlinBuildSystemType : KotlinBuildSystemType<IntelliJKotlinBuildSystemSettings>("IntelliJ") {
    override val settingsKey = IntelliJKotlinBuildSystemSettings.KEY
    override fun createSettings() = IntelliJKotlinBuildSystemSettings()

    override fun advancedSettings(settings: IntelliJKotlinBuildSystemSettings, context: WizardContext): DialogPanel =
        panel {
            hideableRow(UIBundle.message("label.project.wizard.new.project.advanced.settings")) {
                row {
                    cell { label(UIBundle.message("label.project.wizard.new.project.module.name")) }
                    cell {
                        textField(settings::moduleName)
                    }
                }

                row {
                    cell { label(UIBundle.message("label.project.wizard.new.project.content.root")) }
                    cell {
                        textFieldWithBrowseButton(
                            settings::contentRoot, UIBundle.message("label.project.wizard.new.project.content.root"), null,
                            FileChooserDescriptorFactory.createSingleFolderDescriptor()
                        )
                    }
                }

                row {
                    cell { label(UIBundle.message("label.project.wizard.new.project.module.file.location")) }
                    cell {
                        textFieldWithBrowseButton(
                            settings::moduleFileLocation,
                            UIBundle.message("label.project.wizard.new.project.module.file.location"), null,
                            FileChooserDescriptorFactory.createSingleFolderDescriptor()
                        )
                    }
                }
            }.largeGapAfter()
        }

    override fun setupProject(project: Project, settings: IntelliJKotlinBuildSystemSettings, context: WizardContext) {
        TODO()
    }
}

class IntelliJKotlinBuildSystemSettings {
    var moduleName: String = ""
    var contentRoot: String = ""
    var moduleFileLocation: String = ""

    companion object {
        val KEY = Key.create<IntelliJKotlinBuildSystemSettings>(IntelliJKotlinBuildSystemSettings::class.java.name)
    }
}