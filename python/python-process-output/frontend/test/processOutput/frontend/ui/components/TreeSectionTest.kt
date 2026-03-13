package com.intellij.python.processOutput.frontend.ui.components

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import com.intellij.python.processOutput.common.ProcessWeightDto
import com.intellij.python.processOutput.common.TraceContextKind
import com.intellij.python.processOutput.frontend.ProcessOutputTest
import com.intellij.python.processOutput.frontend.TreeFilter
import com.intellij.python.processOutput.frontend.finish
import com.intellij.python.processOutput.frontend.formatTime
import com.intellij.python.processOutput.frontend.ui.ProcessIsBackgroundKey
import com.intellij.python.processOutput.frontend.ui.ProcessIsErrorKey
import io.mockk.called
import io.mockk.verify
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

internal class TreeSectionTest : ProcessOutputTest() {
    @BeforeTest
    fun beforeTest() {
        scaffoldTestContent {
            TreeSection(controller)
        }
    }

    @Test
    fun `tree process item labels contain correct text`() = processOutputTest {
        // adding test processes
        setTree {
            addProcess("process", "segment", "--flag1", "-f")
            addProcess("exec", "--flag", "segment2")
        }

        // command string should be displayed
        onAllNodesWithText("process segment --flag1 -f").assertCountEquals(1)
        onAllNodesWithText("exec --flag segment2").assertCountEquals(1)
    }

    @Test
    fun `tree should display contexts and its children`() = processOutputTest {
        val context = traceContext("context")
        val subcontext = traceContext("subcontext")

        // adding test processes
        setTree {
            addProcess("process0")
            addProcess("process1")
            addContext(context) {
                addProcess("process2")
                addProcess("process3")
                addContext(subcontext) {
                    addProcess("process4")
                    addProcess("process5")
                }
            }
        }

        // should display process0, process1 and context, but not the rest
        listOf("process0", "process1", "context").forEach {
            onAllNodesWithText(it).assertCountEquals(1)
        }
        listOf("process2", "process3", "subcontext", "process4", "process5").forEach {
            onAllNodesWithText(it).assertCountEquals(0)
        }

        // expanding context
        expandContext(context)

        // should display process0, process1, context, process2, process3 and subcontext,
        // but not the rest
        listOf("process0", "process1", "context", "process2", "process3", "subcontext").forEach {
            onAllNodesWithText(it).assertCountEquals(1)
        }
        listOf("process4", "process5").forEach {
            onAllNodesWithText(it).assertCountEquals(0)
        }

        // expanding subcontext
        expandContext(subcontext)
        awaitIdle()

        // should display everything
        listOf(
            "process0",
            "process1",
            "context",
            "process2",
            "process3",
            "subcontext",
            "process4",
            "process5",
        ).forEach {
            onAllNodesWithText(it).assertCountEquals(1)
        }
    }

    @Test
    fun `tree process items should have correct semantics on error code`() = processOutputTest {
        val runningProcess = process("running")
        val successProcess = process("success").finish(0)
        val failProcess = process("fail").finish(1)

        // adding processes
        setTree {
            addProcess(runningProcess)
            addProcess(successProcess)
            addProcess(failProcess)
        }

        // running should have IsError semantic set to false & not display error icon
        onNodeWithText("running", useUnmergedTree = true)
            .onParent()
            .assert(SemanticsMatcher.expectValue(ProcessIsErrorKey, false))
            .onChildren()
            .filter(hasTestTag(TreeSectionTestTags.PROCESS_ITEM_ERROR_ICON))
            .assertCountEquals(0)

        // success should have IsError semantic set to false & not display error icon
        onNodeWithText("success", useUnmergedTree = true)
            .onParent()
            .assert(SemanticsMatcher.expectValue(ProcessIsErrorKey, false))
            .onChildren()
            .filter(hasTestTag(TreeSectionTestTags.PROCESS_ITEM_ERROR_ICON))
            .assertCountEquals(0)

        // fail should have IsError semantic set to true & display error icon
        onNodeWithText("fail", useUnmergedTree = true)
            .onParent()
            .assert(SemanticsMatcher.expectValue(ProcessIsErrorKey, true))
            .onChildren()
            .filter(hasTestTag(TreeSectionTestTags.PROCESS_ITEM_ERROR_ICON))
            .assertCountEquals(1)
    }

    @Test
    fun `tree process items should display time when the filter is enabled`() = processOutputTest {
        val startingTime = Instant.parse("2023-01-02T00:00:00+00:00")

        // adding some processes
        setTree {
            repeat(10) {
                addProcess(
                    "process$it",
                    startedAt = startingTime + it.minutes,
                )
            }
        }

        // should display time by default
        repeat(10) {
            onAllNodesWithText((startingTime + it.minutes).formatTime()).assertCountEquals(1)
        }

        // turn off display time filter
        toggleTreeFilter(TreeFilter.Item.SHOW_TIME)

        // should not display time when the filter is turned off
        repeat(10) {
            onAllNodesWithText((startingTime + it.minutes).formatTime()).assertCountEquals(0)
        }
    }

    @Test
    fun `tree background process items should have correct semantic`() = processOutputTest {
        // adding background and non-background processes
        setTree {
            repeat(5) {
                addProcess("process$it")
            }
            repeat(5) {
                addProcess(
                    "background$it",
                    traceContext = traceContext(
                        "non-interactive",
                        kind = TraceContextKind.NON_INTERACTIVE,
                    ),
                )
            }
        }

        // background should have IsBackground semantic, non-background processes shouldn't
        repeat(5) {
            onNodeWithText("process$it").assert(
                SemanticsMatcher.expectValue(
                    ProcessIsBackgroundKey,
                    false,
                ),
            )
            onNodeWithText("background$it").assert(
                SemanticsMatcher.expectValue(
                    ProcessIsBackgroundKey,
                    true,
                ),
            )
        }
    }

    @Test
    fun `tree process items should call the selectProcess() on click`() = processOutputTest {
        val processes = (0..3).map { process("process$it") }

        // adding some processes
        setTree {
            processes.forEach {
                addProcess(it)
            }
        }

        // no processes were clicked, no functions were called
        verify { controllerSpy wasNot called }

        // clicking the first item
        onNodeWithText("process0").performClick()

        // selectProcess() should have been called with the first process
        verify(exactly = 1) { controllerSpy.selectProcess(processes[0]) }
        verify(exactly = 0) { controllerSpy.selectProcess(processes[1]) }
        verify(exactly = 0) { controllerSpy.selectProcess(processes[2]) }

        // clicking the second item
        onNodeWithText("process1").performClick()

        // selectProcess() should have been called with the first and second process
        verify(exactly = 1) { controllerSpy.selectProcess(processes[0]) }
        verify(exactly = 1) { controllerSpy.selectProcess(processes[1]) }
        verify(exactly = 0) { controllerSpy.selectProcess(processes[2]) }

        // clicking the third item
        onNodeWithText("process2").performClick()

        // selectProcess() should have been called with the first and second process
        verify(exactly = 1) { controllerSpy.selectProcess(processes[0]) }
        verify(exactly = 1) { controllerSpy.selectProcess(processes[1]) }
        verify(exactly = 1) { controllerSpy.selectProcess(processes[2]) }
    }

    @Test
    fun `tree expand all and collapse all buttons should be disabled when tree is empty`() =
        processOutputTest {
            // tree is empty by default; expand/collapse should be disabled
            onNodeWithTag(TreeSectionTestTags.EXPAND_ALL_BUTTON).assert(isNotEnabled())
            onNodeWithTag(TreeSectionTestTags.COLLAPSE_ALL_BUTTON).assert(isNotEnabled())
        }

    @Test
    fun `list expand all and collapse all buttons call their respective functions`() =
        processOutputTest {
            // adding some processes & enabling categorization by coroutine
            setTree {
                repeat(5) {
                    addProcess("process$it")
                }
            }

            // no buttons were clicked, no functions were called
            verify { controllerSpy wasNot called }

            // clicking on expand
            onNodeWithTag(TreeSectionTestTags.EXPAND_ALL_BUTTON).performClick()

            // expand should have been called, but not collapse
            verify(exactly = 1) { controllerSpy.expandAllContexts() }
            verify(exactly = 0) { controllerSpy.collapseAllContexts() }

            // clicking on collapse
            onNodeWithTag(TreeSectionTestTags.COLLAPSE_ALL_BUTTON).performClick()

            // expand should have been called, but not collapse
            verify(exactly = 1) { controllerSpy.expandAllContexts() }
            verify(exactly = 1) { controllerSpy.collapseAllContexts() }
        }

    @Test
    fun `view filters buttons call their respective functions`() = processOutputTest {
        // clicking the view filters button
        onNodeWithTag(TreeSectionTestTags.FILTERS_BUTTON).performClick()

        // no menu buttons were clicked, no functions were called
        verify { controllerSpy wasNot called }

        // clicking on categorize
        onNodeWithTag(
            TreeSectionTestTags.FILTERS_BACKGROUND,
            useUnmergedTree = true,
        ).performClick()

        // background should have been called, but not time nor process weights
        verify(exactly = 1) {
            controllerSpy.onTreeFilterItemToggled(
                TreeFilter.Item.SHOW_BACKGROUND_PROCESSES,
                true,
            )
        }
        verify(exactly = 0) {
            controllerSpy.onTreeFilterItemToggled(
                TreeFilter.Item.SHOW_PROCESS_WEIGHT,
                any(),
            )
        }
        verify(exactly = 0) {
            controllerSpy.onTreeFilterItemToggled(
                TreeFilter.Item.SHOW_TIME,
                any(),
            )
        }

        // clicking on time
        onNodeWithTag(
            TreeSectionTestTags.FILTERS_TIME,
            useUnmergedTree = true,
        ).performClick()

        // background and time should have been called, but not process weights
        verify(exactly = 1) {
            controllerSpy.onTreeFilterItemToggled(
                TreeFilter.Item.SHOW_BACKGROUND_PROCESSES,
                true,
            )
        }
        verify(exactly = 0) {
            controllerSpy.onTreeFilterItemToggled(
                TreeFilter.Item.SHOW_PROCESS_WEIGHT,
                any(),
            )
        }
        verify(exactly = 1) {
            controllerSpy.onTreeFilterItemToggled(
                TreeFilter.Item.SHOW_TIME,
                false,
            )
        }

        // clicking on process weights
        onNodeWithTag(
            TreeSectionTestTags.FILTERS_PROCESS_WEIGHTS,
            useUnmergedTree = true,
        ).performClick()

        // all filters should have been called
        verify(exactly = 1) {
            controllerSpy.onTreeFilterItemToggled(
                TreeFilter.Item.SHOW_BACKGROUND_PROCESSES,
                true,
            )
        }
        verify(exactly = 1) {
            controllerSpy.onTreeFilterItemToggled(
                TreeFilter.Item.SHOW_PROCESS_WEIGHT,
                false,
            )
        }
        verify(exactly = 1) {
            controllerSpy.onTreeFilterItemToggled(
                TreeFilter.Item.SHOW_TIME,
                false,
            )
        }
    }

    @Test
    fun `tree process items should display process weights when the filter is enabled`() =
        processOutputTest {
            // adding some processes
            setTree {
                repeat(3) {
                    addProcess("light$it", weight = ProcessWeightDto.LIGHT)
                }
                repeat(3) {
                    addProcess("medium$it", weight = ProcessWeightDto.MEDIUM)
                }
                repeat(3) {
                    addProcess("heavy$it", weight = ProcessWeightDto.HEAVY)
                }
            }

            // should display medium and heavy icons
            onAllNodesWithTag(
                testTag = TreeSectionTestTags.PROCESS_ITEM_WEIGHT_MEDIUM_ICON,
                useUnmergedTree = true,
            ).assertCountEquals(3)
            onAllNodesWithTag(
                testTag = TreeSectionTestTags.PROCESS_ITEM_WEIGHT_HEAVY_ICON,
                useUnmergedTree = true,
            ).assertCountEquals(3)

            // turn off process weight icons
            toggleTreeFilter(TreeFilter.Item.SHOW_PROCESS_WEIGHT)

            // should not display any weight icons
            onAllNodesWithTag(
                testTag = TreeSectionTestTags.PROCESS_ITEM_WEIGHT_MEDIUM_ICON,
                useUnmergedTree = true,
            ).assertCountEquals(0)
            onAllNodesWithTag(
                testTag = TreeSectionTestTags.PROCESS_ITEM_WEIGHT_HEAVY_ICON,
                useUnmergedTree = true,
            ).assertCountEquals(0)
        }
}
