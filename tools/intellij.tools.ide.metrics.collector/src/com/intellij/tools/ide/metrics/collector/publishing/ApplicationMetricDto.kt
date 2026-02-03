package com.intellij.tools.ide.metrics.collector.publishing

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class ApplicationMetricDto<T : Number>(
  /**
   * Metric name.
   */
  val n: String,
  /**
   * Used for "duration" metrics.
   */
  val d: T? = null,
  /**
   * Used for "counter" metrics.
   */
  val c: T? = null,

  val v: T? = d ?: c
) {
  init {
    require((d != null) xor (c != null))
  }
}