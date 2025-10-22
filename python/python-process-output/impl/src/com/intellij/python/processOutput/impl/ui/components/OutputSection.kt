package com.intellij.python.processOutput.impl.ui.components

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
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.intellij.python.community.execService.impl.LoggedProcessLine
import com.intellij.python.processOutput.impl.ui.Icons
import com.intellij.python.processOutput.impl.OutputFilter
import com.intellij.python.processOutput.impl.ProcessOutputBundle.message
import com.intellij.python.processOutput.impl.ProcessOutputController
import com.intellij.python.processOutput.impl.Tag
import com.intellij.python.processOutput.impl.formatFull
import com.intellij.python.processOutput.impl.ui.Colors
import com.intellij.python.processOutput.impl.ui.collectReplayAsState
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.scrollbarContentSafePadding

@Composable
internal fun OutputSection(controller: ProcessOutputController) {
    val listState = remember { controller.processOutputUiState.lazyListState }

    val selectedProcess by controller.selectedProcess.collectAsState()

    Column {
        Toolbar {
            Box(modifier = Modifier.weight(1f)) {
                selectedProcess?.also {
                    InterText(
                        text = it.shortenedCommandString,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            FilterActionGroup(
                tooltipText = message("process.output.viewOptions.tooltip"),
                items = persistentListOf(
                    FilterEntry(
                        item = OutputFilter.ShowTags,
                        testTag = OutputSectionTestTags.FILTERS_TAGS,
                    ),
                ),
                isSelected = { controller.processOutputUiState.filters.contains(it) },
                onItemClick = { controller.toggleOutputFilter(it) },
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

        selectedProcess?.let {
            VerticallyScrollableContainer(
                modifier = Modifier.fillMaxSize(),
                scrollState = listState as ScrollableState,
            ) {
                val lines by it.lines.collectReplayAsState()
                val exitInfo by it.exitInfo.collectAsState()
                val isInfoExpandedState =
                    controller.processOutputUiState.isInfoExpanded.collectAsState()
                val isOutputExpandedState =
                    controller.processOutputUiState.isOutputExpanded.collectAsState()

                val isDisplayTags = controller.processOutputUiState.filters.contains(
                    OutputFilter.ShowTags,
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
                                    it.startedAt.formatFull(),
                                ),
                                InfoLine.Single(
                                    message("process.output.output.sections.info.command"),
                                    it.commandString,
                                ),
                                it.pid?.let { pid ->
                                    InfoLine.Single(
                                        message("process.output.output.sections.info.pid"),
                                        pid.toString(),
                                    )
                                },
                                it.cwd?.let { cwd ->
                                    InfoLine.Single(
                                        message("process.output.output.sections.info.cwd"),
                                        cwd,
                                    )
                                },
                                InfoLine.Multi(
                                    message("process.output.output.sections.info.env"),
                                    it.env.entries.map { (key, value) -> "$key=$value" },
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
                                val outputColor = when (line.kind) {
                                    LoggedProcessLine.Kind.OUT -> Color.Unspecified
                                    LoggedProcessLine.Kind.ERR -> Colors.Output.ErrorText
                                }

                                OutputLine(
                                    displayTags = isDisplayTags,
                                    tag = line.kind.tag.takeIf { tag ->
                                        lines.getOrNull(index - 1)?.kind?.tag != tag
                                    },
                                    text = line.text,
                                    textStyle = SpanStyle(
                                        color = outputColor,
                                    ),
                                )
                            }

                            exitInfo?.also { exitInfo ->
                                item(key = "exit") {
                                    OutputLine(
                                        displayTags = true,
                                        tag = Tag.EXIT,
                                        text = buildString {
                                            append(exitInfo.exitValue)

                                            exitInfo.additionalMessageToUser?.also { message ->
                                                append(": ")
                                                append(message)
                                            }
                                        },
                                        textStyle = SpanStyle(
                                            color =
                                                if (exitInfo.exitValue != 0) {
                                                    Colors.Output.ErrorText
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
            ?: EmptyContainerNotice(
                text = message("process.output.output.blankMessage"),
                modifier = Modifier.testTag(OutputSectionTestTags.NOT_SELECTED_TEXT),
            )
    }
}

@Composable
private fun OutputLine(
    displayTags: Boolean,
    tag: String? = null,
    text: String,
    textStyle: SpanStyle = SpanStyle(),
) {
    Column {
        if (tag != null) {
            LineSpacer()
        }

        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(end = scrollbarContentSafePadding()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DisableSelection {
                val padding = Tag.maxLength + 3

                Text(
                    text =
                        if (displayTags && tag != null) {
                            "$tag:".padStart(padding, ' ')
                        } else {
                            " ".repeat(padding)
                        },
                    style = JewelTheme.consoleTextStyle,
                    fontWeight = FontWeight.Thin,
                )
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

        }
    }
}

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
    val padding = maxLength + 2

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
                    .padding(end = scrollbarContentSafePadding()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
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
    Spacer(modifier = Modifier.height(4.dp))
}

private sealed class InfoLine {
    abstract val key: String

    data class Single(override val key: String, val value: String?) : InfoLine()
    data class Multi(override val key: String, val values: List<String?>) : InfoLine()
}

private val LoggedProcessLine.Kind.tag
    get() =
        when (this) {
            LoggedProcessLine.Kind.ERR -> Tag.ERROR
            LoggedProcessLine.Kind.OUT -> Tag.OUTPUT
        }

internal object OutputSectionTestTags {
    const val NOT_SELECTED_TEXT = "ProcessOutput.Output.NotSelectedText"
    const val INFO_SECTION = "ProcessOutput.Output.InfoSection"
    const val OUTPUT_SECTION = "ProcessOutput.Output.OutputSection"
    const val FILTERS_TAGS = "ProcessOutput.Output.FiltersTags"
    const val FILTERS_BUTTON = "ProcessOutput.Output.FiltersButton"
    const val FILTERS_MENU = "ProcessOutput.Output.FiltersMenu"
    const val COPY_OUTPUT_BUTTON = "ProcessOutput.Output.CopyButton"
}
