package com.intellij.python.processOutput.frontend.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.intellij.python.processOutput.frontend.ProcessOutputBundle.message
import com.intellij.python.processOutput.frontend.ui.Icons
import org.jetbrains.jewel.foundation.modifier.thenIf
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.disabledAppearance
import org.jetbrains.jewel.ui.icon.IconKey

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ActionIconButton(
    modifier: Modifier = Modifier,
    iconKey: IconKey,
    tooltipText: String,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
    iconModifier: Modifier = Modifier,
    isDropdown: Boolean = false,
) {
    Tooltip(
        tooltip = {
            Row {
                Text(tooltipText)
            }
        },
    ) {
        IconButton(
            modifier = modifier.size(26.dp)
                .testTag(ActionIconButtonTestTags.BUTTON),
            enabled = enabled,
            onClick = onClick,
        ) {
            Icon(
                modifier = iconModifier
                    .testTag(ActionIconButtonTestTags.ICON)
                    .thenIf(!enabled) {
                        disabledAppearance()
                    },
                key = iconKey,
                contentDescription = tooltipText,
            )

            if (isDropdown) {
                Icon(
                    modifier = iconModifier
                        .offset(x = 1.dp, y = 1.dp)
                        .testTag(ActionIconButtonTestTags.DROPDOWN_ICON)
                        .thenIf(!enabled) {
                            disabledAppearance()
                        },
                    key = Icons.Keys.Dropdown,
                    contentDescription = message("process.output.icon.description.dropdown"),
                )
            }
        }
    }
}

internal object ActionIconButtonTestTags {
    const val BUTTON = "ProcessOutput.ActionIconButton.Button"
    const val ICON = "ProcessOutput.ActionIconButton.Icon"
    const val DROPDOWN_ICON = "ProcessOutput.ActionIconButton.DropdownIcon"
}
