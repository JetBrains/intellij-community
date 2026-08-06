package com.intellij.python.processOutput.frontend.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import com.intellij.openapi.util.NlsSafe
import com.intellij.python.processOutput.common.LoggedProcessDto
import com.intellij.python.processOutput.frontend.ProcessTreeNode
import com.intellij.python.processOutput.frontend.ui.components.processTreeNode
import java.awt.Cursor
import java.util.Enumeration
import javax.swing.tree.TreeNode

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
  }
  else {
    add(value)
  }
}

internal val LoggedProcessDto.commandString: String
  get() = commandFromSegments(listOf(exe.path) + args)

/**
 * Command string with the full path of the exe trimmed only to the latest segments.
 * E.g., `/usr/bin/uv` -> `uv`.
 */
internal val LoggedProcessDto.shortenedCommandString: @NlsSafe String
  get() = commandFromSegments(listOf(exe.parts.last()) + args)

private fun commandFromSegments(segments: List<String>) =
  segments.joinToString(" ")

internal fun <T : TreeNode> Enumeration<T>.iterate(action: (ProcessTreeNode) -> Unit) {
  for (child in this) {
    child.children().iterate(action)
    action(child.processTreeNode)
  }
}
