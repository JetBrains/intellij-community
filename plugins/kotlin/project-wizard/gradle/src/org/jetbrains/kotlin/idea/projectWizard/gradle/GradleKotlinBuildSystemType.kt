// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.projectWizard.gradle

import com.intellij.ide.wizard.AbstractNewProjectWizardChildStep
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import org.jetbrains.kotlin.tools.projectWizard.KotlinBuildSystemType
import org.jetbrains.kotlin.tools.projectWizard.KotlinNewProjectWizard
import org.jetbrains.plugins.gradle.util.GradleBundle

class GradleKotlinBuildSystemType : KotlinBuildSystemType {
    override val name = "Gradle"

    override fun createStep(parent: KotlinNewProjectWizard.Step) = Step(parent)

    class Step(parent: KotlinNewProjectWizard.Step) : AbstractNewProjectWizardChildStep<KotlinNewProjectWizard.Step>(parent) {
        var groupId: String = ""
        var artifactId: String = ""

        override fun setupUI(builder: Panel) {
            with(builder) {
                collapsibleGroup(GradleBundle.message("label.project.wizard.new.project.advanced.settings.title")) {
                    row(GradleBundle.message("label.project.wizard.new.project.group.id")) {
                        textField()
                            .bindText(::groupId)
                            .horizontalAlign(HorizontalAlign.FILL)
                    }
                    row(GradleBundle.message("label.project.wizard.new.project.artifact.id")) {
                        textField()
                            .bindText(::artifactId)
                            .horizontalAlign(HorizontalAlign.FILL)
                    }
                }
            }
        }

        override fun setupProject(project: Project) {
            TODO("Not yet implemented")
        }
    }
}