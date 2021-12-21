@file:JvmName("PydevConsoleCommunicationUtil")

// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.console

import com.intellij.xdebugger.frame.XValueChildrenList
import com.jetbrains.python.console.protocol.DebugValue
import com.jetbrains.python.console.protocol.GetArrayResponse
import com.jetbrains.python.debugger.ArrayChunk
import com.jetbrains.python.debugger.ArrayChunkBuilder
import com.jetbrains.python.debugger.PyDebugValue
import com.jetbrains.python.debugger.PyFrameAccessor
import com.jetbrains.python.debugger.pydev.GetVariableCommand

fun parseVars(vars: List<DebugValue>, parent: PyDebugValue?, frameAccessor: PyFrameAccessor): XValueChildrenList {
  val list = XValueChildrenList(vars.size)
  for (debugValue in vars) {
    val pyDebugValue = createPyDebugValue(debugValue, frameAccessor)
    if (parent != null) {
      pyDebugValue.parent = parent
    }
    list.add(pyDebugValue.visibleName, pyDebugValue)
  }
  return list
}

fun createPyDebugValue(value: DebugValue, frameAccessor: PyFrameAccessor) =
  PyDebugValue(value.name, value.type, value.qualifier, value.value ?: "",
               value.isContainer, value.shape, value.isReturnedValue, value.isIPythonHidden, value.isErrorOnEval,
               value.typeRendererId, frameAccessor)

fun createArrayChunk(response: GetArrayResponse, frameAccessor: PyFrameAccessor): ArrayChunk {
  val result = ArrayChunkBuilder()

  // `parseArrayValues()`

  result.setSlicePresentation(response.slice)
  result.setRows(response.rows)
  result.setColumns(response.cols)
  result.setFormat(response.format)
  result.setType(response.type)
  result.setMax(response.max)
  result.setMin(response.min)
  result.setValue(PyDebugValue(response.slice, null, null, null, false, null, false, false, false, null, frameAccessor))

  // `parseArrayHeaderData()`

  val rowHeaders = arrayListOf<String>()
  val colHeaders = arrayListOf<ArrayChunk.ColHeader>()

  response.headers?.colHeaders?.let {
    for (colHeader in it) {
      colHeaders.add(ArrayChunk.ColHeader(colHeader.label, colHeader.type, colHeader.format, colHeader.max, colHeader.min))
    }
  }

  response.headers?.rowHeaders?.let {
    for (rowHeader in it) {
      rowHeaders.add(rowHeader.label)
    }
  }

  result.setColHeaders(colHeaders);
  result.setRowLabels(rowHeaders);

  // `parseArrayValues()`

  val responseData = response.data.data

  val data = arrayOfNulls<Array<Any?>>(responseData.size)
  for ((rowIndex, responseRowArray: List<String>) in responseData.withIndex()) {
    val rowValues = arrayOfNulls<Any>(responseRowArray.size)
    for ((colIndex, responseColArray: String) in responseRowArray.withIndex()) {
      rowValues[colIndex] = responseColArray
    }
    data[rowIndex] = rowValues
  }

  result.setData(data)

  return result.createArrayChunk()
}