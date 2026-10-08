// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.performanceTesting.todo

/** The metrics recorded by the TODO commands and published by the tests. */
object TodoSpanNames {
  const val OPEN: String = "todo.open.treeReady"
  const val ADD: String = "todo.edit.add.treeReady"
  const val CHANGE: String = "todo.edit.change.itemShown"
  const val REMOVE: String = "todo.edit.remove.treeReady"

  /** The switch of the Current File tab to a file with [items] items. Each size gets its own chart. */
  fun currentFileSwitch(items: Int): String = "todo.currentFile.switch.items$items"
}
