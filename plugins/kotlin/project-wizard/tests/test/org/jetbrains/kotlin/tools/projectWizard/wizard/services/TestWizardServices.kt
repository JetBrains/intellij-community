// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.tools.projectWizard.wizard.services

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.tools.projectWizard.cli.KotlinVersionProviderTestWizardService
import org.jetbrains.kotlin.tools.projectWizard.cli.TestWizardService
import org.jetbrains.kotlin.tools.projectWizard.wizard.service.IdeaJvmTargetVersionProviderService

object TestWizardServices {
    fun createProjectDependent(project: Project): List<TestWizardService> = listOf(
        GradleProjectImportingTestWizardService(project)
    )

    val PROJECT_INDEPENDENT = listOf(
        FormattingTestWizardService(),
        KotlinVersionProviderTestWizardService(),
        IdeaJvmTargetVersionProviderService()
    )
}