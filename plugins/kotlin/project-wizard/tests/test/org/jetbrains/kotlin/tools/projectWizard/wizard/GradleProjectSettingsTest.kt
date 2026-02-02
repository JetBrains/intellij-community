// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.projectWizard.wizard

import com.intellij.ide.projectWizard.NewProjectWizardConstants.Language.KOTLIN
import com.intellij.testFramework.closeProjectAsync
import com.intellij.testFramework.withProjectAsync
import kotlinx.coroutines.runBlocking
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.settings.DistributionType
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class GradleProjectSettingsTest : GradleKotlinNewProjectWizardTestCase() {

    @ParameterizedTest
    @EnumSource(GradleDsl::class)
    fun testSimpleProject(gradleDsl: GradleDsl): Unit = runBlocking {
        createProjectByWizard(KOTLIN) {
            setGradleWizardData("project", gradleDsl = gradleDsl)
        }.withProjectAsync { project ->
            assertProjectState(project, projectInfo("project", gradleDsl) {
                withKotlinBuildFile()
                withKotlinSettingsFile()
            })

            val settings = GradleSettings.getInstance(project)
            val projectSettings = settings.linkedProjectsSettings

            val expectedDistributionType = DistributionType.DEFAULT_WRAPPED
            val actualDistributionType = projectSettings.map { it.distributionType }.singleOrNull()

            Assertions.assertTrue(projectSettings.isNotEmpty(), "Project settings are empty")
            Assertions.assertTrue(
                actualDistributionType == expectedDistributionType,
                "Distribution type $actualDistributionType, but $expectedDistributionType was expected"
            )
        }.closeProjectAsync()
    }
}