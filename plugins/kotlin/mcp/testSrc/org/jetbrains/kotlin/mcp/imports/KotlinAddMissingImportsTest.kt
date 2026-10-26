// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.mcp.imports

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.testFramework.junit5.fixture.fileOrDirInProjectFixture
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.idea.core.formatter.KotlinCodeStyleSettings
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KotlinAddMissingImportsTest : KotlinImportsTestBase() {
    private val appliedFile: VirtualFile by projectFixture.fileOrDirInProjectFixture("src/Applied.kt")

    @Test
    fun `a class is imported`() = runBlocking {
        testMcpTool(toolName = ADD_MISSING_IMPORTS, input = request("src/UsesClass.kt")) { result ->
            assertContains(result.textContent.text, "+import two.Widget")
        }
    }

    @Test
    fun `a top level function is imported`() = runBlocking {
        testMcpTool(toolName = ADD_MISSING_IMPORTS, input = request("src/UsesTopLevel.kt")) { result ->
            assertContains(result.textContent.text, "+import one.topLevel")
        }
    }

    @Test
    fun `an extension function is imported`() = runBlocking {
        testMcpTool(toolName = ADD_MISSING_IMPORTS, input = request("src/UsesExtension.kt")) { result ->
            // The receiver type and the extension both need an import, and one call adds both.
            val text = result.textContent.text
            assertContains(text, "+import one.Anchor")
            assertContains(text, "+import one.doubled")
        }
    }

    @Test
    fun `a typealias is imported`() = runBlocking {
        testMcpTool(toolName = ADD_MISSING_IMPORTS, input = request("src/UsesTypeAlias.kt")) { result ->
            assertContains(result.textContent.text, "+import one.Handle")
        }
    }

    @Test
    fun `two candidates are reported and nothing is imported`() = runBlocking {
        testMcpTool(toolName = ADD_MISSING_IMPORTS, input = request("src/UsesAmbiguous.kt")) { result ->
            val text = result.textContent.text
            assertContains(text, "one.Marker")
            assertContains(text, "two.Marker")
            assertFalse("+import" in text, "An ambiguous name must not be imported: $text")
        }
    }

    @Test
    fun `an invented name is reported as not found`() = runBlocking {
        testMcpTool(toolName = ADD_MISSING_IMPORTS, input = request("src/UsesInvented.kt")) { result ->
            val text = result.textContent.text
            assertContains(text, "NoSuchTypeAnywhere")
            assertContains(text, "symbol_not_found")
        }
    }

    @Test
    fun `built-in operators are not reported`() = runBlocking {
        testMcpTool(toolName = ADD_MISSING_IMPORTS, input = request("src/UsesOperators.kt")) { result ->
            val text = result.textContent.text
            assertContains(text, "NoSuchTypeAnywhere")
            for (operator in BUILT_IN_OPERATORS) {
                assertFalse("\"shortName\":\"$operator\"" in text.replace(" ", ""), "The tool must not report $operator: $text")
            }
        }
    }

    @Test
    fun `five names of one package stay explicit`() = runBlocking {
        testMcpTool(toolName = ADD_MISSING_IMPORTS, input = request("src/UsesGroup.kt")) { result ->
            val text = result.textContent.text
            for (name in GROUP_NAMES) assertContains(text, "+import one.$name")
            assertFalse("import one.*" in text, "The tool must add no star import: $text")
        }
    }

    @Test
    fun `the optimize flag runs optimize imports`() = runBlocking {
        val settings = CodeStyle.createTestSettings(CodeStyle.getSettings(project))
        settings.getCustomSettings(KotlinCodeStyleSettings::class.java).NAME_COUNT_TO_USE_STAR_IMPORT =
            KotlinCodeStyleSettings.DEFAULT_NAME_COUNT_TO_USE_STAR_IMPORT
        val settingsManager = CodeStyleSettingsManager.getInstance(project)
        settingsManager.setTemporarySettings(settings)
        try {
            testMcpTool(toolName = ADD_MISSING_IMPORTS, input = request("src/UsesGroup.kt", optimize = true)) { result ->
                val text = result.textContent.text
                assertContains(text, "+import one.*")
                for (name in GROUP_NAMES) assertFalse("+import one.$name" in text, "Optimize Imports must join $name: $text")
            }
        }
        finally {
            settingsManager.dropTemporarySettings()
        }
    }

    @Test
    fun `the apply mode writes the file`() = runBlocking {
        testMcpTool(toolName = ADD_MISSING_IMPORTS, input = request("src/Applied.kt", mode = "apply")) { result ->
            assertContains(result.textContent.text, "two.Widget")
        }
        val onDisk = appliedFile.textOnDisk()
        assertTrue("import two.Widget" in onDisk, "The apply mode must write the file: $onDisk")
    }
}

private val GROUP_NAMES = listOf("Alpha", "Beta", "Gamma", "Delta", "Epsilon")

private val BUILT_IN_OPERATORS = listOf("?:", "||", "&&", "as?", "as", "is", "!is")
