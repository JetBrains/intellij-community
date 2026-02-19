package com.intellij.python.processOutput.impl.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.intellij.python.processOutput.impl.ProcessOutputController
import org.jetbrains.jewel.ui.component.HorizontalSplitLayout
import org.jetbrains.jewel.ui.component.rememberSplitLayoutState

internal object ToolWindowStyling {
    val SECTION_PANE_MIN_WIDTH = 300.dp
    const val SECTION_SPLIT_FRACTION = .25f
}

@Composable
internal fun ToolWindow(controller: ProcessOutputController) {
    HorizontalSplitLayout(
        first = { TreeSection(controller) },
        second = { OutputSection(controller) },
        modifier = Modifier.fillMaxSize(),
        firstPaneMinWidth = ToolWindowStyling.SECTION_PANE_MIN_WIDTH,
        secondPaneMinWidth = ToolWindowStyling.SECTION_PANE_MIN_WIDTH,
        state = rememberSplitLayoutState(ToolWindowStyling.SECTION_SPLIT_FRACTION),
    )
}
