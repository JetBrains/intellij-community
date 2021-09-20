// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard

import com.intellij.ide.wizard.AbstractNewProjectWizardChildStep
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.Panel

class IntelliJKotlinBuildSystemType : KotlinBuildSystemType {
    override val name = "IntelliJ"

    override fun createStep(parent: KotlinNewProjectWizard.Step) = Step(parent)

    class Step(parent: KotlinNewProjectWizard.Step) : AbstractNewProjectWizardChildStep<KotlinNewProjectWizard.Step>(parent) {
        override fun setupUI(builder: Panel) {}

        override fun setupProject(project: Project) {
            TODO("Not yet implemented")
        }
    }
}