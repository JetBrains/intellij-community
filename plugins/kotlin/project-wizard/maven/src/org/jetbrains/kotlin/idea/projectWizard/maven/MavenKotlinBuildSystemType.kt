// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.projectWizard.maven

import com.intellij.ide.wizard.AbstractNewProjectWizardChildStep
import com.intellij.openapi.project.Project
import com.intellij.ui.layout.*
import org.jetbrains.idea.maven.wizards.MavenWizardBundle
import org.jetbrains.kotlin.tools.projectWizard.KotlinBuildSystemType
import org.jetbrains.kotlin.tools.projectWizard.KotlinNewProjectWizard

class MavenKotlinBuildSystemType : KotlinBuildSystemType {
    override val name = "Maven"

    override fun createStep(parent: KotlinNewProjectWizard.Step) = Step(parent)

    class Step(parent: KotlinNewProjectWizard.Step) : AbstractNewProjectWizardChildStep<KotlinNewProjectWizard.Step>(parent) {
        var groupId: String = ""
        var artifactId: String = ""

        override fun setupUI(builder: RowBuilder) {
            with(builder) {
                hideableRow(MavenWizardBundle.message("label.project.wizard.new.project.advanced.settings.title")) {
                    row(MavenWizardBundle.message("label.project.wizard.new.project.group.id")) {
                        textField(::groupId)
                    }
                    row(MavenWizardBundle.message("label.project.wizard.new.project.artifact.id")) {
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

