// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.values

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.NlsSafe
import com.jetbrains.python.debugger.PyDebugValue
import org.jetbrains.annotations.ApiStatus
import java.lang.invoke.MethodHandles

/**
 *
 * This function extracts children-columns of the current sub-table.
 * @param treeColumns - DataFrame columns in a tree structure
 * @param columnsBefore - list of columns-nodes (path to the current node)
 * @return set of children of an exact node
 *
 * Example: df.foo.bar.ba<caret>, columnsBefore = ["foo", "bar"]
 */
fun completeDataFrameColumns(treeColumns: DataFrameDebugValue.ColumnNode, columnsBefore: List<String?>): Set<String>? {
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

private val LOG = Logger.getInstance(MethodHandles.lookup().lookupClass())
private val MULTI_INDEX_DATA_REGEX = Regex("\\[(\\(.*?(?=\\)).*?)+]")
private val MULTI_INDEX_COLUMN_GROUP_REGEX = Regex("\\((.*?(?=\\).*?))\\)")
private val COLUMN_NAMES_REGEX = Regex("'(.*?(?<!\\\\))'|\"(.*?(?<!\\\\))\"")

/**
 * Retrieves column names and index data from [PyDebugValue.getValue].
 *
 * The [value] is produced by _get_df_variable_repr.
 */
@ApiStatus.Internal
@Throws(CannotRetrieveColumnDataException::class)
fun getInformationColumns(value: @NlsSafe String): DataFrameDebugValue.InformationColumns? {
  val columnsNames = getDataWithColumnsNames(value) ?: throw CannotRetrieveColumnDataException("Can't retrieve column data")

  getMultiIndexData(columnsNames)?.let { return it }

  val columns = COLUMN_NAMES_REGEX.findAll(columnsNames).map { listOf(it.groups.filterNotNull().last().value) }.toList()

  return if (columns.isNotEmpty()) {
    DataFrameDebugValue.InformationColumns().apply {
      this.isMultiIndex = false
      this.columns = columns
    }
  }
  else {
    null
  }
}

private fun getDataWithColumnsNames(value: String): String? {
  var escaped = false
  var inSingleQuotes = false
  var inDoubleQuotes = false

  val index = value.indexOfFirst { char ->
    inSingleQuotes = inSingleQuotes.xor(char == '\'' && !escaped) && !inDoubleQuotes
    inDoubleQuotes = inDoubleQuotes.xor(char == '"' && !escaped) && !inSingleQuotes
    escaped = !escaped && char == '\\'

    !inSingleQuotes && !inDoubleQuotes && char == ']'
  }

  return when {
    index != -1 -> value.substring(0, index + 1)
    value.endsWith("...") -> value.substring(0, value.length - 3)
    else -> null
  }
}

private fun getMultiIndexData(columnData: @NlsSafe String): DataFrameDebugValue.InformationColumns? {
  MULTI_INDEX_DATA_REGEX.find(columnData)?.groups?.get(0)?.value ?: return null
  val columns = MULTI_INDEX_COLUMN_GROUP_REGEX.findAll(columnData).map { result ->
    COLUMN_NAMES_REGEX.findAll(result.value).map { it.groups.filterNotNull().last().value }.toList()
  }.toList()

  return if (columns.isNotEmpty()) {
    DataFrameDebugValue.InformationColumns().apply {
      this.isMultiIndex = true
      this.columns = columns
    }
  }
  else {
    null
  }
}

class CannotRetrieveColumnDataException(message: String): Exception(message)
