package com.intellij.tools.ide.metrics.collector.telemetry

class SpanFilter(val filter: (SpanElement) -> Boolean) {
  companion object {
    fun nameEquals(name: String) = SpanFilter { it.name == name }
    fun containsNameIn(names: List<String>) = SpanFilter { it.name in names }
    fun containsNameIn(vararg names: String) = SpanFilter { it.name in names }
    fun nameContains(substring: String) = SpanFilter { it.name.contains(substring) }
  }
}