/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.perf.synthetic

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.codeStyle.lineIndent.LineIndentProvider
import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.kotlin.formatter.FormatSettingsUtil
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.formatter.KotlinLineIndentProvider
import org.jetbrains.kotlin.idea.perf.Stats
import org.jetbrains.kotlin.idea.perf.performanceTest
import org.jetbrains.kotlin.idea.test.KotlinLightPlatformCodeInsightTestCase
import org.jetbrains.kotlin.idea.test.configureCodeStyleAndRun
import org.jetbrains.kotlin.idea.testFramework.dispatchAllInvocationEvents
import org.jetbrains.kotlin.test.InTextDirectivesUtils
import org.jetbrains.kotlin.test.KotlinTestUtils
import org.junit.Assert
import java.io.File


/**
 * inspired by @see [org.jetbrains.kotlin.formatter.AbstractTypingIndentationTestBase]
 */
abstract class AbstractPerformanceTypingIndentationTest : KotlinLightPlatformCodeInsightTestCase() {
    companion object {
        @JvmStatic
        val stats: Stats = Stats("typing-indentation")
    }

    private val customLineIndentProvider: LineIndentProvider = KotlinLineIndentProvider()

    fun doNewlineTestWithInvert(afterInvFilePath: String) {
        doNewlineTest(afterInvFilePath, true)
    }

    @JvmOverloads
    fun doNewlineTest(afterFilePath: String, inverted: Boolean = false) {
        val afterFile = File(afterFilePath)
        assert(afterFile.exists())
        val testFileName = afterFile.name.substring(0, afterFile.name.indexOf("."))
        val testFileExtension = afterFile.name.substring(afterFile.name.lastIndexOf("."))
        val originFileName = testFileName + testFileExtension
        val originalFile = File(afterFile.parent, originFileName)
        assert(originalFile.exists())
        val originalFileText = FileUtil.loadFile(originalFile, true)
        val withoutCustomLineIndentProvider = InTextDirectivesUtils.findStringWithPrefixes(
            originalFileText,
            "// WITHOUT_CUSTOM_LINE_INDENT_PROVIDER"
        ) != null

        val ignoreFormatter = InTextDirectivesUtils.findStringWithPrefixes(
            originalFileText,
            "// IGNORE_FORMATTER"
        ) != null

        Assert.assertFalse(
            "Only one option of 'WITHOUT_CUSTOM_LINE_INDENT_PROVIDER' and 'IGNORE_FORMATTER' is available at the same time",
            withoutCustomLineIndentProvider && ignoreFormatter
        )

        configureCodeStyleAndRun(
            project = project,
            configurator = {
                val configurator = FormatSettingsUtil.createConfigurator(originalFileText, it)
                if (!inverted) {
                    configurator.configureSettings()
                } else {
                    configurator.configureInvertedSettings()
                }
            },
            body = {
                doNewlineTest(
                    originalFile.path, afterFilePath,
                    withoutCustomLineIndentProvider = withoutCustomLineIndentProvider,
                    ignoreFormatter = ignoreFormatter
                )
            }
        )
    }

    private fun doNewlineTest(
        beforeFilePath: String,
        afterFilePath: String,
        withoutCustomLineIndentProvider: Boolean,
        ignoreFormatter: Boolean
    ) {
        val testName = getTestName(false)
        KotlinLineIndentProvider.useFormatter = true
        typeAndCheck(testName, beforeFilePath, afterFilePath, "with FormatterBasedLineIndentProvider", ignoreFormatter)
        KotlinLineIndentProvider.useFormatter = false

        if (!withoutCustomLineIndentProvider) {
            typeAndCheck(testName, beforeFilePath, afterFilePath, "with ${customLineIndentProvider.javaClass.simpleName}")
        }

        configureByFile(beforeFilePath)
        assertCustomIndentExist(withoutCustomLineIndentProvider)
    }

    private fun assertCustomIndentExist(withoutCustomLineIndentProvider: Boolean) {
        val offset = editor.caretModel.offset
        runWriteAction {
            editor.document.insertString(offset, "\n")
        }

        val customIndent = customLineIndentProvider.getLineIndent(project, editor, KotlinLanguage.INSTANCE, offset + 1)
        val condition = customIndent == null
        Assert.assertTrue(
            "${if (withoutCustomLineIndentProvider) "Remove" else "Add"} \"// WITHOUT_CUSTOM_LINE_INDENT_PROVIDER\" or fix ${customLineIndentProvider.javaClass.simpleName}",
            if (withoutCustomLineIndentProvider) condition else !condition
        )
    }

    private fun typeAndCheck(testName: String, beforeFilePath: String, afterFilePath: String, errorMessage: String, invertResult: Boolean = false) {
        performanceTest<Unit, Unit> {
            name(testName)
            stats(stats)
            warmUpIterations(20)
            iterations(30)
            setUp {
                configureByFile(beforeFilePath)
            }
            test {
                executeAction(IdeActions.ACTION_EDITOR_ENTER)
                dispatchAllInvocationEvents()
            }
            tearDown {
                val actualTextWithCaret = StringBuilder(editor.document.text).insert(
                    editor.caretModel.offset,
                    EditorTestUtil.CARET_TAG
                ).toString()

                val result = kotlin.runCatching {
                    KotlinTestUtils.assertEqualsToFile(errorMessage, File(afterFilePath), actualTextWithCaret)
                }

                if (invertResult)
                    Assert.assertTrue("Remove // IGNORE_FORMATTER", result.isFailure)
                else
                    result.getOrThrow()
            }
        }
    }

    override fun getTestDataPath(): String = ""
}