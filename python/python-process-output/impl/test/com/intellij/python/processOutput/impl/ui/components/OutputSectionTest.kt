package com.intellij.python.processOutput.impl.ui.components

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.intellij.python.community.execService.impl.LoggedProcess
import com.intellij.python.processOutput.impl.OutputFilter
import com.intellij.python.processOutput.impl.ProcessOutputTest
import io.mockk.called
import io.mockk.verify
import kotlin.test.BeforeTest
import kotlin.test.Test

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

    private suspend fun selectTestProcess(): LoggedProcess {
        val newProcess =
            process(
                "process0",
                "arg1",
                "--flag1",
                "-f",
                cwd = "some/random/path",
            )

        setSelectedProcess(newProcess)

        return newProcess
    }
}
