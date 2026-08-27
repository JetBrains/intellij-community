package com.intellij.python.processOutput.frontend.ui

import com.intellij.openapi.util.NlsSafe
import com.intellij.python.processOutput.common.LoggedProcessDto
import com.intellij.python.processOutput.frontend.ProcessTreeNode
import com.intellij.python.processOutput.frontend.ui.components.processTreeNode
import java.util.Enumeration
import javax.swing.tree.TreeNode

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
