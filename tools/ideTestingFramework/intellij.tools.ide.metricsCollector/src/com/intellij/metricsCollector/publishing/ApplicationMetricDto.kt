package com.intellij.metricsCollector.publishing

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class ApplicationMetricDto(
  /**
   * Metric name.
   */
  val n: String,
  /**
   * Used for "duration" metrics.
   */
  val d: Long? = null,
  /**
   * Used for "counter" metrics.
   */
  val c: Long? = null,

  val v: Long? = d ?: c
) {
  init {
    require((d != null) xor (c != null))
  }
}