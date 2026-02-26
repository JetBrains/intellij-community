package com.intellij.python.processOutput.frontend.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.intellij.python.processOutput.frontend.ui.Colors
import org.jetbrains.jewel.ui.component.Text

@Composable
internal fun EmptyContainerNotice(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            modifier = modifier,
            color = Colors.Output.Info,
        )
    }
}
