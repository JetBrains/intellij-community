// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard.core.service

interface WizardService

interface IdeaIndependentWizardService : WizardService

object Services {
    val IDEA_INDEPENDENT_SERVICES: List<IdeaIndependentWizardService> = listOf(
        ProjectImportingWizardServiceImpl(),
        OsFileSystemWizardService(),
        BuildSystemAvailabilityWizardServiceImpl(),
        DummyFileFormattingService(),
        CoreKotlinVersionProviderService(),
        CoreJvmTargetVersionsProviderService(),
        RunConfigurationsServiceImpl(),
        EmptyInspectionWizardService(),
        SettingSavingWizardServiceImpl(),
        VelocityTemplateEngineServiceImpl()
    )
}

