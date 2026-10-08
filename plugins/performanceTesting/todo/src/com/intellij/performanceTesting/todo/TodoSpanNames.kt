// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.performanceTesting.todo

/** The metrics recorded by the TODO commands and published by the tests. */
object TodoSpanNames {
  const val OPEN: String = "todo.open.treeReady"
  const val ADD: String = "todo.edit.add.treeReady"
  const val CHANGE: String = "todo.edit.change.itemShown"
  const val REMOVE: String = "todo.edit.remove.treeReady"
  const val FILTER_APPLY: String = "todo.filter.apply.treeReady"
  const val FILTER_CLEAR: String = "todo.filter.clear.treeReady"
  const val SCOPE_NARROW: String = "todo.scope.narrow.treeReady"
  const val SCOPE_PROJECT: String = "todo.scope.project.treeReady"
  const val PREVIEW_ENABLE: String = "todo.preview.enable"
  const val PREVIEW_SELECT_SIZED: String = "todo.preview.select.sized500"
  const val PREVIEW_SELECT_TODO: String = "todo.preview.select.todo"
  const val PREVIEW_SELECT_MULTILINE: String = "todo.preview.select.multiline"
  const val PREVIEW_SELECT_LAST: String = "todo.preview.select.lastItem"
  const val NAVIGATE_NEXT: String = "todo.navigate.next"
  const val NAVIGATE_PREVIOUS: String = "todo.navigate.previous"
  const val NAVIGATE_NEXT_FILE: String = "todo.navigate.nextFile"
  const val NAVIGATE_PREVIOUS_FILE: String = "todo.navigate.previousFile"

  /** The switch of the Current File tab to a file with [items] items. Each size gets its own chart. */
  fun currentFileSwitch(items: Int): String = "todo.currentFile.switch.items$items"
}
