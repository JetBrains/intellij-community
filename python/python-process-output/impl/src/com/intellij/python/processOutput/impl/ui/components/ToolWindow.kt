package com.intellij.python.processOutput.impl.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.intellij.python.processOutput.impl.ProcessOutputController
import org.jetbrains.jewel.ui.component.HorizontalSplitLayout
import org.jetbrains.jewel.ui.component.rememberSplitLayoutState

@Composable
internal fun ToolWindow(controller: ProcessOutputController) {
    HorizontalSplitLayout(
        first = { TreeSection(controller) },
        second = { OutputSection(controller) },
        modifier = Modifier.fillMaxSize(),
        firstPaneMinWidth = 300.dp,
        secondPaneMinWidth = 300.dp,
        state = rememberSplitLayoutState(.15f),
    )
}
