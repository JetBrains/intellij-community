package com.intellij.python.processOutput.frontend.ui.components

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import com.intellij.python.processOutput.frontend.ProcessOutputTest
import kotlin.test.Test
import org.jetbrains.jewel.ui.component.Text

internal class ToolbarTest : ProcessOutputTest() {
    @Test
    fun `should render passed content`() = processOutputTest {
        scaffoldTestContent {
            Toolbar {
                Text(text = TEST_TEXT)
            }
        }

        // should have rendered text with test content
        onAllNodesWithText(TEST_TEXT).assertCountEquals(1)
    }
}

private const val TEST_TEXT = "some text content"
