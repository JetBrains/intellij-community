// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard

import com.intellij.ide.projectWizard.NewProjectWizardCollector
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.NewProjectWizardMultiStepFactory
import com.intellij.openapi.extensions.ExtensionPointName

interface BuildSystemKotlinNewProjectWizard : NewProjectWizardMultiStepFactory<KotlinNewProjectWizard.Step> {
    companion object {
        var EP_NAME = ExtensionPointName<BuildSystemKotlinNewProjectWizard>("com.intellij.newProjectWizard.kotlin.buildSystem")
        private val collector = NewProjectWizardCollector.BuildSystemCollector(EP_NAME.extensions.map { it.name })

        @JvmStatic
        fun logBuildSystemChanged(context: WizardContext, name: String) = collector.logBuildSystemChanged(context, name)
    }
}
