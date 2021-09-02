// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard

import com.intellij.ide.JavaUiBundle
import com.intellij.ide.projectWizard.generators.AbstractNewProjectWizardSdkStep
import com.intellij.ide.wizard.*
import com.intellij.openapi.projectRoots.SdkTypeId

class KotlinNewProjectWizard : NewProjectWizard {
    override val name: String = "Kotlin"

    override fun createStep(parent: NewProjectWizardLanguageStep) = Step(parent, SdkStep(parent))

    class Step(
        parent: NewProjectWizardLanguageStep,
        override val commonStep: SdkStep
    ) : AbstractNewProjectWizardMultiStep<NewProjectWizardLanguageStep, Step>(parent, KotlinBuildSystemType.EP_NAME),
        NewProjectWizardBuildSystemData,
        NewProjectWizardLanguageData by parent {

        override val self = this

        override val label = JavaUiBundle.message("label.project.wizard.new.project.build.system")

        override val buildSystemProperty by ::stepProperty
        override val buildSystem by ::step
    }

    class SdkStep(parent: NewProjectWizardLanguageStep) : AbstractNewProjectWizardSdkStep(parent) {
        override fun sdkTypeFilter(type: SdkTypeId) = true
    }
}
