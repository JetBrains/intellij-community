// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.projectWizard

import com.intellij.ide.projectWizard.generators.IntelliJNewProjectWizardData
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.util.Key

interface IntelliJKotlinNewProjectWizardData : IntelliJNewProjectWizardData, KotlinOnboardingProjectWizardData, BuildSystemKotlinNewProjectWizardData {

    val useCompactProjectStructureProperty: GraphProperty<Boolean>

    var useCompactProjectStructure: Boolean

    companion object {

        val KEY = Key.create<IntelliJKotlinNewProjectWizardData>(IntelliJKotlinNewProjectWizardData::class.java.name)

        @JvmStatic
        val NewProjectWizardStep.kotlinData: IntelliJKotlinNewProjectWizardData?
            get() = data.getUserData(KEY)
    }
}