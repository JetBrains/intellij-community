// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.resolve

import com.intellij.debugger.streams.trace.TraceElement

/**
 * @author Vitaliy.Bibaev
 */
class IntervalMapResolver : PartialReductionResolverBase() {
  override fun buildResult(mapping: Map<TraceElement, List<TraceElement>>): ValuesOrderResolver.Result {
    val direct = mutableMapOf<TraceElement, MutableList<TraceElement>>()
    val reverse = mutableMapOf<TraceElement, MutableList<TraceElement>>()

    for (valueAfter in mapping.keys.sortedBy { it.time }) {
      val valuesBefore = mapping[valueAfter]!!.sortedBy { it.time }
      val reverseMapping = mutableListOf<TraceElement>()
      if (valuesBefore.isNotEmpty()) {
        direct[valuesBefore.first()] = mutableListOf(valueAfter)
        reverseMapping += valuesBefore.first()
        if (valuesBefore.size > 1) {
          direct[valuesBefore.last()] = mutableListOf(valueAfter)
          reverseMapping += valuesBefore.last()
        }
      }

      reverse[valueAfter] = reverseMapping
    }

    return ValuesOrderResolver.Result.of(direct, reverse)
  }
}