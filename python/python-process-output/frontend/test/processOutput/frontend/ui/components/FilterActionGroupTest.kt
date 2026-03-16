package com.intellij.python.processOutput.frontend.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.intellij.python.processOutput.frontend.ProcessOutputTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.jetbrains.annotations.ApiStatus

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
            TestFilter.Item.OPTION_1 to 0,
            TestFilter.Item.OPTION_2 to 0,
        )
        val state = scaffold(
            onFilterItemToggled = { filterItem, _ ->
                clicks[filterItem] = clicks[filterItem]!!.plus(1)
            },
        )

        // clicking the view filters button
        onNodeWithTag(TestTags.BUTTON).performClick()

        // no menu buttons were clicked, no active filters
        assertEquals(state.active.size, 0)

        // clicking on option1
        onNodeWithText(
            TestFilter.Item.OPTION_1.title,
            useUnmergedTree = true,
        ).performClick()

        // option1 should be active
        assertEquals(setOf(TestFilter.Item.OPTION_1), state.active)
        assertEquals(clicks[TestFilter.Item.OPTION_1], 1)

        // clicking on option2
        onNodeWithText(
            TestFilter.Item.OPTION_2.title,
            useUnmergedTree = true,
        ).performClick()

        // both filters should be active
        assertEquals(
            setOf(TestFilter.Item.OPTION_1, TestFilter.Item.OPTION_2),
            state.active,
        )
        assertEquals(clicks[TestFilter.Item.OPTION_1], 1)
        assertEquals(clicks[TestFilter.Item.OPTION_2], 1)
    }

    @Test
    fun `view filters buttons include checked icon when selected`() = processOutputTest {
        val state = scaffold(selectedItems = setOf(TestFilter.Item.OPTION_2))

        // clicking the view filters button
        onNodeWithTag(TestTags.BUTTON).performClick()

        // option1 disabled, option2 enabled
        onNodeWithText(TestFilter.Item.OPTION_1.title, useUnmergedTree = true)
            .onSiblings()
            .filter(hasTestTag(FilterActionGroupTestTags.CHECKED_ICON))
            .assertCountEquals(0)

        onNodeWithText(TestFilter.Item.OPTION_2.title, useUnmergedTree = true)
            .onSiblings()
            .filter(hasTestTag(FilterActionGroupTestTags.CHECKED_ICON))
            .assertCountEquals(1)

        // enabling option1, disabling option2
        state.active.remove(TestFilter.Item.OPTION_2)
        state.active.add(TestFilter.Item.OPTION_1)

        // option1 enabled, option2 disabled
        onNodeWithText(TestFilter.Item.OPTION_1.title, useUnmergedTree = true)
            .onSiblings()
            .filter(hasTestTag(FilterActionGroupTestTags.CHECKED_ICON))
            .assertCountEquals(1)

        onNodeWithText(TestFilter.Item.OPTION_2.title, useUnmergedTree = true)
            .onSiblings()
            .filter(hasTestTag(FilterActionGroupTestTags.CHECKED_ICON))
            .assertCountEquals(0)
    }

    private fun scaffold(
        selectedItems: Set<TestFilter.Item> = setOf(),
        onFilterItemToggled: (TestFilter.Item, Boolean) -> Unit = { _, _ -> },
    ): FilterActionGroupState<TestFilter, TestFilter.Item> {
        val state = FilterActionGroupState(TestFilter)
        state.active.addAll(selectedItems)
        scaffoldTestContent {
            Box(modifier = Modifier.size(256.dp).padding(16.dp)) {
                FilterActionGroup(
                    tooltipText = DEFAULT_TOOLTIP_TEXT,
                    state = state,
                    onFilterItemToggled = onFilterItemToggled,
                    modifier = Modifier.testTag(TestTags.BUTTON),
                    menuModifier = Modifier.testTag(TestTags.MENU),
                )
            }
        }
        return state;
    }
}

@ApiStatus.Internal
private object TestFilter : Filter<TestFilter.Item> {
    enum class Item(override val title: String, override val testTag: String) : FilterItem {
        OPTION_1("option1", TestTags.OPTION_1),
        OPTION_2("option2", TestTags.OPTION_2),
    }

    override val defaultActive: Set<Item> = setOf()
}

private object TestTags {
    const val BUTTON = "Button"
    const val MENU = "Menu"
    const val OPTION_1 = "Option1"
    const val OPTION_2 = "Option2"
}

private const val DEFAULT_TOOLTIP_TEXT = "filter action tooltip text"

