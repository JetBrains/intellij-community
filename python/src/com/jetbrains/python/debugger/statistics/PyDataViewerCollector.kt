// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId1
import com.intellij.internal.statistic.eventLog.events.RoundedIntEventField
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project

object PyDataViewerCollector : CounterUsagesCollector() {

  private val GROUP = EventLogGroup("python.dataview", 4)

  /* Fields */
  private val DATA_TYPE_FIELD = EventFields.Enum<DataType>("type")
  private val DIMENSIONS_FIELD = EventFields.Enum<DataDimensions>("dimensions")
  private val ROWS_COUNT_FIELD = RoundedIntEventField("rows_count")
  private val COLUMNS_COUNT_FIELD = RoundedIntEventField("columns_count")
  private val IS_NEW_TABLE_FIELD = EventFields.Boolean("is_new_table")

  /* Events */
  private val DATA_OPENED_EVENT = GROUP.registerVarargEvent("data.opened",
                                                            DATA_TYPE_FIELD,
                                                            DIMENSIONS_FIELD,
                                                            ROWS_COUNT_FIELD,
                                                            COLUMNS_COUNT_FIELD,
                                                            IS_NEW_TABLE_FIELD)
  val SLICING_APPLIED_EVENT: EventId1<Boolean> = GROUP.registerEvent("slicing.applied", IS_NEW_TABLE_FIELD)
  val FORMATTING_APPLIED_EVENT: EventId1<Boolean> = GROUP.registerEvent("formatting.applied", IS_NEW_TABLE_FIELD)

  enum class DataType(private val typeName: String?) {
    ARRAY("ndarray"),
    DATAFRAME("DataFrame"),
    GEO_DATAFRAME("GeoDataFrame"),
    SERIES("Series"),
    GEO_SERIES("GeoSeries"),
    EAGER_TENSOR("EagerTensor"),
    RESOURCE_VARIABLE("ResourceVariable"),
    SPARSE_TENSOR("SparseTensor"),
    TORCH_TENSOR("Tensor"),
    HF_DATASET("Dataset"),
    UNKNOWN(null);

    companion object {
      fun getDataType(typeName: String?): DataType {
        return DataType.entries.firstOrNull { it.typeName == typeName } ?: UNKNOWN
      }
    }
  }

  enum class DataDimensions {
    ONE,
    TWO,
    THREE,
    MULTIPLE,
    UNKNOWN;

    companion object {
      fun getDataDimensions(dimensions: Int?): DataDimensions {
        return when (dimensions) {
          null -> UNKNOWN
          1 -> ONE
          2 -> TWO
          3 -> THREE
          else -> MULTIPLE
        }
      }
    }
  }

  override fun getGroup(): EventLogGroup = GROUP

  fun logDataOpened(
    project: Project?,
    type: String?,
    dimensions: Int?,
    rowsCount: Int,
    columnsCount: Int,
    isNewTable: Boolean,
  ) {
    DATA_OPENED_EVENT.log(project,
                          DATA_TYPE_FIELD.with(DataType.getDataType(type)),
                          DIMENSIONS_FIELD.with(DataDimensions.getDataDimensions(dimensions)),
                          ROWS_COUNT_FIELD.with(rowsCount),
                          COLUMNS_COUNT_FIELD.with(columnsCount),
                          IS_NEW_TABLE_FIELD.with(isNewTable))
  }

  fun logDataSlicingApplied(isNewTable: Boolean) {
    SLICING_APPLIED_EVENT.log(isNewTable)
  }

  fun logDataFormattingApplied(isNewTable: Boolean) {
    FORMATTING_APPLIED_EVENT.log(isNewTable)
  }
}