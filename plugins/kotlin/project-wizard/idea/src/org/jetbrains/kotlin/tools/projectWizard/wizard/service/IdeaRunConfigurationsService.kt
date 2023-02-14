// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.tools.projectWizard.wizard.service

import com.intellij.execution.RunManager
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.externalSystem.KotlinGradleFacade
import org.jetbrains.kotlin.tools.projectWizard.WizardGradleRunConfiguration
import org.jetbrains.kotlin.tools.projectWizard.WizardRunConfiguration
import org.jetbrains.kotlin.tools.projectWizard.core.Reader

import org.jetbrains.kotlin.tools.projectWizard.core.service.RunConfigurationsService
import org.jetbrains.kotlin.tools.projectWizard.core.service.isBuildSystemAvailable
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.BuildSystemType

class IdeaRunConfigurationsService(private val project: Project) : RunConfigurationsService, IdeaWizardService {
    override fun Reader.addRunConfigurations(configurations: List<WizardRunConfiguration>) {
        configurations.forEach { wizardConfiguration ->
            if (wizardConfiguration is WizardGradleRunConfiguration && isBuildSystemAvailable(BuildSystemType.GradleKotlinDsl)) {
                addGradleRunConfiguration(wizardConfiguration)
            }
        }
    }

    private fun addGradleRunConfiguration(wizardConfiguration: WizardGradleRunConfiguration) {
        val runManager = RunManager.getInstance(project)
        val configurationFactory = KotlinGradleFacade.getInstance()?.runConfigurationFactory ?: return
        val ideaConfiguration = runManager.createConfiguration(wizardConfiguration.configurationName, configurationFactory)
        val runConfiguration = ideaConfiguration.configuration
        if (runConfiguration is ExternalSystemRunConfiguration) {
            runConfiguration.settings.apply {
                taskNames = listOf(wizardConfiguration.taskName)
                scriptParameters = wizardConfiguration.parameters.joinToString(separator = " ")
                externalProjectPath = project.basePath
            }
            runManager.apply {
                addConfiguration(ideaConfiguration)
                selectedConfiguration = ideaConfiguration
            }
        }
    }
}