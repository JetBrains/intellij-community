// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.RoundedIntEventField
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

object PyDataViewerCollector : CounterUsagesCollector() {

  private val GROUP = EventLogGroup("python.dataview", 1)

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

  val dataOpened = GROUP.registerEvent("data.opened",
                                       EventFields.Enum<DataType>("type"),
                                       RoundedIntEventField("rows_count"),
                                       RoundedIntEventField("columns_count"))

  val slicingApplied = GROUP.registerEvent("slicing.applied")

  override fun getGroup() = GROUP

  fun logDataOpened(type: String?, rowCount: Int, colCount: Int) {
    dataOpened.log(DataType.getDataType(type), rowCount, colCount)
  }
}