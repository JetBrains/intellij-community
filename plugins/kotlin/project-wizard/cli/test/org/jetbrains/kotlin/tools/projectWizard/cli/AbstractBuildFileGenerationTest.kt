// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.tools.projectWizard.cli

import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.kotlin.tools.projectWizard.core.div
import org.jetbrains.kotlin.tools.projectWizard.core.service.Services
import org.jetbrains.kotlin.tools.projectWizard.phases.GenerationPhase
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.KotlinPlugin
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.Repositories
import org.jetbrains.kotlin.tools.projectWizard.wizard.Wizard
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

abstract class AbstractBuildFileGenerationTest : UsefulTestCase() {
    abstract fun createWizard(directory: Path, buildSystem: BuildSystem, projectDirectory: Path): Wizard

    fun doTest(directoryPath: String) {
        val directory = Paths.get(directoryPath)

        val testParameters = DefaultTestParameters.fromTestDataOrDefault(directory)

        val buildSystemsToRunFor = listOfNotNull(
            BuildSystem.GRADLE_KOTLIN_DSL,
            if (testParameters.runForGradleGroovy) BuildSystem.GRADLE_GROOVY_DSL else null,
            if (testParameters.runForMaven) BuildSystem.MAVEN else null
        )

        for (buildSystem in buildSystemsToRunFor) {
            doTest(directory, buildSystem, testParameters)
        }
    }

    private fun doTest(directory: Path, buildSystem: BuildSystem, testParameters: DefaultTestParameters) {
        val tempDirectory = Files.createTempDirectory(null)
        val wizard = createWizard(directory, buildSystem, tempDirectory)
        val result = wizard.apply(Services.IDEA_INDEPENDENT_SERVICES, GenerationPhase.ALL)
        result.assertSuccess()

        val expectedDirectory = expectedDirectory(directory)

        compareFilesAndGenerateMissing(
            expectedDirectory.allBuildFiles(buildSystem), expectedDirectory,
            tempDirectory.allBuildFiles(buildSystem), tempDirectory
        ) { path ->
            val fileContent = path.readFile()
            if (testParameters.keepKotlinVersion) {
                fileContent
            } else {
                val pluginVersion = wizard.context.read { KotlinPlugin.version.propertyValue.version.toString() }
                fileContent.replace(pluginVersion, KOTLIN_VERSION_PLACEHOLDER)
            }.also { it.replaceAllTo(
                listOf(Repositories.JETBRAINS_KOTLIN_DEV.url),
                KOTLIN_REPO_PLACEHOLDER
            ) }
        }
    }

    private fun Path.allBuildFiles(buildSystem: BuildSystem) =
        listFiles { it.fileName.toString() in buildSystem.allBuildFileNames }

    private fun expectedDirectory(directory: Path): Path =
        (directory / EXPECTED_DIRECTORY_NAME).takeIf { Files.exists(it) } ?: directory

    companion object {
        private const val EXPECTED_DIRECTORY_NAME = "expected"
        private const val KOTLIN_VERSION_PLACEHOLDER = "KOTLIN_VERSION"
        private const val KOTLIN_REPO_PLACEHOLDER = "KOTLIN_REPO"
    }
}

private fun String.replaceAllTo(oldValues: Collection<String>, newValue: String) =
    oldValues.fold(this) { state, oldValue ->
        state.replace(oldValue, newValue)
    }
