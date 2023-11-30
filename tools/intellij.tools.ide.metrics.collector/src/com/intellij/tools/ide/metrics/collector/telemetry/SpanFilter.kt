package com.intellij.tools.ide.metrics.collector.telemetry

class SpanFilter(val filter: (SpanElement) -> Boolean) {
  companion object {
    fun equals(name: String) = SpanFilter { it.name == name }
    fun containsIn(names: List<String>) = SpanFilter { it.name in names }
    fun contain(substring: String) = SpanFilter { it.name.contains(substring) }
  }
}