package com.intellij.tools.ide.metrics.collector.telemetry

import java.util.function.Predicate

class SpanFilter internal constructor(
  @JvmField internal val filter: (SpanElement) -> Boolean,
  @JvmField internal val rawFilter: Predicate<SpanData>,
) {
  companion object {
    fun any(): SpanFilter {
      return SpanFilter(filter = { true }, rawFilter = { true })
    }

    fun none(): SpanFilter {
      return SpanFilter(filter = { false }, rawFilter = { false })
    }

    fun nameEquals(name: String): SpanFilter {
      return SpanFilter(filter = { spanData -> spanData.name == name }, rawFilter = { it.operationName == name })
    }

    fun nameInList(names: List<String>): SpanFilter {
      return SpanFilter(filter = { names.contains(it.name) }, rawFilter = { names.contains(it.operationName) })
    }

    fun nameInList(vararg names: String): SpanFilter {
      return nameInList(names.toList())
    }

    fun nameContainsAny(names: List<String>): SpanFilter {
      return SpanFilter(filter = { span -> names.any { span.name.contains(it) } }, rawFilter = { span -> names.any { span.operationName.contains(it) } })
    }

    fun nameContainsAny(vararg names: String): SpanFilter {
      return nameContainsAny(names.toList())
    }


    fun nameContains(substring: String): SpanFilter {
      return SpanFilter(filter = { it.name.contains(substring) }, rawFilter = { it.operationName.contains(substring) })
    }

    fun hasTags(vararg tags: Pair<String, String>): SpanFilter {
      return SpanFilter(
        filter = {
          tags.all { tag -> it.tags.contains(tag) }
        },
        rawFilter = {
          tags.all { tag -> it.tags.any { it.key == tag.first && it.value == tag.second } }
        },
      )
    }
  }
}