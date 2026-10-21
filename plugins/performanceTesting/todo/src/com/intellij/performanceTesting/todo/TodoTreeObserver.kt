// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.performanceTesting.todo

import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.tree.TreeModelAdapter
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.swing.JTree

/**
 * Runs [action] and checks [isReady] after tree model changes and caret moves.
 * Caret events also signal preview updates that leave the tree model unchanged.
 */
internal suspend fun awaitTodoTree(
  tree: JTree,
  description: String,
  action: suspend () -> Unit = {},
  isReady: () -> Boolean,
): Unit =
  withContext(Dispatchers.EDT) {
    val changes = Channel<Unit>(Channel.CONFLATED)
    val listener = TreeModelAdapter.create { _, _ -> changes.trySend(Unit) }
    val model = tree.model
    val disposable = Disposer.newDisposable()
    model.addTreeModelListener(listener)
    EditorFactory.getInstance().eventMulticaster.addCaretListener(object : CaretListener {
      override fun caretPositionChanged(event: CaretEvent) {
        changes.trySend(Unit)
      }
    }, disposable)
    try {
      withTimeoutOrNull(TODO_READY_TIMEOUT) {
        action()
        while (!isReady()) changes.receive()
      } ?: error("Timed out waiting for $description; summary=${tree.todoSummaryText()}, " +
                 "rows=${tree.rowCount}, firstRowExpanded=${tree.isExpanded(0)}")
    }
    finally {
      model.removeTreeModelListener(listener)
      Disposer.dispose(disposable)
    }
  }

/** Waits as [awaitTodoTree] does and records the time from the start of [action] to the ready state as the span [spanName]. */
internal suspend fun measureTodoTree(
  tree: JTree,
  description: String,
  spanName: String,
  files: Int? = null,
  items: Int? = null,
  action: suspend () -> Unit,
  isReady: () -> Boolean,
) {
  lateinit var span: Span
  awaitTodoTree(tree, description, action = {
    check(!isReady()) { "The result is already ready before the action: $description" }
    span = startTodoSpan(spanName, files, items)
    action()
  }, isReady)
  span.end()
}
