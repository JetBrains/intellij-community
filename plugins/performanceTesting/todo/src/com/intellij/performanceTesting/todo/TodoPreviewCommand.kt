// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.performanceTesting.todo

import com.intellij.ide.IdeBundle
import com.intellij.ide.todo.TodoPanel
import com.intellij.ide.todo.nodes.TodoRemoteItemNode
import com.intellij.openapi.application.EDT
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.tree.TreeUtil
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.NonNls
import javax.swing.JTree
import javax.swing.tree.TreePath

/**
 * Measures the preview on the Project tab. Waits until the preview shows the expected TODO item.
 *
 * - `select`: expands the path before timing starts, then selects the item.
 * - `next`, `previous`: use the panel navigation, including movement between files.
 * - `off`: turns the preview off, then selects the optional item. Records no measurement.
 * - `on`: turns the preview on and measures until it shows the selected item.
 *
 * The preview starts enabled. The caller must alternate `off` and `on`, and select another item before `on`.
 *
 * Syntax: `%todoPreview <select|next|previous> <path> line <n> span <name>`, `%todoPreview off [<path> line <n>]`,
 * or `%todoPreview on span <name>`. Lines are 1-based.
 */
internal class TodoPreviewCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: @NonNls String = "${CMD_PREFIX}todoPreview"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val arguments = extractCommandArgument(PREFIX).split(' ').filter { it.isNotBlank() }
    val kind = arguments.firstOrNull() ?: error("Missing action")

    withContext(Dispatchers.EDT) {
      val project = context.project
      val panel = showTodoTab(project, TodoTab.PROJECT)
      val tree = panel.todoTree
      when (kind) {
        "off" -> {
          panel.togglePreview()
          if ("line" in arguments) {
            val file = openedTodoFile(project, arguments[1])
            TreeUtil.selectPath(tree, itemPath(tree, file, arguments.argument("line") - 1))
          }
          return@withContext
        }
        "on" -> {
          val item = tree.selectedItem() ?: error("No TODO item is selected")
          measureTodoTree(tree, "the preview of ${item.file.name}:${item.line + 1}", arguments.textArgument("span"),
                          action = { panel.togglePreview() }) {
            panel.previewShows(item)
          }
          return@withContext
        }
      }

      val file = openedTodoFile(project, arguments.getOrNull(1) ?: error("Missing file path"))
      val line = arguments.argument("line") - 1
      val path = if (kind == "select") itemPath(tree, file, line) else null
      measureTodoTree(tree, "$kind to ${file.name}:${line + 1}", arguments.textArgument("span"), action = {
        when (kind) {
          "select" -> TreeUtil.selectPath(tree, checkNotNull(path))
          "next" -> panel.goNextOccurence()
          "previous" -> panel.goPreviousOccurence()
          else -> error("The first argument must be 'select', 'next', 'previous', 'on', or 'off'")
        }
      }) {
        val item = tree.selectedItem()
        item != null && item.file == file && item.line == line && panel.previewShows(item)
      }
    }
  }

  private fun TodoPanel.togglePreview() {
    val text = IdeBundle.message("todo.panel.preview.source.action.text")
    val action = toolbarActions().firstOrNull { it.templatePresentation.text == text } ?: error("The TODO panel has no preview action")
    perform(action)
  }

  /** Expands the groups and the file that hold the item at [line] (0-based) and returns the path of the item row. */
  private suspend fun itemPath(tree: JTree, file: VirtualFile, line: Int): TreePath {
    val filePath = tree.revealFile(file)
    val isItem = { path: TreePath -> TreeUtil.getUserObject(TodoRemoteItemNode::class.java, path.lastPathComponent)?.value?.line == line }
    awaitTodoTree(tree, "the item at ${file.name}:${line + 1}", action = { tree.expandPath(filePath) }) {
      tree.childPaths(filePath).any(isItem)
    }
    return tree.childPaths(filePath).first(isItem)
  }

  private fun JTree.selectedItem(): TodoRemoteItemNode.Value? =
    TreeUtil.getUserObject(TodoRemoteItemNode::class.java, selectionPath?.lastPathComponent)?.value

  override fun getName(): String = "todoPreview"
}
