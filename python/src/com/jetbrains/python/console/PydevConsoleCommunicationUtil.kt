@file:JvmName("PydevConsoleCommunicationUtil")

// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.console

import com.intellij.xdebugger.frame.XValueChildrenList
import com.jetbrains.python.console.completion.collectParentReferences
import com.jetbrains.python.console.protocol.DebugValue
import com.jetbrains.python.console.protocol.GetArrayResponse
import com.jetbrains.python.debugger.ArrayChunk
import com.jetbrains.python.debugger.ArrayChunkBuilder
import com.jetbrains.python.debugger.PyDebugValue
import com.jetbrains.python.debugger.PyFrameAccessor
import com.jetbrains.python.debugger.values.DataFrameDebugValue
import kotlinx.serialization.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder.Companion.DECODE_DONE
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.json.Json
import java.util.*

@OptIn(ExperimentalSerializationApi::class)
@Serializer(forClass = DataFrameDebugValue.InformationColumns::class)
object InformationColumnsSerializer : DeserializationStrategy<DataFrameDebugValue.InformationColumns> {
  override val descriptor: SerialDescriptor = buildClassSerialDescriptor("InformationColumns") {
    element<Boolean>("isMultiIndex")
    element<List<List<String>>?>("columns")
  }

  override fun deserialize(decoder: Decoder): DataFrameDebugValue.InformationColumns {
    return decoder.decodeStructure(descriptor) {
      var isMultiIndex = false
      var columns: List<List<String>>? = null

      while (true) {
        when (val index = decodeElementIndex(descriptor)) {
          DECODE_DONE -> break
          0 -> isMultiIndex = decodeBooleanElement(descriptor, 0)
          1 -> columns = decodeNullableSerializableElement(descriptor, 1, ListSerializer(ListSerializer(String.serializer())).nullable)
          else -> throw SerializationException("Unexpected index $index")
        }
      }
      DataFrameDebugValue.InformationColumns().apply {
        this.isMultiIndex = isMultiIndex
        this.columns = columns
      }
    }
  }
}

private fun parseDebugValue(value: String): DataFrameDebugValue.InformationColumns? {

  try {
    return Json.decodeFromString(InformationColumnsSerializer, value)
  }
  catch (_: SerializationException) {
    return null
  }
  catch (_: IllegalArgumentException) {
    return null
  }
}

private fun extractDataFrameColumns(dfReference: String,
                                    frameAccessor: PydevConsoleCommunication): DataFrameDebugValue.InformationColumns? {
  val additionalInformation = frameAccessor.evaluate(DataFrameDebugValue.commandExtractPandasColumns(dfReference, true), true, true)
  return when (additionalInformation.type) {
    "str" -> additionalInformation.value?.let { parseDebugValue(it) }
    else -> null
  }
}

fun parseVars(vars: List<DebugValue>, parent: PyDebugValue?, frameAccessor: PyFrameAccessor): XValueChildrenList {
  val list = XValueChildrenList(vars.size)
  for (debugValue in vars) {
    val pyDebugValue = createPyDebugValue(debugValue, frameAccessor)
    if (frameAccessor is PydevConsoleCommunication && parent !is DataFrameDebugValue && pyDebugValue is DataFrameDebugValue) {
      val dfReference = collectParentReferences(parent, pyDebugValue)
      val columns = extractDataFrameColumns(dfReference, frameAccessor)
      columns?.let {
        pyDebugValue.setColumns(it)
      }
    }
    if (parent != null) {
      pyDebugValue.parent = parent
    }
    list.add(pyDebugValue.visibleName, pyDebugValue)
  }
  return list
}

fun createPyDebugValue(value: DebugValue, frameAccessor: PyFrameAccessor): PyDebugValue {
  return if (value.type == "DataFrame") {
    DataFrameDebugValue(value.name, value.type, value.qualifier, value.value ?: "",
                        value.isContainer, value.shape, value.isReturnedValue, value.isIPythonHidden, value.isErrorOnEval,
                        value.typeRendererId, frameAccessor)
  }
  else PyDebugValue(value.name, value.type, value.qualifier, value.value ?: "",
                    value.isContainer, value.shape, value.isReturnedValue, value.isIPythonHidden, value.isErrorOnEval,
                    value.typeRendererId, frameAccessor)
}

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

  result.setColHeaders(colHeaders)
  result.setRowLabels(rowHeaders)

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