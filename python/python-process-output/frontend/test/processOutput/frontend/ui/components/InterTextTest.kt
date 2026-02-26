package com.intellij.python.processOutput.frontend.ui.components

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.text.AnnotatedString
import com.intellij.python.processOutput.frontend.ProcessOutputTest
import org.junit.Test

internal class InterTextTest : ProcessOutputTest() {
    @Test
    fun `string text is properly rendered`(): Unit = processOutputTest {
        scaffoldTestContent {
            InterText(text = DEFAULT_TEXT)
        }

        // should reflect the test text string
        onAllNodesWithText(DEFAULT_TEXT).assertCountEquals(1)
    }

    @Test
    fun `annotated string text is properly rendered`(): Unit = processOutputTest {
        scaffoldTestContent {
            InterText(text = AnnotatedString(DEFAULT_TEXT))
        }

        // should reflect the test text annotated string
        onAllNodesWithText(DEFAULT_TEXT).assertCountEquals(1)
    }
}

private const val DEFAULT_TEXT = "inter text"
