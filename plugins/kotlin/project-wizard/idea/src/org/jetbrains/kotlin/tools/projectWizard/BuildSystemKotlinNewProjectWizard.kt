// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard

import com.intellij.ide.wizard.NewProjectWizardMultiStepFactory
import com.intellij.openapi.extensions.ExtensionPointName

interface BuildSystemKotlinNewProjectWizard : NewProjectWizardMultiStepFactory<KotlinNewProjectWizard.Step> {
    companion object {
        const val DEFAULT_KOTLIN_VERSION = "1.9.0"

        var EP_NAME = ExtensionPointName<BuildSystemKotlinNewProjectWizard>("com.intellij.newProjectWizard.kotlin.buildSystem")
    }
}
