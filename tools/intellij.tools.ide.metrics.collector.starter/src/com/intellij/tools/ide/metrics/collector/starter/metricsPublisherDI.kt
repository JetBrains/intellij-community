package com.intellij.tools.ide.metrics.collector.starter

import com.intellij.tools.ide.metrics.collector.starter.publishing.DefaultMetricsPublisher
import com.intellij.tools.ide.metrics.collector.starter.publishing.MetricsPublisher
import org.kodein.di.DI
import org.kodein.di.bindProvider

val metricsPublisherDI by DI.Module {
  bindProvider<MetricsPublisher<*>>() { DefaultMetricsPublisher() }
}