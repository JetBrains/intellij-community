package com.intellij.tools.ide.metrics.collector.metrics

fun String.toDurationMetricId(): PerformanceMetrics.MetricId.Duration = PerformanceMetrics.MetricId.Duration(this)

fun String.toCounterMetricId(): PerformanceMetrics.MetricId.Counter = PerformanceMetrics.MetricId.Counter(this)

fun String.toPerformanceMetricCounter(counter: Long): PerformanceMetrics.Metric =
  PerformanceMetrics.Metric(id = this.toCounterMetricId(), value = counter)

fun String.toPerformanceMetricDuration(durationMs: Long): PerformanceMetrics.Metric =
  PerformanceMetrics.Metric(id = this.toDurationMetricId(), value = durationMs)