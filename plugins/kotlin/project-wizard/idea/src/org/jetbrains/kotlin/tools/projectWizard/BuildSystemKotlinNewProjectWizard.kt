// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.projectWizard

import com.intellij.ide.wizard.NewProjectWizardMultiStepFactory
import com.intellij.openapi.extensions.ExtensionPointName

interface BuildSystemKotlinNewProjectWizard : NewProjectWizardMultiStepFactory<KotlinNewProjectWizard.Step> {
    companion object {
        const val DEFAULT_KOTLIN_VERSION: String = "2.3.10"

        val EP_NAME: ExtensionPointName<BuildSystemKotlinNewProjectWizard> = ExtensionPointName("com.intellij.newProjectWizard.kotlin.buildSystem")
    }
}
