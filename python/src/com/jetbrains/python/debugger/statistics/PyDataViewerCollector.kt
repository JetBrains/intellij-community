// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.RoundedIntEventField
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project

object PyDataViewerCollector : CounterUsagesCollector() {

  private val GROUP = EventLogGroup("python.dataview", 2)

  enum class DataType(val typeName: String?) {
    ARRAY("ndarray"),
    DATAFRAME("DataFrame"),
    GEO_DATAFRAME("GeoDataFrame"),
    SERIES("Series"),
    GEO_SERIES("GeoSeries"),
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

  private val typeField = EventFields.Enum<DataType>("type")
  private val dimensionsField = EventFields.Enum<DataDimensions>("dimensions")
  private val rowsCountField = RoundedIntEventField("rows_count")
  private val columnsCountField = RoundedIntEventField("columns_count")

  val dataOpened = GROUP.registerVarargEvent("data.opened", typeField, dimensionsField, rowsCountField, columnsCountField)

  val slicingApplied = GROUP.registerEvent("slicing.applied")

  override fun getGroup() = GROUP

  fun logDataOpened(project: Project?, type: String?, dimensions: Int?, rowsCount: Int, columnsCount: Int) {
    dataOpened.log(project, this.typeField.with(DataType.getDataType(type)),
                   this.dimensionsField.with(DataDimensions.getDataDimensions(dimensions)),
                   this.rowsCountField.with(rowsCount),
                   this.columnsCountField.with(columnsCount))
  }
}