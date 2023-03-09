// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.values


/**
 *
 * This function extracts children-columns of current sub-table.
 * @param treeColumns - DataFrame columns in a tree structure
 * @param columnsBefore - list of columns-nodes (path to the current node)
 * @return set of children of an exact node
 *
 * Example: df.foo.bar.ba<caret>, columnsBefore = ["foo", "bar"]
 */
fun completePandasDataFrameColumns(treeColumns: DataFrameDebugValue.ColumnNode, columnsBefore: List<String?>): Set<String>? {
  return if (columnsBefore.isNotEmpty()) {
    var node: DataFrameDebugValue.ColumnNode? = treeColumns.children?.get(columnsBefore[0]) ?: return emptySet()
    for (i in 1 until columnsBefore.size) {
      node = node?.getChildIfExist(columnsBefore[i]!!)
      if (node == null) {
        return emptySet()
      }
    }
    node?.childrenName
  }
  else {
    treeColumns.childrenName
  }
}