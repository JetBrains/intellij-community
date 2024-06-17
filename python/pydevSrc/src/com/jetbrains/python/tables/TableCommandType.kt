package com.jetbrains.python.tables

import com.intellij.openapi.util.IntellijInternalApi

@IntellijInternalApi
enum class TableCommandType {
  DF_INFO,
  SLICE,
  DF_DESCRIBE,
  VISUALIZATION_DATA
}
