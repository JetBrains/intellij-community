// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.perf.synthetic

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.completion.CompletionBindingContextProvider
import org.jetbrains.kotlin.idea.performance.tests.utils.commitAllDocuments
import org.jetbrains.kotlin.idea.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.idea.testFramework.Stats
import org.jetbrains.kotlin.idea.testFramework.Stats.Companion.WARM_UP
import org.jetbrains.kotlin.idea.testFramework.TestData
import org.jetbrains.kotlin.idea.testFramework.performanceTest
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand

/**
 * inspired by @see AbstractCompletionIncrementalResolveTest
 */
abstract class AbstractPerformanceCompletionIncrementalResolveTest : KotlinLightCodeInsightFixtureTestCase() {
    private val BEFORE_MARKER = "<before>" // position to invoke completion before
    private val CHANGE_MARKER = "<change>" // position to insert text specified by "TYPE" directive
    private val TYPE_DIRECTIVE_PREFIX = "// TYPE:"
    private val BACKSPACES_DIRECTIVE_PREFIX = "// BACKSPACES:"

    companion object {
        @JvmStatic
        var warmedUp: Boolean = false

        @JvmStatic
        val stats: Stats = Stats("completion-incremental")
    }

    override fun getProjectDescriptor() = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

    override fun setUp() {
        super.setUp()

        if (!warmedUp) {
            doWarmUpPerfTest()
            warmedUp = true
        }
    }

    override fun tearDown() {
        runAll(
            ThrowableRunnable { commitAllDocuments() },
            ThrowableRunnable { super.tearDown() }
        )
    }

    private fun doWarmUpPerfTest() {
        innerPerfTest(WARM_UP) {
            myFixture.configureByText(
                KotlinFileType.INSTANCE,
                "class Foo {\n    private val value: String? = n<caret>\n}"
            )
        }
    }

    protected fun doPerfTest(unused: String) {
        val testPath = dataFilePath(fileName())
        val testName = getTestName(false)
        innerPerfTest(testName) {
            myFixture.configureByFile(fileName())

            val document = myFixture.editor.document
            val beforeMarkerOffset = document.text.indexOf(BEFORE_MARKER)
            assertTrue("\"$BEFORE_MARKER\" is missing in file \"$testPath\"", beforeMarkerOffset >= 0)

            val changeMarkerOffset = document.text.indexOf(CHANGE_MARKER)
            assertTrue("\"$CHANGE_MARKER\" is missing in file \"$testPath\"", changeMarkerOffset >= 0)

            val textToType = InTextDirectivesUtils.findArrayWithPrefixes(document.text, TYPE_DIRECTIVE_PREFIX).singleOrNull()
                ?.let { StringUtil.unquoteString(it) }
            val backspaceCount = InTextDirectivesUtils.getPrefixedInt(document.text, BACKSPACES_DIRECTIVE_PREFIX)
            assertTrue(
                "At least one of \"$TYPE_DIRECTIVE_PREFIX\" and \"$BACKSPACES_DIRECTIVE_PREFIX\" should be defined",
                textToType != null || backspaceCount != null
            )

            val beforeMarker = document.createRangeMarker(beforeMarkerOffset, beforeMarkerOffset + BEFORE_MARKER.length)
            val changeMarker = document.createRangeMarker(changeMarkerOffset, changeMarkerOffset + CHANGE_MARKER.length)
            changeMarker.isGreedyToRight = true

            project.executeWriteCommand("") {
                document.deleteString(beforeMarker.startOffset, beforeMarker.endOffset)
                document.deleteString(changeMarker.startOffset, changeMarker.endOffset)
            }

            editor.caretModel.moveToOffset(beforeMarker.startOffset)
        }
    }

    private fun innerPerfTest(name: String, setUpBody: (TestData<Unit, Array<LookupElement>>) -> Unit) {
        CompletionBindingContextProvider.ENABLED = true
        try {
            performanceTest<Unit, Array<LookupElement>> {
                name(name)
                stats(stats)
                setUp(setUpBody)
                test { it.value = perfTestCore() }
                tearDown {
                    // no reasons to validate output as it is a performance test
                    assertNotNull(it.value)
                    runWriteAction {
                        myFixture.file.delete()
                    }
                }
            }
        } finally {
            CompletionBindingContextProvider.ENABLED = false
        }
    }

    private fun perfTestCore() = myFixture.complete(CompletionType.BASIC)
}