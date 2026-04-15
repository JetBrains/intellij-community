package com.intellij.python.processOutput.frontend.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import com.intellij.python.processOutput.common.OutputKindDto
import com.intellij.python.processOutput.common.OutputLineDto
import com.intellij.python.processOutput.frontend.InfoTag
import com.intellij.python.processOutput.frontend.OutputFilter
import com.intellij.python.processOutput.frontend.OutputTag
import com.intellij.python.processOutput.frontend.ProcessOutputBundle.message
import com.intellij.python.processOutput.frontend.ProcessOutputController
import com.intellij.python.processOutput.frontend.ProcessStatus
import com.intellij.python.processOutput.frontend.formatFull
import com.intellij.python.processOutput.frontend.ui.Colors
import com.intellij.python.processOutput.frontend.ui.Icons
import com.intellij.python.processOutput.frontend.ui.commandString
import com.intellij.python.processOutput.frontend.ui.shortenedCommandString

@Composable
internal fun OutputSection(controller: ProcessOutputController) {
    val selectedProcess by controller.selectedProcess.collectAsState()

    Column {
        Toolbar {
            Box(modifier = Modifier.weight(1f)) {
                selectedProcess?.also {
                    InterText(
                        text = it.data.shortenedCommandString,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            FilterActionGroup(
                tooltipText = message("process.output.viewOptions.tooltip"),
                state = controller.processOutputUiState.filters,
                onFilterItemToggled = { filterItem, enabled ->
                    controller.onOutputFilterItemToggled(filterItem, enabled)
                },
                modifier = Modifier.testTag(OutputSectionTestTags.FILTERS_BUTTON),
                menuModifier = Modifier.testTag(OutputSectionTestTags.FILTERS_MENU),
            )

            ActionIconButton(
                modifier = Modifier.testTag(OutputSectionTestTags.COPY_OUTPUT_BUTTON),
                iconKey = Icons.Keys.Copy,
                tooltipText = message("process.output.output.buttons.copyOutput"),
                enabled = selectedProcess != null,
                onClick = {
                    selectedProcess?.let {
                        controller.copyOutputToClipboard(it)
                    }
                },
            )
        }

        if (selectedProcess == null) {
            EmptyContainerNotice(
                text = message("process.output.output.blankMessage"),
                modifier = Modifier.testTag(OutputSectionTestTags.NOT_SELECTED_TEXT),
            )
        } else {
            OutputView(controller)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OutputView(controller: ProcessOutputController) {
    val isInfoExpanded by controller.processOutputUiState.isInfoExpanded.collectAsState()
    val isOutputExpanded by controller.processOutputUiState.isOutputExpanded.collectAsState()
    val selectedProcess by controller.selectedProcess.collectAsState()
    val verticalScrollState = remember { controller.processOutputUiState.verticalScrollState }
    val horizontalScrollState = remember { controller.processOutputUiState.horizontalScrollState }
    val filters = remember { controller.processOutputUiState.filters }

    selectedProcess?.let { loggedProcess ->
        val data = loggedProcess.data
        var lines by remember { mutableStateOf(emptyList<OutputLineDto>()) }
        val status by loggedProcess.status.collectAsState()
        val outputLines = remember(lines, filters, status) {
            buildList {
                for (line in lines) {
                    add(ConsoleLine(line.kind.tag, AnnotatedString(line.text)))
                }

                when (val status = status) {
                    ProcessStatus.Running -> {}
                    is ProcessStatus.Done -> {
                        val textColor = if (status.exitCode != 0)
                            Colors.ErrorText
                        else
                            Color.Unspecified

                        val exitText = buildAnnotatedString {
                            withStyle(SpanStyle(color = textColor)) {
                                append(status.exitCode.toString())
                                status.additionalMessageToUser?.also { message ->
                                    append(": ")
                                    append(message)
                                }
                            }
                        }

                        add(
                            ConsoleLine(tag = OutputTag.EXIT, text = exitText),
                        )
                    }
                }
            }
        }
        val infoLines = remember(data) {
            buildList {
                add(ConsoleLine(InfoTag.STARTED, AnnotatedString(data.startedAt.formatFull())))
                add(ConsoleLine(InfoTag.COMMAND, AnnotatedString(data.commandString)))
                data.pid?.also { pid ->
                    add(ConsoleLine(InfoTag.PID, AnnotatedString(pid.toString())))
                }
                data.cwd?.also { cwd ->
                    add(ConsoleLine(InfoTag.CWD, AnnotatedString(cwd)))
                }
                add(ConsoleLine(InfoTag.TARGET, AnnotatedString(data.target)))

                for ((key, value) in data.env.entries) {
                    add(ConsoleLine(InfoTag.ENV, AnnotatedString("$key=$value")))
                }
            }
        }

        LaunchedEffect(loggedProcess) {
            snapshotFlow { loggedProcess.lines.toList() }
                .collect { lines = it }
        }

        ConsoleContainer(
            verticalScrollState = verticalScrollState,
            horizontalScrollState = horizontalScrollState,
            wrapContent = filters.active.contains(OutputFilter.Item.WRAP_CONTENT),
        ) {
            CollapsibleListSection(
                text = message("process.output.output.sections.info"),
                modifier = Modifier.testTag(OutputSectionTestTags.INFO_SECTION),
                isExpanded = isInfoExpanded,
                onToggle = { controller.toggleProcessInfo() },
            )

            if (isInfoExpanded) {
                ConsoleOutput(
                    lines = infoLines,
                    formatter = InfoTag.formatter,
                    inputTestTag = OutputSectionTestTags.INFO_SECTION_CONTENT,
                    tagTestTag = OutputSectionTestTags.INFO_SECTION_TAG,
                    copyButtonTestTag = OutputSectionTestTags.INFO_SECTION_COPY_BUTTON,
                )
            }

            CollapsibleListSection(
                text = message("process.output.output.sections.output"),
                modifier = Modifier.testTag(OutputSectionTestTags.OUTPUT_SECTION),
                isExpanded = isOutputExpanded,
                onToggle = { controller.toggleProcessOutput() },
            )

            if (isOutputExpanded) {
                ConsoleOutput(
                    lines = outputLines,
                    formatter = OutputTag.formatter,
                    displayTags = filters.active.contains(OutputFilter.Item.SHOW_TAGS),
                    displayCopyButtons = true,
                    onCopy = { line, index ->
                        when (line.tag) {
                            OutputTag.EXIT ->
                                controller.copyOutputExitInfoToClipboard(loggedProcess)
                            OutputTag.OUTPUT, OutputTag.ERROR ->
                                controller.copyOutputTagAtIndexToClipboard(loggedProcess, index)
                        }
                    },
                    inputTestTag = OutputSectionTestTags.OUTPUT_SECTION_CONTENT,
                    tagTestTag = OutputSectionTestTags.OUTPUT_SECTION_TAG,
                    copyButtonTestTag = OutputSectionTestTags.OUTPUT_SECTION_COPY_BUTTON,
                )
            }
        }
    }
}

private val OutputKindDto.tag
    get() =
        when (this) {
            OutputKindDto.OUT -> OutputTag.OUTPUT
            OutputKindDto.ERR -> OutputTag.ERROR
        }

internal object OutputSectionTestTags {
    const val NOT_SELECTED_TEXT = "ProcessOutput.Output.NotSelectedText"
    const val INFO_SECTION = "ProcessOutput.Output.InfoSection"
    const val INFO_SECTION_CONTENT = "ProcessOutput.Output.InfoSectionContent"
    const val INFO_SECTION_TAG = "ProcessOutput.Output.InfoSection.Tag"
    const val INFO_SECTION_COPY_BUTTON = "ProcessOutput.Output.InfoSection.CopyButton"
    const val OUTPUT_SECTION = "ProcessOutput.Output.OutputSection"
    const val OUTPUT_SECTION_CONTENT = "ProcessOutput.Output.OutputSectionContent"
    const val OUTPUT_SECTION_TAG = "ProcessOutput.Output.OutputSection.Tag"
    const val OUTPUT_SECTION_COPY_BUTTON = "ProcessOutput.Output.OutputSection.CopyButton"
    const val FILTERS_TAGS = "ProcessOutput.Output.FiltersTags"
    const val FILTERS_WRAP = "ProcessOutput.Output.FiltersWrap"
    const val FILTERS_BUTTON = "ProcessOutput.Output.FiltersButton"
    const val FILTERS_MENU = "ProcessOutput.Output.FiltersMenu"
    const val COPY_OUTPUT_BUTTON = "ProcessOutput.Output.CopyButton"
}
