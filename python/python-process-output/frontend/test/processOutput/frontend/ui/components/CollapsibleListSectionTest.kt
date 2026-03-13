package com.intellij.python.processOutput.frontend.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.intellij.python.processOutput.frontend.ProcessOutputTest
import com.intellij.python.processOutput.frontend.ui.IsExpanded
import kotlin.test.Test
import kotlin.test.assertEquals

internal class CollapsibleListSectionTest : ProcessOutputTest() {
    @Test
    fun `section text should be correctly displayed`() = processOutputTest {
        scaffold(text = "some section text")

        // section text should be correct
        onAllNodesWithText("some section text").assertCountEquals(1)
    }

    @Test
    fun `collapsed section should have correct icon and semantics`() = processOutputTest {
        scaffold(isExpanded = false)

        // should have isExpanded semantics set to false
        onNodeWithText(DEFAULT_SECTION_TEXT)
            .assert(SemanticsMatcher.expectValue(IsExpanded, false))

        // icon should be chevron right
        onAllNodesWithTag(CollapsibleListSectionTestTags.CHEVRON_RIGHT, useUnmergedTree = true)
            .assertCountEquals(1)
    }

    @Test
    fun `expanded section should have correct icon and semantics`() = processOutputTest {
        scaffold(isExpanded = true)

        // should have isExpanded semantics set to true
        onNodeWithText(DEFAULT_SECTION_TEXT)
            .assert(SemanticsMatcher.expectValue(IsExpanded, true))

        // icon should be chevron down
        onAllNodesWithTag(CollapsibleListSectionTestTags.CHEVRON_DOWN, useUnmergedTree = true)
            .assertCountEquals(1)
    }

    @Test
    fun `onToggle should be triggered on click`() = processOutputTest {
        var clicks = 0
        scaffold { clicks += 1 }

        // no calls should have been made at first
        assertEquals(0, clicks)

        // click on the section
        onNodeWithText(DEFAULT_SECTION_TEXT).performClick()

        // should have been called exactly once
        assertEquals(1, clicks)
    }

    private fun scaffold(
        text: String = DEFAULT_SECTION_TEXT,
        modifier: Modifier = Modifier.testTag(""),
        isExpanded: Boolean = false,
        onToggle: () -> Unit = {},
    ) {
        scaffoldTestContent {
            Box(modifier = Modifier.size(256.dp)) {
                CollapsibleListSection(
                    text = text,
                    modifier = modifier,
                    isExpanded = isExpanded,
                    onToggle = onToggle,
                )
            }
        }
    }
}

private const val DEFAULT_SECTION_TEXT = "section text"
