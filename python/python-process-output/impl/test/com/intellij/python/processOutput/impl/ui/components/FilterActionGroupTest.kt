package com.intellij.python.processOutput.impl.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateSet
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onSiblings
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.unit.dp
import com.intellij.python.processOutput.impl.ProcessOutputTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf

internal class FilterActionGroupTest : ProcessOutputTest() {
    @Test
    fun `filters menu expands when clicked`() = processOutputTest {
        scaffold()

        // menu is not displayed at first
        onAllNodesWithTag(TestTags.MENU, useUnmergedTree = true).assertCountEquals(0)

        // clicking the view filters button
        onNodeWithTag(TestTags.BUTTON).performClick()

        // menu is now visible
        onAllNodesWithTag(TestTags.MENU, useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun `view filters menu disappears when clicked outside`() = processOutputTest {
        scaffold()

        // clicking the view filters button
        onNodeWithTag(TestTags.BUTTON).performClick()

        // menu is visible
        onAllNodesWithTag(TestTags.MENU).assertCountEquals(1)

        // click outside the menu (-10f on both coordinates relative to top left corner)
        onNodeWithTag(TestTags.MENU).performMouseInput {
            click(position = Offset(-10f, -10f))
        }

        // menu is now invisible
        onAllNodesWithTag(TestTags.MENU).assertCountEquals(0)
    }

    @Test
    fun `view filters menu doesn't disappear when clicked inside`() = processOutputTest {
        scaffold()

        // clicking the view filters button
        onNodeWithTag(TestTags.BUTTON).performClick()

        // menu is visible
        onAllNodesWithTag(TestTags.MENU).assertCountEquals(1)

        // click inside the menu (+10f on both coordinates relative to top left corner)
        onNodeWithTag(TestTags.MENU).performMouseInput {
            click(position = Offset(10f, 10f))
        }

        // menu is still visible
        onAllNodesWithTag(TestTags.MENU).assertCountEquals(1)
    }

    @Test
    fun `view filters buttons call their respective functions`() = processOutputTest {
        val clicks = mutableMapOf(
            TestFilter.Option1 to 0,
            TestFilter.Option2 to 0,
        )
        scaffold(
            onItemClick = {
                clicks[it] = clicks[it]!!.plus(1)
            },
        )

        // clicking the view filters button
        onNodeWithTag(TestTags.BUTTON).performClick()

        // no menu buttons were clicked, no functions were called
        assertEquals(0, clicks[TestFilter.Option1])
        assertEquals(0, clicks[TestFilter.Option2])

        // clicking on option1
        onNodeWithText(
            TestFilter.Option1.title,
            useUnmergedTree = true,
        ).performClick()

        // option1 should have been called, but not option2
        assertEquals(1, clicks[TestFilter.Option1])
        assertEquals(0, clicks[TestFilter.Option2])

        // clicking on option2
        onNodeWithText(
            TestFilter.Option2.title,
            useUnmergedTree = true,
        ).performClick()

        // both filters should have been called
        assertEquals(1, clicks[TestFilter.Option1])
        assertEquals(1, clicks[TestFilter.Option2])
    }

    @Test
    fun `view filters buttons include checked icon when selected`() = processOutputTest {
        val selected = mutableStateSetOf<TestFilter>(TestFilter.Option2)
        scaffold(selectedItems = selected)

        // clicking the view filters button
        onNodeWithTag(TestTags.BUTTON).performClick()

        // option1 disabled, option2 enabled
        onNodeWithText(TestFilter.Option1.title, useUnmergedTree = true)
            .onSiblings()
            .filter(hasTestTag(FilterActionGroupTestTags.CHECKED_ICON))
            .assertCountEquals(0)

        onNodeWithText(TestFilter.Option2.title, useUnmergedTree = true)
            .onSiblings()
            .filter(hasTestTag(FilterActionGroupTestTags.CHECKED_ICON))
            .assertCountEquals(1)

        // enabling option1, disabling option2
        selected.remove(TestFilter.Option2)
        selected.add(TestFilter.Option1)

        // option1 enabled, option2 disabled
        onNodeWithText(TestFilter.Option1.title, useUnmergedTree = true)
            .onSiblings()
            .filter(hasTestTag(FilterActionGroupTestTags.CHECKED_ICON))
            .assertCountEquals(1)

        onNodeWithText(TestFilter.Option2.title, useUnmergedTree = true)
            .onSiblings()
            .filter(hasTestTag(FilterActionGroupTestTags.CHECKED_ICON))
            .assertCountEquals(0)
    }

    private fun scaffold(
        selectedItems: SnapshotStateSet<TestFilter> = mutableStateSetOf(),
        onItemClick: (TestFilter) -> Unit = {},
    ) {
        scaffoldTestContent {
            val selected = remember { selectedItems }

            Box(modifier = Modifier.size(256.dp).padding(16.dp)) {
                FilterActionGroup(
                    tooltipText = DEFAULT_TOOLTIP_TEXT,
                    items = persistentListOf(
                        FilterEntry(
                            item = TestFilter.Option1,
                            testTag = TestTags.OPTION_1,
                        ),
                        FilterEntry(
                            item = TestFilter.Option2,
                            testTag = TestTags.OPTION_2,
                        ),
                    ),
                    isSelected = { selected.contains(it) },
                    onItemClick = onItemClick,
                    modifier = Modifier.testTag(TestTags.BUTTON),
                    menuModifier = Modifier.testTag(TestTags.MENU),
                )
            }
        }
    }
}

private abstract class TestFilter : FilterItem {
    object Option1 : TestFilter() {
        override val title = "option1"
    }

    object Option2 : TestFilter() {
        override val title = "option2"
    }
}

private object TestTags {
    const val BUTTON = "Button"
    const val MENU = "Menu"
    const val OPTION_1 = "Option1"
    const val OPTION_2 = "Option2"
}

private const val DEFAULT_TOOLTIP_TEXT = "filter action tooltip text"

