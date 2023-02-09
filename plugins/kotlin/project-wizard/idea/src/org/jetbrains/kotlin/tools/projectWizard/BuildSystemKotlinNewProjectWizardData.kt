// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard

import com.intellij.ide.wizard.BuildSystemNewProjectWizardData
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.util.Key

interface BuildSystemKotlinNewProjectWizardData: BuildSystemNewProjectWizardData {

    companion object {

        val KEY = Key.create<BuildSystemKotlinNewProjectWizardData>(BuildSystemKotlinNewProjectWizardData::class.java.name)

        @JvmStatic
        val NewProjectWizardStep.kotlinBuildSystemData: BuildSystemKotlinNewProjectWizardData?
            get() = data.getUserData(KEY)
    }
}