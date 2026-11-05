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
    fun `a call with an ambiguous argument is not reported as not found`() = runBlocking {
        testMcpTool(toolName = ADD_MISSING_IMPORTS, input = request("src/UsesAmbiguousArgument.kt")) { result ->
            val text = result.textContent.text
            assertContains(text, "one.Marker")
            assertFalse("\"target\"" in text, "The tool must not report target: $text")
        }
    }

    @Test
    fun `get, set, component and rangeTo extensions are imported`() =
        assertOperatorsImported("src/UsesOperatorExtensions.kt", "get", "set", "component1", "rangeTo")

    @Test
    fun `contains extension is imported for in and not in`() = assertOperatorsImported("src/UsesContains.kt", "contains")

    @Test
    fun `contains extension is imported when other contains candidates do not apply`() =
        assertOperatorsImported("src/UsesContainsWithInapplicableCandidates.kt", "contains")

    @Test
    fun `rangeUntil extension is imported`() = assertOperatorsImported("src/UsesRangeUntil.kt", "rangeUntil")

    @Test
    fun `iterator extension is imported for a for loop`() = assertOperatorsImported("src/UsesIterator.kt", "iterator")

    @Test
    fun `getValue extension is imported for a delegate`() = assertOperatorsImported("src/UsesDelegate.kt", "getValue")

    @Test
    fun `arithmetic, call and infix extensions are imported`() =
        assertOperatorsImported("src/UsesCallOperators.kt", "plus", "unaryMinus", "not", "inc", "invoke", "split")

    @Test
    fun `one operator name for two receivers imports both`() = runBlocking {
        testMcpTool(toolName = ADD_MISSING_IMPORTS, input = request("src/UsesTwoReceivers.kt")) { result ->
            val text = result.textContent.text
            assertContains(text, "+import ops1.get")
            assertContains(text, "+import ops3.get")
            assertFalse("symbol_not_found" in text, "Nothing may stay unresolved: $text")
        }
    }

    @Test
    fun `one operator name for two receivers imports both when they resolve in different passes`() = runBlocking {
        testMcpTool(toolName = ADD_MISSING_IMPORTS, input = request("src/UsesTwoReceiversInTwoPasses.kt")) { result ->
            val text = result.textContent.text
            assertContains(text, "+import ops1.get")
            assertContains(text, "+import ops3.get")
            assertFalse("symbol_not_found" in text, "Nothing may stay unresolved: $text")
        }
    }

    @Test
    fun `an ambiguous operator is reported at its line and not imported`() = runBlocking {
        testMcpTool(toolName = ADD_MISSING_IMPORTS, input = request("src/UsesAmbiguousOperator.kt")) { result ->
            val text = result.textContent.text
            assertContains(text, "ops1.get")
            assertContains(text, "ops2.get")
            // The file is written, so the line counts the three lines of the added imports.
            assertContains(text, "\"line\":8")
            assertFalse("+import ops1.get" in text || "+import ops2.get" in text, "An ambiguous operator must not be imported: $text")
        }
    }

    @Test
    fun `the best candidate is imported for an ambiguous operator`() = runBlocking {
        testMcpTool(toolName = ADD_MISSING_IMPORTS, input = request("src/UsesAmbiguousOperator.kt", ambiguity = "best")) { result ->
            val text = result.textContent.text
            assertTrue("+import ops1.get" in text || "+import ops2.get" in text, "One candidate must be imported: $text")
            assertFalse("+import ops1.get" in text && "+import ops2.get" in text, "Only one candidate may be imported: $text")
        }
    }

    @Test
    fun `a second run after apply adds nothing`() = runBlocking {
        testMcpTool(toolName = ADD_MISSING_IMPORTS, input = request("src/UsesAppliedOperators.kt", mode = "apply")) { result ->
            assertContains(result.textContent.text, "+import ops1.get")
        }
        testMcpTool(toolName = ADD_MISSING_IMPORTS, input = request("src/UsesAppliedOperators.kt", mode = "apply")) { result ->
            val text = result.textContent.text
            assertFalse("+import" in text, "The second run must add nothing: $text")
            assertFalse("symbol_not_found" in text, "Nothing may stay unresolved: $text")
            assertContains(text, "\"applied\":false")
        }
    }

    @Test
    fun `an operator without any candidate is reported by its function name`() = runBlocking {
        testMcpTool(toolName = ADD_MISSING_IMPORTS, input = request("src/UsesMissingOperator.kt")) { result ->
            val text = result.textContent.text.replace(" ", "")
            assertContains(text, "\"shortName\":\"get\"")
            assertContains(text, "\"shortName\":\"iterator\"")
            assertContains(text, "symbol_not_found")
        }
    }

    private fun assertOperatorsImported(path: String, vararg names: String) = runBlocking {
        testMcpTool(toolName = ADD_MISSING_IMPORTS, input = request(path)) { result ->
            val text = result.textContent.text
            for (name in names) assertContains(text, "+import ops1.$name")
            assertFalse("symbol_not_found" in text, "Nothing may stay unresolved: $text")
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
