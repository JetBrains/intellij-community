package com.intellij.python.processOutput.frontend.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.intellij.python.processOutput.common.ProcessWeightDto
import com.intellij.python.processOutput.common.TraceContextKind
import com.intellij.python.processOutput.frontend.ProcessOutputBundle.message
import com.intellij.python.processOutput.frontend.ProcessOutputController
import com.intellij.python.processOutput.frontend.ProcessStatus
import com.intellij.python.processOutput.frontend.TreeFilter
import com.intellij.python.processOutput.frontend.TreeNode
import com.intellij.python.processOutput.frontend.formatTime
import com.intellij.python.processOutput.frontend.ui.Colors
import com.intellij.python.processOutput.frontend.ui.Icons
import com.intellij.python.processOutput.frontend.ui.processIsBackground
import com.intellij.python.processOutput.frontend.ui.processIsError
import com.intellij.python.processOutput.frontend.ui.shortenedCommandString
import kotlin.time.Instant
import org.jetbrains.jewel.bridge.icon.fromPlatformIcon
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.lazy.tree.Tree
import org.jetbrains.jewel.foundation.modifier.thenIf
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.LazyTree
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.component.styling.LazyTreeMetrics
import org.jetbrains.jewel.ui.component.styling.LazyTreeStyle
import org.jetbrains.jewel.ui.component.styling.SimpleListItemMetrics
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icon.IntelliJIconKey
import org.jetbrains.jewel.ui.theme.treeStyle

private object TreeSectionStyling {
    const val ERROR_CUTOUT_OFFSET = 6
    const val ERROR_CUTOUT_RADIUS = 13f
    const val BACKGROUND_PROCESS_ALPHA = 0.5f
    val ERROR_ICON_OFFSET = 2.dp
    val ERROR_ICON_SIZE = 10.dp
    val SEARCH_FIELD_PADDING = PaddingValues(start = 1.dp, top = 2.dp, bottom = 2.dp)
    val SEARCH_FIELD_CLEAR_ICON_SPACER_WIDTH = 16.dp
    val SEARCH_FIELD_MARGIN_END = 8.dp
    val TREE_ITEM_INNER_PADDING = PaddingValues(horizontal = 4.dp)
    val TREE_ITEM_OUTER_PADDING = PaddingValues(all = 1.dp)
    val TREE_COLUMN_PADDING = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
    val TREE_ROW_HEIGHT = 24.dp
    val TREE_ITEM_HORIZONTAL_ARRANGEMENT = 6.dp
    val TREE_ITEM_CONTEXT_PADDING = PaddingValues(start = 3.dp)
    val TREE_ITEM_PROCESS_PADDING = PaddingValues(start = 21.dp)
    val TREE_ITEM_PROCESS_WEIGHT_PADDING = PaddingValues(end = 3.dp)
    val TREE_ITEM_TIME_PADDING = PaddingValues(end = 6.dp)
}

private enum class ErrorKind {
    NONE,
    NORMAL,
    CRITICAL,
}

private data class ProcessWeightViewModel(
    val iconKey: IconKey,
    val contentDesc: String,
    val tooltipMessage: String,
    val testId: String,
)

@Composable
internal fun TreeSection(controller: ProcessOutputController) {
    Column(modifier = Modifier.fillMaxSize()) {
        val tree by controller.processTreeUiState.tree.collectAsState()

        TreeToolbar(
            controller = controller,
            areExpansionActionsEnabled = !tree.isEmpty(),
        )

        TreeContent(
            controller = controller,
            tree = tree,
        )
    }
}

@Composable
private fun TreeToolbar(
    controller: ProcessOutputController,
    areExpansionActionsEnabled: Boolean,
) {
    val clearInteractionSource = remember { MutableInteractionSource() }
    val isClearHovered by clearInteractionSource.collectIsHoveredAsState()
    val inputState = remember { controller.processTreeUiState.searchState }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Toolbar {
        Icon(
            key = Icons.Keys.Search,
            contentDescription = message("process.output.icon.description.search"),
        )

        TextField(
            state = inputState,
            modifier =
                Modifier
                    .padding(TreeSectionStyling.SEARCH_FIELD_PADDING)
                    .weight(1f)
                    .focusRequester(focusRequester),
            placeholder = { Text(message("process.output.tree.search.placeholder")) },
            undecorated = true,
        )

        if (inputState.text.isNotBlank()) {
            Icon(
                key =
                    if (isClearHovered) {
                        Icons.Keys.CloseHovered
                    } else {
                        Icons.Keys.Close
                    },
                contentDescription = message("process.output.icon.description.clear"),
                modifier = Modifier
                    .pointerHoverIcon(PointerIcon.Default)
                    .clickable(
                        interactionSource = clearInteractionSource,
                        indication = null,
                        role = Role.Button,
                    ) {
                        inputState.clearText()
                    },
            )
        } else {
            Spacer(
                modifier = Modifier.width(TreeSectionStyling.SEARCH_FIELD_CLEAR_ICON_SPACER_WIDTH),
            )
        }

        Spacer(modifier = Modifier.width(TreeSectionStyling.SEARCH_FIELD_MARGIN_END))

        FilterActionGroup(
            tooltipText = message("process.output.viewOptions.tooltip"),
            state = controller.processTreeUiState.filters,
            onFilterItemToggled = { filterItem, enabled ->
                controller.onTreeFilterItemToggled(filterItem, enabled)
            },
            modifier = Modifier.testTag(TreeSectionTestTags.FILTERS_BUTTON),
            menuModifier = Modifier.testTag(TreeSectionTestTags.FILTERS_MENU),
        )

        ActionIconButton(
            modifier = Modifier.testTag(TreeSectionTestTags.EXPAND_ALL_BUTTON),
            iconKey = Icons.Keys.ExpandAll,
            tooltipText = message("process.output.tree.buttons.expandAll"),
            enabled = areExpansionActionsEnabled,
            onClick = { controller.expandAllContexts() },
        )

        ActionIconButton(
            modifier = Modifier.testTag(TreeSectionTestTags.COLLAPSE_ALL_BUTTON),
            iconKey = Icons.Keys.CollapseAll,
            tooltipText = message("process.output.tree.buttons.collapseAll"),
            enabled = areExpansionActionsEnabled,
            onClick = { controller.collapseAllContexts() },
        )
    }
}

@OptIn(ExperimentalJewelApi::class)
@Composable
private fun TreeContent(controller: ProcessOutputController, tree: Tree<TreeNode>) {
    val filters = remember { controller.processTreeUiState.filters }
    val selectableLazyListState = remember { controller.processTreeUiState.selectableLazyListState }

    if (!tree.isEmpty()) {
        VerticallyScrollableContainer(
            scrollState = selectableLazyListState.lazyListState as ScrollableState,
        ) {
            val treeState = remember { controller.processTreeUiState.treeState }
            val style = JewelTheme.treeStyle

            Column(modifier = Modifier.padding(TreeSectionStyling.TREE_COLUMN_PADDING)) {
                // Seems like the current implementation keeps track of all previous trees,
                // causing OOM issues. Wrapping it in `key(tree)` will force a full
                // recomposition of the tree, removing any garbage that was not collected.
                key(tree) {
                    LazyTree(
                        tree = tree,
                        modifier = Modifier.fillMaxSize(),
                        treeState = treeState,
                        onSelectionChange = {
                            val node = it.firstOrNull()?.data

                            if (node is TreeNode.Process) {
                                controller.selectProcess(node.process)
                            } else {
                                controller.selectProcess(null)
                            }
                        },
                        style = LazyTreeStyle(
                            colors = style.colors,
                            metrics = LazyTreeMetrics(
                                indentSize = style.metrics.indentSize,
                                elementMinHeight = style.metrics.elementMinHeight,
                                chevronContentGap = style.metrics.chevronContentGap,
                                simpleListItemMetrics = SimpleListItemMetrics(
                                    innerPadding = TreeSectionStyling.TREE_ITEM_INNER_PADDING,
                                    outerPadding = TreeSectionStyling.TREE_ITEM_OUTER_PADDING,
                                    selectionBackgroundCornerSize =
                                        style
                                            .metrics
                                            .simpleListItemMetrics
                                            .selectionBackgroundCornerSize,
                                    iconTextGap =
                                        style
                                            .metrics
                                            .simpleListItemMetrics
                                            .iconTextGap,
                                ),
                            ),
                            icons = style.icons,
                        ),
                        interactionSource = remember { MutableInteractionSource() },
                    ) {
                        TreeRow(
                            controller,
                            it.data,
                            filters.active.contains(TreeFilter.Item.SHOW_TIME),
                            filters.active.contains(TreeFilter.Item.SHOW_PROCESS_WEIGHT),
                        )
                    }
                }
            }
        }
    } else {
        EmptyContainerNotice(
            text = message("process.output.tree.blankMessage"),
            modifier = Modifier.testTag(TreeSectionTestTags.EMPTY_TREE_TEXT),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TreeRow(
    controller: ProcessOutputController,
    node: TreeNode,
    isTimeDisplayed: Boolean,
    isProcessWeightDisplayed: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(TreeSectionStyling.TREE_ROW_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (node) {
            is TreeNode.Context -> {
                Tooltip(
                    tooltip = {
                        Row {
                            Text(node.traceContext.title)
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Row(
                        modifier = Modifier.padding(
                            TreeSectionStyling.TREE_ITEM_CONTEXT_PADDING,
                        ),
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                TreeSectionStyling.TREE_ITEM_HORIZONTAL_ARRANGEMENT,
                            ),
                    ) {
                        Icon(
                            key = Icons.Keys.Folder,
                            contentDescription =
                                message("process.output.icon.description.folder"),
                            modifier = Modifier.testTag(
                                TreeSectionTestTags.FOLDER_ITEM_ICON,
                            ),
                        )

                        InterText(
                            text = node.traceContext.title,
                            modifier = Modifier.weight(1f),
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            is TreeNode.Process -> {
                val icon = node.icon
                val status by node.process.status.collectAsState()
                val errorKind = remember(status) {
                    when (val status = status) {
                        ProcessStatus.Running -> ErrorKind.NONE
                        is ProcessStatus.Done if status.exitCode == 0 -> ErrorKind.NONE
                        is ProcessStatus.Done if !status.isCritical -> ErrorKind.NORMAL
                        else -> ErrorKind.CRITICAL
                    }
                }
                val kind =
                    node.process.data.traceContextUuid
                        ?.let { controller.resolveTraceContext(it) }
                        ?.kind
                val isBackground = when (kind) {
                    TraceContextKind.NON_INTERACTIVE -> true
                    TraceContextKind.INTERACTIVE, null -> false
                }
                val isRunning = remember(status) { status == ProcessStatus.Running }

                Tooltip(
                    tooltip = {
                        Row {
                            Text(node.process.data.shortenedCommandString)
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Row(
                        modifier = Modifier.padding(TreeSectionStyling.TREE_ITEM_PROCESS_PADDING)
                            .semantics {
                                processIsError = errorKind != ErrorKind.NONE
                                processIsBackground = isBackground
                            },
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                TreeSectionStyling.TREE_ITEM_HORIZONTAL_ARRANGEMENT,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProcessIcon(
                            icon
                                ?.let {
                                    IntelliJIconKey.fromPlatformIcon(
                                        icon.icon,
                                        icon.iconClass,
                                    )
                                }
                                ?: Icons.Keys.Process,
                            errorKind,
                            isBackground,
                            isRunning,
                        )

                        InterText(
                            text = node.process.data.shortenedCommandString,
                            overflow = TextOverflow.Ellipsis,
                            color =
                                when (errorKind) {
                                    ErrorKind.NONE, ErrorKind.NORMAL -> Color.Unspecified
                                    ErrorKind.CRITICAL -> Colors.ErrorText
                                },
                        )
                    }
                }
            }
        }

        if (isProcessWeightDisplayed) {
            when (node) {
                is TreeNode.Process ->
                    ProcessWeightIcon(node.process.data.weight)
                is TreeNode.Context -> {}
            }
        }

        if (isTimeDisplayed) {
            val instant = when (node) {
                is TreeNode.Context -> Instant.fromEpochMilliseconds(node.traceContext.timestamp)
                is TreeNode.Process -> node.process.data.startedAt
            }

            InterText(
                text = instant.formatTime(),
                modifier = Modifier.padding(TreeSectionStyling.TREE_ITEM_TIME_PADDING),
                color = Colors.Tree.Info,
            )
        }
    }
}

@Composable
private fun ProcessIcon(
    icon: IconKey,
    errorKind: ErrorKind,
    isBackground: Boolean,
    isRunning: Boolean,
) {
    val isError = errorKind != ErrorKind.NONE

    when {
        isRunning -> {
            CircularProgressIndicator()
        }
        errorKind == ErrorKind.CRITICAL -> {
            Icon(
                key = Icons.Keys.ResultIncorrect,
                contentDescription =
                    message("process.output.icon.description.criticalProcessError"),
                tint = Colors.ErrorText,
            )
        }
        else -> {
            Box {
                Icon(
                    key = icon,
                    contentDescription = message("process.output.icon.description.process"),
                    modifier = Modifier
                        .thenIf(isError) {
                            // Clips a circle out of the icon on bottom right, around the place that
                            // will be occupied by the error icon. This visually separates the error
                            // icon from the base icon, making it look more pleasing.
                            graphicsLayer {
                                compositingStrategy =
                                    CompositingStrategy.Offscreen
                            }
                                .drawWithContent {
                                    drawContent()
                                    drawCircle(
                                        color = Color(0xFFFFFFFF),
                                        center = Offset(
                                            x = size.width -
                                                TreeSectionStyling.ERROR_CUTOUT_OFFSET,
                                            y = size.height -
                                                TreeSectionStyling.ERROR_CUTOUT_OFFSET,
                                        ),
                                        radius = TreeSectionStyling.ERROR_CUTOUT_RADIUS,
                                        blendMode = BlendMode.DstOut,
                                    )
                                }
                        }
                        .thenIf(isBackground) {
                            alpha(TreeSectionStyling.BACKGROUND_PROCESS_ALPHA)
                        }
                        .testTag(
                            when {
                                isBackground && isError ->
                                    TreeSectionTestTags.PROCESS_ITEM_BACK_ERROR_ICON
                                isBackground ->
                                    TreeSectionTestTags.PROCESS_ITEM_BACK_ICON
                                isError ->
                                    TreeSectionTestTags.PROCESS_ITEM_ERROR_ICON
                                else ->
                                    TreeSectionTestTags.PROCESS_ITEM_ICON
                            },
                        ),
                )

                if (isError) {
                    Icon(
                        key = Icons.Keys.Error,
                        contentDescription = null,
                        modifier = Modifier
                            .offset(
                                x = TreeSectionStyling.ERROR_ICON_OFFSET,
                                y = TreeSectionStyling.ERROR_ICON_OFFSET,
                            )
                            .size(TreeSectionStyling.ERROR_ICON_SIZE)
                            .align(Alignment.BottomEnd)
                            .thenIf(isBackground) {
                                alpha(TreeSectionStyling.BACKGROUND_PROCESS_ALPHA)
                            },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProcessWeightIcon(weight: ProcessWeightDto?) {
    when (weight) {
        ProcessWeightDto.MEDIUM, ProcessWeightDto.HEAVY -> {
            val viewModel = when (weight) {
                ProcessWeightDto.MEDIUM -> ProcessWeightViewModel(
                    iconKey = Icons.Keys.ProcessMedium,
                    contentDesc = message("process.output.icon.description.mediumProcess"),
                    tooltipMessage = message("process.output.tree.weight.medium.tooltip"),
                    testId = TreeSectionTestTags.PROCESS_ITEM_WEIGHT_MEDIUM_ICON,
                )
                ProcessWeightDto.HEAVY -> ProcessWeightViewModel(
                    iconKey = Icons.Keys.ProcessHeavy,
                    contentDesc = message("process.output.icon.description.heavyProcess"),
                    tooltipMessage = message("process.output.tree.weight.heavy.tooltip"),
                    testId = TreeSectionTestTags.PROCESS_ITEM_WEIGHT_HEAVY_ICON,
                )
            }

            Tooltip(
                tooltip = {
                    Row {
                        Text(viewModel.tooltipMessage)
                    }
                },
            ) {
                Icon(
                    key = viewModel.iconKey,
                    contentDescription = viewModel.contentDesc,
                    modifier = Modifier
                        .padding(TreeSectionStyling.TREE_ITEM_PROCESS_WEIGHT_PADDING)
                        .testTag(viewModel.testId),
                )
            }
        }
        null, ProcessWeightDto.LIGHT -> {}
    }
}

internal object TreeSectionTestTags {
    const val EMPTY_TREE_TEXT = "ProcessOutput.Tree.EmptyTreeText"
    const val PROCESS_ITEM_BACK_ERROR_ICON = "ProcessOutput.Tree.ProcessItemBackErrorIcon"
    const val PROCESS_ITEM_BACK_ICON = "ProcessOutput.Tree.ProcessItemBackIcon"
    const val PROCESS_ITEM_ERROR_ICON = "ProcessOutput.Tree.ProcessItemErrorIcon"
    const val PROCESS_ITEM_ICON = "ProcessOutput.Tree.ProcessItemIcon"
    const val PROCESS_ITEM_WEIGHT_MEDIUM_ICON = "ProcessOutput.Tree.Weight.MediumIcon"
    const val PROCESS_ITEM_WEIGHT_HEAVY_ICON = "ProcessOutput.Tree.Weight.HeavyIcon"
    const val FOLDER_ITEM_ICON = "ProcessOutput.Tree.FolderItemIcon"
    const val EXPAND_ALL_BUTTON = "ProcessOutput.Tree.ExpandAllButton"
    const val COLLAPSE_ALL_BUTTON = "ProcessOutput.Tree.CollapseAllButton"
    const val FILTERS_BUTTON = "ProcessOutput.Tree.FiltersButton"
    const val FILTERS_MENU = "ProcessOutput.Tree.FiltersMenu"
    const val FILTERS_BACKGROUND = "ProcessOutput.Tree.FiltersBackground"
    const val FILTERS_TIME = "ProcessOutput.Tree.FiltersTime"
    const val FILTERS_PROCESS_WEIGHTS = "ProcessOutput.Tree.FiltersProcessWeights"
}
