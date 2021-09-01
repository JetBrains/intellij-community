// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard

import com.intellij.ide.JavaUiBundle
import com.intellij.ide.projectWizard.generators.SdkNewProjectWizardStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.NewProjectWizard
import com.intellij.ide.wizard.NewProjectWizardMultiStep
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.projectRoots.impl.DependentSdkType
import com.intellij.openapi.util.Key

class KotlinNewProjectWizard : NewProjectWizard {
    override val name: String = "Kotlin"

    override fun createStep(context: WizardContext) = Step(context)

    class Step(context: WizardContext) : NewProjectWizardMultiStep(context, KotlinBuildSystemType.EP_NAME) {
        override val label = JavaUiBundle.message("label.project.wizard.new.project.build.system")

        override val commonSteps = listOf(SdkStep(context))

        init {
            BUILD_SYSTEM_STEP_KEY.set(context, this)
        }
    }

    class SdkStep(context: WizardContext) : SdkNewProjectWizardStep(context) {
        override fun sdkTypeFilter(type: SdkTypeId): Boolean {
            return type is JavaSdkType && type !is DependentSdkType
        }

        init {
            SDK_STEP_KEY.set(context, this)
        }
    }

    @Suppress("unused")
    companion object {
        val BUILD_SYSTEM_STEP_KEY = Key.create<Step>(Step::class.java.name)
        fun getBuildSystemProperty(context: WizardContext) = BUILD_SYSTEM_STEP_KEY.get(context).stepProperty
        fun getBuildSystem(context: WizardContext) = BUILD_SYSTEM_STEP_KEY.get(context).step

        val SDK_STEP_KEY = Key.create<SdkStep>(SdkStep::class.java.name)
        fun getSdkComboBox(context: WizardContext) = SDK_STEP_KEY.get(context).sdkComboBox
        fun getSdkProperty(context: WizardContext) = SDK_STEP_KEY.get(context).sdkProperty
        fun getSdk(context: WizardContext) = SDK_STEP_KEY.get(context).sdk
    }
}
