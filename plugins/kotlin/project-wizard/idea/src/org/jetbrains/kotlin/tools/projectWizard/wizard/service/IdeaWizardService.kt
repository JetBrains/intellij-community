// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.tools.projectWizard.wizard.service

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.tools.projectWizard.core.service.WizardService

interface IdeaWizardService : WizardService {
    companion object {
        val EP_NAME: ExtensionPointName<IdeaWizardService> =
            ExtensionPointName.create("org.jetbrains.kotlin.idea.ideaWizardService")
    }
}

object IdeaServices {
    val PROJECT_INDEPENDENT: List<IdeaWizardService> = listOf(
        IdeaFileSystemWizardService(),
        IdeaBuildSystemAvailabilityWizardService(),
        IdeaKotlinVersionProviderService(),
        IdeaJvmTargetVersionProviderService(),
        IdeaSettingSavingWizardService(),
        IdeaVelocityEngineTemplateService()
    )

    fun createScopeDependent(project: Project): List<IdeaWizardService> {
        return IdeaWizardService.EP_NAME.getExtensionList(project)
    }
}
