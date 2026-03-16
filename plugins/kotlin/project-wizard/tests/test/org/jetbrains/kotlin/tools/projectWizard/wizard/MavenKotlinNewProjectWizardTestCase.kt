// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.projectWizard.wizard

import com.intellij.application.options.CodeStyle
import com.intellij.ide.projectWizard.NewProjectWizardConstants.BuildSystem.MAVEN
import com.intellij.ide.projectWizard.NewProjectWizardConstants.Language.KOTLIN
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.baseData
import com.intellij.openapi.project.Project
import com.intellij.platform.testFramework.assertion.moduleAssertion.ModuleAssertions.assertModules
import com.intellij.testFramework.assertEqualsToFile
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.useProject
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.wizards.MavenNewProjectWizardTestCase
import org.jetbrains.idea.maven.wizards.sdk
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.jetbrains.kotlin.tools.projectWizard.BuildSystemKotlinNewProjectWizardData.Companion.kotlinBuildSystemData
import org.jetbrains.kotlin.tools.projectWizard.maven.MavenKotlinNewProjectWizardData.Companion.kotlinMavenData
import org.jetbrains.kotlin.util.capitalizeDecapitalize.decapitalizeAsciiOnly
import org.junit.Assert
import org.junit.Rule
import org.junit.jupiter.api.Assertions
import org.junit.rules.TestName
import java.io.File

internal const val ONBOARDING_TIPS_SEARCH_STR = "with your caret at the highlighted text"

abstract class MavenKotlinNewProjectWizardTestCase : MavenNewProjectWizardTestCase() {

    abstract val testDirectory: String

    abstract val testRoot: File?

    @JvmField
    @Rule
    var testName = TestName()

    override fun runInDispatchThread() = false

    override fun tearDown() {
        runAll(
            { CodeStyle.getDefaultSettings().clearCodeStyleSettings() },
            { KotlinSdkType.removeKotlinSdkInTests() },
            { super.tearDown() })
    }

    private fun getTestDataFolder(testRoot: File?): File {
        return File(testRoot, "$testDirectory/${getTestFolderName()}")
    }

    private fun getTestFolderName(): String {
        return testName.methodName.takeWhile { it != '(' }.removePrefix("test").decapitalizeAsciiOnly()
    }

    fun Project.assertCorrectProjectFiles(testRoot: File?, substituteVersions: Boolean = true) {
        val testDataFolder = getTestDataFolder(testRoot)
        val basePath = File(basePath!!)
        var foundExpectedFiles = 0
        testDataFolder.walkTopDown().forEach {
            if (!it.isFile) return@forEach
            foundExpectedFiles++
            val relativePath = it.relativeTo(testDataFolder).toPath().toString()
            val pathInProject = File(basePath, relativePath)
            Assertions.assertTrue(
                pathInProject.exists() && pathInProject.isFile,
                "Expected ${it.name} file to exist in output, but it could not be found."
            )
            val fileContents = pathInProject.readText()
            val processedFileContent = if (substituteVersions) {
                substituteVersions(fileContents)
            } else {
                fileContents
            }
            assertEqualsToFile("Expected correct file after generation", it, processedFileContent)
        }
        Assertions.assertTrue(
            foundExpectedFiles > 0,
            "Asserted that project files are correct, but test folder contained no expected files"
        )
    }

    fun createKotlinProjectFromTemplate(
        groupId: String = "org.testcase",
        version: String = "1.0.0",
        addSampleCode: Boolean = false,
    ): Project {
        return createProjectFromTemplate(KOTLIN) {
            it.baseData!!.name = "project"
            it.kotlinBuildSystemData!!.buildSystem = MAVEN
            it.kotlinMavenData!!.sdk = mySdk
            it.kotlinMavenData!!.parentData = null

            it.kotlinMavenData!!.groupId = groupId
            it.kotlinMavenData!!.artifactId = "project"
            it.kotlinMavenData!!.version = version

            it.kotlinMavenData!!.addSampleCode = addSampleCode
        }
    }

    fun Project.findMainFileContent(modulePath: String? = null): String? {
        val path = StringBuilder().apply {
            if (modulePath != null) {
                append(modulePath)
                append("/")
            }
            append("src/main/kotlin/Main.kt")
        }.toString()
        return findRelativeFile(path)?.readText()
    }

    fun Project.findRelativeFile(path: String): File? {
        Assertions.assertNotNull(basePath)
        return File(this.basePath!!, path).takeIf { it.isFile }
    }

    fun substituteVersions(fileContents: String): String {
        val fileContentWithSubstitutedCompilerSourceAndTarget = substituteCompilerSourceAndTargetLevels(fileContents)
        val fileContentWithSubstitutedKotlinVersion = substituteKotlinVersion(fileContentWithSubstitutedCompilerSourceAndTarget)
        return substituteArtifactsVersions(fileContentWithSubstitutedKotlinVersion)
    }

    private fun substituteCompilerSourceAndTargetLevels(fileContents: String): String {
        val compilerSourceRegex = Regex("<maven.compiler.source>(\\d\\d)</maven.compiler.source>")
        val compilerTargetRegex = Regex("<maven.compiler.target>(\\d\\d)</maven.compiler.target>")
        var result = fileContents
        if (result.contains(compilerSourceRegex)) {
            result = result.replace(compilerSourceRegex, "<maven.compiler.source>VERSION</maven.compiler.source>")
        }
        if (result.contains(compilerTargetRegex)) {
            result = result.replace(compilerTargetRegex, "<maven.compiler.target>VERSION</maven.compiler.target>")
        }
        return result
    }

    private fun substituteKotlinVersion(fileContents: String): String {
        val kotlinVersionRegex = Regex("<kotlin.version>\\d\\.\\d\\.(\\d)+</kotlin.version>")
        var result = fileContents
        if (result.contains(kotlinVersionRegex)) {
            result = result.replace(
                kotlinVersionRegex, "<kotlin.version>VERSION</kotlin.version>"
            )
        }
        return result
    }

    fun substituteArtifactsVersions(str: String): String {
        var result = str

        result = substituteVersionForArtifact(result, "maven-surefire-plugin", needMoreSpaces = true)
        result = substituteVersionForArtifact(result, "maven-failsafe-plugin", needMoreSpaces = true)
        result = substituteVersionForArtifact(result, "junit-jupiter")
        result = substituteVersionForArtifact(result, "exec-maven-plugin", needMoreSpaces = true)

        return result
    }

    private fun substituteVersionForArtifact(fileContents: String, artifactId: String, needMoreSpaces: Boolean = false): String {
        val regex =
            Regex("<artifactId>$artifactId</artifactId>\n( )*<version>(([a-zA-Z]|(\\.)|(\\d)|-)+)</version>")
        var result = fileContents
        if (result.contains(regex)) {
            val additionalSpaces = if (needMoreSpaces) {
                "    "
            } else {
                ""
            }
            result = result.replace(
                regex, "<artifactId>$artifactId</artifactId>\n" +
                        "$additionalSpaces            <version>VERSION</version>"
            )
        }
        return result
    }

    fun runNewProjectTestCase(
        groupId: String = "org.testcase",
        version: String = "1.0.0",
        addSampleCodeToProject: Boolean = false,
        additionalAssertions: (Project) -> Unit = {}
    ) = runBlocking {
        waitForProjectCreation {
            createKotlinProjectFromTemplate(
                groupId = groupId,
                version = version,
                addSampleCode = addSampleCodeToProject
            )
        }.useProject { project ->
            assertModules(project, "project")
            val mavenProjectsManager = MavenProjectsManager.getInstance(project)
            Assert.assertEquals(setOf("project"), mavenProjectsManager.projects.map { it.mavenId.artifactId }.toSet())

            project.assertCorrectProjectFiles(testRoot, substituteVersions = false)
            additionalAssertions(project)
        }
        return@runBlocking
    }
}