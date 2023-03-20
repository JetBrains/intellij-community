// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.tools.projectWizard.gradle

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.tools.projectWizard.core.Reader
import org.jetbrains.kotlin.tools.projectWizard.core.TaskResult
import org.jetbrains.kotlin.tools.projectWizard.core.UNIT_SUCCESS
import org.jetbrains.kotlin.tools.projectWizard.core.service.ProjectImportingWizardService
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.ModuleIR
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.BuildSystemType
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.isGradle
import org.jetbrains.kotlin.tools.projectWizard.wizard.service.IdeaWizardService
import org.jetbrains.plugins.gradle.service.project.open.linkAndRefreshGradleProject
import org.jetbrains.plugins.gradle.settings.DistributionType
import org.jetbrains.plugins.gradle.settings.GradleDefaultProjectSettings
import java.nio.file.Path

internal class IdeaGradleWizardService(private val project: Project) : ProjectImportingWizardService, IdeaWizardService {
    override fun isSuitableFor(buildSystemType: BuildSystemType): Boolean = buildSystemType.isGradle

    override fun importProject(
        reader: Reader,
        path: Path,
        modulesIrs: List<ModuleIR>,
        buildSystem: BuildSystemType
    ): TaskResult<Unit> {
        withGradleWrapperEnabled {
            linkAndRefreshGradleProject(path.toString(), project)
        }
        return UNIT_SUCCESS
    }

    private fun withGradleWrapperEnabled(action: () -> Unit) {
        val settings = GradleDefaultProjectSettings.getInstance()
        val oldGradleDistributionType = settings.distributionType
        settings.distributionType = DistributionType.WRAPPED
        try {
            action()
        } finally {
            settings.distributionType = oldGradleDistributionType
        }
    }
}