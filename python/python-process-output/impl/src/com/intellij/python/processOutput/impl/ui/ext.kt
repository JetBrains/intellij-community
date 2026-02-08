package com.intellij.python.processOutput.impl.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import java.awt.Cursor
import kotlinx.coroutines.flow.SharedFlow

internal inline fun <T> Modifier.thenIfNotNull(
    nullable: T?,
    action: Modifier.(T) -> Modifier,
): Modifier =
    nullable?.let { action(it) } ?: this

internal fun Modifier.expandable(
    interactionSource: MutableInteractionSource,
    onToggle: () -> Unit,
): Modifier =
    this.clickable(
        indication = null,
        interactionSource = interactionSource,
        onClick = { onToggle() },
    )
        .hoverable(interactionSource)
        .pointerHoverIcon(
            PointerIcon(
                Cursor.getPredefinedCursor(Cursor.HAND_CURSOR),
            ),
        )

internal fun <T> MutableSet<T>.toggle(value: T) {
    if (contains(value)) {
        remove(value)
    } else {
        add(value)
    }
}

@Composable
internal fun <T> SharedFlow<T>.collectReplayAsState(): State<List<T>> {
    val data = remember(this) { mutableStateOf(replayCache) }

    LaunchedEffect(this) {
        collect { _ ->
            data.value = replayCache
        }
    }

    return data
}
