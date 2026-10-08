// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.performanceTesting.todo

import com.intellij.ide.todo.TodoPanel
import com.intellij.ide.util.scopeChooser.ScopeDescriptor
import com.intellij.openapi.application.EDT
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.util.ui.UIUtil
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.annotations.NonNls
import javax.swing.JComboBox
import kotlin.time.Duration.Companion.milliseconds

/**
 * Selects a scope on the Scope Based tab and waits for the displayed summary and file rows.
 * With a span name, it measures the change. Without one, it prepares the initial scope.
 * Before the timer starts, the command shows the tab and waits until the scope chooser lists the scope.
 * The chooser loads its scopes asynchronously and fires no tree event, so this wait checks it every 50 ms.
 * Each scope change includes the fixed 300 ms delay of `TodoPanel.rebuildWithAlarm`.
 *
 * Syntax: `%todoSelectScope <name> files <count> items <count> [span <name>]`
 * `SPACE_SYMBOL` stands for a space in the scope name.
 */
internal class SelectTodoScopeCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: @NonNls String = "${CMD_PREFIX}todoSelectScope"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val arguments = extractCommandArgument(PREFIX).split(' ').filter { it.isNotBlank() }
    val name = arguments.firstOrNull()?.replace("SPACE_SYMBOL", " ") ?: error("Missing scope name")
    val files = arguments.argument("files")
    val items = arguments.argument("items")

    withContext(Dispatchers.EDT) {
      val panel = showTodoTab(context.project, TodoTab.SCOPE)
      val (combo, scope) = withTimeoutOrNull(TODO_READY_TIMEOUT) {
        var found = findScope(panel, name)
        while (found == null) {
          delay(50.milliseconds)
          found = findScope(panel, name)
        }
        found
      } ?: error("The scope chooser has no scope '$name'; scopes=${scopes(panel).map { it.second.displayName }}")
      val tree = panel.todoTree
      val description = "scope '$name': $items items in $files files"
      val action = { combo.selectedItem = scope }
      val isReady = { tree.hasTodoResults(files, items, files) }
      if ("span" in arguments) {
        measureTodoTree(tree, description, arguments.textArgument("span"), files, items, action = action, isReady = isReady)
      }
      else {
        awaitTodoTree(tree, description, action = action) { tree.hasTodoSummary(files, items) }
        awaitTodoTree(tree, description, action = { tree.expandRow(0) }, isReady = isReady)
      }
    }
  }

  private fun findScope(panel: TodoPanel, name: String): Pair<JComboBox<*>, ScopeDescriptor>? =
    scopes(panel).firstOrNull { it.second.displayName == name }

  private fun scopes(panel: TodoPanel): List<Pair<JComboBox<*>, ScopeDescriptor>> =
    UIUtil.findComponentsOfType(panel, JComboBox::class.java).flatMap { combo ->
      (0 until combo.itemCount).mapNotNull { (combo.getItemAt(it) as? ScopeDescriptor)?.let { scope -> combo to scope } }
    }

  override fun getName(): String = "todoSelectScope"
}
