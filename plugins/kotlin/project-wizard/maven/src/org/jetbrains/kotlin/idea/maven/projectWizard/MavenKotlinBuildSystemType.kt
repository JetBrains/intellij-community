// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.maven.projectWizard

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardStepSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.ui.layout.*
import org.jetbrains.idea.maven.wizards.MavenWizardBundle
import org.jetbrains.kotlin.tools.projectWizard.KotlinBuildSystemType

class MavenKotlinBuildSystemType : KotlinBuildSystemType {
    override val name = "Maven"

    override fun createStep(context: WizardContext) = Step(context)

    class Step(context: WizardContext) : NewProjectWizardStep<Settings> {
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
                }.largeGapAfter()
            }
        }

        override fun setupProject(project: Project) {
            TODO("Not yet implemented")
        }
    }

    class Settings(context: WizardContext) : NewProjectWizardStepSettings<Settings>(KEY, context) {
        var groupId: String = ""
        var artifactId: String = ""
        var version: String = ""

        companion object {
            val KEY = Key.create<Settings>(Settings::class.java.name)
        }
    }
}

