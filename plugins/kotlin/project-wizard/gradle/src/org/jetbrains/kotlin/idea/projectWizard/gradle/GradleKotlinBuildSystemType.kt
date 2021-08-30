// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.projectWizard.gradle

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.project.Project
import com.intellij.ui.layout.*
import org.jetbrains.kotlin.tools.projectWizard.KotlinBuildSystemType
import org.jetbrains.plugins.gradle.util.GradleBundle

class GradleKotlinBuildSystemType : KotlinBuildSystemType {
    override val name = "Gradle"

    override fun createStep(context: WizardContext) = Step(context)

    class Step(context: WizardContext) : NewProjectWizardStep(context) {
        private var groupId: String = ""
        private var artifactId: String = ""

        override fun setupUI(builder: RowBuilder) {
            with(builder) {
                hideableRow(GradleBundle.message("label.project.wizard.new.project.advanced.settings.title")) {
                    row(GradleBundle.message("label.project.wizard.new.project.group.id")) {
                        textField(::groupId)
                    }
                    row(GradleBundle.message("label.project.wizard.new.project.artifact.id")) {
                        textField(::artifactId)
                    }
                }.largeGapAfter()
            }
        }

        override fun setupProject(project: Project) {
            TODO("Not yet implemented")
        }
    }
}