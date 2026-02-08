package com.intellij.python.processOutput.impl.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.intellij.python.processOutput.impl.ProcessOutputBundle.message
import com.intellij.python.processOutput.impl.ui.Icons
import com.intellij.python.processOutput.impl.ui.thenIfNotNull
import kotlinx.collections.immutable.ImmutableList
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

@Composable
internal fun <T : FilterItem> FilterActionGroup(
    tooltipText: String,
    items: ImmutableList<FilterEntry<T>>,
    isSelected: (T) -> Boolean,
    onItemClick: (T) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    menuModifier: Modifier = Modifier,
) {
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
                    items = items,
                    isSelected = { isSelected(it.item) },
                    onItemClick = { onItemClick(it.item) },
                ) {
                    Row(
                        modifier = Modifier.thenIfNotNull(it.testTag) { tag ->
                            testTag(tag)
                        },
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isSelected(it.item)) {
                            Icon(
                                Icons.Keys.Checked,
                                message("process.output.icon.description.checked"),
                                modifier = Modifier.testTag(FilterActionGroupTestTags.CHECKED_ICON),
                            )
                        } else {
                            Spacer(Modifier.width(16.dp))
                        }

                        Text(it.item.title)
                    }
                }
            }
        }
    }
}

@ApiStatus.Internal
interface FilterItem {
    val title: String
}

internal data class FilterEntry<T : FilterItem>(
    val item: T,
    val testTag: String? = null,
)

internal object FilterActionGroupTestTags {
    const val CHECKED_ICON = "ProcessOutput.FilterActionGroup.CheckedIcon"
}
