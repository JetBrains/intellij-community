package com.intellij.python.processOutput.frontend.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import com.intellij.python.processOutput.frontend.ProcessOutputTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys

internal class ActionIconButtonTest : ProcessOutputTest() {
    @BeforeTest
    fun beforeTest() {
        scaffoldTestContent {
            OutputSection(controller)
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `tooltip should be displayed as expected`() = processOutputTest {
        val tooltipText = "test tooltip text"
        scaffold(tooltipText = tooltipText)

        // no text displayed at first
        onAllNodesWithText(tooltipText).assertCountEquals(0)

        // hovering over the button
        onNodeWithTag(ActionIconButtonTestTags.BUTTON).performMouseInput {
            moveTo(Offset(5f, 5f))
        }

        // tooltip should be displayed
        waitUntilAtLeastOneExists(hasText(tooltipText))
    }

    @Test
    fun `enabled true should propagate`() = processOutputTest {
        scaffold(enabled = true)

        // button should be enabled
        onNodeWithTag(ActionIconButtonTestTags.BUTTON).assertIsEnabled()
    }

    @Test
    fun `enabled false should propagate`() = processOutputTest {
        scaffold(enabled = false)

        // button should be enabled
        onNodeWithTag(ActionIconButtonTestTags.BUTTON).assertIsNotEnabled()
    }

    @Test
    fun `icon should be displayed`() = processOutputTest {
        scaffold()

        // icon should be displayed
        onAllNodesWithTag(
            ActionIconButtonTestTags.ICON,
            useUnmergedTree = true,
        ).assertCountEquals(1)
    }

    @Test
    fun `dropdown icon should not be displayed when not a dropdown`() = processOutputTest {
        scaffold(isDropdown = false)

        // dropdown icon should not be displayed
        onAllNodesWithTag(
            ActionIconButtonTestTags.DROPDOWN_ICON,
            useUnmergedTree = true,
        ).assertCountEquals(0)
    }

    @Test
    fun `dropdown icon should be displayed when dropdown is true`() = processOutputTest {
        scaffold(isDropdown = true)

        // dropdown icon should be displayed
        onAllNodesWithTag(
            ActionIconButtonTestTags.DROPDOWN_ICON,
            useUnmergedTree = true,
        ).assertCountEquals(1)
    }

    @Test
    fun `onClick should be triggered when the button is clicked`() = processOutputTest {
        var clicks = 0
        scaffold(onClick = { clicks += 1 })

        // no clicks should have been made at first
        assertEquals(0, clicks)

        // performing a click
        onNodeWithTag(ActionIconButtonTestTags.BUTTON).performClick()

        // should have been called once
        assertEquals(1, clicks)
    }

    private fun scaffold(
        modifier: Modifier = Modifier,
        iconKey: IconKey = AllIconsKeys.General.Menu,
        tooltipText: String = "tooltip",
        enabled: Boolean = true,
        onClick: () -> Unit = {},
        iconModifier: Modifier = Modifier,
        isDropdown: Boolean = false,
    ) {
        scaffoldTestContent {
            ActionIconButton(
                modifier = modifier,
                iconKey = iconKey,
                tooltipText = tooltipText,
                enabled = enabled,
                onClick = onClick,
                iconModifier = iconModifier,
                isDropdown = isDropdown,
            )
        }
    }
}
