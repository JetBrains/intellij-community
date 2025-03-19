// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.projectWizard.wizard

import com.intellij.ide.projectWizard.generators.AssetsOnboardingTipsProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.tools.projectWizard.wizard.withKotlinSampleCode as withKotlinSampleCodeImpl
import org.jetbrains.kotlin.tools.projectWizard.wizard.prepareKotlinSampleOnboardingTips as prepareKotlinSampleOnboardingTipsImpl

@Deprecated("Use AssetsKotlin util instead")
abstract class AssetsKotlinNewProjectWizardStep(parent: NewProjectWizardStep) : AssetsOnboardingTipsProjectWizardStep(parent) {

    fun withKotlinSampleCode(sourceRootPath: String, packageName: String?, generateOnboardingTips: Boolean, shouldOpenFile: Boolean = true) =
        withKotlinSampleCodeImpl(sourceRootPath, packageName, generateOnboardingTips, shouldOpenFile)

    fun prepareOnboardingTips(project: Project) =
        prepareKotlinSampleOnboardingTipsImpl(project)
}