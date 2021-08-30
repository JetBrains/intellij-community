// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard

import com.intellij.ide.JavaUiBundle
import com.intellij.ide.NewProjectWizard
import com.intellij.ide.projectWizard.generators.SdkNewProjectWizardStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.NewProjectWizardMultiStep
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.tools.projectWizard.KotlinNewProjectWizard.Settings

class KotlinNewProjectWizard : NewProjectWizard {
    override val name: String = "Kotlin"

    override fun createStep(context: WizardContext) = Step(context)

    class Step(context: WizardContext) : NewProjectWizardMultiStep<Settings>(context, KotlinBuildSystemType.EP_NAME) {
        override val label = JavaUiBundle.message("label.project.wizard.new.project.build.system")

        override val settings = Settings(context)

        override val commonSteps = listOf(SdkStep(context))
    }

    class Settings(context: WizardContext) : NewProjectWizardMultiStep.Settings<Settings>(KEY, context) {
        companion object {
            val KEY = Key.create<Settings>(Settings::class.java.name)
        }
    }

    class SdkStep(context: WizardContext) : SdkNewProjectWizardStep<SdkSettings>(context) {
        override val settings = SdkSettings(context)

        override fun sdkTypeFilter(type: SdkTypeId) = true
    }

    class SdkSettings(context: WizardContext) : SdkNewProjectWizardStep.Settings<SdkSettings>(KEY, context) {
        companion object {
            val KEY = Key.create<SdkSettings>(SdkSettings::class.java.name)
        }
    }
}
