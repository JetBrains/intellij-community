// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard

import com.intellij.ide.JavaUiBundle
import com.intellij.ide.projectWizard.generators.AbstractNewProjectWizardSdkStep
import com.intellij.ide.wizard.*
import com.intellij.openapi.projectRoots.SdkTypeId

class KotlinNewProjectWizard : NewProjectWizard {
    override val name: String = "Kotlin"

    override fun createStep(parent: NewProjectStep.Step) = Step(parent, SdkStep(parent))

    class Step(
        parent: NewProjectStep.Step,
        sdkStep: SdkStep
    ) : AbstractNewProjectWizardMultiStep<NewProjectStep.Step, Step>(parent, KotlinBuildSystemType.EP_NAME),
        NewProjectWizardBuildSystemData,
        NewProjectWizardLanguageData by parent {

        override val self = this

        override val label = JavaUiBundle.message("label.project.wizard.new.project.build.system")

        override val commonSteps = listOf(sdkStep)

        override val buildSystemProperty by ::stepProperty
        override val buildSystem by ::step
    }

    class SdkStep(parent: NewProjectStep.Step) : AbstractNewProjectWizardSdkStep(parent) {
        override fun sdkTypeFilter(type: SdkTypeId) = true
    }
}
