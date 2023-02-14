// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.scratch

import org.jetbrains.kotlin.idea.scratch.actions.RunScratchFromHereAction
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class SequentialScratchExecutorTest : AbstractScratchRunActionTest() {

    fun testSingleLine() {
        doTest(
            listOf(
                "2 + 2\n" to "RESULT: res1: kotlin.Int = 4"
            )
        )
    }

    fun testMultipleLinesAtOnce() {
        doTest(
            listOf(
                "2\n3\n4\n" to "RESULT: res1: kotlin.Int = 2, RESULT: res2: kotlin.Int = 3, RESULT: res3: kotlin.Int = 4"
            )
        )
    }

    fun testMultipleLinesAfterEach() {
        doTest(
            listOf(
                "2\n" to "RESULT: res1: kotlin.Int = 2",
                "3\n" to "RESULT: res2: kotlin.Int = 3",
                "4\n" to "RESULT: res3: kotlin.Int = 4"
            )
        )
    }

    fun doTest(expression: List<Pair<String, String>>) {
        configureScratchByText("scratch_1.kts", doTestScratchText().inlinePropertiesValues(isRepl = true))

        myFixture.editor.caretModel.moveToOffset(myFixture.file.textLength)

        try {
            launchScratch()
            waitUntilScratchFinishes(shouldStopRepl = false)

            for ((text, expected) in expression) {
                typeAndCheckOutput(text, expected)
            }
        } finally {
            stopReplProcess()
        }
    }

    private fun typeAndCheckOutput(text: String, expected: String) {
        val inlaysBefore = getInlays()

        myFixture.type(text)

        launchAction(RunScratchFromHereAction())
        waitUntilScratchFinishes(shouldStopRepl = false)

        val inlayAfter = getInlays().filterNot { inlaysBefore.contains(it) }

        assertEquals(expected, inlayAfter.joinToString { it.toString().trim() })
    }
}