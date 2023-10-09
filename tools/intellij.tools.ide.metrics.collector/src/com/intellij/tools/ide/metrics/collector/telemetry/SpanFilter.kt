package com.intellij.tools.ide.metrics.collector.telemetry

class SpanFilter(val filter: (String) -> Boolean) {
  companion object {
    fun equals(name: String) = SpanFilter { it == name }
    fun containsIn(names: List<String>) = SpanFilter { it in names }
    fun contain(substring: String) = SpanFilter { it.contains(substring) }
  }
}