package com.jetbrains.python.tables

import com.intellij.openapi.util.IntellijInternalApi

@IntellijInternalApi
enum class TableCommandType {
  DF_INFO,
  SLICE,
  SLICE_CSV,
  DF_DESCRIBE,
  VISUALIZATION_DATA,
  IMAGE_START_CHUNK_LOAD,
  IMAGE_CHUNK_LOAD
}
