package com.intellij.tools.ide.metrics.collector.starter.publishing

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.tools.ide.metrics.collector.metrics.MetricsSelectionStrategy
import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics
import com.intellij.tools.ide.metrics.collector.starter.collector.ProvidedMetricsCollector
import com.intellij.tools.ide.metrics.collector.starter.collector.StarterMetricsCollector
import com.intellij.tools.ide.metrics.collector.starter.collector.StarterTelemetryJsonMeterCollector
import com.intellij.tools.ide.metrics.collector.starter.collector.StarterTelemetrySpanCollector
import com.intellij.tools.ide.metrics.collector.telemetry.SpanFilter
import com.intellij.tools.ide.util.common.PrintFailuresMode
import com.intellij.tools.ide.util.common.withRetryBlocking
import io.opentelemetry.sdk.metrics.data.MetricData
import org.kodein.di.direct
import org.kodein.di.provider

/**
 * Aggregates metrics from different collectors of [StarterMetricsCollector].
 * Publishes metrics with custom publishing logic.
 * Can compare metrics if needed during publishing.
 *
 * Start usage with [MetricsPublisher.newInstance]
 *
 * OpenTelemetry span collector [com.intellij.tools.ide.metrics.collector.starter.collector.StarterTelemetrySpanCollector].
 *
 * OpenTelemetry meter collector [com.intellij.tools.ide.metrics.collector.starter.collector.StarterTelemetryJsonMeterCollector].
 *
 * Example of a custom collector [com.intellij.tools.ide.metrics.collector.starter.collector.ProvidedMetricsCollector].
 *
 * Note:
 * MetricsPublisher implementation can be overridden via DI.
 */
abstract class MetricsPublisher<T> {
  companion object {
    /** Return a new instance of metric publisher.
     * That is by design since tests may need their own metrics publishing configuration.
     */
    val newInstance: MetricsPublisher<*>
      get() {
        try {
          return di.direct.provider<MetricsPublisher<*>>().invoke()
        }
        catch (e: Throwable) {
          throw IllegalStateException("No metrics publishers were registered in Starter DI")
        }
      }
  }

  private var configuration: T.(IDEStartResult) -> Unit = {}

  protected val metricsCollectors: MutableList<StarterMetricsCollector> = mutableListOf()
  protected abstract val publishAction: (IDEStartResult, List<PerformanceMetrics.Metric>) -> Unit

  /** Will be invoked before publishing */
  fun configure(config: T.(IDEStartResult) -> Unit): MetricsPublisher<T> {
    configuration = config
    return this
  }

  fun addMetricsCollector(vararg collectors: StarterMetricsCollector): MetricsPublisher<T> {
    metricsCollectors.addAll(collectors)
    return this
  }

  fun collectMetrics(runContext: IDERunContext): List<PerformanceMetrics.Metric> = metricsCollectors.flatMap {
    withRetryBlocking(
      messageOnFailure = "Failure on metrics collection",
      printFailuresMode = PrintFailuresMode.ONLY_LAST_FAILURE,
    ) { it.collect(runContext) }
    ?: throw RuntimeException("Couldn't collect metrics from collector ${it::class.simpleName}")
  }

  fun collectMetrics(ideStartResult: IDEStartResult): List<PerformanceMetrics.Metric> {
    configuration(this as T, ideStartResult)
    return collectMetrics(ideStartResult.runContext)
  }

  /** Collect metrics from all registered collectors and publish them */
  fun publish(ideStartResult: IDEStartResult): MetricsPublisher<T> {
    publishAction(ideStartResult, collectMetrics(ideStartResult))
    return this
  }
}

/** Return a new instance of metric publisher. Method is just for the sake of better discoverability of MetricsPublisher. */
val IDEStartResult.newMetricsPublisher: MetricsPublisher<*>
  get() = MetricsPublisher.newInstance

/**
 * Shortcut for creating [StarterTelemetrySpanCollector], adding it as a collector, and publishing the collected metrics.
 * @see [com.intellij.tools.ide.metrics.collector.starter.publishing.MetricsPublisher.publish]
 */
fun MetricsPublisher<*>.publishOnlySpans(
  ideStartResult: IDEStartResult,
  spanFilter: SpanFilter,
  spanAliases: Map<String, String> = mapOf(),
): MetricsPublisher<*> =
  this.addSpanCollector(spanFilter, spanAliases = spanAliases).publish(ideStartResult)

/**
 * Shortcut for creating [StarterTelemetryJsonMeterCollector], adding it as a collector, and publishing the collected metrics
 * @see [com.intellij.tools.ide.metrics.collector.starter.publishing.MetricsPublisher.publish]
 */
fun MetricsPublisher<*>.publishOnlyMeters(
  ideStartResult: IDEStartResult,
  metricsSelectionStrategy: MetricsSelectionStrategy,
  meterFilter: (MetricData) -> Boolean,
): MetricsPublisher<*> =
  this.addMeterCollector(metricsSelectionStrategy, meterFilter).publish(ideStartResult)

/** Shortcut for adding a new span collector */
fun MetricsPublisher<*>.addSpanCollector(
  spanFilter: SpanFilter,
  spanAliases: Map<String, String> = mapOf(),
): MetricsPublisher<*> = this.addMetricsCollector(StarterTelemetrySpanCollector(spanFilter, spanAliases = spanAliases))

/** Shortcut for adding a new meter collector */
fun MetricsPublisher<*>.addMeterCollector(
  metricsSelectionStrategy: MetricsSelectionStrategy,
  meterFilter: (MetricData) -> Boolean,
): MetricsPublisher<*> = this.addMetricsCollector(StarterTelemetryJsonMeterCollector(metricsSelectionStrategy, meterFilter))

/** Shortcut for adding a new [ProvidedMetricsCollector] collector */
fun MetricsPublisher<*>.addProvidedMetricsCollector(metrics: List<PerformanceMetrics.Metric>): MetricsPublisher<*> =
  this.addMetricsCollector(ProvidedMetricsCollector(metrics))

/**
 * Shortcut for creating [ProvidedMetricsCollector], adding it as a collector, and publishing the collected metrics.
 * @see [com.intellij.tools.ide.metrics.collector.starter.publishing.MetricsPublisher.publish]
 */
fun MetricsPublisher<*>.publishOnlyProvidedMetrics(ideStartResult: IDEStartResult, metrics: List<PerformanceMetrics.Metric>): MetricsPublisher<*> =
  this.addProvidedMetricsCollector(metrics).publish(ideStartResult)