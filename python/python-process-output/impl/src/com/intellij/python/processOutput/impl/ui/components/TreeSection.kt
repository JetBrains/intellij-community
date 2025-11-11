package com.intellij.python.processOutput.impl.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.intellij.python.processOutput.impl.ProcessOutputBundle.message
import com.intellij.python.processOutput.impl.ProcessOutputController
import com.intellij.python.processOutput.impl.TreeFilter
import com.intellij.python.processOutput.impl.TreeNode
import com.intellij.python.processOutput.impl.formatTime
import com.intellij.python.processOutput.impl.ui.Colors
import com.intellij.python.processOutput.impl.ui.Icons
import com.intellij.python.processOutput.impl.ui.processIsBackground
import com.intellij.python.processOutput.impl.ui.processIsError
import com.jetbrains.python.NON_INTERACTIVE_ROOT_TRACE_CONTEXT
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.LazyTree
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.component.styling.LazyTreeMetrics
import org.jetbrains.jewel.ui.component.styling.LazyTreeStyle
import org.jetbrains.jewel.ui.component.styling.SimpleListItemMetrics
import org.jetbrains.jewel.ui.theme.treeStyle

@OptIn(ExperimentalJewelApi::class)
@Composable
internal fun TreeSection(controller: ProcessOutputController) {
    val selectableLazyListState = remember { controller.processTreeUiState.selectableLazyListState }

    Column(modifier = Modifier.fillMaxSize()) {
        val tree by controller.processTreeUiState.tree.collectAsState()
        val filters = remember { controller.processTreeUiState.filters }
        val isTreeEmpty = remember(tree) { tree.isEmpty() }

        TreeToolbar(
            controller = controller,
            areExpansionActionsEnabled = !isTreeEmpty,
        )

        tree.takeIf { !it.isEmpty() }
            ?.also {
                VerticallyScrollableContainer(
                    scrollState = selectableLazyListState.lazyListState as ScrollableState,
                ) {
                    val treeState = remember { controller.processTreeUiState.treeState }
                    val style = JewelTheme.treeStyle

                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
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
                                        innerPadding = PaddingValues(horizontal = 4.dp),
                                        outerPadding = PaddingValues(1.dp),
                                        selectionBackgroundCornerSize =
                                            style.metrics.simpleListItemMetrics.selectionBackgroundCornerSize,
                                        iconTextGap = style.metrics.simpleListItemMetrics.iconTextGap,
                                    ),
                                ),
                                icons = style.icons,
                            ),
                            interactionSource = remember { MutableInteractionSource() },
                        ) {
                            TreeRow(it.data, filters.contains(TreeFilter.ShowTime))
                        }
                    }
                }
            }
            ?: EmptyContainerNotice(
                text = message("process.output.tree.blankMessage"),
                modifier = Modifier.testTag(TreeSectionTestTags.EMPTY_TREE_TEXT),
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
                    .padding(start = 1.dp, top = 2.dp, bottom = 2.dp)
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
            Spacer(modifier = Modifier.width(16.dp))
        }

        Spacer(modifier = Modifier.width(8.dp))

        FilterActionGroup(
            tooltipText = message("process.output.viewOptions.tooltip"),
            items = persistentListOf(
                FilterEntry(
                    item = TreeFilter.ShowTime,
                    testTag = TreeSectionTestTags.FILTERS_TIME,
                ),
                FilterEntry(
                    item = TreeFilter.ShowBackgroundProcesses,
                    testTag = TreeSectionTestTags.FILTERS_BACKGROUND,
                ),
            ),
            isSelected = { controller.processTreeUiState.filters.contains(it) },
            onItemClick = { controller.toggleTreeFilter(it) },
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TreeRow(
    node: TreeNode,
    isTimeDisplayed: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp),
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
                        modifier = Modifier.padding(start = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                val exitInfo by node.process.exitInfo.collectAsState()
                val isError = remember(exitInfo) {
                    exitInfo?.takeIf { it.exitValue != 0 } != null
                }
                val isBackground = node.process.traceContext == NON_INTERACTIVE_ROOT_TRACE_CONTEXT

                Tooltip(
                    tooltip = {
                        Row {
                            Text(node.process.shortenedCommandString)
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Row(
                        modifier = Modifier.padding(start = 21.dp)
                            .semantics {
                                processIsError = isError
                                processIsBackground = isBackground
                            },
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        when {
                            isBackground && isError ->
                                Icon(
                                    key = Icons.Keys.ProcessBackError,
                                    contentDescription =
                                        message("process.output.icon.description.processBackError"),
                                    modifier = Modifier.testTag(
                                        TreeSectionTestTags.PROCESS_ITEM_BACK_ERROR_ICON,
                                    ),
                                )
                            isBackground ->
                                Icon(
                                    key = Icons.Keys.ProcessBack,
                                    contentDescription =
                                        message("process.output.icon.description.processBack"),
                                    modifier = Modifier.testTag(
                                        TreeSectionTestTags.PROCESS_ITEM_BACK_ICON,
                                    ),
                                )
                            isError ->
                                Icon(
                                    key = Icons.Keys.ProcessError,
                                    contentDescription =
                                        message("process.output.icon.description.processError"),
                                    modifier = Modifier.testTag(
                                        TreeSectionTestTags.PROCESS_ITEM_ERROR_ICON,
                                    ),
                                )
                            else ->
                                Icon(
                                    key = Icons.Keys.Process,
                                    contentDescription =
                                        message("process.output.icon.description.process"),
                                    modifier = Modifier.testTag(
                                        TreeSectionTestTags.PROCESS_ITEM_ICON,
                                    ),
                                )
                        }

                        InterText(
                            text = node.process.shortenedCommandString,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        val instant = when (node) {
            is TreeNode.Context -> Instant.fromEpochMilliseconds(node.traceContext.timestamp)
            is TreeNode.Process -> node.process.startedAt
        }

        if (isTimeDisplayed) {
            InterText(
                text = instant.formatTime(),
                modifier = Modifier.padding(end = 6.dp),
                color = Colors.Tree.Info,
            )
        }
    }
}

internal object TreeSectionTestTags {
    const val EMPTY_TREE_TEXT = "ProcessOutput.Tree.EmptyTreeText"
    const val PROCESS_ITEM_BACK_ERROR_ICON = "ProcessOutput.Tree.ProcessItemBackErrorIcon"
    const val PROCESS_ITEM_BACK_ICON = "ProcessOutput.Tree.ProcessItemBackIcon"
    const val PROCESS_ITEM_ERROR_ICON = "ProcessOutput.Tree.ProcessItemErrorIcon"
    const val PROCESS_ITEM_ICON = "ProcessOutput.Tree.ProcessItemIcon"
    const val FOLDER_ITEM_ICON = "ProcessOutput.Tree.FolderItemIcon"
    const val EXPAND_ALL_BUTTON = "ProcessOutput.Tree.ExpandAllButton"
    const val COLLAPSE_ALL_BUTTON = "ProcessOutput.Tree.CollapseAllButton"
    const val FILTERS_BUTTON = "ProcessOutput.Tree.FiltersButton"
    const val FILTERS_MENU = "ProcessOutput.Tree.FiltersMenu"
    const val FILTERS_BACKGROUND = "ProcessOutput.Tree.FiltersBackground"
    const val FILTERS_TIME = "ProcessOutput.Tree.FiltersTime"
}
