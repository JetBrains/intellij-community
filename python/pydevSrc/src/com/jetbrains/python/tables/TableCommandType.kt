package com.jetbrains.python.tables

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
enum class TableCommandType {
  DF_INFO,
  SLICE,
  SLICE_CSV,
  DF_DESCRIBE,
  VISUALIZATION_DATA,
  IMAGE_START_CHUNK_LOAD,
  IMAGE_CHUNK_LOAD,
  INSPECTIONS
}
