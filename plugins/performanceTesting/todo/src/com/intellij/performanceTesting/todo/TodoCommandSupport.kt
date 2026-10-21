// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.performanceTesting.todo

import com.intellij.ide.DataManager
import com.intellij.ide.IdeBundle
import com.intellij.ide.todo.TodoPanel
import com.intellij.ide.todo.nodes.BaseToDoNode
import com.intellij.ide.todo.nodes.SummaryNode
import com.intellij.ide.todo.nodes.TodoRemoteFileNode
import com.intellij.ide.todo.nodes.TodoRemoteItemNode
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import com.jetbrains.performancePlugin.PerformanceTestSpan
import io.opentelemetry.api.trace.Span
import javax.swing.JTree
import javax.swing.tree.TreePath
import kotlin.time.Duration.Companion.minutes

internal val TODO_READY_TIMEOUT = 2.minutes

/** The tabs of the TODO tool window, in the order that `TodoView.initToolWindow` creates them. */
internal enum class TodoTab { PROJECT, CURRENT_FILE, SCOPE }

internal fun todoToolWindow(project: Project): ToolWindow {
  check(Registry.`is`("todo.toolwindow.split")) { "The split TODO implementation must be enabled" }
  return ToolWindowManager.getInstance(project).getToolWindow("TODO") ?: error("The TODO tool window is not registered")
}

/** Shows the TODO tool window, selects [tab], and returns its panel. Nothing changes when the tab is already shown. */
internal fun showTodoTab(project: Project, tab: TodoTab): TodoPanel {
  val toolWindow = todoToolWindow(project)
  toolWindow.show()
  val content = toolWindow.contentManager.getContent(tab.ordinal) ?: error("The TODO tool window has no $tab tab")
  toolWindow.contentManager.setSelectedContent(content)
  return content.component as? TodoPanel ?: error("The content '${content.displayName}' is not a TODO panel")
}

internal val TodoPanel.todoTree: JTree
  get() = UIUtil.findComponentOfType(this, JTree::class.java) ?: error("The TODO panel has no tree")

/** Resolves a file prepared with `%openFile`, including a remote file in split mode. */
internal fun openedTodoFile(project: Project, relativePath: String): VirtualFile =
  FileEditorManager.getInstance(project).openFiles.singleOrNull { it.path.endsWith("/$relativePath") }
  ?: error("Open '$relativePath' before running the TODO command")

/** The actions of the panel toolbar. The filter actions are the children of `SetTodoFilterAction`. */
internal fun TodoPanel.toolbarActions(): List<AnAction> {
  val group = (toolbar as? ActionToolbar)?.actionGroup as? DefaultActionGroup ?: error("The TODO panel has no toolbar")
  return group.childActionsOrStubs.toList()
}

/** Performs [action] as a click in the panel toolbar does. */
internal fun TodoPanel.perform(action: AnAction) {
  ActionUtil.performAction(action, actionEvent(action))
}

internal fun TodoPanel.actionEvent(action: AnAction): AnActionEvent =
  AnActionEvent.createEvent(action, DataManager.getInstance().getDataContext(this), null,
                           ActionPlaces.TODO_VIEW_TOOLBAR, ActionUiKind.NONE, null)

/** Returns true when the preview shows the file of [item] with the caret in the item. The preview is the only editor of the panel. */
internal fun TodoPanel.previewShows(item: TodoRemoteItemNode.Value): Boolean {
  val editor: Editor = UIUtil.findComponentOfType(this, EditorComponentImpl::class.java)?.editor ?: return false
  return editor.component.isShowing && FileDocumentManager.getInstance().getFile(editor.document) == item.file &&
         editor.caretModel.logicalPosition.line == item.line
}

internal fun JTree.todoSummaryText(): String? =
  TreeUtil.getUserObject(SummaryNode::class.java, getPathForRow(0)?.lastPathComponent)?.presentation?.presentableText

internal fun JTree.hasTodoSummary(files: Int, items: Int): Boolean =
  todoSummaryText() == IdeBundle.message("node.todo.summary", items, files)

/** Checks the displayed summary and the result rows directly under it. */
internal fun JTree.hasTodoResults(files: Int, items: Int, resultRows: Int): Boolean {
  if (!hasTodoSummary(files, items)) return false
  val summary = getPathForRow(0) ?: return false
  val children = childPaths(summary)
  return children.size == resultRows && children.all {
    TreeUtil.getUserObject(BaseToDoNode::class.java, it.lastPathComponent)?.presentation?.presentableText != null
  }
}

/** The visible rows directly under [parent]. The list is empty when [parent] is collapsed. */
internal fun JTree.childPaths(parent: TreePath): List<TreePath> {
  val parentRow = getRowForPath(parent)
  if (parentRow < 0 || !isExpanded(parent)) return emptyList()
  return (parentRow + 1 until rowCount).asSequence()
    .mapNotNull { getPathForRow(it) }
    .takeWhile { parent.isDescendant(it) }
    .filter { it.parentPath == parent }
    .toList()
}

/** The visible row of [file], or null. */
internal fun JTree.fileNode(file: VirtualFile): TodoRemoteFileNode? = (0 until rowCount).firstNotNullOfOrNull { row ->
  TreeUtil.getUserObject(TodoRemoteFileNode::class.java, getPathForRow(row)?.lastPathComponent)?.takeIf { it.value?.file == file }
}

internal fun JTree.itemNodes(filePath: TreePath): List<TodoRemoteItemNode> =
  childPaths(filePath).mapNotNull { TreeUtil.getUserObject(TodoRemoteItemNode::class.java, it.lastPathComponent) }

/** Expands the summary and the groups that hold [file], one level at a time, and returns the path of the file row. */
internal suspend fun JTree.revealFile(file: VirtualFile): TreePath {
  val target = TodoRemoteFileNode.Value(file)
  var path = getPathForRow(0) ?: error("The TODO tree is empty")
  while (TreeUtil.getUserObject(TodoRemoteFileNode::class.java, path.lastPathComponent) == null) {
    val parent = path
    var next: TreePath? = null
    awaitTodoTree(this, "the row that holds ${file.name}", action = { expandPath(parent) }) {
      next = childPaths(parent).firstOrNull { child ->
        val node = TreeUtil.getUserObject(BaseToDoNode::class.java, child.lastPathComponent)
        if (node is TodoRemoteFileNode) node.value?.file == file else node?.contains(target) == true
      }
      next != null
    }
    path = next!!
  }
  return path
}

/** Starts a measurement. The caller ends the span only on success, so a failed run records no span. */
internal fun startTodoSpan(name: String, files: Int? = null, items: Int? = null): Span =
  PerformanceTestSpan.TRACER.spanBuilder(name)
    .apply { if (files != null) setAttribute("files", files.toLong()) }
    .apply { if (items != null) setAttribute("items", items.toLong()) }
    .startSpan()

internal fun List<String>.argument(name: String): Int {
  val value = textArgument(name).toIntOrNull()
  require(value != null && value >= 0) { "Invalid argument '$name'" }
  return value
}

/** The word after [name]. `SPACE_SYMBOL` stands for a space, as in `%openFile`. */
internal fun List<String>.textArgument(name: String): String {
  val index = indexOf(name)
  val value = if (index < 0) null else getOrNull(index + 1)
  require(value != null) { "Missing argument '$name'" }
  return value.replace("SPACE_SYMBOL", " ")
}
