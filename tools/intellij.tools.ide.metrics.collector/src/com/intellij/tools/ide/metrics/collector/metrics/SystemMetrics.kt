package com.intellij.tools.ide.metrics.collector.metrics

import java.util.*

data class MetricData<T : Number>(val name: String, val data: DataPoint<T>)

data class DataPoint<T : Number>(val value: T, val measurementTime: Date)

data class MetricGroup(val name: String, val metrics: Map<String, LinkedList<DataPoint<*>>>)