// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.projectWizard.wizard

import org.jetbrains.kotlin.tools.projectWizard.cli.BuildSystem
import org.jetbrains.kotlin.tools.projectWizard.cli.assertSuccess
import org.jetbrains.kotlin.tools.projectWizard.phases.GenerationPhase
import org.jetbrains.kotlin.tools.projectWizard.wizard.service.IdeaServices
import org.jetbrains.plugins.gradle.settings.DistributionType
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleEnvironment
import java.nio.file.Files
import java.nio.file.Paths

class GradleProjectSettingsTest : AbstractProjectTemplateNewWizardProjectImportTestBase() {
    fun testDistributionTypeIsDefaultWrapped() {
        val directory = Paths.get("consoleApplication")
        val tempDirectory = Files.createTempDirectory(null)

        val wizard = createWizard(directory, BuildSystem.GRADLE_KOTLIN_DSL, tempDirectory)

        // here we force loading and initialization of `GradleEnvironment.Headless`, see KTIJ-24569
        @Suppress("UNUSED_VARIABLE")
        val distributionType = GradleEnvironment.Headless.GRADLE_DISTRIBUTION_TYPE

        val projectDependentServices = IdeaServices.createScopeDependent(project)
        wizard.apply(projectDependentServices, GenerationPhase.ALL).assertSuccess()

        val settings = GradleSettings.getInstance(project)
        val projectSettings = settings.linkedProjectsSettings

        val expectedDistributionType = DistributionType.DEFAULT_WRAPPED
        val actualDistributionType = projectSettings.map { it.distributionType }.singleOrNull()

        assert(projectSettings.isNotEmpty()) { "Project settings are empty" }
        assert(actualDistributionType == expectedDistributionType) {
            "Distribution type $actualDistributionType, but $expectedDistributionType was expected"
        }
    }
}