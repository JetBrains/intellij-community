package com.intellij.python.processOutput.impl.ui.components

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.intellij.python.community.execService.impl.LoggedProcess
import com.intellij.python.community.execService.impl.LoggedProcessExitInfo
import com.intellij.python.community.execService.impl.LoggedProcessLine
import com.intellij.python.processOutput.impl.OutputFilter
import com.intellij.python.processOutput.impl.ProcessOutputTest
import io.mockk.called
import io.mockk.verify
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Clock

internal class OutputSectionTest : ProcessOutputTest() {
    @BeforeTest
    fun beforeTest() {
        scaffoldTestContent {
            OutputSection(controller)
        }
    }

    @Test
    fun `output area displays placeholder text when no process is selected`() = processOutputTest {
        // no process is selected at first, text should be shown
        onAllNodesWithTag(OutputSectionTestTags.NOT_SELECTED_TEXT).assertCountEquals(1)
    }

    @Test
    fun `action buttons are disabled when no process is selected`() = processOutputTest {
        // no process is selected at first, buttons should be disabled
        onNodeWithTag(OutputSectionTestTags.COPY_OUTPUT_BUTTON).assertIsNotEnabled()
    }

    @Test
    fun `action buttons are enabled when process is selected`() = processOutputTest {
        // selecting a process
        setSelectedProcess(process("process0"))

        // process is selected, should be enabled
        onNodeWithTag(OutputSectionTestTags.COPY_OUTPUT_BUTTON).assertIsEnabled()
    }

    @Test
    fun `action buttons call appropriate functions when clicked`() = processOutputTest {
        // selecting a process
        val testProcess = selectTestProcess()

        // no calls at first
        verify(exactly = 0) { controllerSpy.copyOutputToClipboard(testProcess) }

        // clicking on the section
        onNodeWithTag(OutputSectionTestTags.COPY_OUTPUT_BUTTON).performClick()

        // one call should have been made
        verify(exactly = 1) { controllerSpy.copyOutputToClipboard(testProcess) }
    }

    @Test
    fun `view filters buttons call their respective functions`() = processOutputTest {
        // clicking the view filters button
        onNodeWithTag(OutputSectionTestTags.FILTERS_BUTTON).performClick()

        // no menu buttons were clicked, no functions were called
        verify { controllerSpy wasNot called }

        // clicking on tags
        onNodeWithTag(
            OutputSectionTestTags.FILTERS_TAGS,
            useUnmergedTree = true,
        ).performClick()

        // tags should have been called
        verify(exactly = 1) { controllerSpy.toggleOutputFilter(OutputFilter.ShowTags) }
    }

    @Test
    fun `info section calls appropriate function when clicked`() = processOutputTest {
        // selecting a process
        selectTestProcess()

        // no calls at first
        verify(exactly = 0) { controllerSpy.toggleProcessInfo() }

        // clicking on the section
        onNodeWithTag(OutputSectionTestTags.INFO_SECTION).performClick()

        // one call should have been made
        verify(exactly = 1) { controllerSpy.toggleProcessInfo() }
    }

    @Test
    fun `output section calls appropriate function when clicked`() = processOutputTest {
        // selecting a process
        selectTestProcess()

        // no calls at first
        verify(exactly = 0) { controllerSpy.toggleProcessOutput() }

        // clicking on the section
        onNodeWithTag(OutputSectionTestTags.OUTPUT_SECTION).performClick()

        // one call should have been made
        verify(exactly = 1) { controllerSpy.toggleProcessOutput() }
    }

    @Test
    fun `copy output tag section button calls appropriate function when clicked`() =
        processOutputTest {
            // selecting a process with the following line sections:
            // 0..2 - stdout
            // 3..6 - stderr
            // 7..9 - stdout
            val process = selectTestProcess(
                lines = listOf(
                    outLine("out1"),
                    outLine("out2"),
                    outLine("out3"),

                    errLine("err4"),
                    errLine("err5"),
                    errLine("err6"),
                    errLine("err7"),

                    outLine("out8"),
                    outLine("out9"),
                    outLine("out10"),
                ),
            )

            // three copy tag section buttons should appear in total
            onAllNodesWithTag(
                OutputSectionTestTags.COPY_OUTPUT_TAG_SECTION_BUTTON,
                useUnmergedTree = true,
            ).assertCountEquals(3)

            // no buttons were clicked, no calls to the controller should have been made
            verify { controllerSpy wasNot called }

            // clicking on the first copy button (for section 0..2)
            onAllNodesWithTag(
                OutputSectionTestTags.COPY_OUTPUT_TAG_SECTION_BUTTON,
                useUnmergedTree = true,
            )[0].performClick()

            // copyOutputTagAtIndexToClipboard should have been called with index 0
            verify(exactly = 1) { controllerSpy.copyOutputTagAtIndexToClipboard(process, 0) }
            verify(exactly = 0) { controllerSpy.copyOutputTagAtIndexToClipboard(process, 3) }
            verify(exactly = 0) { controllerSpy.copyOutputTagAtIndexToClipboard(process, 7) }

            // clicking on the second copy button (for section 3..6)
            onAllNodesWithTag(
                OutputSectionTestTags.COPY_OUTPUT_TAG_SECTION_BUTTON,
                useUnmergedTree = true,
            )[1].performClick()

            // copyOutputTagAtIndexToClipboard should have been called with index 3
            verify(exactly = 1) { controllerSpy.copyOutputTagAtIndexToClipboard(process, 0) }
            verify(exactly = 1) { controllerSpy.copyOutputTagAtIndexToClipboard(process, 3) }
            verify(exactly = 0) { controllerSpy.copyOutputTagAtIndexToClipboard(process, 7) }

            // clicking on the second copy button (for section 7..9)
            onAllNodesWithTag(
                OutputSectionTestTags.COPY_OUTPUT_TAG_SECTION_BUTTON,
                useUnmergedTree = true,
            )[2].performClick()

            // copyOutputTagAtIndexToClipboard should have been called with index 7
            verify(exactly = 1) { controllerSpy.copyOutputTagAtIndexToClipboard(process, 0) }
            verify(exactly = 1) { controllerSpy.copyOutputTagAtIndexToClipboard(process, 3) }
            verify(exactly = 1) { controllerSpy.copyOutputTagAtIndexToClipboard(process, 7) }
        }

    @Test
    fun `copy output exit info button calls appropriate function when clicked`() =
        processOutputTest {
            // selecting a process with defined exit info
            val process = selectTestProcess(
                exitInfo =
                    LoggedProcessExitInfo(
                        exitedAt = Clock.System.now(),
                        exitValue = 0,
                    ),
            )

            // a copy exit info button should appear
            onNodeWithTag(
                OutputSectionTestTags.COPY_OUTPUT_EXIT_INFO_BUTTON,
                useUnmergedTree = true,
            ).assertExists()

            // no buttons were clicked, no calls to the controller should have been made
            verify { controllerSpy wasNot called }

            // clicking the copy exit info button
            onNodeWithTag(
                OutputSectionTestTags.COPY_OUTPUT_EXIT_INFO_BUTTON,
                useUnmergedTree = true,
            ).performClick()

            // copyOutputExitInfoToClipboard should have been called exactly once
            verify(exactly = 1) { controllerSpy.copyOutputExitInfoToClipboard(process) }
        }

    @Test
    fun `tags are displayed or hidden depending on show tags filter`() = processOutputTest {
        // selecting a process with 3 tags:
        // 0..2 - stdout
        // 3..5 - stderr
        // 6    - exit
        selectTestProcess(
            lines = listOf(
                outLine("out1"),
                outLine("out2"),
                outLine("out3"),

                errLine("err4"),
                errLine("err5"),
                errLine("err6"),
            ),
            exitInfo =
                LoggedProcessExitInfo(
                    exitedAt = Clock.System.now(),
                    exitValue = 0,
                ),
        )

        // total displayed tags should be 3
        onAllNodesWithTag(
            OutputSectionTestTags.OUTPUT_SECTION_TAG,
            useUnmergedTree = true,
        ).assertCountEquals(3)

        // remove show tags filter
        processOutputFilters.remove(OutputFilter.ShowTags)

        // total displayed tags should be 0
        onAllNodesWithTag(
            OutputSectionTestTags.OUTPUT_SECTION_TAG,
            useUnmergedTree = true,
        ).assertCountEquals(0)
    }

    private suspend fun selectTestProcess(
        lines: List<LoggedProcessLine> = listOf(),
        exitInfo: LoggedProcessExitInfo? = null,
    ): LoggedProcess {
        val newProcess =
            process(
                "process0",
                "arg1",
                "--flag1",
                "-f",
                cwd = "some/random/path",
                lines = lines,
                exitInfo = exitInfo,
            )

        setSelectedProcess(newProcess)

        return newProcess
    }
}
