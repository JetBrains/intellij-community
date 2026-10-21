// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.performanceTesting.todo

import com.intellij.ide.IdeBundle
import com.intellij.ide.todo.SetTodoFilterAction
import com.intellij.ide.todo.TodoConfiguration
import com.intellij.ide.todo.TodoFilter
import com.intellij.openapi.application.EDT
import com.intellij.openapi.ui.playback.PlaybackContext
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.NonNls

/**
 * Adds a TODO filter, or applies a filter to the Project tab through the filter action of the panel toolbar.
 * Add a filter before the tool window opens, because the change makes every open panel scan again.
 * The apply form waits for the displayed summary and result groups. The name `all` stands for "Show All".
 *
 * Syntax: `%todoFilter add <name> <regex>` or `%todoFilter apply <name|all> files <count> items <count> groups <count>`
 */
internal class TodoFilterCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: @NonNls String = "${CMD_PREFIX}todoFilter"
    private const val ALL = "all"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val arguments = extractCommandArgument(PREFIX).split(' ').filter { it.isNotBlank() }
    val name = arguments.getOrNull(1) ?: error("Missing filter name")

    withContext(Dispatchers.EDT) {
      when (arguments.firstOrNull()) {
        "add" -> {
          val configuration = TodoConfiguration.getInstance()
          val patternString = arguments.getOrNull(2) ?: error("Missing pattern")
          val pattern = configuration.todoPatterns.firstOrNull { it.patternString == patternString }
                        ?: error("The pattern '$patternString' does not exist")
          val filter = TodoFilter().apply {
            setName(name)
            addTodoPattern(pattern)
          }
          configuration.todoFilters += filter
        }
        "apply" -> {
          val files = arguments.argument("files")
          val items = arguments.argument("items")
          val groups = arguments.argument("groups")
          val panel = showTodoTab(context.project, TodoTab.PROJECT)
          val text = if (name == ALL) IdeBundle.message("action.todo.show.all") else name
          val filterAction = panel.toolbarActions().filterIsInstance<SetTodoFilterAction>().singleOrNull()
                             ?: error("The TODO panel has no filter action")
          val action = filterAction.getChildren(panel.actionEvent(filterAction)).firstOrNull { it.templatePresentation.text == text }
                       ?: error("The filter action has no item '$text'")
          val tree = panel.todoTree
          val spanName = if (name == ALL) TodoSpanNames.FILTER_CLEAR else TodoSpanNames.FILTER_APPLY
          measureTodoTree(tree, "filter '$name': $items items in $files files", spanName, files, items, action = { panel.perform(action) }) {
            tree.hasTodoResults(files, items, groups)
          }
        }
        else -> error("The first argument must be 'add' or 'apply'")
      }
    }
  }

  override fun getName(): String = "todoFilter"
}
