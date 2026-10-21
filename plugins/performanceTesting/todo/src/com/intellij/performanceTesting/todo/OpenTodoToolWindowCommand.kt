// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.performanceTesting.todo

import com.intellij.openapi.application.EDT
import com.intellij.openapi.ui.playback.PlaybackContext
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.NonNls

/**
 * Measures the first opening of the Project tab in its default view, which groups the files by packages.
 * Panel creation and the initial scan are measured.
 * The timer ends when the summary shows the expected counts and the summary has the expected number of top-level groups.
 *
 * Syntax: `%todoOpen files <count> items <count> groups <count> [keepOpen]`
 */
internal class OpenTodoToolWindowCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: @NonNls String = "${CMD_PREFIX}todoOpen"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val arguments = extractCommandArgument(PREFIX).split(' ').filter { it.isNotBlank() }
    val files = arguments.argument("files")
    val items = arguments.argument("items")
    val groups = arguments.argument("groups")
    val keepOpen = "keepOpen" in arguments

    withContext(Dispatchers.EDT) {
      val toolWindow = todoToolWindow(context.project)
      check(!toolWindow.isVisible) { "The TODO tool window is already open" }
      val span = startTodoSpan(TodoSpanNames.OPEN, files, items)
      try {
        val tree = showTodoTab(context.project, TodoTab.PROJECT).todoTree
        awaitTodoTree(tree, "the summary of $items items in $files files") {
          tree.hasTodoSummary(files, items)
        }
        awaitTodoTree(tree, "$groups top-level groups", action = { tree.expandRow(0) }) {
          tree.hasTodoResults(files, items, groups)
        }
        span.end()
      }
      finally {
        if (!keepOpen) toolWindow.hide()
      }
    }
  }

  override fun getName(): String = "todoOpen"
}
