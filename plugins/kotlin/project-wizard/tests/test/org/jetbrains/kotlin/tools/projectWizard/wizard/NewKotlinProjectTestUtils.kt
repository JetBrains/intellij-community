// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.projectWizard.wizard

import com.intellij.openapi.project.Project
import com.intellij.testFramework.assertEqualsToFile
import org.jetbrains.kotlin.idea.test.TestMetadataUtil
import org.junit.jupiter.api.Assertions
import java.io.File

internal const val ONBOARDING_TIPS_SEARCH_STR = "with your caret at the highlighted text"

interface NewKotlinProjectTestUtils {

    val testDirectory: String

    fun getTestFolderName(): String

    private fun getTestDataFolder(): File {
        val testRoot = TestMetadataUtil.getTestRoot(MavenNewKotlinModuleTest::class.java)
        return File(testRoot, "$testDirectory/${getTestFolderName()}")
    }

    fun Project.assertCorrectProjectFiles() {
        val testDataFolder = getTestDataFolder()
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
            val processedFileContent = postprocessOutputFile(relativePath, pathInProject.readText())
            assertEqualsToFile("Expected correct file after generation", it, processedFileContent)
        }
        Assertions.assertTrue(
            foundExpectedFiles > 0,
            "Asserted that project files are correct, but test folder contained no expected files"
        )
    }

    fun postprocessOutputFile(relativePath: String, fileContents: String): String

    fun String.replaceFirstGroup(regex: Regex, replacement: String): String {
        val matchResult = regex.find(this) ?: return this
        val groupRange = matchResult.groups[1]?.range ?: return this
        return this.replaceRange(groupRange, replacement)
    }

    fun substituteArtifactsVersions(str: String): String

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

}