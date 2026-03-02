package com.intellij.python.processOutput.frontend.ui.components

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.intellij.python.processOutput.common.OutputKindDto
import com.intellij.python.processOutput.frontend.OutputFilter
import com.intellij.python.processOutput.frontend.ProcessOutputBundle.message
import com.intellij.python.processOutput.frontend.ProcessOutputController
import com.intellij.python.processOutput.frontend.ProcessStatus
import com.intellij.python.processOutput.frontend.Tag
import com.intellij.python.processOutput.frontend.formatFull
import com.intellij.python.processOutput.frontend.ui.Colors
import com.intellij.python.processOutput.frontend.ui.Icons
import com.intellij.python.processOutput.frontend.ui.commandString
import com.intellij.python.processOutput.frontend.ui.shortenedCommandString
import com.intellij.python.processOutput.frontend.ui.thenIfNotNull
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.component.scrollbarContentSafePadding

private object OutputSectionStyling {
    val COPY_SECTION_BUTTON_SPACE_SIZE = 18.dp
    val LINE_START_PADDING = 8.dp
    val LINE_HORIZONTAL_ALIGNMENT = 10.dp
    val LINE_SPACER_HEIGHT = 4.dp
}

@Composable
internal fun OutputSection(controller: ProcessOutputController) {
    val listState = remember { controller.processOutputUiState.lazyListState }

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

        selectedProcess?.let { loggedProcess ->
            VerticallyScrollableContainer(
                modifier = Modifier.fillMaxSize(),
                scrollState = listState as ScrollableState,
            ) {
                val lines = remember(loggedProcess) { loggedProcess.lines }
                val status by loggedProcess.status.collectAsState()
                val isInfoExpandedState =
                    controller.processOutputUiState.isInfoExpanded.collectAsState()
                val isOutputExpandedState =
                    controller.processOutputUiState.isOutputExpanded.collectAsState()

                val isDisplayTags = controller.processOutputUiState.filters.active.contains(
                    OutputFilter.Item.SHOW_TAGS,
                )

                SelectionContainer {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                    ) {
                        collapsibleSectionItem(
                            title = message("process.output.output.sections.info"),
                            modifier = Modifier.testTag(OutputSectionTestTags.INFO_SECTION),
                            isExpandedState = isInfoExpandedState,
                            onToggle = { controller.toggleProcessInfo() },
                        ) {
                            infoLineItems(
                                InfoLine.Single(
                                    message("process.output.output.sections.info.started"),
                                    loggedProcess.data.startedAt.formatFull(),
                                ),
                                InfoLine.Single(
                                    message("process.output.output.sections.info.command"),
                                    loggedProcess.data.commandString,
                                ),
                                loggedProcess.data.pid?.let { pid ->
                                    InfoLine.Single(
                                        message("process.output.output.sections.info.pid"),
                                        pid.toString(),
                                    )
                                },
                                loggedProcess.data.cwd?.let { cwd ->
                                    InfoLine.Single(
                                        message("process.output.output.sections.info.cwd"),
                                        cwd,
                                    )
                                },
                                InfoLine.Single(
                                    message("process.output.output.sections.info.target"),
                                    loggedProcess.data.target,
                                ),
                                InfoLine.Multi(
                                    message("process.output.output.sections.info.env"),
                                    loggedProcess.data.env.entries.map { (key, value) ->
                                        "$key=$value"
                                    },
                                ),
                            )

                            item(key = "blank") { Text("") }
                        }

                        collapsibleSectionItem(
                            title = message("process.output.output.sections.output"),
                            modifier = Modifier.testTag(OutputSectionTestTags.OUTPUT_SECTION),
                            isExpandedState = isOutputExpandedState,
                            onToggle = { controller.toggleProcessOutput() },
                        ) {
                            itemsIndexed(
                                items = lines,
                                key = { index, _ -> index },
                            ) { index, line ->
                                // when the kind of the current line does not match the kind of the
                                // previous line, it means that the current line is the start of a
                                // new section
                                val startOfNewSection =
                                    lines.getOrNull(index - 1)?.kind != line.kind

                                OutputLine(
                                    displayTags = isDisplayTags,
                                    sectionIndicator =
                                        if (startOfNewSection) {
                                            SectionIndicator(line.kind.tag) {
                                                controller.copyOutputTagAtIndexToClipboard(
                                                    loggedProcess,
                                                    index,
                                                )
                                            }
                                        } else {
                                            null
                                        },
                                    text = line.text,
                                )
                            }

                            when (val status = status) {
                                ProcessStatus.Running -> {}
                                is ProcessStatus.Done -> {
                                    item(key = "exit") {
                                        OutputLine(
                                            displayTags = isDisplayTags,
                                            sectionIndicator =
                                                SectionIndicator(
                                                    Tag.EXIT,
                                                    OutputSectionTestTags.COPY_OUTPUT_EXIT_INFO_BUTTON,
                                                ) {
                                                    controller.copyOutputExitInfoToClipboard(
                                                        loggedProcess,
                                                    )
                                                },
                                            text = buildString {
                                                append(status.exitCode)

                                                status.additionalMessageToUser?.also { message ->
                                                    append(": ")
                                                    append(message)
                                                }
                                            },
                                            textStyle = SpanStyle(
                                                color =
                                                    if (status.exitCode != 0) {
                                                        Colors.ErrorText
                                                    } else {
                                                        Color.Unspecified
                                                    },
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
            ?: EmptyContainerNotice(
                text = message("process.output.output.blankMessage"),
                modifier = Modifier.testTag(OutputSectionTestTags.NOT_SELECTED_TEXT),
            )
    }
}

@Composable
private fun OutputLine(
    displayTags: Boolean,
    sectionIndicator: SectionIndicator? = null,
    text: String,
    textStyle: SpanStyle = SpanStyle(),
) {
    Column {
        if (sectionIndicator != null) {
            LineSpacer()
        }

        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(
                    end = scrollbarContentSafePadding(),
                    start = OutputSectionStyling.LINE_START_PADDING,
                ),
            horizontalArrangement = Arrangement.spacedBy(OutputSectionStyling.LINE_HORIZONTAL_ALIGNMENT),
        ) {
            if (displayTags) {
                DisableSelection {
                    val padding = Tag.maxLength + 1

                    Text(
                        text =
                            if (sectionIndicator != null) {
                                "${sectionIndicator.tag}:".padStart(padding, ' ')
                            } else {
                                " ".repeat(padding)
                            },
                        style = JewelTheme.consoleTextStyle,
                        fontWeight = FontWeight.Thin,
                        modifier =
                            Modifier.thenIfNotNull(sectionIndicator) {
                                testTag(OutputSectionTestTags.OUTPUT_SECTION_TAG)
                            },
                    )
                }
            }

            Text(
                text = buildAnnotatedString {
                    withStyle(style = textStyle) {
                        append(text)
                    }
                },
                style = JewelTheme.consoleTextStyle,
                modifier = Modifier.fillMaxWidth()
                    .weight(1f),
            )

            if (sectionIndicator != null) {
                ActionIconButton(
                    modifier = Modifier
                        .size(OutputSectionStyling.COPY_SECTION_BUTTON_SPACE_SIZE)
                        .testTag(sectionIndicator.copyButtonTestTag),
                    iconKey = Icons.Keys.Copy,
                    tooltipText = message("process.output.output.copySection.tooltip"),
                    onClick = sectionIndicator.onCopy,
                )
            } else {
                Spacer(
                    modifier = Modifier.size(OutputSectionStyling.COPY_SECTION_BUTTON_SPACE_SIZE),
                )
            }
        }
    }
}

private data class SectionIndicator(
    val tag: String,
    val copyButtonTestTag: String = OutputSectionTestTags.COPY_OUTPUT_TAG_SECTION_BUTTON,
    val onCopy: () -> Unit,
)

private fun LazyListScope.collapsibleSectionItem(
    title: String,
    modifier: Modifier = Modifier,
    isExpandedState: State<Boolean>,
    onToggle: () -> Unit,
    content: LazyListScope.() -> Unit,
) {
    item(key = "collapsibleSection $title") {
        val isExpanded by isExpandedState

        CollapsibleListSection(
            text = title,
            modifier = modifier,
            isExpanded = isExpanded,
            onToggle = onToggle,
        )
    }

    if (isExpandedState.value) {
        this.content()
    }
}

private fun LazyListScope.infoLineItems(
    vararg infoLines: InfoLine?,
) {
    val maxLength = infoLines.maxOfOrNull {
        when (it) {
            is InfoLine.Single -> it.key.length
            else -> 0
        }
    } ?: 0
    val padding = maxLength + 1

    infoLines.forEach { infoLine ->
        when (infoLine) {
            is InfoLine.Single ->
                infoLineItemSingle(infoLine.key, infoLine.key, infoLine.value, padding)
            is InfoLine.Multi -> {
                infoLineItemSingle(
                    infoLine.key,
                    infoLine.key,
                    infoLine.values.takeIf { it.isNotEmpty() }?.let { it[0] },
                    padding,
                )

                infoLine.values.drop(1).forEachIndexed { index, value ->
                    infoLineItemSingle("${infoLine.key} $index", null, value, padding)
                }
            }
            null -> {}
        }
    }
}

private fun LazyListScope.infoLineItemSingle(
    id: Any,
    key: String?,
    value: String?,
    padding: Int,
) {
    item(key = "infoLineItem ${id}") {
        Column {
            if (key != null) {
                LineSpacer()
            }

            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(
                        end = scrollbarContentSafePadding(),
                        start = OutputSectionStyling.LINE_START_PADDING,
                    ),
                horizontalArrangement = Arrangement.spacedBy(OutputSectionStyling.LINE_HORIZONTAL_ALIGNMENT),
            ) {
                Text(
                    text =
                        if (key != null) {
                            "${key}:".padStart(padding)
                        } else {
                            " ".repeat(padding)
                        },
                    style = JewelTheme.consoleTextStyle,
                    fontWeight = FontWeight.Thin,
                )

                Text(
                    text = value ?: "(empty)",
                    modifier = Modifier.fillMaxWidth(),
                    color = if (value == null) {
                        Colors.Output.Info
                    } else {
                        Color.Unspecified
                    },
                    style = JewelTheme.consoleTextStyle,
                )
            }
        }
    }
}

@Composable
private fun LineSpacer() {
    Spacer(modifier = Modifier.height(OutputSectionStyling.LINE_SPACER_HEIGHT))
}

private sealed class InfoLine {
    abstract val key: String

    data class Single(override val key: String, val value: String?) : InfoLine()
    data class Multi(override val key: String, val values: List<String?>) : InfoLine()
}

private val OutputKindDto.tag
    get() =
        when (this) {
            OutputKindDto.OUT -> Tag.ERROR
            OutputKindDto.ERR -> Tag.OUTPUT
        }

internal object OutputSectionTestTags {
    const val NOT_SELECTED_TEXT = "ProcessOutput.Output.NotSelectedText"
    const val INFO_SECTION = "ProcessOutput.Output.InfoSection"
    const val OUTPUT_SECTION = "ProcessOutput.Output.OutputSection"
    const val OUTPUT_SECTION_TAG = "ProcessOutput.Output.OutputSection.Tag"
    const val FILTERS_TAGS = "ProcessOutput.Output.FiltersTags"
    const val FILTERS_BUTTON = "ProcessOutput.Output.FiltersButton"
    const val FILTERS_MENU = "ProcessOutput.Output.FiltersMenu"
    const val COPY_OUTPUT_BUTTON = "ProcessOutput.Output.CopyButton"
    const val COPY_OUTPUT_TAG_SECTION_BUTTON = "ProcessOutput.Output.CopyTagSectionButton"
    const val COPY_OUTPUT_EXIT_INFO_BUTTON = "ProcessOutput.Output.CopyExitInfoButton"
}
