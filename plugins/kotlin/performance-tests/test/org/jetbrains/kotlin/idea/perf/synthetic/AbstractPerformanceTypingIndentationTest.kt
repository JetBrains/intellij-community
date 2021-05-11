// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.perf.synthetic

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.kotlin.formatter.FormatSettingsUtil
import org.jetbrains.kotlin.idea.perf.Stats
import org.jetbrains.kotlin.idea.perf.performanceTest
import org.jetbrains.kotlin.idea.test.KotlinLightPlatformCodeInsightTestCase
import org.jetbrains.kotlin.idea.test.configureCodeStyleAndRun
import org.jetbrains.kotlin.idea.testFramework.dispatchAllInvocationEvents
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import java.io.File


/**
 * inspired by @see [org.jetbrains.kotlin.formatter.AbstractTypingIndentationTestBase]
 */
abstract class AbstractPerformanceTypingIndentationTest : KotlinLightPlatformCodeInsightTestCase() {
    companion object {
        @JvmStatic
        val stats: Stats = Stats("typing-indentation")
    }

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
                performanceTest<Unit, Unit> {
                    name(getTestName(false))
                    stats(stats)
                    warmUpIterations(20)
                    iterations(30)
                    setUp {
                        configureByFile(originalFile.path)
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

                        KotlinTestUtils.assertEqualsToFile(File(afterFilePath), actualTextWithCaret)
                    }
                }
            }
        )
    }

    override fun getTestDataPath(): String = ""
}