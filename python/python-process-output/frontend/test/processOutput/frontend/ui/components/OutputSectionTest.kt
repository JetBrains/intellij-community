package com.intellij.python.processOutput.frontend.ui.components

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.intellij.python.processOutput.common.OutputLineDto
import com.intellij.python.processOutput.frontend.InfoTag
import com.intellij.python.processOutput.frontend.LoggedProcess
import com.intellij.python.processOutput.frontend.OutputFilter
import com.intellij.python.processOutput.frontend.OutputTag
import com.intellij.python.processOutput.frontend.ProcessOutputTest
import com.intellij.python.processOutput.frontend.ProcessStatus
import com.intellij.python.processOutput.frontend.formatFull
import com.intellij.python.processOutput.frontend.ui.commandString
import io.mockk.called
import io.mockk.verify
import kotlin.collections.listOf
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

        // tags should have been called with enabled false
        verify(exactly = 1) {
            controllerSpy.onOutputFilterItemToggled(OutputFilter.Item.SHOW_TAGS, false)
        }
        verify(exactly = 0) {
            controllerSpy.onOutputFilterItemToggled(OutputFilter.Item.WRAP_CONTENT, false)
        }

        // clicking on wrap content
        onNodeWithTag(
            OutputSectionTestTags.FILTERS_WRAP,
            useUnmergedTree = true,
        ).performClick()

        // wrap should have been called with enabled false
        verify(exactly = 1) {
            controllerSpy.onOutputFilterItemToggled(OutputFilter.Item.SHOW_TAGS, false)
        }
        verify(exactly = 1) {
            controllerSpy.onOutputFilterItemToggled(OutputFilter.Item.WRAP_CONTENT, false)
        }
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
                OutputSectionTestTags.OUTPUT_SECTION_COPY_BUTTON,
                useUnmergedTree = true,
            ).assertCountEquals(3)

            // no buttons were clicked, no calls to the controller should have been made
            verify { controllerSpy wasNot called }

            // clicking on the first copy button (for section 0..2)
            onAllNodesWithTag(
                OutputSectionTestTags.OUTPUT_SECTION_COPY_BUTTON,
                useUnmergedTree = true,
            )[0].performClick()

            // copyOutputTagAtIndexToClipboard should have been called with index 0
            verify(exactly = 1) { controllerSpy.copyOutputTagAtIndexToClipboard(process, 0) }
            verify(exactly = 0) { controllerSpy.copyOutputTagAtIndexToClipboard(process, 3) }
            verify(exactly = 0) { controllerSpy.copyOutputTagAtIndexToClipboard(process, 7) }

            // clicking on the second copy button (for section 3..6)
            onAllNodesWithTag(
                OutputSectionTestTags.OUTPUT_SECTION_COPY_BUTTON,
                useUnmergedTree = true,
            )[1].performClick()

            // copyOutputTagAtIndexToClipboard should have been called with index 3
            verify(exactly = 1) { controllerSpy.copyOutputTagAtIndexToClipboard(process, 0) }
            verify(exactly = 1) { controllerSpy.copyOutputTagAtIndexToClipboard(process, 3) }
            verify(exactly = 0) { controllerSpy.copyOutputTagAtIndexToClipboard(process, 7) }

            // clicking on the second copy button (for section 7..9)
            onAllNodesWithTag(
                OutputSectionTestTags.OUTPUT_SECTION_COPY_BUTTON,
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
                status =
                    ProcessStatus.Done(
                        exitedAt = Clock.System.now(),
                        exitCode = 0,
                    ),
            )

            // a copy exit info button should appear
            onNodeWithTag(
                OutputSectionTestTags.OUTPUT_SECTION_COPY_BUTTON,
                useUnmergedTree = true,
            ).assertExists()

            // no buttons were clicked, no calls to the controller should have been made
            verify { controllerSpy wasNot called }

            // clicking the copy exit info button
            onNodeWithTag(
                OutputSectionTestTags.OUTPUT_SECTION_COPY_BUTTON,
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
            status =
                ProcessStatus.Done(
                    exitedAt = Clock.System.now(),
                    exitCode = 0,
                ),
        )

        // total displayed tags should be 3
        onAllNodesWithTag(
            OutputSectionTestTags.OUTPUT_SECTION_TAG,
            useUnmergedTree = true,
        ).assertCountEquals(3)

        // remove show tags filter
        testProcessOutputUiState.filters.active.remove(OutputFilter.Item.SHOW_TAGS)

        // total displayed tags should be 0
        onAllNodesWithTag(
            OutputSectionTestTags.OUTPUT_SECTION_TAG,
            useUnmergedTree = true,
        ).assertCountEquals(0)
    }

    @Test
    fun `copy buttons should not appear for info section`() = processOutputTest {
        // selecting a process
        selectTestProcess()

        // no copy section buttons should appear
        onAllNodesWithTag(OutputSectionTestTags.INFO_SECTION_COPY_BUTTON).assertCountEquals(0)

        // expanding the info section
        processOutputInfoExpanded.value = true

        // no copy section buttons should appear even with info section expanded
        onAllNodesWithTag(OutputSectionTestTags.INFO_SECTION_COPY_BUTTON).assertCountEquals(0)
    }

    @Test
    fun `info section should contain correct information`() = processOutputTest {
        // selecting a process
        val process =
            selectTestProcess(
                env = mapOf(
                    "foo" to "123",
                    "bar" to "hello",
                    "baz" to "world",
                ),
            )

        // expanding the info section
        processOutputInfoExpanded.value = true

        // info should be correct
        onNodeWithTag(OutputSectionTestTags.INFO_SECTION_CONTENT)
            .assertTextEquals(
                """
                    ${process.data.startedAt.formatFull()}
                    ${process.data.commandString}
                    ${process.data.pid}
                    ${process.data.cwd}
                    ${process.data.target}
                    foo=123
                    bar=hello
                    baz=world
                    
                """.trimIndent(),
            )

        // tags should be correct
        onAllNodesWithTag(OutputSectionTestTags.INFO_SECTION_TAG, useUnmergedTree = true).apply {
            assertCountEquals(6)
            get(0).assertTextContains(InfoTag.STARTED.text, substring = true)
            get(1).assertTextContains(InfoTag.COMMAND.text, substring = true)
            get(2).assertTextContains(InfoTag.PID.text, substring = true)
            get(3).assertTextContains(InfoTag.CWD.text, substring = true)
            get(4).assertTextContains(InfoTag.TARGET.text, substring = true)
            get(5).assertTextContains(InfoTag.ENV.text, substring = true)
        }
    }

    @Test
    fun `output section should contain correct information`() = processOutputTest {
        // selecting process that has stdout, stderr and exit sections
        selectTestProcess(
            lines = listOf(
                outLine("out1"),
                outLine("out2"),
                outLine("out3"),

                errLine("err4"),
                errLine("err5"),
                errLine("err6"),
            ),
            status =
                ProcessStatus.Done(
                    exitedAt = Clock.System.now(),
                    exitCode = 0,
                ),
        )

        // output should be correct
        onNodeWithTag(OutputSectionTestTags.OUTPUT_SECTION_CONTENT)
            .assertTextEquals(
                """
                    out1
                    out2
                    out3
                    err4
                    err5
                    err6
                    0
                    
                """.trimIndent(),
            )

        // tags should be correct
        onAllNodesWithTag(OutputSectionTestTags.OUTPUT_SECTION_TAG, useUnmergedTree = true).apply {
            assertCountEquals(3)
            get(0).assertTextContains(OutputTag.OUTPUT.text, substring = true)
            get(1).assertTextContains(OutputTag.ERROR.text, substring = true)
            get(2).assertTextContains(OutputTag.EXIT.text, substring = true)
        }
    }

    @Test
    fun `output section should reflect newly added lines in an existing process`() =
        processOutputTest {
            // creating process with initial lines
            val lines = SnapshotStateList<OutputLineDto>()
            lines += outLine("line 1")
            selectTestProcess(lines = lines)

            // output should reflect line 1
            onNodeWithTag(OutputSectionTestTags.OUTPUT_SECTION_CONTENT)
                .assertTextEquals(
                    """
                        line 1
                        
                    """.trimIndent(),
                )

            // adding additional lines
            lines += outLine("line 2")
            lines += outLine("line 3")
            awaitIdle()

            // output should now reflect line 1, 2 and 3
            onNodeWithTag(OutputSectionTestTags.OUTPUT_SECTION_CONTENT)
                .assertTextEquals(
                    """
                        line 1
                        line 2
                        line 3
                        
                    """.trimIndent(),
                )
        }

    @Test
    fun `sections should be shown or hidden depending on state`() = processOutputTest {
        // selecting a process
        selectTestProcess()

        // info should be hidden, output should be shown
        onAllNodesWithTag(OutputSectionTestTags.INFO_SECTION_CONTENT).assertCountEquals(0)
        onAllNodesWithTag(OutputSectionTestTags.OUTPUT_SECTION_CONTENT).assertCountEquals(1)

        // toggling the state values
        processOutputInfoExpanded.value = true
        processOutputOutputExpanded.value = false

        // info should be shown, output should be hidden
        onAllNodesWithTag(OutputSectionTestTags.INFO_SECTION_CONTENT).assertCountEquals(1)
        onAllNodesWithTag(OutputSectionTestTags.OUTPUT_SECTION_CONTENT).assertCountEquals(0)
    }

    private fun selectTestProcess(
        lines: List<OutputLineDto> = listOf(),
        status: ProcessStatus = ProcessStatus.Running,
        env: Map<String, String> = mapOf(),
    ): LoggedProcess {
        val newProcess =
            process(
                "process0",
                "arg1",
                "--flag1",
                "-f",
                cwd = "some/random/path",
                lines = lines,
                status = status,
                env = env,
            )

        setSelectedProcess(newProcess)

        return newProcess
    }
}
