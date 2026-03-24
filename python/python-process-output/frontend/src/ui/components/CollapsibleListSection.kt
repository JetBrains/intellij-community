package com.intellij.python.processOutput.frontend.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.intellij.python.processOutput.frontend.ProcessOutputBundle.message
import com.intellij.python.processOutput.frontend.ui.Icons
import com.intellij.python.processOutput.frontend.ui.expandable
import com.intellij.python.processOutput.frontend.ui.isExpanded
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.scrollbarContentSafePadding

@Composable
internal fun CollapsibleListSection(
    text: String,
    modifier: Modifier = Modifier,
    isExpanded: Boolean,
    onToggle: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier =
            modifier.fillMaxWidth()
                .height(28.dp)
                .padding(start = 8.dp, end = scrollbarContentSafePadding())
                .expandable(interactionSource, onToggle)
                .semantics { this.isExpanded = isExpanded },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val (key, contentDescription, testTag) =
            if (isExpanded) {
                Triple(
                    Icons.Keys.ChevronDown,
                    message("process.output.icon.description.chevronDown"),
                    CollapsibleListSectionTestTags.CHEVRON_DOWN,
                )
            } else {
                Triple(
                    Icons.Keys.ChevronRight,
                    message("process.output.icon.description.chevronRight"),
                    CollapsibleListSectionTestTags.CHEVRON_RIGHT,
                )
            }

        Icon(
            key = key,
            contentDescription = contentDescription,
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .size(16.dp)
                .testTag(testTag),
        )

        InterText(
            text = text,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Normal,
        )
    }
}

internal object CollapsibleListSectionTestTags {
    const val CHEVRON_RIGHT = "ProcessOutput.CollapsibleListSection.ChevronRight"
    const val CHEVRON_DOWN = "ProcessOutput.CollapsibleListSection.ChevronDown"
}
