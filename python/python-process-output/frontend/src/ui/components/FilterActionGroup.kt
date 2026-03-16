package com.intellij.python.processOutput.frontend.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateSet
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.intellij.python.processOutput.frontend.ProcessOutputBundle.message
import com.intellij.python.processOutput.frontend.ui.Icons
import com.intellij.python.processOutput.frontend.ui.thenIfNotNull
import com.intellij.python.processOutput.frontend.ui.toggle
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.foundation.modifier.border
import org.jetbrains.jewel.foundation.modifier.onHover
import org.jetbrains.jewel.foundation.modifier.thenIf
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.items
import org.jetbrains.jewel.ui.theme.iconButtonStyle

@ApiStatus.Internal
class FilterActionGroupState<TFilter, TItem>(treeFilter: TFilter)
    where TItem : Enum<TItem>,
          TItem : FilterItem,
          TFilter : Filter<TItem> {
    internal val active: SnapshotStateSet<TItem> = mutableStateSetOf()

    init {
        active.addAll(treeFilter.defaultActive)
    }
}

@ApiStatus.Internal
interface Filter<TItem>
    where TItem : Enum<TItem>,
          TItem : FilterItem {
    val defaultActive: Set<TItem>
}

@ApiStatus.Internal
interface FilterItem {
    val title: String
    val testTag: String
}

@Composable
internal inline fun <TFilter, reified TItem> FilterActionGroup(
    tooltipText: String,
    state: FilterActionGroupState<TFilter, TItem>,
    crossinline onFilterItemToggled: (filterItem: TItem, enabled: Boolean) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    menuModifier: Modifier = Modifier,
) where TItem : Enum<TItem>,
        TItem : FilterItem,
        TFilter : Filter<TItem> {
    var isMenuOpen by remember { mutableStateOf(false) }

    Box {
        ActionIconButton(
            modifier = modifier
                .thenIf(isMenuOpen) {
                    background(
                        color = JewelTheme.iconButtonStyle.colors.backgroundPressed,
                        shape = RoundedCornerShape(JewelTheme.iconButtonStyle.metrics.cornerSize),
                    )
                        .border(
                            alignment = Stroke.Alignment.Inside,
                            width = JewelTheme.iconButtonStyle.metrics.borderWidth,
                            color = JewelTheme.iconButtonStyle.colors.backgroundPressed,
                            shape = RoundedCornerShape(JewelTheme.iconButtonStyle.metrics.cornerSize),
                        )
                },
            iconKey = Icons.Keys.Filter,
            tooltipText = tooltipText,
            enabled = enabled,
            onClick = { isMenuOpen = !isMenuOpen },
            isDropdown = true,
        )

        if (isMenuOpen) {
            var isHovered by remember { mutableStateOf(false) }

            PopupMenu(
                onDismissRequest = {
                    if (it == InputMode.Touch && !isHovered) {
                        isMenuOpen = false
                        true
                    } else {
                        false
                    }
                },
                horizontalAlignment = Alignment.Start,
                modifier = menuModifier.onHover { isHovered = it },
            ) {
                items(
                    items = enumValues<TItem>().toList(),
                    isSelected = { state.active.contains(it) },
                    onItemClick = {
                        state.active.toggle(it)
                        onFilterItemToggled(it, state.active.contains(it))
                    },
                ) {
                    Row(
                        modifier = Modifier.thenIfNotNull(it.testTag) { tag ->
                            testTag(tag)
                        },
                        horizontalArrangement = spacedBy(8.dp, Alignment.Start),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (state.active.contains(it)) {
                            Icon(
                                Icons.Keys.Checked,
                                message("process.output.icon.description.checked"),
                                modifier = Modifier.testTag(FilterActionGroupTestTags.CHECKED_ICON),
                            )
                        } else {
                            Spacer(Modifier.width(16.dp))
                        }

                        Text(it.title)
                    }
                }
            }
        }
    }
}

internal object FilterActionGroupTestTags {
    const val CHECKED_ICON = "ProcessOutput.FilterActionGroup.CheckedIcon"
}
