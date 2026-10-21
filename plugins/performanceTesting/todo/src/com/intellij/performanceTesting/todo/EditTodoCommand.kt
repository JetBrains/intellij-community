// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.performanceTesting.todo

import com.intellij.ide.todo.nodes.TodoRemoteItemNode
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.util.await
import com.intellij.openapi.wm.IdeFocusManager
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter
import com.jetbrains.performancePlugin.commands.ReplaceTextCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jetbrains.annotations.NonNls

/**
 * Measures an edit until the Project tab shows the expected counts and comment.
 * Expands the file before timing the edit. Counts describe the result after the edit. Lines are 1-based.
 *
 * Syntax: `%todoEdit <add|change|remove> line <n> files <count> items <count> fileItems <count> command %replaceText ...`
 */
internal class EditTodoCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: @NonNls String = "${CMD_PREFIX}todoEdit"
    private const val MARKER = "perf-added"
    private const val ADDED_TEXT = "TODO $MARKER"
    private const val CHANGED_TEXT = "FIXME $MARKER"
  }

  private enum class Edit(val delta: Int, val span: String, val beforeText: String?, val afterText: String?) {
    ADD(1, TodoSpanNames.ADD, null, ADDED_TEXT),
    CHANGE(0, TodoSpanNames.CHANGE, ADDED_TEXT, CHANGED_TEXT),
    REMOVE(-1, TodoSpanNames.REMOVE, CHANGED_TEXT, null),
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val (options, command) = extractCommandArgument(PREFIX).split(" command ", limit = 2)
    require(command.startsWith("${ReplaceTextCommand.PREFIX} ")) { "Expected a replaceText command" }
    val arguments = options.split(' ').filter { it.isNotBlank() }
    val edit = Edit.valueOf(arguments.first().uppercase())
    val lineIndex = arguments.argument("line") - 1
    val files = arguments.argument("files")
    val items = arguments.argument("items")
    val fileItems = arguments.argument("fileItems")
    require(lineIndex >= 0 && fileItems > 0) { "The line and fileItems arguments must be positive" }
    val beforeItems = items - edit.delta
    val beforeFileItems = fileItems - edit.delta

    withContext(Dispatchers.EDT) {
      val project = context.project
      val tree = showTodoTab(project, TodoTab.PROJECT).todoTree
      val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: error("No editor is selected")
      val file = editor.virtualFile ?: error("The selected editor has no file")
      val fileNodePath = tree.revealFile(file)

      awaitTodoTree(tree, "$beforeFileItems items in ${file.name}", action = { tree.expandPath(fileNodePath) }) {
        val nodes = tree.itemNodes(fileNodePath)
        tree.hasTodoSummary(files, beforeItems) && nodes.size == beforeFileItems && hasExpectedComment(nodes, edit.beforeText, lineIndex)
      }
      withTimeout(TODO_READY_TIMEOUT) {
        IdeFocusManager.getInstance(project).requestFocus(editor.contentComponent, true).await()
      }
      measureTodoTree(tree, "${edit.name}: $items items, $fileItems in ${file.name}", edit.span, files, items,
                      action = { ReplaceTextCommand(command, 0).execute(context).await() }) {
        val nodes = tree.itemNodes(fileNodePath)
        tree.hasTodoSummary(files, items) && nodes.size == fileItems && hasExpectedComment(nodes, edit.afterText, lineIndex)
      }
    }
  }

  private fun hasExpectedComment(nodes: List<TodoRemoteItemNode>, text: String?, lineIndex: Int): Boolean =
    if (text == null) {
      nodes.none { it.presentation.presentableText?.contains(MARKER) == true }
    }
    else {
      nodes.any { it.value?.line == lineIndex && it.presentation.presentableText?.contains(text) == true }
    }

  override fun getName(): String = "todoEdit"
}
