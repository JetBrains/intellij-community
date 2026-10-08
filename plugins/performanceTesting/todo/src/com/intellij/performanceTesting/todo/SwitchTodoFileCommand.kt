// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.performanceTesting.todo

import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.playback.PlaybackContext
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.NonNls

/**
 * Measures a switch between open editors while the Current File tab is shown.
 * Before the timer starts, the command shows the tab and waits until it shows the file of the selected editor.
 * The timer ends when the tab shows the new file with the expected number of items.
 *
 * Syntax: `%todoSwitchFile <path> items <count>`
 */
internal class SwitchTodoFileCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: @NonNls String = "${CMD_PREFIX}todoSwitchFile"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val arguments = extractCommandArgument(PREFIX).split(' ').filter { it.isNotBlank() }
    val path = arguments.firstOrNull() ?: error("Missing file path")
    val items = arguments.argument("items")

    withContext(Dispatchers.EDT) {
      val project = context.project
      val file = openedTodoFile(project, path)
      val editors = FileEditorManager.getInstance(project)
      val current = editors.selectedTextEditor?.virtualFile ?: error("No editor is selected")
      check(current != file) { "The editor of '$path' is already selected" }
      val tree = showTodoTab(project, TodoTab.CURRENT_FILE).todoTree
      awaitTodoTree(tree, "the row of ${current.name}") { tree.fileNode(current) != null }

      measureTodoTree(tree, "$items items in ${file.name}", TodoSpanNames.currentFileSwitch(items), items = items,
                      action = { editors.openFile(file, true) }) {
        val node = tree.fileNode(file)
        node?.presentation?.locationString == IdeBundle.message("node.todo.items", items)
      }
    }
  }

  override fun getName(): String = "todoSwitchFile"
}
