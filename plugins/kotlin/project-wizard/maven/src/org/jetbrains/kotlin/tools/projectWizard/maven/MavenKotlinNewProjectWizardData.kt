// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.projectWizard.maven

import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.util.Key
import org.jetbrains.idea.maven.wizards.MavenNewProjectWizardData
import org.jetbrains.kotlin.tools.projectWizard.KotlinOnboardingProjectWizardData

interface MavenKotlinNewProjectWizardData : MavenNewProjectWizardData, KotlinOnboardingProjectWizardData {
    companion object {

        val KEY = Key.create<MavenKotlinNewProjectWizardData>(MavenKotlinNewProjectWizardData::class.java.name)

        @JvmStatic
        val NewProjectWizardStep.kotlinMavenData: MavenKotlinNewProjectWizardData?
            get() = data.getUserData(KEY)
    }
}