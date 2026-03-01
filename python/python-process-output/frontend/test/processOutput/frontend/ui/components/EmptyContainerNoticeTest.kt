package com.intellij.python.processOutput.frontend.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.dp
import com.intellij.python.processOutput.frontend.ProcessOutputTest
import kotlin.test.Test

internal class EmptyContainerNoticeTest : ProcessOutputTest() {
    @Test
    fun `should display correct text`() = processOutputTest {
        scaffold()

        // text should be correct
        onAllNodesWithText(DEFAULT_NOTICE_TEXT).assertCountEquals(1)
    }

    private fun scaffold(
        text: String = DEFAULT_NOTICE_TEXT,
        modifier: Modifier = Modifier,
    ) {
        scaffoldTestContent {
            Box(modifier = Modifier.size(256.dp)) {
                EmptyContainerNotice(
                    text = text,
                    modifier = modifier,
                )
            }
        }
    }
}

private const val DEFAULT_NOTICE_TEXT = "empty container notice text"
